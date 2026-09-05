/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.util.SettingsManager;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLoggerDisplaySmokeTest {
    @Test void launchPresentationUsesTouchAndActualFullScreenWithoutConnecting() throws Exception {
        FxTestRuntime.run(() -> {
            boolean previous = SettingsManager.getSettings().getAutoConnectOnStartup();
            SettingsManager.getSettings().setAutoConnectOnStartup(false);
            FxLoggerWindow window = null;
            try {
                window = new FxLoggerWindow(() -> {});
                Stage stage = FxEditorControlsSmokeTest.field(window, "stage");
                FxWindowPlacement.show(stage); // Do not invoke startup/setup/auto-connect.
                window.setTouchMode();
                assertTrue(stage.getScene().getRoot().getStyleClass().contains("touch-controls"));
                window.enterFullScreen();
                assertTrue(stage.isFullScreen());
                stage.setFullScreen(false);
            } finally {
                if (window != null) window.close();
                SettingsManager.getSettings().setAutoConnectOnStartup(previous);
            }
        });
    }
}
