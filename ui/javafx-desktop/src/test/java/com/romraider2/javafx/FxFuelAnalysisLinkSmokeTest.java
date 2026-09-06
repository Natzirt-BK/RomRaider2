/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxFuelAnalysisLinkSmokeTest {
    @TempDir Path temporary;
    private LogDataset data() throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time (msec),MAF (V),Learn (%),Correct (%),Pulse (ms),Load (g/rev),State,State\n"
                + "0,2,1,2,3,1,8,1\n1,2,2,3,4,2,10,0\n2,2,3,4,5,3,NaN,1\n3,2,4,5,6,4,8,1\n"));
    }
    private static void text(Object target, String name, String value) throws Exception { ((TextField) field(target, name)).setText(value); }
    private static void channel(Object target, String name, LogChannel value) throws Exception { ((ComboBox<LogChannel>) field(target, name)).setValue(value); }
    private static CheckBox confirmed(FxFuelAnalysisPane pane) throws Exception { return field(pane, "confirmed"); }
    private static Object filter(FxFuelAnalysisPane pane) throws Exception { return ((List<?>) field(pane, "filters")).get(0); }
    private static void configureFilter(FxFuelAnalysisPane pane, LogDataset data) throws Exception {
        Object filter = filter(pane); channel(filter, "channel", data.getChannels().get(6));
        text(filter, "minimum", "8"); text(filter, "maximum", "8");
    }
    private final class Pair implements AutoCloseable {
        final FxFuelAnalysisPane maf = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.MAF, () -> {});
        final FxFuelAnalysisPane injector = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.INJECTOR, () -> {});
        final FxFuelAnalysisLink link = new FxFuelAnalysisLink(maf, injector);
        Pair(LogDataset data) throws Exception {
            maf.setDataset(data); injector.setDataset(data);
            channel(maf, "x", data.getChannels().get(1)); channel(maf, "y", data.getChannels().get(2)); channel(maf, "correction", data.getChannels().get(3));
            channel(injector, "x", data.getChannels().get(4)); channel(injector, "y", data.getChannels().get(5));
        }
        public void close() { maf.close(); injector.close(); }
    }

    @Test void reviewedLinkMirrorsBothDirectionsAndPreservesIndependentAssumptions() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (Pair pair = new Pair(data)) {
                configureFilter(pair.maf, data); text(pair.maf, "first", "2"); text(pair.maf, "last", "4");
                text(pair.injector, "density", "800"); text(pair.injector, "binWidth", ".7");
                confirmed(pair.maf).setSelected(true); confirmed(pair.injector).setSelected(true);
                pair.link.enable(pair.maf, () -> true);
                assertTrue(pair.link.isLinked()); assertEquals(pair.maf.conditionsDraft(), pair.injector.conditionsDraft());
                assertFalse(confirmed(pair.maf).isSelected()); assertFalse(confirmed(pair.injector).isSelected());
                assertSame(data.getChannels().get(4), ((ComboBox<?>) field(pair.injector, "x")).getValue());
                assertEquals("800", ((TextField) field(pair.injector, "density")).getText());
                assertEquals(".7", ((TextField) field(pair.injector, "binWidth")).getText());
                assertSame(data.getChannels().get(6), pair.injector.conditionsDraft().filters().get(0).channel());
                text(pair.injector, "first", "1"); text(filter(pair.injector), "maximum", "10");
                assertEquals(pair.maf.conditionsDraft(), pair.injector.conditionsDraft());
                confirmed(pair.maf).setSelected(true); confirmed(pair.injector).setSelected(true);
                text(pair.injector, "density", "810");
                assertFalse(confirmed(pair.injector).isSelected()); assertTrue(confirmed(pair.maf).isSelected());
                assertEquals("732", ((TextField) field(pair.maf, "density")).getText());
                pair.link.disconnect(); text(pair.maf, "first", "2");
                assertEquals("1", pair.injector.conditionsDraft().first());
            }
        });
    }

    @Test void cancellationAndEditsDuringReviewNeverOverwriteTheOtherSetup() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (Pair pair = new Pair(data)) {
                text(pair.maf, "first", "2"); var before = pair.injector.conditionsDraft();
                pair.link.enable(pair.maf, () -> false);
                assertEquals(before, pair.injector.conditionsDraft()); assertFalse(pair.link.isLinked());
                assertThrows(IllegalArgumentException.class, () -> pair.link.enable(pair.maf, () -> {
                    try { text(pair.injector, "binWidth", ".9"); } catch (Exception failure) { throw new AssertionError(failure); }
                    return true;
                }));
                assertEquals(before, pair.injector.conditionsDraft()); assertFalse(pair.link.isLinked());
                assertThrows(IllegalArgumentException.class, () -> pair.link.enable(pair.maf, () -> {
                    try { text(pair.maf, "first", "3"); } catch (Exception failure) { throw new AssertionError(failure); }
                    return true;
                }));
                assertEquals(before, pair.injector.conditionsDraft()); assertFalse(pair.link.isLinked());
            }
        });
    }

    @Test void invalidOrForeignConditionsAreRejectedBeforeReview() throws Exception {
        LogDataset data = data(), other = data();
        FxTestRuntime.run(() -> {
            try (Pair pair = new Pair(data)) {
                for (String invalid : List.of("0", "5", "", "NaN", "2147483648")) {
                    text(pair.maf, "first", invalid);
                    assertThrows(IllegalArgumentException.class, () -> pair.link.enable(pair.maf, () -> { fail("Review must not open"); return true; }));
                }
                text(pair.maf, "first", "1"); Object filter = filter(pair.maf);
                text(filter, "minimum", "8"); text(filter, "maximum", "9");
                assertThrows(IllegalArgumentException.class, () -> pair.link.enable(pair.maf, () -> true));
                channel(filter, "channel", other.getChannels().get(6));
                assertThrows(IllegalArgumentException.class, () -> pair.link.enable(pair.maf, () -> true));
                channel(filter, "channel", data.getChannels().get(6));
                for (String invalid : List.of("NaN", "Infinity", "10")) {
                    text(filter, "minimum", invalid);
                    assertThrows(IllegalArgumentException.class, () -> pair.link.enable(pair.maf, () -> true));
                }
                assertFalse(pair.link.isLinked()); assertNull(pair.injector.conditionsDraft().filters().get(0).channel());
            }
        });
    }

    @Test void replacementAndClosureDisconnectEvenAnIdenticallyShapedDataset() throws Exception {
        LogDataset data = data(), replacement = data();
        FxTestRuntime.run(() -> {
            try (Pair pair = new Pair(data)) {
                pair.link.enable(pair.maf, () -> true); text(pair.maf, "first", "2");
                pair.maf.setDataset(replacement);
                assertFalse(pair.link.isLinked()); assertEquals("2", pair.injector.conditionsDraft().first());
                assertThrows(IllegalArgumentException.class, () -> pair.link.enable(pair.maf, () -> true));
                pair.injector.setDataset(replacement); pair.link.enable(pair.maf, () -> true);
                pair.maf.close(); assertFalse(pair.link.isLinked());
                assertTrue(((CheckBox) field(pair.injector, "linkConditions")).isDisabled());
                assertThrows(IllegalArgumentException.class, () -> pair.link.enable(pair.injector, () -> true));
            }
        });
    }

    @Test void successfulSetupImportDisconnectsButWrongKindLeavesLinkUntouched() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (Pair pair = new Pair(data)) {
                FuelAnalysisSetup mafSetup = pair.maf.snapshotSetup(), injectorSetup = pair.injector.snapshotSetup();
                pair.link.enable(pair.maf, () -> true); text(pair.maf, "first", "2");
                assertThrows(IllegalArgumentException.class, () -> pair.maf.applySetup(injectorSetup));
                assertTrue(pair.link.isLinked()); pair.maf.applySetup(mafSetup);
                assertFalse(pair.link.isLinked()); assertEquals("1", pair.maf.conditionsDraft().first());
                assertEquals("2", pair.injector.conditionsDraft().first());
            }
        });
    }

    @Test void sharedFiltersProduceMatchingCountsAndIncompleteEditsClearBothResults() throws Exception {
        LogDataset data = data(); Pair[] pair = {null}; Future<?>[] jobs = new Future<?>[2];
        try {
            FxTestRuntime.run(() -> {
                pair[0] = new Pair(data); configureFilter(pair[0].maf, data); pair[0].link.enable(pair[0].maf, () -> true);
                int i = 0;
                for (FxFuelAnalysisPane pane : List.of(pair[0].maf, pair[0].injector)) {
                    confirmed(pane).setSelected(true); ((Button) field(pane, "calculate")).fire(); jobs[i++] = field(pane, "pending");
                }
            });
            for (Future<?> job : jobs) job.get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                for (FxFuelAnalysisPane pane : List.of(pair[0].maf, pair[0].injector)) {
                    assertTrue(((Label) field(pane, "status")).getText().startsWith("2 accepted · 1 filtered · 1 invalid"));
                    assertFalse(((TableView<?>) field(pane, "results")).getItems().isEmpty());
                }
                text(filter(pair[0].injector), "minimum", "-");
                assertEquals(pair[0].maf.conditionsDraft(), pair[0].injector.conditionsDraft());
                for (FxFuelAnalysisPane pane : List.of(pair[0].maf, pair[0].injector)) {
                    assertTrue(((TableView<?>) field(pane, "results")).getItems().isEmpty());
                    assertFalse(confirmed(pane).isSelected());
                    confirmed(pane).setSelected(true); ((Button) field(pane, "calculate")).fire();
                    assertTrue(((Label) field(pane, "status")).getText().contains("finite number"));
                }
            });
        } finally { FxTestRuntime.run(() -> { if (pair[0] != null) pair[0].close(); }); }
    }

    @Test void linkedEditsCancelBothQueuedCalculations() throws Exception {
        LogDataset data = data(); Pair[] pair = {null}; Future<?>[] jobs = new Future<?>[2];
        CountDownLatch release = new CountDownLatch(1);
        try {
            FxTestRuntime.run(() -> {
                pair[0] = new Pair(data); configureFilter(pair[0].maf, data); pair[0].link.enable(pair[0].maf, () -> true);
                int i = 0;
                for (FxFuelAnalysisPane pane : List.of(pair[0].maf, pair[0].injector)) {
                    ((ExecutorService) field(pane, "worker")).submit(() -> {
                        try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test gate timed out"); }
                        catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                    });
                    confirmed(pane).setSelected(true); ((Button) field(pane, "calculate")).fire(); jobs[i++] = field(pane, "pending");
                }
                text(filter(pair[0].injector), "maximum", "10");
                for (Future<?> job : jobs) assertTrue(job.isCancelled());
                assertFalse(confirmed(pair[0].maf).isSelected()); assertFalse(confirmed(pair[0].injector).isSelected());
            });
            release.countDown();
            for (FxFuelAnalysisPane pane : List.of(pair[0].maf, pair[0].injector)) {
                ((ExecutorService) field(pane, "worker")).submit(() -> {}).get(10, TimeUnit.SECONDS);
            }
            FxTestRuntime.run(() -> {
                for (FxFuelAnalysisPane pane : List.of(pair[0].maf, pair[0].injector)) {
                    assertTrue(((TableView<?>) field(pane, "results")).getItems().isEmpty());
                    assertTrue(((Button) field(pane, "copy")).isDisabled());
                }
            });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pair[0] != null) pair[0].close(); }); }
    }

    @Test void enablingLinkInvalidatesAnOlderQueuedSetupImport() throws Exception {
        LogDataset data = data(); Pair[] pair = {null}; Future<?>[] job = {null};
        CountDownLatch release = new CountDownLatch(1);
        Path setup = temporary.resolve("synthetic.rr2analysis");
        try {
            FxTestRuntime.run(() -> {
                pair[0] = new Pair(data); new FuelAnalysisSetupStore().write(setup, pair[0].injector.snapshotSetup());
                ((ExecutorService) field(pair[0].injector, "setupWorker")).submit(() -> {
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test gate timed out"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                });
                job[0] = pair[0].injector.loadSetup(setup);
                configureFilter(pair[0].maf, data); text(pair[0].maf, "first", "2");
                pair[0].link.enable(pair[0].maf, () -> true);
            });
            release.countDown(); job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertTrue(pair[0].link.isLinked()); assertEquals(pair[0].maf.conditionsDraft(), pair[0].injector.conditionsDraft());
                assertEquals("2", pair[0].injector.conditionsDraft().first());
                assertTrue(((Label) field(pair[0].injector, "status")).getText().contains("skipped"));
            });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (pair[0] != null) pair[0].close(); }); }
    }

    @Test void nativeLinkCheckboxRequiresReviewAndCanBeCancelled() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (Pair pair = new Pair(data)) {
                configureFilter(pair.maf, data);
                Stage stage = new Stage(); stage.setScene(new Scene(pair.maf, 800, 540)); stage.show();
                try {
                    for (String action : List.of("Cancel", "Link conditions")) {
                        AtomicReference<Throwable> failure = new AtomicReference<>();
                        javafx.animation.Timeline responder = new javafx.animation.Timeline();
                        responder.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(50), event -> {
                            Stage dialog = null;
                            try {
                                dialog = (Stage) Window.getWindows().stream().filter(w -> w instanceof Stage s
                                        && s.isShowing() && "Link analysis conditions?".equals(s.getTitle())).findFirst().orElse(null);
                                if (dialog == null) return;
                                responder.stop();
                                DialogPane pane = (DialogPane) dialog.getScene().lookup(".dialog-pane");
                                assertTrue(pane.getContentText().contains("Column 7: State"));
                                ButtonType type = pane.getButtonTypes().stream().filter(b -> b.getText().equals(action)).findFirst().orElseThrow();
                                ((Button) pane.lookupButton(type)).fire();
                            } catch (Throwable problem) { responder.stop(); failure.set(problem); if (dialog != null) dialog.hide(); }
                        }));
                        responder.setCycleCount(100); responder.play();
                        try { ((CheckBox) field(pair.maf, "linkConditions")).fire(); }
                        finally { responder.stop(); }
                        if (failure.get() != null) throw new AssertionError(failure.get());
                        assertEquals(action.equals("Link conditions"), pair.link.isLinked());
                        assertEquals(pair.link.isLinked(), ((CheckBox) field(pair.injector, "linkConditions")).isSelected());
                    }
                } finally { stage.close(); }
            }
        });
    }
}
