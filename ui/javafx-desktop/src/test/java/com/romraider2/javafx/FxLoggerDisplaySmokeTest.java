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
    @Test void elapsedFormattingSupportsLongCapturesWithoutWrappingAtOneDay() {
        assertEquals("00:00:00", FxLoggerWindow.formatRecordingElapsed(0));
        assertEquals("00:01:05", FxLoggerWindow.formatRecordingElapsed(65_999));
        assertEquals("01:00:00", FxLoggerWindow.formatRecordingElapsed(3_600_000));
        assertEquals("25:00:00", FxLoggerWindow.formatRecordingElapsed(90_000_000));
        assertEquals("00:00:00", FxLoggerWindow.formatRecordingElapsed(-1));
    }
    @Test void loadProfileSitsBesideDefinitionAndSetupWithoutClippingAtSmallWidths() throws Exception {
        FxTestRuntime.run(() -> {
            FxLoggerWindow window = new FxLoggerWindow(() -> {});
            Stage stage = FxEditorControlsSmokeTest.field(window, "stage");
            try {
                FxWindowPlacement.show(stage); // No startup or vehicle connection.
                var actions = (javafx.scene.layout.FlowPane) stage.getScene().lookup("#logger-header-actions");
                var elapsed = (javafx.scene.control.Label) stage.getScene().lookup("#logger-recording-elapsed");
                assertEquals("00:00:00", elapsed.getText());
                assertNotNull(elapsed.getTooltip());
                var menu = (javafx.scene.control.MenuBar) stage.getScene().lookup(".menu-bar");
                var file = menu.getMenus().getFirst();
                var profileActions = file.getItems().stream()
                        .filter(item -> item.getText() != null && item.getText().contains("Profile")).toList();
                assertEquals(List.of("Load Profile…", "Save Profile", "Save Profile As…", "Reload Profile"),
                        profileActions.stream().map(javafx.scene.control.MenuItem::getText).toList());
                assertNotNull(profileActions.get(1).getAccelerator());
                assertNotNull(profileActions.get(2).getAccelerator());
                assertTrue(profileActions.get(3).isDisable());
                var labels = actions.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .map(Button::getText).toList();
                int definition = labels.indexOf("Load Definition");
                assertEquals("Load Profile", labels.get(definition + 1));
                assertEquals("Logger Setup", labels.get(definition + 2));
                for (int width : new int[] {900, 1024, 1380}) {
                    stage.setWidth(width);
                    var root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                    for (var node : actions.getChildren()) {
                        if (!(node instanceof javafx.scene.layout.Region control)) continue;
                        assertTrue(control.getWidth() + 1 >= control.prefWidth(-1), "Clipped control at " + width);
                        var bounds = control.localToScene(control.getBoundsInLocal());
                        assertTrue(bounds.getMaxX() <= stage.getScene().getWidth(), "Control outside window");
                        assertTrue(bounds.getMaxY() <= actions.localToScene(actions.getBoundsInLocal()).getMaxY() + 1);
                    }
                }
            } finally { window.close(); }
        });
    }

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
