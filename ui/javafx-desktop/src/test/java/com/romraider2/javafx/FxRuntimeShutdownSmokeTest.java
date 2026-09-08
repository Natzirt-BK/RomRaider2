/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** A disposable JVM is required: this checks the real toolkit shutdown. */
@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxRuntimeShutdownSmokeTest {
    @TempDir Path temporary;

    @Test void lastWindowCloseFinishesBeforeToolkitShutdown() throws Exception {
        runProbe("early");
        runProbe("dialog");
    }

    private void runProbe(String mode) throws Exception {
        Path output = temporary.resolve("shutdown-" + mode + ".log");
        String executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"),
                Probe.class.getName(), temporary.resolve(mode).toString(), mode).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        try {
            boolean exited = process.waitFor(35, TimeUnit.SECONDS);
            String log = Files.readString(output);
            assertTrue(exited, "Shutdown probe timed out\n" + log);
            assertEquals(0, process.exitValue(), log);
            assertTrue(log.contains("NATIVE_RUNTIME_SHUTDOWN_PASS"), log);
            assertFalse(log.contains("This operation is permitted on the event thread only"), log);
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            System.setProperty("romraider2.settings.dir", args[0]);
            System.setProperty("romraider2.log.dir", args[0]);
            com.romraider.util.SettingsManager.setTesting(true);
            com.romraider.util.SettingsManager.getSettings().setAutoConnectOnStartup(false);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread.setDefaultUncaughtExceptionHandler((thread, problem) -> {
                failure.set(problem); problem.printStackTrace();
            });
            CountDownLatch closeReturned = new CountDownLatch(1);
            Thread closer = new Thread(() -> {
                for (int attempt = 0; attempt < 100 && closeReturned.getCount() != 0; attempt++) {
                    try {
                        Platform.runLater(() -> {
                            if (closeReturned.getCount() == 0) return;
                            if (args.length > 1 && args[1].equals("dialog") && Window.getWindows().stream()
                                    .noneMatch(window -> window instanceof Stage candidate
                                            && "ECU Definitions Manager".equals(candidate.getTitle()))) return;
                            Stage stage = Window.getWindows().stream()
                                    .filter(window -> window instanceof Stage && window.isShowing())
                                    .map(window -> (Stage) window)
                                    .filter(window -> window.getTitle().startsWith("RomRaider2"))
                                    .findFirst().orElse(null);
                            if (stage == null) return;
                            try {
                                stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                            } catch (Throwable problem) {
                                failure.set(problem);
                            } finally { closeReturned.countDown(); }
                        });
                    } catch (IllegalStateException notStartedYet) { /* wait for startup */ }
                    try { Thread.sleep(100); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
            }, "synthetic-window-close");
            closer.setDaemon(true); closer.start();
            JavaFxDesktopRuntime.launch(new String[0]);
            if (!closeReturned.await(5, TimeUnit.SECONDS) || failure.get() != null) {
                throw new AssertionError("Window close did not finish cleanly", failure.get());
            }
            System.out.println("NATIVE_RUNTIME_SHUTDOWN_PASS");
        }
    }
}
