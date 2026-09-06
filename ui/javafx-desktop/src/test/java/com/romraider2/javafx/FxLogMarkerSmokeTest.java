/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.nio.file.*;
import java.util.List;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLogMarkerSmokeTest {
    @TempDir Path directory;
    private static FlowPane controls(FxLogAnalysisPane pane) {
        return (FlowPane) ((VBox) ((BorderPane) ((TabPane) pane.getCenter()).getTabs().get(4).getContent()).getTop()).getChildren().get(0);
    }
    private static Button button(FxLogAnalysisPane pane, String label) {
        return controls(pane).getChildren().stream().filter(node -> node instanceof Button).map(node -> (Button) node)
                .filter(button -> label.equals(button.getText())).findFirst().orElseThrow();
    }
    private static void await(FxLogAnalysisPane pane) throws Exception {
        FxLogMarkerSession session = field(pane, "markerSession"); FxLogStatisticsSmokeTest.awaitWork(session.pending());
    }
    @Test void nativeControlsLoadSaveConflictReloadAndEmptyStateLeaveCsvUntouched() throws Exception {
        Path source = directory.resolve("synthetic.csv"); String csv = "Value\n1\n2\n"; Files.writeString(source, csv);
        var data = new RomRaiderCsvLogParser().parse(source.toFile()); var store = new LogMarkerStore();
        FxTestRuntime.run(() -> {
            try (var pane = new FxLogAnalysisPane(source.toFile(), data)) {
                assertTrue(button(pane, "Add at cursor").isDisabled()); await(pane);
                assertFalse(button(pane, "Add at cursor").isDisabled());
                ListView<LogMarker> markers = field(pane, "markers");
                TextField label = controls(pane).getChildren().stream().filter(node -> node instanceof TextField).map(node -> (TextField) node).findFirst().orElseThrow();
                label.setText("my marker"); button(pane, "Add at cursor").fire();
                assertTrue(markers.getItems().isEmpty()); assertTrue(button(pane, "Add at cursor").isDisabled()); await(pane);
                assertEquals("my marker", markers.getItems().get(0).getLabel()); assertEquals("my marker", store.load(source.toFile(), 2).get(0).getLabel());
                store.save(source.toFile(), List.of(new LogMarker(1, LogMarkerType.CUSTOM, "external")));
                label.setText("attempt"); button(pane, "Add at cursor").fire(); await(pane);
                assertTrue(button(pane, "Add at cursor").isDisabled()); assertEquals("my marker", markers.getItems().get(0).getLabel());
                assertEquals("attempt", label.getText());
                button(pane, "Reload markers").fire(); await(pane); assertEquals("external", markers.getItems().get(0).getLabel());
                markers.getSelectionModel().select(0); button(pane, "Remove selected").fire(); await(pane);
                assertTrue(markers.getItems().isEmpty()); assertTrue(store.load(source.toFile(), 2).isEmpty());
                assertTrue(Files.isRegularFile(store.sidecar(source.toFile()))); assertEquals(csv, Files.readString(source));
            }
        });
    }
    @Test void invalidSidecarKeepsMarkerEditingDisabledWithoutPreventingLogAnalysis() throws Exception {
        Path source = directory.resolve("synthetic.csv"); Files.writeString(source, "Value\n1\n2\n"); var store = new LogMarkerStore();
        Files.writeString(store.sidecar(source.toFile()), "format.version=77\nmarker.count=0\n");
        var data = new RomRaiderCsvLogParser().parse(source.toFile());
        FxTestRuntime.run(() -> {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            try (var pane = new FxLogAnalysisPane(source.toFile(), data)) {
                await(pane); assertTrue(button(pane, "Add at cursor").isDisabled());
                assertFalse(button(pane, "Reload markers").isDisabled());
                FxLogStatisticsSmokeTest.awaitStatistics(pane);
                TableView<ChannelStatistics> stats = field(pane, "statistics"); assertEquals(1.5, stats.getItems().get(0).getMean());
                Label status = field(pane, "markerStatus"); assertTrue(status.getText().contains("could not be loaded"));
                assertTrue(Files.readString(store.sidecar(source.toFile())).contains("77"));
                javafx.scene.Scene scene = new javafx.scene.Scene(pane, 800, 600);
                FxTheme.apply(stage, scene); stage.setScene(scene); stage.show();
                ((TabPane) pane.getCenter()).getSelectionModel().select(4); pane.applyCss(); pane.layout();
                ListView<?> markers = field(pane, "markers"); assertTrue(markers.getHeight() > 150);
                assertTrue(button(pane, "Reload markers").localToScene(button(pane, "Reload markers").getBoundsInLocal()).getMaxX() <= 800);
                String capture = System.getenv("RR2_MARKER_CAPTURE");
                if (capture != null && !capture.isBlank()) {
                    var snapshot = pane.snapshot(null, null);
                    var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    assertTrue(javax.imageio.ImageIO.write(image, "png", new java.io.File(capture)));
                }
            } finally { stage.close(); }
        });
    }
}
