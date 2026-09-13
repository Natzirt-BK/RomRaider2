package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.Settings;
import com.romraider.logger.runtime.LoggerDebugSettings.Verbosity;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLoggerTroubleshootingTest {
    @TempDir Path directory;

    @Test void editsRequireSaveAndFailureKeepsDialogAndPreviousPreference() throws Exception {
        Settings settings = new Settings(); settings.setLoggerDebuggingLevel("info");
        var original = LogManager.getRootLogger().getLevel();
        AtomicInteger saves = new AtomicInteger(), opens = new AtomicInteger();
        try {
            FxTestRuntime.run(() -> {
                var dialog = new FxLoggerTroubleshooting(null, settings, () -> {
                    if (saves.getAndIncrement() == 0) throw new IllegalStateException("Synthetic save failure");
                }, path -> opens.incrementAndGet());
                try {
                    dialog.show(); dialog.verbosity.setValue(Verbosity.DEBUG);
                    assertEquals("info", settings.getLoggerDebuggingLevel()); assertEquals(0, opens.get());
                    dialog.save.fire();
                    assertTrue(dialog.stage.isShowing()); assertTrue(dialog.status.getText().contains("Could not save"));
                    assertEquals("info", settings.getLoggerDebuggingLevel()); assertEquals(original, LogManager.getRootLogger().getLevel());
                    dialog.save.fire();
                    assertFalse(dialog.stage.isShowing()); assertEquals("debug", settings.getLoggerDebuggingLevel());
                    assertEquals(2, saves.get()); assertEquals(0, opens.get());
                } finally { dialog.stage.close(); }
            });
        } finally { Configurator.setRootLevel(original); }
    }

    @Test void closingDiscardsVerbosityEdits() throws Exception {
        Settings settings = new Settings(); settings.setLoggerDebuggingLevel("info");
        FxTestRuntime.run(() -> {
            var dialog = new FxLoggerTroubleshooting(null, settings, () -> fail("Must not save"), path -> fail("Must not open folder"));
            dialog.show(); dialog.verbosity.setValue(Verbosity.TRACE); dialog.stage.close();
            assertEquals("info", settings.getLoggerDebuggingLevel());
            dialog.save.fire(); assertEquals("info", settings.getLoggerDebuggingLevel());
        });
    }

    @Test void folderOpenUsesConfiguredPathOffUiThreadAndLateCompletionIsIgnored() throws Exception {
        String previous = System.getProperty("romraider2.log.dir");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        FxLoggerTroubleshooting[] dialog = new FxLoggerTroubleshooting[1];
        AtomicInteger calls = new AtomicInteger();
        try {
            System.setProperty("romraider2.log.dir", directory.toString());
            FxTestRuntime.run(() -> {
                dialog[0] = new FxLoggerTroubleshooting(null, new Settings(), () -> {}, path -> {
                    assertFalse(Platform.isFxApplicationThread()); assertEquals(directory, path);
                    calls.incrementAndGet(); entered.countDown();
                    while (release.getCount() != 0) try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                    finished.countDown();
                });
                dialog[0].show(); dialog[0].openFolder.fire();
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            FxTestRuntime.run(() -> {
                assertTrue(dialog[0].openFolder.isDisabled()); dialog[0].openFolder.fire();
                assertEquals(1, calls.get()); dialog[0].stage.close();
            });
            release.countDown(); assertTrue(finished.await(3, TimeUnit.SECONDS));
            FxTestRuntime.run(() -> {
                assertFalse(dialog[0].stage.isShowing()); assertEquals("Opening log folder…", dialog[0].status.getText());
            });
        } finally {
            release.countDown(); FxTestRuntime.run(() -> { if (dialog[0] != null) dialog[0].stage.close(); });
            if (previous == null) System.clearProperty("romraider2.log.dir"); else System.setProperty("romraider2.log.dir", previous);
        }
    }
}
