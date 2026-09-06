/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxBinnedLogPaneSmokeTest {
    private LogDataset data() throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader("Time (msec),Load (g/rev),Speed (rpm),AFR\n"
                + "0,0,0,10\n100,.5,.5,20\n200,2,0,30\n300,0,2,40\n400,2,2,50\n500,1,1,\n"));
    }
    private void map(FxBinnedLogPane pane, LogDataset data, boolean grid) throws Exception {
        for (int i = 0; i < 3; i++) ((ComboBox<LogChannel>) field(pane, List.of("x", "y", "value").get(i))).setValue(data.getChannels().get(i + 1));
        ((TextField) field(pane, "xWidth")).setText("1"); ((TextField) field(pane, "yWidth")).setText("1");
        ((CheckBox) field(pane, "surface")).setSelected(grid);
    }
    private static Future<?> calculate(FxBinnedLogPane pane) throws Exception { ((Button) field(pane, "calculate")).fire(); return field(pane, "pending"); }
    private static BinnedLogAnalysis.Result result(FxBinnedLogPane pane) throws Exception { return field(pane, "result"); }
    private static TableView<BinnedLogAnalysis.Cell> table(FxBinnedLogPane pane) throws Exception { return field(pane, "table"); }
    @Test void surfaceTableHeatmapAndPlotKeepGapsAndRenderCompactly() throws Exception {
        LogDataset data = data(); FxLogAnalysisPane[] log = {null}; FxBinnedLogPane[] pane = {null}; Future<?>[] job = {null}; Stage[] stage = {null};
        try {
            FxTestRuntime.run(() -> {
                log[0] = new FxLogAnalysisPane(null, data); pane[0] = field(log[0], "binned"); map(pane[0], data, true); job[0] = calculate(pane[0]);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertEquals(9, table(pane[0]).getItems().size()); assertEquals(0, result(pane[0]).cellAt(1, 1).count());
                TableView<Integer> heatmap = field(pane[0], "heatmap"); assertEquals(3, heatmap.getItems().size()); assertEquals(4, heatmap.getColumns().size());
                assertEquals("— / 0", heatmap.getColumns().get(2).getCellObservableValue(Integer.valueOf(1)).getValue());
                stage[0] = new Stage(); Scene scene = new Scene(log[0], 1000, 640); FxTheme.apply(stage[0], scene); stage[0].setScene(scene); stage[0].show();
                TabPane outer = (TabPane) log[0].getCenter(); outer.getSelectionModel().select(outer.getTabs().stream().filter(tab -> tab.getText().equals("Binned analysis")).findFirst().orElseThrow());
                TabPane views = (TabPane) pane[0].getCenter();
                for (String name : List.of("Mean heatmap", "2D / 3D plot")) {
                    if (name.equals("2D / 3D plot")) ((ToggleButton) field(pane[0], "hideAxes")).setSelected(true);
                    views.getSelectionModel().select(views.getTabs().stream().filter(tab -> tab.getText().equals(name)).findFirst().orElseThrow());
                    log[0].applyCss(); log[0].layout(); FxBinnedLogPlot plot = field(pane[0], "plot"); assertTrue(views.getHeight() > 150);
                    String prefix = System.getenv("RR2_BINNED_CAPTURE");
                    if (prefix != null && !prefix.isBlank()) {
                        var snapshot = log[0].snapshot(null, null);
                        var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        assertTrue(javax.imageio.ImageIO.write(image, "png", new java.io.File(prefix + (name.equals("Mean heatmap") ? "-heatmap.png" : "-3d.png"))));
                    }
                }
            });
        } finally { FxTestRuntime.run(() -> { if (log[0] != null) log[0].close(); if (stage[0] != null) stage[0].close(); }); }
    }
    @Test void curveIgnoresUnusedYAndMinimumCountSuppressesValuesWithoutInventingCounts() throws Exception {
        LogDataset data = data(); FxBinnedLogPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxBinnedLogPane(data); map(pane[0], data, false);
                ((ComboBox<LogChannel>) field(pane[0], "y")).setValue(null); ((TextField) field(pane[0], "yWidth")).setText("");
                ((Spinner<Integer>) field(pane[0], "minimumCount")).getValueFactory().setValue(3); job[0] = calculate(pane[0]);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertEquals(5, result(pane[0]).accepted()); assertEquals(3, result(pane[0]).columns());
                assertEquals(3, result(pane[0]).cellAt(0, 0).count()); assertEquals(2, result(pane[0]).cellAt(0, 2).count());
                assertEquals("—", table(pane[0]).getColumns().get(5).getCellObservableValue(result(pane[0]).cellAt(0, 2)).getValue());
                assertEquals("Low count", table(pane[0]).getColumns().get(8).getCellObservableValue(result(pane[0]).cellAt(0, 2)).getValue());
                FxBinnedLogPlot plot = field(pane[0], "plot"); assertSame(result(pane[0]), field(plot, "result"));
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void copyUsesCanonicalHeadersAndOrderAfterVisualColumnReordering() throws Exception {
        LogDataset data = data(); FxBinnedLogPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> { pane[0] = new FxBinnedLogPane(data); map(pane[0], data, false); job[0] = calculate(pane[0]); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                var first = table(pane[0]).getColumns().remove(0); table(pane[0]).getColumns().add(first); ((Button) field(pane[0], "copy")).fire();
                String copied = Clipboard.getSystemClipboard().getString(); assertTrue(copied.contains("X from\tX to (exclusive)\tY from"));
                assertTrue(copied.contains("\n0.0\t1.0\t—\t—\t3\t23.333333333333332\t10.0\t40.0\tMeets count"));
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void changedInputsCancelQueuedJobsAndCompletedCallbacksCannotReviveThem() throws Exception {
        LogDataset data = data(); FxBinnedLogPane[] pane = {null}; Future<?>[] job = {null}; CountDownLatch release = new CountDownLatch(1);
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxBinnedLogPane(data); map(pane[0], data, true);
                ((ExecutorService) field(pane[0], "worker")).submit(() -> { try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); } });
                job[0] = calculate(pane[0]); ((TextField) field(pane[0], "xWidth")).setText("2"); assertTrue(job[0].isCancelled()); assertNull(result(pane[0]));
            });
            release.countDown(); ((ExecutorService) field(pane[0], "worker")).submit(() -> {}).get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { calculate(pane[0]).get(10, TimeUnit.SECONDS); ((CheckBox) field(pane[0], "surface")).setSelected(false); });
            FxTestRuntime.run(() -> { assertNull(result(pane[0])); assertTrue(table(pane[0]).getItems().isEmpty()); });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void sharedRangeDraftBlocksBinningAndAppliedRangeReplacesOldResults() throws Exception {
        LogDataset data = data(); FxLogAnalysisPane[] log = {null}; FxBinnedLogPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                log[0] = new FxLogAnalysisPane(null, data); pane[0] = field(log[0], "binned"); map(pane[0], data, false);
                log[0].invalidateSharedRange(); assertTrue(((Button) field(pane[0], "calculate")).isDisabled());
                log[0].selectRange(LogRange.of(2, 4, 6)); assertFalse(((Button) field(pane[0], "calculate")).isDisabled()); job[0] = calculate(pane[0]);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertEquals(2, result(pane[0]).accepted()); log[0].selectRange(LogRange.all(data)); assertNull(result(pane[0]));
                log[0].close(); assertTrue(((ExecutorService) field(pane[0], "worker")).isShutdown());
            });
        } finally { FxTestRuntime.run(() -> { if (log[0] != null) log[0].close(); }); }
    }
    @Test void missingMappingAndInvalidWidthProduceActionableErrors() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (var pane = new FxBinnedLogPane(data)) {
                assertNull(calculate(pane)); assertTrue(((Label) field(pane, "status")).getText().contains("Map every"));
                map(pane, data, true); ((TextField) field(pane, "xWidth")).setText("0");
                assertNull(calculate(pane)); assertTrue(((Label) field(pane, "status")).getText().contains("positive")); assertNull(result(pane));
                assertEquals(.5, FxBinnedLogPlot.normalized(0, -Double.MAX_VALUE, Double.MAX_VALUE));
            }
        });
    }
}
