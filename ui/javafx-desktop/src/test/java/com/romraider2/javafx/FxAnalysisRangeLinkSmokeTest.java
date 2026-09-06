/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import javafx.event.ActionEvent;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxAnalysisRangeLinkSmokeTest {
    private LogDataset data() throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time (msec),Voltage (V),Learning (%),Correction (%),Pulse (ms),Load (g/rev),State\n"
                + "0,1,2,0,1,1,8\n100,2,4,0,2,2,0\n200,3,6,0,3,3,8\n300,4,8,0,4,4,0\n400,5,10,0,5,5,8\n"));
    }
    private static final class Trio implements AutoCloseable {
        final FxLogAnalysisPane log;
        final FxFuelAnalysisPane maf = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.MAF, () -> {});
        final FxFuelAnalysisPane injector = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.INJECTOR, () -> {});
        final FxAnalysisRangeLink link;
        Trio(LogDataset data) throws Exception {
            log = new FxLogAnalysisPane(null, data); maf.setDataset(data); injector.setDataset(data);
            for (int i = 0; i < 3; i++) ((ComboBox<LogChannel>) field(maf, List.of("x", "y", "correction").get(i))).setValue(data.getChannels().get(i + 1));
            ((ComboBox<LogChannel>) field(injector, "x")).setValue(data.getChannels().get(4));
            ((ComboBox<LogChannel>) field(injector, "y")).setValue(data.getChannels().get(5));
            link = new FxAnalysisRangeLink(log, maf, injector);
        }
        @Override public void close() { link.close(); log.close(); maf.close(); injector.close(); }
    }
    private static TextField text(Object target, String field) throws Exception { return field(target, field); }
    private static void confirm(FxFuelAnalysisPane pane) throws Exception { ((CheckBox) field(pane, "confirmed")).setSelected(true); }
    private static TableView<ChannelStatistics> stats(Trio trio) throws Exception {
        FxLogStatisticsSmokeTest.awaitStatistics(trio.log);
        return field(trio.log, "statistics");
    }
    private static void sameRange(Trio trio, String first, String last) {
        var expected = new FxAnalysisRangeLink.Draft(first, last);
        assertEquals(expected, trio.log.rangeDraft()); assertEquals(expected, trio.maf.rangeDraft()); assertEquals(expected, trio.injector.rangeDraft());
    }
    @Test void activationIsReviewedAndCancellationPreservesIndependentRangesAndFilters() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (var trio = new Trio(data)) {
                trio.log.selectRange(LogRange.of(1, 4, 5)); text(trio.maf, "first").setText("3");
                var before = trio.maf.conditionsDraft(); trio.link.enable(() -> false);
                assertFalse(trio.link.isLinked()); assertEquals(before, trio.maf.conditionsDraft());
                var mafSetup = trio.maf.snapshotSetup(); var injectorSetup = trio.injector.snapshotSetup();
                trio.link.enable(() -> true); sameRange(trio, "2", "4"); assertTrue(trio.link.isApplied());
                assertEquals(mafSetup, trio.maf.snapshotSetup()); assertEquals(injectorSetup, trio.injector.snapshotSetup());
                assertFalse(((CheckBox) field(trio.maf, "confirmed")).isSelected());
            }
        });
    }
    @Test void fuelRangeDraftClearsStaleViewsPausesPlaybackAndRequiresApply() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (var trio = new Trio(data)) {
                trio.link.enable(() -> true); LogPlaybackService playback = field(trio.log, "playback"); playback.play();
                text(trio.maf, "first").setText("2"); text(trio.maf, "last").setText("4");
                sameRange(trio, "2", "4"); assertFalse(trio.link.isApplied()); assertTrue(stats(trio).getItems().isEmpty());
                assertTrue(((TableView<?>) field(trio.log, "values")).getItems().isEmpty());
                assertTrue(((LineChart<?, ?>) field(trio.log, "timelineChart")).getData().isEmpty());
                assertTrue(((Slider) field(trio.log, "position")).isDisabled()); assertEquals(PlaybackState.PAUSED, playback.snapshot().getState());
                confirm(trio.maf); ((Button) field(trio.maf, "calculate")).fire();
                assertTrue(((Label) field(trio.maf, "status")).getText().contains("Apply the shared")); assertNull(field(trio.maf, "pending"));
                ((Button) field(trio.injector, "applySharedRange")).fire(); assertTrue(trio.link.isApplied());
                assertEquals(3, stats(trio).getItems().get(1).getSampleCount()); assertEquals(3, stats(trio).getItems().get(1).getMean());
                assertFalse(((Slider) field(trio.log, "position")).isDisabled()); assertFalse(((CheckBox) field(trio.maf, "confirmed")).isSelected());
                playback.seek(99); assertEquals(3, playback.snapshot().getSampleIndex());
            }
        });
    }
    @Test void invalidDraftCannotRefreshAndUnlinkDoesNotRevertDraftFields() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (var trio = new Trio(data)) {
                trio.link.enable(() -> true); text(trio.log, "rangeStart").setText("incomplete");
                sameRange(trio, "incomplete", "5"); assertThrows(IllegalArgumentException.class, trio.link::commit);
                assertTrue(stats(trio).getItems().isEmpty()); trio.link.disconnect(); sameRange(trio, "incomplete", "5");
                text(trio.log, "rangeStart").setText("2"); text(trio.log, "rangeEnd").fireEvent(new ActionEvent());
                assertEquals(4, stats(trio).getItems().get(1).getSampleCount()); assertEquals("incomplete", trio.maf.rangeDraft().first());
            }
        });
    }
    @Test void directLogRangeSelectionSharesTheAppliedRangeAndPreservesFuelFilterLink() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (var trio = new Trio(data)) {
                var conditions = new FxFuelAnalysisLink(trio.maf, trio.injector); conditions.enable(trio.maf, () -> true);
                trio.link.enable(() -> true); trio.log.selectRange(LogRange.of(1, 4, 5)); sameRange(trio, "2", "4"); assertTrue(trio.link.isApplied());
                List<?> filters = field(trio.maf, "filters"); Object row = filters.get(0);
                ((ComboBox<LogChannel>) field(row, "channel")).setValue(data.getChannels().get(6));
                text(row, "minimum").setText("8"); text(row, "maximum").setText("8");
                assertTrue(conditions.isLinked()); assertEquals(trio.maf.conditionsDraft().filters(), trio.injector.conditionsDraft().filters());
                assertTrue(trio.link.isApplied()); assertEquals(3, stats(trio).getItems().get(1).getSampleCount());
                text(trio.injector, "first").setText("3"); sameRange(trio, "3", "4"); assertFalse(trio.link.isApplied()); trio.link.commit();
                sameRange(trio, "3", "4"); assertTrue(conditions.isLinked());
            }
        });
    }
    @Test void setupImportDisconnectsOnlyAfterSuccessfulKindValidation() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (var trio = new Trio(data)) {
                var saved = trio.maf.snapshotSetup(); trio.link.enable(() -> true);
                assertThrows(IllegalArgumentException.class, () -> trio.maf.applySetup(trio.injector.snapshotSetup())); assertTrue(trio.link.isLinked());
                trio.maf.applySetup(saved); assertFalse(trio.link.isLinked()); assertFalse(((CheckBox) field(trio.log, "shareRange")).isDisabled());
                trio.link.enable(() -> true); assertTrue(trio.link.isApplied());
            }
        });
    }
    @Test void staleActivationAndDatasetReplacementCannotReuseAnOldLink() throws Exception {
        LogDataset data = data(), replacement = data();
        FxTestRuntime.run(() -> {
            try (var trio = new Trio(data)) {
                TextField first = text(trio.log, "rangeStart");
                assertThrows(IllegalArgumentException.class, () -> trio.link.enable(() -> { first.setText("2"); return true; }));
                assertFalse(trio.link.isLinked()); trio.link.enable(() -> true); trio.maf.setDataset(replacement);
                assertFalse(trio.link.isLinked()); assertTrue(((CheckBox) field(trio.log, "shareRange")).isDisabled());
                assertThrows(IllegalArgumentException.class, () -> trio.link.enable(() -> true));
            }
        });
    }
    @Test void logStatisticsRemainRangeOnlyWhileFuelAnalysisAppliesItsConditions() throws Exception {
        LogDataset data = data(); Trio[] trio = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                trio[0] = new Trio(data); trio[0].log.selectRange(LogRange.of(1, 4, 5));
                List<?> filters = field(trio[0].maf, "filters"); Object row = filters.get(0);
                ((ComboBox<LogChannel>) field(row, "channel")).setValue(data.getChannels().get(6));
                text(row, "minimum").setText("8"); text(row, "maximum").setText("8");
                trio[0].link.enable(() -> true); confirm(trio[0].maf); ((Button) field(trio[0].maf, "calculate")).fire(); job[0] = field(trio[0].maf, "pending");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertTrue(((Label) field(trio[0].maf, "status")).getText().contains("1 accepted · 2 filtered"));
                assertEquals(3, stats(trio[0]).getItems().get(1).getSampleCount());
                assertTrue(((Label) field(trio[0].log, "sharedRangeStatus")).getText().contains("do not apply MAF/Injector filters"));
                FxFuelCurvePane curve = field(trio[0].maf, "curve"); assertNotNull(field(curve, "analysis"));
                text(trio[0].log, "rangeEnd").setText("5"); assertNull(field(curve, "analysis"));
            });
        } finally { FxTestRuntime.run(() -> { if (trio[0] != null) trio[0].close(); }); }
    }
    @Test void nativeLinkCheckboxOffersCancellableReviewAndFitsCompactWindow() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            try (var trio = new Trio(data)) {
                javafx.stage.Stage owner = new javafx.stage.Stage();
                try {
                    javafx.scene.Scene scene = new javafx.scene.Scene(trio.log, 1000, 640); FxTheme.apply(owner, scene); owner.setScene(scene); owner.show();
                    for (boolean approve : new boolean[] {false, true}) {
                        Throwable[] error = {null}; boolean[] answered = {false};
                        javafx.application.Platform.runLater(() -> {
                            javafx.stage.Stage dialog = javafx.stage.Window.getWindows().stream().filter(javafx.stage.Stage.class::isInstance)
                                    .map(javafx.stage.Stage.class::cast).filter(stage -> "Share analysis sample range?".equals(stage.getTitle())).findFirst().orElseThrow();
                            DialogPane pane = (DialogPane) dialog.getScene().lookup(".dialog-pane");
                            ButtonType action = pane.getButtonTypes().stream().filter(type -> type.getButtonData() == (approve ? ButtonBar.ButtonData.OK_DONE : ButtonBar.ButtonData.CANCEL_CLOSE)).findFirst().orElseThrow();
                            try {
                                assertInstanceOf(ScrollPane.class, pane.getContent()); assertTrue(pane.getContentText().contains("Only the sample range is shared"));
                                answered[0] = true;
                            } catch (Throwable failure) { error[0] = failure; }
                            finally { ((Button) pane.lookupButton(action)).fire(); }
                        });
                        ((CheckBox) field(trio.log, "shareRange")).fire(); assertTrue(answered[0]);
                        if (error[0] != null) throw new AssertionError(error[0]); assertEquals(approve, trio.link.isLinked());
                    }
                    trio.log.applyCss(); trio.log.layout();
                    CheckBox share = field(trio.log, "shareRange"); assertTrue(share.localToScene(share.getBoundsInLocal()).getMaxX() <= 1000);
                    String capture = System.getenv("RR2_SHARED_RANGE_CAPTURE");
                    if (capture != null && !capture.isBlank()) {
                        var snapshot = trio.log.snapshot(null, null);
                        var bitmap = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) bitmap.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        assertTrue(javax.imageio.ImageIO.write(bitmap, "png", new java.io.File(capture)));
                    }
                } finally { owner.close(); }
            }
        });
    }
    @Test void sharedDraftCancelsQueuedFuelJobsInBothWorkspaces() throws Exception {
        LogDataset data = data(); Trio[] trio = {null}; Future<?>[] jobs = new Future<?>[2]; CountDownLatch release = new CountDownLatch(1);
        try {
            FxTestRuntime.run(() -> {
                trio[0] = new Trio(data); trio[0].link.enable(() -> true); int index = 0;
                for (FxFuelAnalysisPane pane : List.of(trio[0].maf, trio[0].injector)) {
                    ((ExecutorService) field(pane, "worker")).submit(() -> {
                        try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test gate timeout"); }
                        catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                    });
                    confirm(pane); ((Button) field(pane, "calculate")).fire(); jobs[index++] = field(pane, "pending");
                }
                text(trio[0].log, "rangeStart").setText("2");
                for (Future<?> job : jobs) assertTrue(job.isCancelled());
                assertFalse(((CheckBox) field(trio[0].maf, "confirmed")).isSelected());
                assertFalse(((CheckBox) field(trio[0].injector, "confirmed")).isSelected());
            });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (trio[0] != null) trio[0].close(); }); }
    }
}
