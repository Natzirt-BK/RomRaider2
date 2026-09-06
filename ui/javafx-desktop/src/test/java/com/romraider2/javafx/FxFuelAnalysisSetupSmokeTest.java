/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxFuelAnalysisSetupSmokeTest {
    @TempDir Path temporary;
    private LogDataset log(String headers, String rows) throws Exception {
        return new RomRaiderCsvLogParser().parse("private-path-not-exported.csv", new StringReader(headers + "\n" + rows));
    }
    private LogDataset data() throws Exception { return log("Time (msec),MAF (V),Learn (%),Correct (%),State", "0,2,1,2,8\n1,2,2,3,10\n"); }
    private FxFuelAnalysisPane pane(LogDataset data) throws Exception {
        FxFuelAnalysisPane pane = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.MAF, () -> {}); pane.setDataset(data);
        for (int i = 0; i < 3; i++) ((ComboBox<LogChannel>) field(pane, List.of("x", "y", "correction").get(i))).setValue(data.getChannels().get(i + 1));
        return pane;
    }
    private String status(FxFuelAnalysisPane pane) throws Exception { return ((Label) field(pane, "status")).getText(); }

    @Test void exportImportReviewsUnitsResetsRangeAndPreservesFilterIntent() throws Exception {
        LogDataset data = data(); FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null};
        Path path = temporary.resolve("saved.rr2analysis");
        try {
            FxTestRuntime.run(() -> {
                pane[0] = pane(data); ((TextField) field(pane[0], "first")).setText("2");
                Object filter = ((List<?>) field(pane[0], "filters")).get(0);
                ((ComboBox<LogChannel>) field(filter, "channel")).setValue(data.getChannels().get(4));
                ((TextField) field(filter, "minimum")).setText("8"); ((TextField) field(filter, "maximum")).setText("8");
                job[0] = pane[0].saveSetup(path);
            });
            job[0].get(10, TimeUnit.SECONDS);
            assertFalse(Files.readString(path).contains("private-path"));
            FxTestRuntime.run(() -> {
                ((TextField) field(pane[0], "binWidth")).setText(".5");
                ((CheckBox) field(pane[0], "confirmed")).setSelected(true);
                job[0] = pane[0].loadSetup(path);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertEquals("0.05", ((TextField) field(pane[0], "binWidth")).getText());
                assertEquals("1", ((TextField) field(pane[0], "first")).getText());
                assertEquals("2", ((TextField) field(pane[0], "last")).getText());
                assertFalse(((CheckBox) field(pane[0], "confirmed")).isSelected());
                assertTrue(status(pane[0]).contains("review"));
                ((CheckBox) field(pane[0], "confirmed")).setSelected(true);
                ((Button) field(pane[0], "calculate")).fire(); job[0] = field(pane[0], "pending");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertTrue(status(pane[0]).contains("1 filtered"));
                assertEquals(1, ((TableView<?>) field(pane[0], "results")).getItems().size());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void unresolvedFiltersAreNotDroppedAndDuplicateRequiredHeadersNeedRemapping() throws Exception {
        LogDataset first = data();
        LogDataset changed = log("Time (msec),MAF (V),MAF (V),Learn (%),Correct (%)", "0,1,2,3,4\n");
        FxFuelAnalysisPane[] pane = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = pane(first);
                Object filter = ((List<?>) field(pane[0], "filters")).get(0);
                ((ComboBox<LogChannel>) field(filter, "channel")).setValue(first.getChannels().get(4));
                ((TextField) field(filter, "minimum")).setText("8"); ((TextField) field(filter, "maximum")).setText("9");
                FuelAnalysisSetup saved = pane[0].snapshotSetup(); pane[0].setDataset(changed); pane[0].applySetup(saved);
                assertNull(((ComboBox<?>) field(pane[0], "x")).getValue());
                assertNull(((ComboBox<?>) field(filter, "channel")).getValue());
                assertEquals("8.0", ((TextField) field(filter, "minimum")).getText());
                assertTrue(status(pane[0]).contains("MAF (V)")); assertTrue(status(pane[0]).contains("State"));
                assertEquals("1", ((TextField) field(pane[0], "last")).getText());
                assertThrows(IllegalArgumentException.class, () -> pane[0].snapshotSetup());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void malformedAndWrongKindImportsPreserveInputsAndDisplayedResults() throws Exception {
        LogDataset data = data(); FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null};
        Path file = temporary.resolve("invalid.rr2analysis"); Files.writeString(file, "format.version=99\n");
        try {
            FxTestRuntime.run(() -> {
                pane[0] = pane(data); ((CheckBox) field(pane[0], "confirmed")).setSelected(true);
                ((Button) field(pane[0], "calculate")).fire(); job[0] = field(pane[0], "pending");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertEquals(1, ((TableView<?>) field(pane[0], "results")).getItems().size()); job[0] = pane[0].loadSetup(file); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                FuelAnalysisSetup before = pane[0].snapshotSetup();
                assertTrue(status(pane[0]).contains("unchanged"));
                FuelAnalysisSetup wrong = new FuelAnalysisSetup(FuelAnalysisSetup.Kind.INJECTOR, before.x(), before.y(), null, .1, 14.7, 732, List.of());
                assertThrows(IllegalArgumentException.class, () -> pane[0].applySetup(wrong));
                assertEquals(before, pane[0].snapshotSetup());
                assertTrue(((CheckBox) field(pane[0], "confirmed")).isSelected());
                assertEquals(1, ((TableView<?>) field(pane[0], "results")).getItems().size());
                ((TextField) field(pane[0], "last")).setText("999");
                assertThrows(IllegalArgumentException.class, () -> pane[0].snapshotSetup());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void lateImportCannotReplaceNewerEditsOrClosedPaneAndExportRejectsCsv() throws Exception {
        LogDataset data = data(); FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null};
        CountDownLatch release = new CountDownLatch(1);
        Path file = temporary.resolve("saved.rr2analysis"), csv = temporary.resolve("capture.csv"); Files.writeString(csv, "preserved");
        try {
            FxTestRuntime.run(() -> { pane[0] = pane(data); new FuelAnalysisSetupStore().write(file, pane[0].snapshotSetup());
                ExecutorService io = field(pane[0], "setupWorker");
                io.submit(() -> { try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } });
                job[0] = pane[0].loadSetup(file); ((TextField) field(pane[0], "binWidth")).setText(".9");
            });
            release.countDown(); job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertEquals(".9", ((TextField) field(pane[0], "binWidth")).getText());
                assertTrue(status(pane[0]).contains("skipped")); job[0] = pane[0].saveSetup(csv);
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertTrue(status(pane[0]).contains("not saved")); job[0] = pane[0].loadSetup(file); pane[0].close(); });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertNull(field(pane[0], "dataset")); assertTrue(((ExecutorService) field(pane[0], "setupWorker")).isShutdown()); });
            assertEquals("preserved", Files.readString(csv));
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void applyingReviewedSetupCancelsOldAnalysisAndLeavesNoStaleResult() throws Exception {
        LogDataset data = data(); FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null};
        CountDownLatch release = new CountDownLatch(1);
        try {
            FxTestRuntime.run(() -> {
                pane[0] = pane(data); FuelAnalysisSetup saved = pane[0].snapshotSetup();
                ExecutorService worker = field(pane[0], "worker");
                worker.submit(() -> { try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); } });
                ((CheckBox) field(pane[0], "confirmed")).setSelected(true);
                ((Button) field(pane[0], "calculate")).fire(); job[0] = field(pane[0], "pending");
                pane[0].applySetup(saved);
                assertTrue(job[0].isCancelled());
                assertFalse(((CheckBox) field(pane[0], "confirmed")).isSelected());
                assertFalse(((Button) field(pane[0], "saveSetup")).isDisabled());
                assertFalse(((Button) field(pane[0], "loadSetup")).isDisabled());
            });
            release.countDown(); ((ExecutorService) field(pane[0], "worker")).submit(() -> {}).get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> { assertEquals(0, ((TableView<?>) field(pane[0], "results")).getItems().size()); assertTrue(status(pane[0]).contains("review")); });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }

    @Test void queuedImportCannotMapAReplacementLog() throws Exception {
        LogDataset original = data(), replacement = log("Time (msec),Different (V)", "0,1\n");
        FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null}; CountDownLatch release = new CountDownLatch(1);
        Path file = temporary.resolve("saved.rr2analysis");
        try {
            FxTestRuntime.run(() -> {
                pane[0] = pane(original); new FuelAnalysisSetupStore().write(file, pane[0].snapshotSetup());
                ((ExecutorService) field(pane[0], "setupWorker")).submit(() -> {
                    try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                });
                job[0] = pane[0].loadSetup(file); pane[0].setDataset(replacement);
            });
            release.countDown(); job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertSame(replacement, field(pane[0], "dataset"));
                assertNull(((ComboBox<?>) field(pane[0], "x")).getValue());
                assertEquals("1", ((TextField) field(pane[0], "last")).getText());
                assertTrue(status(pane[0]).contains("skipped"));
            });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
}
