/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.ui.DesktopDisplayAwake;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxDisplayAwakeSmokeTest {
    @Test void onlyFocusedVisibleFullScreenOwnsAnAwakeRequest() throws Exception {
        AtomicInteger held = new AtomicInteger();
        DesktopDisplayAwake awake = new DesktopDisplayAwake(() -> {
            assertEquals(1, held.incrementAndGet());
            return () -> assertEquals(0, held.decrementAndGet());
        });
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        Stage[] stage = new Stage[1], probe = new Stage[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {}, null, awake);
                var field = FxLoggerWindow.class.getDeclaredField("stage"); field.setAccessible(true);
                stage[0] = (Stage) field.get(window[0]);
                FxWindowPlacement.show(stage[0]); window[0].setGaugesOnly(true);
            });
            assertEquals(0, held.get());
            FxTestRuntime.run(() -> { window[0].setMountedFullScreen(true); stage[0].requestFocus(); });
            await(() -> held.get() == 1);
            FxTestRuntime.run(() -> {
                probe[0] = new Stage(); probe[0].setScene(new Scene(new Label("Synthetic focus probe"), 160, 100));
                probe[0].show(); probe[0].requestFocus();
            });
            await(() -> held.get() == 0);
            FxTestRuntime.run(() -> { probe[0].close(); stage[0].requestFocus(); });
            await(() -> held.get() == 1);
            FxTestRuntime.run(() -> stage[0].setIconified(true));
            await(() -> held.get() == 0);
            FxTestRuntime.run(() -> { stage[0].setIconified(false); stage[0].requestFocus(); });
            // Some window managers leave native full screen when minimized. Merely
            // restoring that now-windowed logger must not request screen awake.
            boolean[] stillFullScreen = new boolean[1];
            FxTestRuntime.run(() -> stillFullScreen[0] = stage[0].isFullScreen());
            try { await(() -> held.get() == (stillFullScreen[0] ? 1 : 0)); }
            catch (AssertionError error) {
                FxTestRuntime.run(() -> System.err.println("Restored stage: full=" + stage[0].isFullScreen()
                        + ", focused=" + stage[0].isFocused() + ", showing=" + stage[0].isShowing()
                        + ", minimized=" + stage[0].isIconified() + ", awake=" + awake.getStatus()));
                throw error;
            }
            FxTestRuntime.run(() -> { window[0].setMountedFullScreen(true); stage[0].requestFocus(); });
            await(() -> held.get() == 1);
            FxTestRuntime.run(() -> window[0].setMountedFullScreen(false));
            await(() -> held.get() == 0);
            FxTestRuntime.run(() -> { window[0].setMountedFullScreen(true); stage[0].requestFocus(); });
            await(() -> held.get() == 1);
            FxTestRuntime.run(() -> stage[0].hide());
            await(() -> awake.getStatus() == DesktopDisplayAwake.Status.OFF);
            assertEquals(0, held.get());
        } finally {
            FxTestRuntime.run(() -> { if (probe[0] != null) probe[0].close(); if (stage[0] != null) stage[0].hide(); });
            awake.close();
        }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 6_000_000_000L;
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "Display-awake lifecycle timed out");
            Thread.sleep(20);
        }
    }
}
