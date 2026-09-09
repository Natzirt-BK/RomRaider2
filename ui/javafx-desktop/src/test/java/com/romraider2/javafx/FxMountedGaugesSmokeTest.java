/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import java.util.List;
import java.util.Map;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxMountedGaugesSmokeTest {
    @Test void nativeInstrumentFramesDisappearAndRestoreWithoutLosingReadings() throws Exception {
        FxTestRuntime.run(() -> {
            for (var style : com.romraider.portable.gauge.GaugeFaceRenderer.Style.values()) {
                var view = new FxInstrumentView(style, new com.romraider.portable.gauge.GaugeFaceRenderer.Reading(
                        "Synthetic RPM", "3210", "rpm", 3210, 0, 9000, 5000,
                        "SIMULATED", "REFERENCE SCALE", false));
                view.resize(320, 250);
                var parameters = new javafx.scene.SnapshotParameters();
                parameters.setFill(javafx.scene.paint.Color.TRANSPARENT);
                for (var presentation : new com.romraider.portable.gauge.GaugeFaceRenderer.Presentation[]{
                        com.romraider.portable.gauge.GaugeFaceRenderer.Presentation.CARD,
                        com.romraider.portable.gauge.GaugeFaceRenderer.Presentation.SEAMLESS,
                        com.romraider.portable.gauge.GaugeFaceRenderer.Presentation.CARD}) {
                    view.setPresentation(presentation);
                    view.layout();
                    double alpha = view.snapshot(parameters, null).getPixelReader().getColor(4, 125).getOpacity();
                    assertEquals(presentation == com.romraider.portable.gauge.GaugeFaceRenderer.Presentation.SEAMLESS,
                            alpha == 0, style + "/" + presentation);
                    assertTrue(view.getAccessibleText().contains("3210"));
                }
            }
        });
    }

    @Test void switchesPreservePublishedRecordingStateAndBlankStaleReadings() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> { });
                Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage");
                FxWindowPlacement.show(stage); // No startup or hardware commands.
                javafx.scene.control.TabPane tabs = FxEditorControlsSmokeTest.field(window[0], "views");
                assertEquals(8, tabs.getTabs().size());
                assertFalse(tabs.getTabs().stream().anyMatch(tab -> tab.getText().equals("Gauges only")));
                tabs.getSelectionModel().select(3);
                LoggerWorkspaceContext context = context(window[0]);
                context.getChannels().replaceChannels(List.of(new LoggerChannel("gauge-fixture",
                        "Synthetic boost", "psi", LoggerChannelKind.PARAMETER, true, List.of(), "psi")));
                context.getPreferences().setGaugeDisplay(new LoggerGaugeDisplay().useLoggerChannels(context.getChannels().getChannels()));
                context.getLiveData().loggingData(); // Publish synthetic state; does not open a writer/adapter.
                context.getLiveData().publish(sample(12.7));
            });
            FxTestRuntime.run(() -> {
                LoggerWorkspaceContext context = context(window[0]);
                Object session = context.getSession();
                int historySize = context.getLiveData().getRecentSamples().get("gauge-fixture").size();
                Stage stage = FxEditorControlsSmokeTest.field(window[0], "stage");
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                assertNull(stage.getScene().lookup("#dashboard-tile-customization"), "Tile settings belong to each tile, not a second panel");
                var customize = stage.getScene().getRoot().lookupAll(".menu-button").stream()
                        .filter(javafx.scene.control.MenuButton.class::isInstance)
                        .map(javafx.scene.control.MenuButton.class::cast)
                        .filter(button -> "Customize".equals(button.getText())).findFirst().orElseThrow();
                assertTrue(customize.getItems().stream().anyMatch(item -> "Display type".equals(item.getText())));
                assertTrue(customize.getItems().stream().anyMatch(item -> "Tile size".equals(item.getText())));
                captureDashboard(stage);
                ((javafx.scene.control.Button) stage.getScene().lookup("#dashboard-open-gauge-display")).fire();
                assertTrue((Boolean) FxEditorControlsSmokeTest.field(window[0], "gaugesOnly"));
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                assertNotNull(stage.getScene().lookup("#gauge-slot-0-limits"));
                window[0].setGaugesOnly(false);
                javafx.scene.control.TabPane tabs = FxEditorControlsSmokeTest.field(window[0], "views");
                assertEquals("Dashboard", tabs.getSelectionModel().getSelectedItem().getText());
                for (LoggerGaugeTheme theme : LoggerGaugeTheme.values()) {
                    context.getPreferences().setGaugeTheme(theme);
                    window[0].setGaugesOnly(true);
                    assertSame(session, context.getSession());
                    assertEquals(LoggerSessionState.RECORDING, context.getSession().getState());
                    FxMountedGaugePane gauges = FxEditorControlsSmokeTest.field(window[0], "mountedGauges");
                    assertEquals(1, gauges.getChildren().size());
                    assertFalse(gauges.getChildren().getFirst().getStyleClass().contains("logger-card"));
                    assertFalse(gauges.getChildren().getFirst().getStyleClass().contains("logger-card-selected"));
                    window[0].setGaugesOnly(false);
                    assertEquals(historySize, context.getLiveData().getRecentSamples().get("gauge-fixture").size());
                }
                context.getPreferences().setGaugeTheme(LoggerGaugeTheme.TWIN_ARC);
                Map<String, Long> times = FxEditorControlsSmokeTest.field(window[0], "receivedAt");
                times.put("gauge-fixture", System.nanoTime() - 4_000_000_000L);
                window[0].setGaugesOnly(true);
                assertTrue(face(window[0]).getAccessibleText().contains("NO RECENT DATA"));
                assertTrue(face(window[0]).getAccessibleText().contains("no valid data"));
                context.getLiveData().publish(sample(13.5));
            });
            FxTestRuntime.run(() -> {
                assertTrue(face(window[0]).getAccessibleText().contains("13.5"));
                context(window[0]).getLiveData().stopped();
            });
            FxTestRuntime.run(() -> {
                window[0].setGaugesOnly(false); window[0].setGaugesOnly(true);
                assertTrue(face(window[0]).getAccessibleText().contains("STOPPED"));
                assertTrue(face(window[0]).getAccessibleText().contains("no valid data"));
            });
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) {
                    context(window[0]).getLiveData().stopped();
                    context(window[0]).getPreferences().setGaugeTheme(LoggerGaugeTheme.RR2_CLASSIC);
                    context(window[0]).getPreferences().setGaugeDisplay(new LoggerGaugeDisplay());
                    window[0].close();
                }
            });
        }
    }
    private static FxInstrumentView face(FxLoggerWindow window) throws Exception {
        FxMountedGaugePane gauges = FxEditorControlsSmokeTest.field(window, "mountedGauges");
        return (FxInstrumentView) gauges.getChildren().getFirst();
    }
    private static void captureDashboard(Stage stage) throws Exception {
        String directory = System.getenv("RR2_CHANNEL_CAPTURE_DIR");
        if (directory == null) return;
        var image = stage.getScene().getRoot().snapshot(null, null);
        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
            bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(bitmap, "png", new java.io.File(directory, "desktop-dashboard.png"));
    }
    private static LoggerWorkspaceContext context(FxLoggerWindow window) throws Exception {
        return FxEditorControlsSmokeTest.field(window, "context");
    }
    private static LiveDataSample sample(double value) {
        return new LiveDataSample("gauge-fixture", "Synthetic boost", value, Double.toString(value), "psi", 1, "psi");
    }
}
