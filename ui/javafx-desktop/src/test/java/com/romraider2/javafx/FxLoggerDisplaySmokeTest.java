/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.util.SettingsManager;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLoggerDisplaySmokeTest {
    @Test void dataStatisticsAndResetUseRealBusWithoutConnecting() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {});
                Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage");
                FxWindowPlacement.show(stage); // Never call startup or Connect.
                LoggerWorkspaceContext context = FxEditorControlsSmokeTest.field(window[0], "context");
                context.getChannels().replaceChannels(List.of(new LoggerChannel(
                        "synthetic", "Synthetic", "V", LoggerChannelKind.PARAMETER, true)));
                context.getLiveData().publish(new LiveDataSample("synthetic", "Synthetic", 2, "2", "V", 1));
                context.getLiveData().publish(new LiveDataSample("synthetic", "Synthetic", 4, "4", "V", 2));
            });
            FxTestRuntime.run(() -> {
                TableView<LiveDataSample> table = FxEditorControlsSmokeTest.field(window[0], "data");
                assertEquals(1, table.getItems().size());
                assertEquals("2.00000", table.getColumns().get(3).getCellData(0));
                assertEquals("4.00000", table.getColumns().get(4).getCellData(0));
                assertEquals("3.00000", table.getColumns().get(5).getCellData(0));
                TabPane views = FxEditorControlsSmokeTest.field(window[0], "views");
                Button reset = (Button) views.getTabs().get(1).getContent().lookup("#logger-reset-statistics");
                reset.fire();
                assertEquals("4.00000", table.getColumns().get(3).getCellData(0));
                assertEquals("4.00000", table.getColumns().get(5).getCellData(0));
                LoggerWorkspaceContext context = FxEditorControlsSmokeTest.field(window[0], "context");
                assertEquals(LoggerSessionState.STOPPED, context.getSession().getState());
                assertTrue(context.getChannels().getChannels().get(0).isSelected());
                assertEquals(1, context.getLiveData().getLatestSamples().size());
            });
        } finally {
            FxTestRuntime.run(() -> { if (window[0] != null) window[0].close(); });
        }
    }

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
