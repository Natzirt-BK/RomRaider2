/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.*;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxFuelSamplePaneSmokeTest {
    private LogDataset data() throws Exception {
        StringBuilder csv = new StringBuilder("Time (msec),V (V),Learning (%),Correction (%),State,Aux,Aux\n");
        for (int row = 0; row < 410; row++) csv.append(row * 100).append(",1,2,0,").append(row % 2 == 0 ? 8 : 0)
                .append(',').append(row == 0 ? "" : Integer.toString(row)).append(',').append(-row).append('\n');
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(csv.toString()));
    }
    private FuelLogAnalysis.Result result(LogDataset data, int state) {
        return FuelLogAnalysis.maf(data, LogRange.all(data), 1, 2, 3, 1, List.of(new FuelLogAnalysis.Filter(4, state, state)));
    }
    private static Future<?> click(Object pane, String name) throws Exception {
        ((Button) field(pane, name)).fire(); return field(pane, "pending");
    }
    private static TableView<Integer> rows(FxFuelSamplePane pane) throws Exception { return field(pane, "samples"); }
    private static String statistics(FxFuelSamplePane pane) throws Exception { return ((TextArea) field(pane, "statistics")).getText(); }
    private static void select(FxFuelSamplePane pane, LogDataset data, int index) throws Exception {
        ((ComboBox<LogChannel>) field(pane, "channel")).setValue(data.getChannels().get(index));
    }
    private static void gate(FxFuelSamplePane pane, CountDownLatch release) throws Exception {
        ((ExecutorService) field(pane, "worker")).submit(() -> {
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test gate timeout"); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        });
    }
    @Test void pagesOriginalRowsButCalculatesAcrossEveryAcceptedSampleAndDistinguishesDuplicateColumns() throws Exception {
        LogDataset data = data(); FxFuelSamplePane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> { pane[0] = new FxFuelSamplePane(); pane[0].setAnalysis(result(data, 8)); job[0] = click(pane[0], "inspect"); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertEquals(200, rows(pane[0]).getItems().size()); assertEquals(0, rows(pane[0]).getItems().get(0));
                ((Button) field(pane[0], "next")).fire(); assertEquals(List.of(400, 402, 404, 406, 408), rows(pane[0]).getItems());
                assertTrue(((Button) field(pane[0], "next")).isDisabled());
                select(pane[0], data, 5); job[0] = click(pane[0], "calculate");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertTrue(statistics(pane[0]).contains("all 205 accepted rows"));
                assertTrue(statistics(pane[0]).contains("Finite: 204 · Missing: 1"));
                assertTrue(statistics(pane[0]).contains("Mean: 205.000000"));
                select(pane[0], data, 6); assertTrue(statistics(pane[0]).isEmpty()); job[0] = click(pane[0], "calculate");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertTrue(statistics(pane[0]).startsWith("[7] Aux")); assertTrue(statistics(pane[0]).contains("Mean: -204.000000"));
                var stage = new javafx.stage.Stage();
                try {
                    stage.setScene(new javafx.scene.Scene(pane[0], 680, 520)); stage.show(); pane[0].applyCss(); pane[0].layout();
                    assertTrue(rows(pane[0]).getHeight() > 80);
                    String capture = System.getenv("RR2_ACCEPTED_SAMPLES_CAPTURE");
                    if (capture != null && !capture.isBlank()) {
                        var snapshot = pane[0].snapshot(null, null);
                        var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        assertTrue(javax.imageio.ImageIO.write(image, "png", new java.io.File(capture)));
                    }
                } finally { stage.close(); }
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void replacingAnalysisCancelsQueuedCaptureAndCloseDisposesWorker() throws Exception {
        LogDataset data = data(); FxFuelSamplePane[] pane = {null}; Future<?>[] job = {null}; CountDownLatch release = new CountDownLatch(1);
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxFuelSamplePane(); pane[0].setAnalysis(result(data, 8)); gate(pane[0], release);
                job[0] = click(pane[0], "inspect"); pane[0].setAnalysis(result(data(), 0));
                assertTrue(job[0].isCancelled()); assertNull(field(pane[0], "review"));
                assertTrue(rows(pane[0]).getItems().isEmpty()); assertTrue(((Button) field(pane[0], "calculate")).isDisabled());
            });
            release.countDown(); ((ExecutorService) field(pane[0], "worker")).submit(() -> {}).get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertTrue(rows(pane[0]).getItems().isEmpty()); pane[0].close();
                assertTrue(((ExecutorService) field(pane[0], "worker")).isShutdown()); assertTrue(((Button) field(pane[0], "inspect")).isDisabled());
            });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void channelChangesCancelQueuedStatisticsWithoutDiscardingAcceptedRows() throws Exception {
        LogDataset data = data(); FxFuelSamplePane[] pane = {null}; Future<?>[] job = {null}; CountDownLatch release = new CountDownLatch(1);
        try {
            FxTestRuntime.run(() -> { pane[0] = new FxFuelSamplePane(); pane[0].setAnalysis(result(data, 8)); job[0] = click(pane[0], "inspect"); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                gate(pane[0], release); job[0] = click(pane[0], "calculate"); select(pane[0], data, 6);
                assertTrue(job[0].isCancelled()); assertEquals(200, rows(pane[0]).getItems().size()); assertTrue(statistics(pane[0]).isEmpty());
            });
            release.countDown(); ((ExecutorService) field(pane[0], "worker")).submit(() -> {}).get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> assertTrue(statistics(pane[0]).isEmpty()));
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void emptyAcceptedSetDisplaysZeroCountsAndUnavailableStatistics() throws Exception {
        LogDataset data = data(); FxFuelSamplePane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> { pane[0] = new FxFuelSamplePane(); pane[0].setAnalysis(result(data, 99)); job[0] = click(pane[0], "inspect"); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertTrue(rows(pane[0]).getItems().isEmpty()); job[0] = click(pane[0], "calculate"); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertTrue(statistics(pane[0]).contains("Finite: 0 · Missing: 0")); assertTrue(statistics(pane[0]).contains("Mean: —")); });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void realFuelAnalysisPublishesReviewAndInputChangesInvalidateIt() throws Exception {
        LogDataset data = data(); FxFuelAnalysisPane[] parent = {null}; FxFuelSamplePane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                parent[0] = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.MAF, () -> {}); parent[0].setDataset(data);
                for (int i = 0; i < 3; i++) ((ComboBox<LogChannel>) field(parent[0], List.of("x", "y", "correction").get(i))).setValue(data.getChannels().get(i + 1));
                ((CheckBox) field(parent[0], "confirmed")).setSelected(true); pane[0] = field(parent[0], "acceptedSamples");
                job[0] = click(parent[0], "calculate");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertNotNull(field(pane[0], "analysis")); job[0] = click(pane[0], "inspect"); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertFalse(rows(pane[0]).getItems().isEmpty()); ((TextField) field(parent[0], "first")).setText("2");
                assertNull(field(pane[0], "analysis")); assertNull(field(pane[0], "review")); assertTrue(rows(pane[0]).getItems().isEmpty());
                parent[0].close(); assertTrue(((ExecutorService) field(pane[0], "worker")).isShutdown());
            });
        } finally { FxTestRuntime.run(() -> { if (parent[0] != null) parent[0].close(); }); }
    }
    @Test void completedButNotYetPublishedWorkersCannotReviveReplacedOutputs() throws Exception {
        LogDataset data = data(); FxFuelSamplePane[] pane = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxFuelSamplePane(); pane[0].setAnalysis(result(data, 8));
                // Only this synthetic test blocks the FX thread: the worker is
                // done but its queued UI callback cannot run until replacement.
                click(pane[0], "inspect").get(10, TimeUnit.SECONDS); pane[0].setAnalysis(null);
            });
            FxTestRuntime.run(() -> {
                assertNull(field(pane[0], "review")); assertTrue(rows(pane[0]).getItems().isEmpty());
                pane[0].setAnalysis(result(data, 8)); click(pane[0], "inspect").get(10, TimeUnit.SECONDS);
            });
            FxTestRuntime.run(() -> {
                assertNotNull(field(pane[0], "review")); click(pane[0], "calculate").get(10, TimeUnit.SECONDS);
                select(pane[0], data, 6);
            });
            FxTestRuntime.run(() -> assertTrue(statistics(pane[0]).isEmpty()));
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
}
