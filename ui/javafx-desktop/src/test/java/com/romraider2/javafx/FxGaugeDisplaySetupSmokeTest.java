/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.api.*;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxGaugeDisplaySetupSmokeTest {
    @Test void fullscreenRecordingButtonsFollowActualStateAndPendingCommands() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {});
                ((Stage) field(window[0], "stage")).show();
                window[0].setGaugesOnly(true);
                Button start = field(window[0], "mountedStartRecording"), stop = field(window[0], "mountedStopRecording");
                var update = FxLoggerWindow.class.getDeclaredMethod("updateMountedRecordingControls"); update.setAccessible(true);
                var bus = context(window[0]).getLiveData();
                var session = context(window[0]).getSession();
                bus.stopped(); update.invoke(window[0]);
                assertTrue(start.isDisabled()); assertTrue(stop.isDisabled());
                bus.readingData(); update.invoke(window[0]);
                assertFalse(start.isDisabled()); assertTrue(stop.isDisabled());
                assertNotNull(start.getOnAction()); assertNotNull(stop.getOnAction());
                java.util.concurrent.atomic.AtomicBoolean pending = field(session, "commandPending");
                try {
                    pending.set(true); update.invoke(window[0]);
                    assertTrue(start.isDisabled()); assertTrue(stop.isDisabled());
                } finally { pending.set(false); }
                bus.loggingData(); update.invoke(window[0]);
                assertTrue(start.isDisabled()); assertFalse(stop.isDisabled());
                bus.readingData(); update.invoke(window[0]);
                assertFalse(start.isDisabled()); assertTrue(stop.isDisabled());
                bus.stopped(); update.invoke(window[0]);
                assertTrue(start.isDisabled()); assertTrue(stop.isDisabled());
            });
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) { context(window[0]).getLiveData().stopped(); window[0].close(); }
            });
        }
    }

    @Test void assignChannelUsesDialogFooterBesideCancelAndDoesNotChangeLoggingSelection() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {});
                ((Stage) field(window[0], "stage")).show();
                context(window[0]).getChannels().replaceChannels(java.util.List.of(
                        new LoggerChannel("fixture-rpm", "Engine Speed", "rpm", LoggerChannelKind.PARAMETER, true)));
            });
            FxTestRuntime.run(() -> {
                window[0].setGaugesOnly(true);
                var open = FxLoggerWindow.class.getDeclaredMethod("chooseMountedChannel", int.class); open.setAccessible(true);
                open.invoke(window[0], 0);
                javafx.scene.control.Dialog<?> picker = field(window[0], "mountedChannelPicker");
                var pane = picker.getDialogPane(); pane.applyCss(); pane.layout();
                Button assign = (Button) pane.lookup("#gauges-assign-channel");
                var cancel = pane.lookupButton(javafx.scene.control.ButtonType.CANCEL);
                assertTrue(assign.isDisabled());
                assertSame(assign.getParent(), cancel.getParent(), "Assign and Cancel must share the dialog button bar");
                javafx.scene.control.ListView<?> list = (javafx.scene.control.ListView<?>) pane.getContent().lookup(".list-view");
                list.getSelectionModel().selectFirst();
                assertFalse(assign.isDisabled());
                var assignBounds = assign.localToScene(assign.getBoundsInLocal());
                var listBounds = list.localToScene(list.getBoundsInLocal());
                assertTrue(assignBounds.getMinY() >= listBounds.getMaxY());
                assertEquals(assignBounds.getMinY(), cancel.localToScene(cancel.getBoundsInLocal()).getMinY(), 1);
                assign.fire();
                assertFalse(picker.isShowing());
                assertEquals("fixture-rpm", context(window[0]).getPreferences().getGaugeDisplay().getSlots().getFirst());
                assertEquals(1, context(window[0]).getChannels().getChannels().stream().filter(LoggerChannel::isSelected).count());
            });
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) {
                    context(window[0]).getPreferences().setGaugeDisplay(new LoggerGaugeDisplay());
                    context(window[0]).getLiveData().stopped(); window[0].close();
                }
            });
        }
    }

    @Test void handheldSetupShowsTheCompleteChannelButtonLabel() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        String name = "Manifold Relative Pressure - Corrected Measurement";
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {});
                Stage stage = field(window[0], "stage");
                stage.setWidth(1280); stage.setHeight(800); stage.show();
                context(window[0]).getChannels().replaceChannels(java.util.List.of(
                    new LoggerChannel("synthetic-long-name", name, "psi", LoggerChannelKind.PARAMETER, true)));
                context(window[0]).getPreferences().setGaugeDisplay(new LoggerGaugeDisplay()
                    .withCount(1).withChannel(0, "synthetic-long-name"));
            });
            FxTestRuntime.run(() -> {
                window[0].setGaugesOnly(true);
                javafx.scene.layout.VBox setup = field(window[0], "mountedSetup");
                setup.setStyle("-fx-font-size: 18px;");
                javafx.scene.layout.BorderPane root = field(window[0], "root"); root.applyCss(); root.layout();
                Button channel = setup.lookupAll(".button").stream().filter(node -> node instanceof Button button
                    && button.getText().equals("1: " + name)).map(node -> (Button) node).findFirst().orElseThrow();
                javafx.scene.text.Text rendered = (javafx.scene.text.Text) channel.lookup(".text");
                assertNotNull(rendered);
                assertEquals(channel.getText(), rendered.getText(), "Channel button text was ellipsized");
                assertTrue(rendered.getBoundsInParent().getMaxY() <= channel.getHeight(), "Wrapped label exceeded button height");
            });
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) {
                    context(window[0]).getPreferences().setGaugeDisplay(new LoggerGaugeDisplay());
                    window[0].close();
                }
            });
        }
    }
    @Test void independentSlotsFitAllLayoutsAndTransientMenuPreservesSession() throws Exception {
        FxLoggerWindow[] window = new FxLoggerWindow[1];
        try {
            FxTestRuntime.run(() -> {
                window[0] = new FxLoggerWindow(() -> {});
                Stage stage = field(window[0], "stage"); FxWindowPlacement.show(stage);
                LoggerWorkspaceContext context = context(window[0]);
                var channels = new java.util.ArrayList<LoggerChannel>();
                for (int i = 0; i < 6; i++) channels.add(new LoggerChannel("slot-" + i, "Synthetic " + i,
                        "rpm", LoggerChannelKind.PARAMETER, i < 2));
                context.getChannels().replaceChannels(channels);
                context.getPreferences().setGaugeDisplay(new LoggerGaugeDisplay());
                context.getLiveData().loggingData(); // No adapter or writer.
                context.getLiveData().publish(new LiveDataSample("slot-0", "Synthetic 0", 4200, "4200", "rpm", 1));
            });
            FxTestRuntime.run(() -> {
                var context = context(window[0]); Object session = context.getSession();
                window[0].setGaugesOnly(true);
                assertTrue(context.getPreferences().getGaugeDisplay().getVisibleChannels().isEmpty());
                assign(window[0], new LoggerGaugeDisplay().withChannel(0, "slot-5").withCount(1));
                FxMountedGaugePane gauges = field(window[0], "mountedGauges");
                assertEquals(1, gauges.getChildren().size());
                assertTrue(gauges.getChildren().getFirst().getAccessibleText().contains("no valid data"));
                javafx.scene.layout.VBox setup = field(window[0], "mountedSetup");
                setup.lookupAll(".button").stream().filter(node -> node instanceof Button button
                        && button.getText().equals("Use Logger Channels")).map(node -> (Button) node).findFirst().orElseThrow().fire();
                assertEquals(java.util.List.of("slot-0", "slot-1"), context.getPreferences().getGaugeDisplay().getVisibleChannels());
                LoggerGaugeDisplay all = new LoggerGaugeDisplay();
                for (int i = 0; i < 6; i++) all = all.withChannel(i, "slot-" + i);
                for (int count = 1; count <= 6; count++) {
                    assign(window[0], all.withCount(count));
                    assertEquals(count, gauges.getChildren().size());
                    for (int[] size : new int[][]{{480, 800}, {800, 480}, {160, 100}}) {
                        gauges.resize(size[0], size[1]); gauges.layout();
                        for (var child : gauges.getChildren()) {
                            var bounds = child.getBoundsInParent();
                            assertTrue(bounds.getMinX() >= 0 && bounds.getMinY() >= 0);
                            assertTrue(bounds.getMaxX() <= size[0] && bounds.getMaxY() <= size[1]);
                        }
                    }
                    assertEquals("slot-5", context.getPreferences().getGaugeDisplay().getSlots().get(5));
                }
                window[0].setMountedFullScreen(true);
                assertTrue(((Stage) field(window[0], "stage")).isFullScreen());
                assertFalse(((HBox) field(window[0], "mountedMenu")).isVisible());
                tap(window[0]);
                assertTrue(((HBox) field(window[0], "mountedMenu")).isVisible());
                assertSame(session, context.getSession());
                assertEquals(2, context.getChannels().getChannels().stream().filter(LoggerChannel::isSelected).count());
            });
            Thread.sleep(2700);
            FxTestRuntime.run(() -> tap(window[0]));
            Thread.sleep(2700);
            FxTestRuntime.run(() -> assertTrue(((HBox) field(window[0], "mountedMenu")).isVisible()));
            Thread.sleep(2800);
            FxTestRuntime.run(() -> {
                assertFalse(((HBox) field(window[0], "mountedMenu")).isVisible());
                capture(window[0], "fullscreen");
                tap(window[0]);
                HBox menu = field(window[0], "mountedMenu"); ((Button) menu.getChildren().getFirst()).fire();
                assertFalse((Boolean) field(window[0], "mountedFullScreen"));
                assertFalse(((Stage) field(window[0], "stage")).isFullScreen());
                assertEquals(LoggerSessionState.RECORDING, context(window[0]).getSession().getState());
                assertEquals(1, context(window[0]).getLiveData().getLatestSamples().size());
                window[0].setGaugesOnly(false); window[0].setGaugesOnly(true);
                assertEquals(6, context(window[0]).getPreferences().getGaugeDisplay().getVisibleChannels().size());
                capture(window[0], "setup");
            });
        } finally {
            FxTestRuntime.run(() -> {
                if (window[0] != null) {
                    context(window[0]).getPreferences().setGaugeDisplay(new LoggerGaugeDisplay());
                    context(window[0]).getLiveData().stopped(); window[0].close();
                }
            });
        }
    }
    private static void tap(FxLoggerWindow window) throws Exception {
        StackPane viewport = field(window, "mountedViewport");
        viewport.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 100, 100, 100, 100, MouseButton.PRIMARY,
                1, false, false, false, false, false, false, false, false, false, false, null));
    }
    private static void assign(FxLoggerWindow window, LoggerGaugeDisplay display) throws Exception {
        var method = FxLoggerWindow.class.getDeclaredMethod("setGaugeDisplay", LoggerGaugeDisplay.class);
        method.setAccessible(true); method.invoke(window, display);
    }
    private static void capture(FxLoggerWindow window, String suffix) throws Exception {
        String prefix = System.getenv("RR2_GAUGE_DISPLAY_CAPTURE_PREFIX");
        if (prefix == null || prefix.isBlank()) return;
        javafx.scene.layout.BorderPane root = field(window, "root"); root.applyCss(); root.layout();
        var snapshot = root.snapshot(null, null);
        var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
        assertTrue(javax.imageio.ImageIO.write(image, "png", new java.io.File(prefix + "-" + suffix + ".png")));
    }
    private static LoggerWorkspaceContext context(FxLoggerWindow window) throws Exception { return field(window, "context"); }
    private static <T> T field(Object object, String name) throws Exception { return FxEditorControlsSmokeTest.field(object, name); }
}
