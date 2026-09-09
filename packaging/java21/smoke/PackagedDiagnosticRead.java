/* Synthetic-only diagnostic entry point; run in a disposable packaged image. */
package com.romraider2.smoke;

import com.romraider.Settings;
import com.romraider.logger.ecu.EcuLogger;
import com.romraider.logger.ecu.DesktopDiagnosticTask;
import com.romraider.logger.ecu.comms.readcodes.*;
import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.EcuQueryImpl;
import com.romraider.logger.ecu.definition.*;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.ui.swing.tools.DiagnosticProgressDialog;
import com.romraider.logger.ecu.ui.swing.tools.ReadCodesResultsPanel;
import com.romraider.util.SettingsManager;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

public final class PackagedDiagnosticRead {
    private static EcuLogger logger;
    private static DiagnosticProgressDialog dialog;
    private static DesktopDiagnosticTask<?> task;
    private static CountDownLatch readRelease, closeRelease;
    private static volatile boolean reading, closing, closed;
    private static boolean delivered, complete;
    private static int scenario, phase, beats;
    private static Path root;
    private static Timer timer;
    private static final String[] MODES = {"success", "cancel", "window-close", "failure"};

    public static void main(String[] args) throws Exception {
        check("1".equals(System.getenv("RR2_PRIVATE_WINDOW_TEST")), "private display required");
        root = Path.of(args[0]).toAbsolutePath();
        check(!Files.exists(root), "fresh output directory required");
        Files.createDirectories(root);
        System.setProperty("user.home", root.toString());
        System.setProperty("romraider2.settings.dir", root.resolve("settings").toString());
        System.setProperty("romraider2.log.dir", root.resolve("logs").toString());
        System.setProperty("romraider2.plugins.dir", Files.createDirectories(root.resolve("plugins")).toString());
        System.setProperty("romraider2.displayAwake.disabled", "true");
        System.setProperty("romraider2.j2534.library", "");
        Path definitions = root.resolve("synthetic.xml");
        Files.writeString(definitions, "<?xml version=\"1.0\"?><!DOCTYPE logger SYSTEM \"logger.dtd\">"
                + "<logger version=\"synthetic\"><protocols><protocol id=\"SSM\" baud=\"4800\" databits=\"8\" stopbits=\"1\" parity=\"0\" connect_timeout=\"2000\" send_timeout=\"55\">"
                + "<transports><transport id=\"ISO9141\" name=\"K-Line\" desc=\"Synthetic\"><module id=\"ecu\" address=\"0x10\" desc=\"Engine\"/></transport></transports>"
                + "</protocol></protocols></logger>");
        Settings settings = SettingsManager.getSettings();
        settings.setAutoConnectOnStartup(false);
        settings.setEcuDefinitionFiles(new Vector<>());
        settings.setLoggerDefinitionFilePath(definitions.toString());
        settings.setLoggerProtocol("SSM"); settings.setTransportProtocol("ISO9141"); settings.setTargetModule("ecu");
        settings.setJ2534Device(""); settings.setLoggerPort(""); settings.setLoggerProfileFilePath("");
        settings.setLoggerOutputDirPath(root.toString());
        SettingsManager.save(settings);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (complete) {
                try {
                    check(Files.size(root.resolve("settings/profiles/profile_backup.xml")) > 0, "missing shutdown backup");
                    System.out.println("PACKAGED_DIAGNOSTIC_READ_PASS");
                } catch (Throwable failure) { fail(failure); }
            }
        }));
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(45000); } catch (InterruptedException ignored) { return; }
            fail(new AssertionError("Diagnostic probe timed out"));
        });
        watchdog.setDaemon(true); watchdog.start();
        SwingUtilities.invokeLater(() -> {
            try {
                logger = EcuLogger.getEcuLogger(null); logger.setSize(1024, 768); logger.setVisible(true);
                timer = new Timer(80, event -> tick()); timer.start();
            } catch (Throwable failure) { fail(failure); }
        });
    }

    @SuppressWarnings("unchecked") private static DiagnosticReadRequest request() {
        EcuSwitch code = new EcuSwitchImpl("D1", "Synthetic code", "Synthetic", new EcuAddressImpl("0x000100", 2, 0),
                null, null, null, new EcuDataConvertor[] {new EcuDtcConvertorImpl(0)});
        return new DiagnosticReadRequest(List.of(code), 104,
                new DmRuntimeReadRequest(null, null, logger::isDiagnosticOwnerOpen),
                new Module("ecu", new byte[] {0x10}, "Engine", new byte[] {(byte) 0xf0}, false),
                () -> (LoggerConnection) Proxy.newProxyInstance(LoggerConnection.class.getClassLoader(),
                        new Class<?>[] {LoggerConnection.class}, (proxy, method, args) -> {
                            check(!SwingUtilities.isEventDispatchThread(), "IO on EDT");
                            if (method.getName().equals("sendAddressReads")) {
                                reading = true; await(readRelease);
                                if (scenario == 3) throw new IllegalStateException("Synthetic failed response");
                                ((Collection<EcuQuery>) args[0]).forEach(q -> q.setResponse(new byte[] {1, 0}));
                            } else if (method.getName().equals("close")) {
                                check(!Thread.currentThread().isInterrupted(), "cleanup interrupted");
                                closing = true; await(closeRelease); closed = true;
                            } else throw new AssertionError("Unexpected IO: " + method.getName());
                            return null;
                        }));
    }

    private static void tick() {
        try {
            check(!logger.isLogging(), "real logger controller started");
            if (phase == 0) {
                if (scenario == MODES.length) {
                    check(!logger.isDiagnosticBusy(), "diagnostic lease retained");
                    phase = 4;
                    EcuQuery code = new EcuQueryImpl(new EcuSwitchImpl("D1", "P0001, \"fixture\"", "Synthetic",
                            new EcuAddressImpl("0x000100", 2, 0), null, null, null,
                            new EcuDataConvertor[] {new EcuDtcConvertorImpl(0)}));
                    code.setResponse(new byte[] {1, 0});
                    SwingUtilities.invokeLater(() -> ReadCodesResultsPanel.displayResultsPane(logger,
                            new ArrayList<>(List.of(code)), Set.of("DM1: Synthetic current"), Set.of("DM2: Synthetic memorized")));
                    return;
                }
                reading = closing = closed = delivered = false;
                readRelease = new CountDownLatch(1); closeRelease = new CountDownLatch(1);
                phase = 1; beats = 0;
                dialog = new DiagnosticProgressDialog(logger, "ECU", () -> task.cancel());
                task = logger.beginDiagnosticRead(request(), result -> {
                    try {
                        check(SwingUtilities.isEventDispatchThread(), "completion off EDT");
                        check(closed && !logger.isDiagnosticBusy(), "completed before cleanup/lease release");
                        check(phase == 3, "unexpected delivery phase");
                        check(scenario == 0 ? result.succeeded() : !result.succeeded(), "wrong completion kind");
                        check(result.cancelled() == (scenario == 1 || scenario == 2), "wrong cancellation kind");
                        if (scenario == 0) check(result.result().codes().size() == 1, "missing code result");
                        if (scenario == 3) check(result.failure() != null, "missing failure");
                        delivered = true; dialog.dispose();
                        System.out.println("Diagnostic scenario passed: " + MODES[scenario]);
                        scenario++; phase = 0;
                    } catch (Throwable failure) { fail(failure); }
                });
                DiagnosticProgressDialog showing = dialog;
                SwingUtilities.invokeLater(() -> showing.setVisible(true)); return;
            }
            if (phase == 1 && reading) {
                check(logger.isDiagnosticBusy(), "missing diagnostic lease");
                logger.startLogging(); check(!logger.isLogging(), "logging bypassed diagnostic lease");
                try { logger.beginDiagnosticRead(request(), value -> {}); throw new AssertionError("duplicate accepted"); }
                catch (IllegalStateException expected) { }
                if (scenario == 1) button(dialog, "Cancel Read").doClick();
                if (scenario == 2) dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
                phase = 2; beats = 0;
            } else if (phase == 2 && ++beats >= 3) {
                check(dialog.isShowing() && !delivered && logger.isDiagnosticBusy(), "cancel released operation early");
                javax.imageio.ImageIO.write(new Robot().createScreenCapture(dialog.getBounds()), "png", root.resolve(MODES[scenario] + ".png").toFile());
                readRelease.countDown(); phase = 3; beats = 0;
            } else if (phase == 3 && closing && ++beats >= 3) {
                check(!delivered && logger.isDiagnosticBusy(), "cleanup released lease early");
                closeRelease.countDown();
            } else if (phase == 4) {
                JDialog results = findDialog("Diagnostic Code Read Results");
                if (results == null) return;
                phase = 5;
                SwingUtilities.invokeLater(() -> button(results, "Save to File").doClick());
            } else if (phase == 5 || phase == 7) {
                JDialog saved = findDialog("Save Success");
                if (saved == null) return;
                saved.dispose(); phase++;
            } else if (phase == 6) {
                JDialog results = findDialog("Diagnostic Code Read Results");
                check(results != null, "results closed early");
                phase = 7;
                SwingUtilities.invokeLater(() -> button(results, "Save as Image").doClick());
            } else if (phase == 8) {
                List<Path> csv, png;
                try (var paths = Files.list(root)) { csv = paths.filter(p -> p.getFileName().toString().startsWith("romraiderDTC_") && p.toString().endsWith(".csv")).toList(); }
                try (var paths = Files.list(root)) { png = paths.filter(p -> p.getFileName().toString().startsWith("romraiderDTC_") && p.toString().endsWith(".png")).toList(); }
                check(csv.size() == 1 && png.size() == 1, "missing or repeated report exports");
                String report = Files.readString(csv.get(0));
                check(report.contains("\"P0001, \"\"fixture\"\"\",true,false"), "standard CSV quoting/flags lost");
                check(report.contains("DM1: Synthetic current,true,false") && report.contains("DM2: Synthetic memorized,false,true"), "DimeMod CSV rows lost");
                JDialog results = findDialog("Diagnostic Code Read Results");
                List<JTable> tables = new ArrayList<>(); collectTables(results, tables);
                check(tables.size() == 2, "expected standard and DimeMod tables");
                int height = tables.stream().mapToInt(t -> t.getHeight() + t.getTableHeader().getHeight()).sum();
                var bitmap = javax.imageio.ImageIO.read(png.get(0).toFile());
                check(bitmap != null && bitmap.getHeight() >= height, "image omitted a results table");
                results.dispose();
                System.out.println("Diagnostic scenario passed: CSV and image exports");
                timer.stop(); complete = true; logger.handleExit();
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static JButton button(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton b && text.equals(b.getText())) return b;
            if (child instanceof Container c) { JButton found = button(c, text); if (found != null) return found; }
        }
        return null;
    }
    private static JDialog findDialog(String title) {
        for (Window window : Window.getWindows())
            if (window.isShowing() && window instanceof JDialog dialog && title.equals(dialog.getTitle())) return dialog;
        return null;
    }
    private static void collectTables(Container parent, List<JTable> tables) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JTable table) tables.add(table);
            else if (child instanceof Container container) collectTables(container, tables);
        }
    }
    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try { check(latch.await(5, TimeUnit.SECONDS), "latch timeout"); break; }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void fail(Throwable failure) { failure.printStackTrace(); Runtime.getRuntime().halt(2); }
}
