/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import com.romraider.portable.gauge.GaugeFaceRenderer;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.TilePane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxGaugeStylePickerSmokeTest {
    @Test void legacyThemesHaveDistinctNativeFacesAndBlankMissingValues() throws Exception {
        FxTestRuntime.run(() -> {
            java.util.Set<Integer> renders = new java.util.HashSet<>();
            for (LoggerGaugeTheme theme : new LoggerGaugeTheme[]{LoggerGaugeTheme.RR2_CLASSIC,
                    LoggerGaugeTheme.RALLY_HERITAGE, LoggerGaugeTheme.AMBER_GT,
                    LoggerGaugeTheme.CENTRAL_TACH, LoggerGaugeTheme.NEON_CIRCUIT}) {
                for (double value : new double[]{4200, Double.NaN}) {
                    var view = new FxLegacyGaugeView(theme, new GaugeFaceRenderer.Reading(
                            "RPM", "4200", "rpm", value, 0, 9000, 6500, "SAMPLE", "REFERENCE SCALE", false), null);
                    view.resize(320, 250); view.layout();
                    var parameters = new javafx.scene.SnapshotParameters();
                    parameters.setFill(javafx.scene.paint.Color.TRANSPARENT);
                    var snapshot = view.snapshot(parameters, null);
                    assertEquals(0, snapshot.getPixelReader().getColor(4, 125).getOpacity());
                    assertEquals(!Double.isFinite(value), view.getAccessibleText().contains("no valid data"));
                    if (Double.isFinite(value)) {
                        int hash = 1;
                        for (int y = 0; y < 250; y++) for (int x = 0; x < 320; x++)
                            hash = 31 * hash + snapshot.getPixelReader().getArgb(x, y);
                        renders.add(hash);
                    }
                }
            }
            assertEquals(5, renders.size(), "Legacy picker choices must not all render the same arc");
        });
    }
    @Test void gallerySearchCancelDefaultAndMixedMountedFacesPreserveSession() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {});
                Stage stage = field(window[0], "stage"); FxWindowPlacement.show(stage);
                LoggerWorkspaceContext context = field(window[0], "context");
                context.getPreferences().setGaugeTheme(LoggerGaugeTheme.ION_OLED);
                context.getChannels().replaceChannels(List.of(
                        new LoggerChannel("style-a", "Synthetic RPM", "rpm", LoggerChannelKind.PARAMETER, true),
                        new LoggerChannel("style-b", "Synthetic voltage", "V", LoggerChannelKind.PARAMETER, true)));
                context.getPreferences().setGaugeDisplay(new LoggerGaugeDisplay().useLoggerChannels(context.getChannels().getChannels()));
                context.getLiveData().loggingData(); // Synthetic state only; never opens an adapter or writer.
                context.getLiveData().publish(new LiveDataSample("style-a", "Synthetic RPM", 4200, "4200", "rpm", 1));
                context.getLiveData().publish(new LiveDataSample("style-b", "Synthetic voltage", 13, "13", "V", 1));
            });
            FxTestRuntime.run(() -> {
                LoggerWorkspaceContext context = field(window[0], "context");
                Object session = context.getSession();
                var method = FxLoggerWindow.class.getDeclaredMethod("chooseGaugeStyle", String.class);
                method.setAccessible(true); method.invoke(window[0], "style-a");
                FxGaugeStylePicker picker = field(window[0], "gaugeStylePicker");
                TilePane faces = (TilePane) picker.getDialogPane().lookup(".tile-pane");
                // TilePane has no default style class: find it from the scroll content.
                if (faces == null) faces = (TilePane) ((javafx.scene.control.ScrollPane)
                        picker.getDialogPane().lookup(".scroll-pane")).getContent();
                assertEquals(25, faces.getChildren().size());
                capture(picker);
                TextField search = (TextField) picker.getDialogPane().lookup(".text-field");
                search.setText("missing face"); assertEquals(0, faces.getChildren().size());
                search.setText(" sti night "); assertEquals(1, faces.getChildren().size());
                ((Button) faces.getChildren().getFirst()).fire();
                assertFalse(picker.isShowing());
                assertEquals(LoggerGaugeTheme.STI_NIGHT, context.getPreferences().getDashboardTile("style-a").getGaugeTheme());
                assertNull(context.getPreferences().getDashboardTile("style-b"));
                context.getPreferences().setGaugeTheme(LoggerGaugeTheme.CHRONO_ROLL);
                window[0].setGaugesOnly(true);
                FxMountedGaugePane mounted = field(window[0], "mountedGauges");
                assertEquals(GaugeFaceRenderer.Style.STI_NIGHT, field(mounted.getChildren().get(0), "style"));
                assertEquals(GaugeFaceRenderer.Style.CHRONO_ROLL, field(mounted.getChildren().get(1), "style"));
                window[0].setGaugesOnly(false);
                method.invoke(window[0], "style-a");
                picker = field(window[0], "gaugeStylePicker"); picker.close();
                assertEquals(LoggerGaugeTheme.STI_NIGHT, context.getPreferences().getDashboardTile("style-a").getGaugeTheme());
                method.invoke(window[0], "style-a");
                picker = field(window[0], "gaugeStylePicker");
                picker.getDialogPane().lookupAll(".button").stream().filter(node -> node instanceof Button button
                        && button.getText().equals("Use default")).map(node -> (Button) node).findFirst().orElseThrow().fire();
                assertNull(context.getPreferences().getDashboardTile("style-a").getGaugeTheme());
                assertSame(session, context.getSession());
                assertEquals(LoggerSessionState.RECORDING, context.getSession().getState());
                assertEquals(2, context.getLiveData().getLatestSamples().size());
                assertTrue(context.getChannels().getChannels().stream().allMatch(LoggerChannel::isSelected));
            });
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) {
                    LoggerWorkspaceContext context = field(window[0], "context");
                    context.getLiveData().stopped();
                    context.getPreferences().setDashboardTile("style-a", null);
                    context.getPreferences().setDashboardTile("style-b", null);
                    context.getPreferences().setGaugeTheme(LoggerGaugeTheme.RR2_CLASSIC);
                    context.getPreferences().setGaugeDisplay(new LoggerGaugeDisplay());
                    window[0].close();
                }
            });
        }
    }
    private static void capture(FxGaugeStylePicker picker) throws Exception {
        String target = System.getenv("RR2_GAUGE_PICKER_CAPTURE");
        if (target == null || target.isBlank()) return;
        picker.getDialogPane().applyCss(); picker.getDialogPane().layout();
        var snapshot = picker.getDialogPane().snapshot(null, null);
        var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
        assertTrue(javax.imageio.ImageIO.write(image, "png", new java.io.File(target)));
    }
    private static <T> T field(Object object, String name) throws Exception {
        return FxEditorControlsSmokeTest.field(object, name);
    }
}
