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
            await("enter fullscreen", stage[0], awake, () -> stage[0].isFocused() && held.get() == 1);
            FxTestRuntime.run(() -> {
                probe[0] = new Stage(); probe[0].setScene(new Scene(new Label("Synthetic focus probe"), 160, 100));
                probe[0].show(); probe[0].requestFocus();
            });
            await("transfer focus", stage[0], awake, () -> probe[0].isFocused() && !stage[0].isFocused() && held.get() == 0);
            FxTestRuntime.run(() -> { probe[0].close(); stage[0].requestFocus(); });
            await("regain focus", stage[0], awake, () -> stage[0].isFocused() && held.get() == 1);
            FxTestRuntime.run(() -> stage[0].setIconified(true));
            await("minimize", stage[0], awake, () -> stage[0].isIconified() && !stage[0].isFocused() && held.get() == 0);
            restore(stage[0], awake);
            // Some window managers leave native full screen when minimized. Merely
            // restoring that now-windowed logger must not request screen awake.
            await("restored awake ownership", stage[0], awake, () -> !stage[0].isIconified()
                    && stage[0].isFocused() && held.get() == (stage[0].isFullScreen() ? 1 : 0));
            FxTestRuntime.run(() -> { window[0].setMountedFullScreen(true); stage[0].requestFocus(); });
            await("reenter fullscreen", stage[0], awake, () -> held.get() == 1);
            FxTestRuntime.run(() -> window[0].setMountedFullScreen(false));
            await("leave fullscreen", stage[0], awake, () -> held.get() == 0);
            FxTestRuntime.run(() -> { window[0].setMountedFullScreen(true); stage[0].requestFocus(); });
            await("enter fullscreen before hiding", stage[0], awake, () -> held.get() == 1);
            FxTestRuntime.run(() -> stage[0].hide());
            await("hide", stage[0], awake, () -> awake.getStatus() == DesktopDisplayAwake.Status.OFF);
            assertEquals(0, held.get());
        } finally {
            FxTestRuntime.run(() -> { if (probe[0] != null) probe[0].close(); if (stage[0] != null) stage[0].hide(); });
            awake.close();
        }
    }
    private static void restore(Stage stage, DesktopDisplayAwake awake) throws Exception {
        // Iconify/restore is asynchronous at the window manager. A late minimize
        // acknowledgement can overwrite JavaFX's optimistic setIconified(false).
        // Retry only the native restore request, never the app's awake request.
        long[] nextRequest = {0};
        await("restore window", stage, awake, () -> {
            if (!stage.isIconified() && stage.isShowing() && stage.isFocused()) return true;
            long now = System.nanoTime();
            if (now >= nextRequest[0]) {
                stage.setIconified(false); stage.toFront(); stage.requestFocus();
                nextRequest[0] = now + 250_000_000L;
            }
            return false;
        });
    }

    private static void await(String transition, Stage stage, DesktopDisplayAwake awake,
            BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 6_000_000_000L;
        long stableSince = 0;
        while (true) {
            boolean[] satisfied = new boolean[1];
            String[] state = new String[1];
            // Read all native window properties on their owning UI thread and
            // wait for a settled state, not a transient backend lease count.
            FxTestRuntime.run(() -> {
                satisfied[0] = condition.getAsBoolean();
                state[0] = "full=" + stage.isFullScreen() + ", focused=" + stage.isFocused()
                        + ", showing=" + stage.isShowing() + ", minimized=" + stage.isIconified()
                        + ", awake=" + awake.getStatus();
            });
            long now = System.nanoTime();
            if (!satisfied[0]) stableSince = 0;
            else if (stableSince == 0) stableSince = now;
            else if (now - stableSince >= 150_000_000L) return;
            assertTrue(now < deadline, "Display-awake lifecycle timed out during " + transition + ": " + state[0]);
            Thread.sleep(20);
        }
    }
}
