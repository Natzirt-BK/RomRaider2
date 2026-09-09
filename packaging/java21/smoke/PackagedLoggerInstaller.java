/* Synthetic-only diagnostic entry point; run only in a disposable packaged image. */
package com.romraider2.smoke;

import com.romraider.Settings;
import com.romraider.logger.ecu.EcuLogger;
import com.romraider.logger.ecu.ui.swing.menubar.action.InstallLoggerDefinitionAction;
import com.romraider.util.SettingsManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Vector;
import javax.swing.*;

/** Drives real chooser/confirmation dialogs; never starts a connection. */
public final class PackagedLoggerInstaller {
    private static Path root, original, candidate, invalid, managed;
    private static EcuLogger logger;
    private static InstallLoggerDefinitionAction action;
    private static Settings settings;
    private static volatile int scenario, phase;
    private static boolean closeScenario, closeDuringWorker, nativePicker;
    private static volatile boolean expectCleanExit;
    private static long deadline;
    private static final String[] cases = {"chooser cancel", "confirmation cancel", "stale confirmation", "invalid definition", "successful installation"};

    public static void main(String[] args) throws Exception {
        root = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(root);
        closeScenario = args.length > 1 && args[1].contains("close");
        closeDuringWorker = args.length > 1 && args[1].contains("worker");
        nativePicker = args.length > 1 && args[1].contains("native");
        if (nativePicker && !"1".equals(System.getenv("RR2_PRIVATE_WINDOW_TEST")))
            throw new IllegalStateException("Native picker automation requires run-desktop-window-tests.sh");
        System.setProperty("romraider2.nativeFileDialogs", Boolean.toString(nativePicker));
        System.setProperty("user.home", root.toString());
        System.setProperty("romraider2.settings.dir", root.resolve("settings").toString());
        System.setProperty("romraider2.log.dir", root.resolve("logs").toString());
        System.setProperty("romraider2.plugins.dir", Files.createDirectories(root.resolve("plugins")).toString());
        System.setProperty("romraider2.displayAwake.disabled", "true");
        System.setProperty("romraider2.j2534.library", "");
        original = definition("original");
        candidate = definition("candidate");
        invalid = root.resolve("invalid.xml");
        Files.writeString(invalid, "<?xml version=\"1.0\"?><settings><description>This is not a logger definition</description></settings>");
        managed = root.resolve("settings/definitions/logger/logger.xml");
        settings = SettingsManager.getSettings();
        settings.setAutoConnectOnStartup(false);
        settings.setEcuDefinitionFiles(new Vector<>());
        settings.setLoggerDefinitionFilePath(original.toString());
        settings.setLoggerProtocol("SSM");
        settings.setTransportProtocol("ISO9141");
        settings.setTargetModule("ecu");
        settings.setJ2534Device("");
        settings.setLoggerPort("");
        settings.setLoggerProfileFilePath("");
        settings.setLoggerOutputDirPath(root.toString());
        settings.setFileLoggingControllerSwitchId("S1");
        SettingsManager.save(settings);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!expectCleanExit) return;
            try {
                check(Files.size(root.resolve("settings/profiles/profile_backup.xml")) > 0, "shutdown did not save backup profile");
                check(!Files.exists(root.resolve(".RomRaider/profile_backup.xml")), "shutdown used legacy backup directory");
                if (closeScenario) {
                    check(!Files.exists(managed), "shutdown installed a definition");
                    check(Files.readString(root.resolve("settings/settings.xml")).contains(original.toString()), "shutdown lost original settings");
                }
                System.out.println(closeDuringWorker ? "PACKAGED_LOGGER_INSTALLER_WORKER_CLOSE_PASS"
                        : closeScenario ? "PACKAGED_LOGGER_INSTALLER_CLOSE_PASS" : "PACKAGED_LOGGER_INSTALLER_PASS");
            } catch (Throwable failure) { failure.printStackTrace(); Runtime.getRuntime().halt(3); }
        }));
        // A hung modal flow must fail the probe, rather than look like a launched-app pass.
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(60000); } catch (InterruptedException ignored) { return; }
            System.err.println("PACKAGED_LOGGER_INSTALLER_TIMEOUT");
            Runtime.getRuntime().halt(2);
        });
        watchdog.setDaemon(true);
        watchdog.start();
        if (nativePicker) {
            Thread picker = new Thread(PackagedLoggerInstaller::driveKdePicker, "isolated-kde-picker-driver");
            picker.setDaemon(true);
            picker.start();
        }
        SwingUtilities.invokeLater(() -> {
            try {
                logger = EcuLogger.getEcuLogger(null);
                logger.setSize(1100, 760);
                logger.setVisible(true);
                action = new InstallLoggerDefinitionAction(logger);
                deadline = System.nanoTime() + 45_000_000_000L;
                new Timer(100, event -> tick()).start();
            } catch (Throwable failure) { fail(failure); }
        });
    }

    // KDE's native picker is a separate process; Swing's EDT waits for its result.
    // Run only under the private Xvfb display so no user window can be selected.
    private static void driveKdePicker() {
        try {
            while (true) {
                Thread.sleep(100);
                if (phase != 1) continue;
                Process search = new ProcessBuilder("xdotool", "search", "--onlyvisible", "--class", "kdialog").start();
                String windows = new String(search.getInputStream().readAllBytes()).trim();
                if (search.waitFor() != 0 || windows.isEmpty()) continue;
                String window = windows.split("\\s+")[0];
                boolean cancel = scenario == 0 && !closeScenario;
                phase = cancel ? 4 : 2;
                run("xdotool", "windowactivate", "--sync", window);
                if (cancel) run("xdotool", "key", "--clearmodifiers", "Escape");
                else {
                    run("xdotool", "key", "--clearmodifiers", "alt+n");
                    run("xdotool", "key", "--clearmodifiers", "ctrl+a");
                    run("xdotool", "type", "--clearmodifiers", "--delay", "1", (scenario == 3 ? invalid : candidate).toString());
                    run("xdotool", "key", "--clearmodifiers", "Return");
                }
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void run(String... command) throws Exception {
        if (new ProcessBuilder(command).inheritIO().start().waitFor() != 0)
            throw new AssertionError("Native picker driver failed");
    }

    private static void tick() {
        try {
            check(System.nanoTime() < deadline, "dialog timeout at " + scenario + "/" + phase);
            check(!logger.isLogging(), "a connection was started");
            if (phase == 0) {
                phase = 1;
                action.actionPerformed(new java.awt.event.ActionEvent(logger, 0, "probe"));
                return;
            }
            if (phase == 1) {
                JFileChooser chooser = showing(JFileChooser.class);
                if (chooser == null) return;
                phase = scenario == 0 && !closeScenario ? 4 : 2;
                if (phase == 4) chooser.cancelSelection();
                else {
                    chooser.setSelectedFile((scenario == 3 ? invalid : candidate).toFile());
                    chooser.approveSelection();
                }
                return;
            }
            if (phase == 2) {
                JDialog dialog = dialog("Install Logger Definition");
                if (dialog == null) return;
                if (closeScenario) {
                    check(!Files.exists(managed), "closing wrote managed definition");
                    if (closeDuringWorker) {
                        phase = 5;
                        button(dialog, "Install").doClick();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                check(!action.isEnabled(), "validation worker was not submitted before close");
                                expectCleanExit = true;
                                logger.handleExit();
                            } catch (Throwable failure) { fail(failure); }
                        });
                        return;
                    }
                    expectCleanExit = true;
                    logger.handleExit();
                    return;
                }
                phase = scenario == 1 || scenario == 2 ? 4 : 3;
                if (scenario == 2) settings.setLoggerDefinitionFilePath(candidate.toString());
                button(dialog, scenario == 1 ? "Cancel" : "Install").doClick();
                return;
            }
            if (phase == 3) {
                JDialog result = dialog(scenario == 3 ? "Definition Installation Failed" : "Definition Installed");
                if (result == null) return;
                phase = 4;
                button(result, "OK").doClick();
                return;
            }
            if (phase == 4) {
                if (showing(JFileChooser.class) != null || showing(JOptionPane.class) != null || !action.isEnabled()) return;
                if (scenario == 4) {
                    check(Files.exists(managed), "missing installed definition");
                    check(settings.getLoggerDefinitionFilePath().equals(managed.toString()), "installed path not active");
                    check(Files.readString(managed).contains("version=\"candidate\""), "wrong installed bytes");
                    check(Files.readString(root.resolve("settings/settings.xml")).contains(managed.toString()), "installed path not saved");
                } else {
                    check(!Files.exists(managed), "cancelled/invalid install changed managed file");
                    check(settings.getLoggerDefinitionFilePath().equals((scenario == 2 ? candidate : original).toString()), "stale settings restored");
                    settings.setLoggerDefinitionFilePath(original.toString());
                }
                System.out.println("PASS " + cases[scenario]);
                if (++scenario == cases.length) {
                    expectCleanExit = true;
                    logger.handleExit();
                } else phase = 0;
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static Path definition(String version) throws Exception {
        Path path = root.resolve(version + ".xml");
        Files.writeString(path, "<?xml version=\"1.0\"?><!DOCTYPE logger SYSTEM \"logger.dtd\">"
                + "<logger version=\"" + version + "\"><protocols><protocol id=\"SSM\" baud=\"4800\" databits=\"8\" stopbits=\"1\" parity=\"0\" connect_timeout=\"2000\" send_timeout=\"55\">"
                + "<transports><transport id=\"ISO9141\" name=\"K-Line\" desc=\"Synthetic\"><module id=\"ecu\" address=\"0x10\" desc=\"Engine\"/></transport></transports>"
                + "</protocol></protocols></logger>");
        return path;
    }
    private static JDialog dialog(String title) {
        for (Window window : Window.getWindows())
            if (window.isShowing() && window instanceof JDialog d && title.equals(d.getTitle())) return d;
        return null;
    }
    private static JButton button(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton b && text.equals(b.getText())) return b;
            if (child instanceof Container c) { JButton b = button(c, text); if (b != null) return b; }
        }
        return null;
    }
    private static <T extends Component> T showing(Class<T> type) {
        for (Window window : Window.getWindows()) {
            if (!window.isShowing()) continue;
            T component = find(window, type);
            if (component != null) return component;
        }
        return null;
    }
    private static <T extends Component> T find(Component component, Class<T> type) {
        if (!component.isShowing()) return null;
        if (type.isInstance(component)) return type.cast(component);
        if (component instanceof Container c) for (Component child : c.getComponents()) {
            T result = find(child, type); if (result != null) return result;
        }
        return null;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void fail(Throwable failure) { failure.printStackTrace(); System.exit(1); }
}
