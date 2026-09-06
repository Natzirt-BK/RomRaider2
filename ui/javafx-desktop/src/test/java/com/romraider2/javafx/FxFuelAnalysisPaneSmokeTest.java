/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.romraider.logger.analysis.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxFuelAnalysisPaneSmokeTest {
    private LogDataset data(String name) throws Exception {
        return new RomRaiderCsvLogParser().parse(name, new StringReader(
                "Time (msec),MAF (V),Learning (%),Correction (%),Pulse (ms),Load (g/rev),State\n"
                + "0,2.31,4,-1,2.25,1.2,8\n1,2.32,2,3,2.26,1.4,10\n"));
    }
    private void map(FxFuelAnalysisPane pane, String name, int index) throws Exception {
        ComboBox<LogChannel> choice = field(pane, name);
        choice.setValue(choice.getItems().stream().filter(channel -> channel.getIndex() == index).findFirst().orElseThrow());
    }
    private FxFuelAnalysisPane maf(LogDataset data) throws Exception {
        FxFuelAnalysisPane pane = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.MAF, () -> {});
        pane.setDataset(data); map(pane, "x", 1); map(pane, "y", 2); map(pane, "correction", 3);
        return pane;
    }
    private void confirmed(FxFuelAnalysisPane pane) throws Exception { ((CheckBox) field(pane, "confirmed")).setSelected(true); }
    private Future<?> calculate(FxFuelAnalysisPane pane) throws Exception {
        ((Button) field(pane, "calculate")).fire(); return field(pane, "pending");
    }
    private int rows(FxFuelAnalysisPane pane) throws Exception { return ((TableView<?>) field(pane, "results")).getItems().size(); }

    @Test void requiresConfirmationAndInvalidatesResultsWhenInputsChange() throws Exception {
        LogDataset data = data("first.csv"); FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = maf(data); assertNull(calculate(pane[0]));
                assertTrue(((Label) field(pane[0], "status")).getText().contains("Confirm"));
                confirmed(pane[0]); job[0] = calculate(pane[0]);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertEquals(1, rows(pane[0]));
                TableView<FuelLogAnalysis.Bin> results = field(pane[0], "results");
                assertEquals(4, results.getItems().get(0).getMean(), 1e-12);
                assertFalse(((Button) field(pane[0], "copy")).isDisabled());
                ((TextField) field(pane[0], "binWidth")).setText("0.2");
                assertEquals(0, rows(pane[0])); assertTrue(((Button) field(pane[0], "copy")).isDisabled());
                map(pane[0], "x", 4);
                assertFalse(((CheckBox) field(pane[0], "confirmed")).isSelected());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void enteredFilterCannotSilentlyDisappearAndInclusiveFilterIsApplied() throws Exception {
        LogDataset data = data("filtered.csv"); FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = maf(data); confirmed(pane[0]);
                Object filter = ((List<?>) field(pane[0], "filters")).get(0);
                ((TextField) field(filter, "minimum")).setText("8");
                ((TextField) field(filter, "maximum")).setText("8");
                assertNull(calculate(pane[0]));
                assertTrue(((Label) field(pane[0], "status")).getText().contains("Select a channel"));
                ComboBox<LogChannel> choice = field(filter, "channel"); choice.setValue(data.getChannels().get(6));
                job[0] = calculate(pane[0]);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                TableView<FuelLogAnalysis.Bin> results = field(pane[0], "results");
                assertEquals(1, results.getItems().get(0).getCount());
                assertEquals(3, results.getItems().get(0).getMean());
                assertTrue(((Label) field(pane[0], "status")).getText().contains("1 filtered"));
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void replacingLogCancelsQueuedWorkAndClearsMappingsAndClosingStopsWorker() throws Exception {
        LogDataset first = data("first.csv"), second = data("second.csv");
        FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null}; CountDownLatch release = new CountDownLatch(1);
        try {
            FxTestRuntime.run(() -> {
                pane[0] = maf(first); confirmed(pane[0]);
                ExecutorService worker = field(pane[0], "worker");
                worker.submit(() -> { try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
                job[0] = calculate(pane[0]); pane[0].setDataset(second);
                assertTrue(job[0].isCancelled());
                assertNull(((ComboBox<?>) field(pane[0], "x")).getValue());
                assertFalse(((CheckBox) field(pane[0], "confirmed")).isSelected());
            });
            release.countDown();
            ExecutorService worker = field(pane[0], "worker"); worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertEquals(0, rows(pane[0])); assertSame(second, field(pane[0], "dataset"));
                pane[0].close(); assertTrue(worker.isShutdown());
                pane[0].setDataset(first); assertNull(field(pane[0], "dataset"));
            });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void injectorProjectionAndSmallWindowSetupWorkWithoutAnyLoggerRuntime() throws Exception {
        LogDataset data = data("injector.csv"); FxFuelAnalysisPane[] pane = {null}; Stage[] stage = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.INJECTOR, () -> {});
                pane[0].setDataset(data); map(pane[0], "x", 4); map(pane[0], "y", 5); confirmed(pane[0]);
                stage[0] = new Stage(); Scene scene = new Scene(pane[0], 853, 400);
                FxTheme.apply(stage[0], scene); stage[0].setScene(scene); FxWindowPlacement.show(stage[0]);
                pane[0].applyCss(); pane[0].layout();
                ScrollPane scroll = (ScrollPane) pane[0].getLeft();
                assertTrue(scroll.isFitToWidth());
                assertTrue(scroll.getContent().getBoundsInLocal().getHeight() > scroll.getViewportBounds().getHeight());
                job[0] = calculate(pane[0]);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                TableView<FuelLogAnalysis.Bin> result = field(pane[0], "results");
                assertEquals(1.3 / 2 / 14.7 * 1000 / 732, result.getItems().get(0).getMean(), 1e-12);
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); if (stage[0] != null) stage[0].close(); }); }
    }
}
