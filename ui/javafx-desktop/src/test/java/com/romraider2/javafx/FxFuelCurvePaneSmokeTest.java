/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.*;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxFuelCurvePaneSmokeTest {
    private FuelLogAnalysis.Result result() throws Exception {
        LogDataset log = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time,Input (V),Learning (%),Correction (%)\n0,.2,2,0\n1,1.2,4,0\n2,3.2,8,0\n"));
        return FuelLogAnalysis.maf(log, LogRange.all(log), 1, 2, 3, 1, List.of());
    }
    private static Future<?> click(FxFuelCurvePane pane, String action) throws Exception {
        ((Button) field(pane, action)).fire(); return field(pane, "pending");
    }
    private static TableView<FxFuelCurvePane.Row> table(FxFuelCurvePane pane) throws Exception { return field(pane, "table"); }

    @Test void interpolationPreservesRequestedOrderAndSplitsChartAtMissingBins() throws Exception {
        FuelLogAnalysis.Result data = result(); FxFuelCurvePane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxFuelCurvePane(false); pane[0].setAnalysis(data);
                ((TextField) field(pane[0], "targets")).setText("3.2, 0.7, 2, 4"); job[0] = click(pane[0], "interpolate");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                var rows = table(pane[0]).getItems(); assertEquals(4, rows.size());
                assertEquals(3.2, rows.get(0).x()); assertEquals(8, rows.get(0).y()); assertEquals(3, rows.get(1).y(), 1e-12);
                assertTrue(Double.isNaN(rows.get(2).y())); assertTrue(Double.isNaN(rows.get(3).y()));
                LineChart<?, ?> chart = field(pane[0], "chart"); assertEquals(2, chart.getData().size());
                assertEquals(2, chart.getData().get(0).getData().size()); assertEquals(1, chart.getData().get(1).getData().size());
                assertFalse(((Button) field(pane[0], "copy")).isDisabled());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void fittingDisplaysStatisticsAndChangingReviewInputsClearsOldOutputs() throws Exception {
        FuelLogAnalysis.Result data = result(); FxFuelCurvePane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxFuelCurvePane(false); pane[0].setAnalysis(data);
                ((Spinner<Integer>) field(pane[0], "degree")).getValueFactory().setValue(1);
                ((TextField) field(pane[0], "targets")).setText(".2,1.5,3.2,4"); job[0] = click(pane[0], "fit");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertEquals(4.6, table(pane[0]).getItems().get(1).y(), 1e-12);
                assertTrue(Double.isNaN(table(pane[0]).getItems().get(3).y()));
                String status = ((Label) field(pane[0], "status")).getText();
                assertTrue(status.contains("3 raw samples")); assertTrue(status.contains("RMSE")); assertTrue(status.contains("R²"));
                ((TextField) field(pane[0], "targets")).setText("NaN");
                assertTrue(table(pane[0]).getItems().isEmpty()); assertTrue(((Button) field(pane[0], "copy")).isDisabled());
                click(pane[0], "fit"); assertTrue(((Label) field(pane[0], "status")).getText().contains("finite numbers"));
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void replacementCancelsQueuedFitAndCloseShutsDownItsWorker() throws Exception {
        FuelLogAnalysis.Result data = result(); FxFuelCurvePane[] pane = {null}; Future<?>[] job = {null};
        CountDownLatch release = new CountDownLatch(1);
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxFuelCurvePane(false); pane[0].setAnalysis(data);
                ((ExecutorService) field(pane[0], "worker")).submit(() -> {
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test gate timeout"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                });
                job[0] = click(pane[0], "fit"); pane[0].setAnalysis(null);
                assertTrue(job[0].isCancelled()); assertTrue(((Button) field(pane[0], "fit")).isDisabled());
            });
            release.countDown(); ((ExecutorService) field(pane[0], "worker")).submit(() -> {}).get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertTrue(table(pane[0]).getItems().isEmpty()); assertNull(field(pane[0], "analysis"));
                pane[0].close(); assertTrue(((ExecutorService) field(pane[0], "worker")).isShutdown());
            });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void parentInputEditsClearTheCompletedAnalysisUsedByTheCurvePane() throws Exception {
        LogDataset data = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time,Input (V),Learning (%),Correction (%)\n0,.2,2,0\n1,1.2,4,0\n2,3.2,8,0\n"));
        FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.MAF, () -> {}); pane[0].setDataset(data);
                for (int i = 0; i < 3; i++) ((ComboBox<LogChannel>) field(pane[0], List.of("x", "y", "correction").get(i))).setValue(data.getChannels().get(i + 1));
                ((CheckBox) field(pane[0], "confirmed")).setSelected(true); ((Button) field(pane[0], "calculate")).fire(); job[0] = field(pane[0], "pending");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                FxFuelCurvePane curve = field(pane[0], "curve"); assertNotNull(field(curve, "analysis"));
                ((Spinner<Integer>) field(curve, "degree")).getValueFactory().setValue(1);
                job[0] = click(curve, "fit");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                FxFuelCurvePane curve = field(pane[0], "curve"); assertFalse(table(curve).getItems().isEmpty());
                javafx.stage.Stage stage = new javafx.stage.Stage();
                try {
                    javafx.scene.Scene scene = new javafx.scene.Scene(pane[0], 1000, 640); FxTheme.apply(stage, scene); stage.setScene(scene); stage.show();
                    ((TabPane) pane[0].getCenter()).getSelectionModel().select(2); pane[0].applyCss(); pane[0].layout();
                    Button fit = field(curve, "fit"); var bounds = fit.localToScene(fit.getBoundsInLocal());
                    assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= scene.getWidth(), "Curve controls must fit the small workspace");
                    String capture = System.getenv("RR2_CURVE_CAPTURE");
                    if (capture != null && !capture.isBlank()) {
                        var snapshot = pane[0].snapshot(null, null);
                        java.awt.image.BufferedImage bitmap = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) bitmap.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        assertTrue(javax.imageio.ImageIO.write(bitmap, "png", new java.io.File(capture)));
                    }
                } finally { stage.close(); }
                ((TextField) field(pane[0], "binWidth")).setText(".2"); assertNull(field(curve, "analysis"));
                assertTrue(((Button) field(curve, "fit")).isDisabled());
                pane[0].close(); assertTrue(((ExecutorService) field(curve, "worker")).isShutdown());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void explicitEvaluationInputsAreFiniteBoundedAndDoNotSortOrDeduplicate() {
        assertEquals(List.of(2.0, 1.0, 2.0), FxFuelCurvePane.parseTargets("2, 1 2"));
        for (String invalid : List.of("NaN", "Infinity", "1,x", "1 ".repeat(513), " ".repeat(8193), ",")) {
            assertThrows(IllegalArgumentException.class, () -> FxFuelCurvePane.parseTargets(invalid));
        }
    }

    @Test void injectorForcesLinearFitAndLabelsItsEstimatesAsUnverified() throws Exception {
        StringBuilder csv = new StringBuilder("Time,Pulse (ms),Load (g/rev)\n");
        for (int i = 1; i <= 5; i++) csv.append(i).append(',').append(i).append(',')
                .append(.02 * (i - .5) * 2 * 14.7 * 732 / 1000).append('\n');
        LogDataset log = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(csv.toString()));
        FuelLogAnalysis.Result data = FuelLogAnalysis.injector(log, LogRange.all(log), 1, 2, 14.7, 732, 1, List.of());
        FxFuelCurvePane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> { pane[0] = new FxFuelCurvePane(true); pane[0].setAnalysis(data); job[0] = click(pane[0], "fit"); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                String status = ((Label) field(pane[0], "status")).getText();
                assertTrue(status.contains("Degree 1")); assertTrue(status.contains("Apparent flow 1200"));
                assertTrue(status.contains("not verified injector latency")); assertTrue(status.contains("not establish a safe calibration"));
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void interpolationChartDoesNotBridgeAGapNarrowerThanThePlottingGrid() throws Exception {
        LogDataset log = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time,Input (V),Learning (%),Correction (%)\n0,.2,2,0\n1,1.2,4,0\n2,3.2,8,0\n3,1000.2,9,0\n"));
        FuelLogAnalysis.Result data = FuelLogAnalysis.maf(log, LogRange.all(log), 1, 2, 3, 1, List.of());
        FxFuelCurvePane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> { pane[0] = new FxFuelCurvePane(false); pane[0].setAnalysis(data); job[0] = click(pane[0], "interpolate"); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                LineChart<?, ?> chart = field(pane[0], "chart");
                assertEquals(3, chart.getData().size()); assertEquals(2, chart.getData().get(0).getData().size());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
}
