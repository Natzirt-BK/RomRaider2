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
class FxFuelRateFilterSmokeTest {
    private LogDataset data() throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time (msec),MAF (V),Learning (%),Correction (%)\n0,1,2,0\n500,2,4,0\n1000,3,6,0\n1500,8,16,0\n"));
    }
    private FxFuelRateFilterPane.Draft valid(LogDataset data) { return new FxFuelRateFilterPane.Draft(true, data.getChannels().get(1), data.getChannels().get(0), ".001", "2", "1"); }
    private FxFuelAnalysisPane pane(LogDataset data) throws Exception {
        var pane = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.MAF, () -> {}); pane.setDataset(data);
        for (int i = 0; i < 3; i++) ((ComboBox<LogChannel>) field(pane, List.of("x", "y", "correction").get(i))).setValue(data.getChannels().get(i + 1));
        return pane;
    }
    private FxFuelRateFilterPane rate(FxFuelAnalysisPane pane) throws Exception { return field(pane, "rateFilter"); }
    @Test void filterStartsOffAndRequiresExplicitSignalTimeAndTimeScale() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            var rate = new FxFuelRateFilterPane(() -> {}); rate.setDataset(data);
            assertFalse(rate.draft().enabled()); assertNull(rate.draft().signal()); assertNull(rate.draft().time());
            assertNull(rate.draft().filter(data)); assertFalse(rate.isExpanded());
            ((CheckBox) field(rate, "enabled")).setSelected(true);
            assertThrows(IllegalArgumentException.class, () -> rate.draft().filter(data));
            rate.apply(valid(data)); assertEquals(.001, rate.draft().filter(data).secondsPerTimeUnit());
            ((TextField) field(rate, "scale")).clear(); assertThrows(IllegalArgumentException.class, () -> rate.draft().filter(data));
        });
    }
    @Test void setupRestoresTheRateAndOldSetupClearsItWithoutRestoringConfirmation() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            var pane = pane(data);
            try {
                rate(pane).apply(valid(data)); var saved = pane.snapshotSetup(); assertNotNull(saved.rate());
                ((CheckBox) field(pane, "confirmed")).setSelected(true); pane.applySetup(saved);
                assertEquals(saved.rate(), pane.snapshotSetup().rate()); assertFalse(((CheckBox) field(pane, "confirmed")).isSelected());
                var old = new FuelAnalysisSetup(saved.kind(), saved.x(), saved.y(), saved.correction(), saved.binWidth(), saved.stoichAfr(), saved.fuelDensity(), saved.filters());
                pane.applySetup(old); assertFalse(rate(pane).draft().enabled()); assertNull(pane.snapshotSetup().rate());
            } finally { pane.close(); }
        });
    }
    @Test void missingImportedTimeRetainsLimitsAndBlocksAnalysisInsteadOfDisablingFilter() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            var pane = pane(data);
            try {
                var base = pane.snapshotSetup(); var rate = new FuelAnalysisSetup.Rate(base.x(), new FuelAnalysisSetup.Channel("Time (seconds)", "seconds"), 1, 2, 1);
                pane.applySetup(new FuelAnalysisSetup(base.kind(), base.x(), base.y(), base.correction(), base.binWidth(), base.stoichAfr(), base.fuelDensity(), base.filters(), rate));
                assertTrue(rate(pane).draft().enabled()); assertNull(rate(pane).draft().time()); assertEquals("2.0", rate(pane).draft().maximum());
                assertThrows(IllegalArgumentException.class, pane::snapshotSetup);
                assertTrue(((Label) field(pane, "status")).getText().contains("recorded time"));
                ((CheckBox) field(pane, "confirmed")).setSelected(true); ((Button) field(pane, "calculate")).fire();
                assertTrue(((Label) field(pane, "status")).getText().contains("recorded-time")); assertNull(field(pane, "pending"));
            } finally { pane.close(); }
        });
    }
    @Test void linkedRateEditsMirrorInvalidDraftsAndClearBothConfirmations() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            var maf = pane(data); var injector = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.INJECTOR, () -> {}); injector.setDataset(data);
            try {
                var link = new FxFuelAnalysisLink(maf, injector); rate(maf).apply(valid(data));
                link.enable(maf, () -> true); assertEquals(rate(maf).draft(), rate(injector).draft());
                assertTrue(maf.conditionsDraft().summary().contains("× .001 s/unit"));
                ((CheckBox) field(maf, "confirmed")).setSelected(true); ((CheckBox) field(injector, "confirmed")).setSelected(true);
                ((TextField) field(rate(injector), "maximum")).setText("unfinished");
                assertEquals(rate(maf).draft(), rate(injector).draft()); assertThrows(IllegalArgumentException.class, () -> maf.conditionsDraft().validate());
                assertFalse(((CheckBox) field(maf, "confirmed")).isSelected()); assertFalse(((CheckBox) field(injector, "confirmed")).isSelected());
                assertTrue(link.isLinked()); link.disconnect();
                assertThrows(IllegalArgumentException.class, () -> link.enable(maf, () -> true)); assertFalse(link.isLinked());
            } finally { maf.close(); injector.close(); }
        });
    }
    @Test void actualAnalysisUsesRateAndEditingItClearsTheCurveSource() throws Exception {
        LogDataset data = data(); FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = pane(data); rate(pane[0]).apply(valid(data)); ((TextField) field(pane[0], "binWidth")).setText("1");
                ((CheckBox) field(pane[0], "confirmed")).setSelected(true); ((Button) field(pane[0], "calculate")).fire(); job[0] = field(pane[0], "pending");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertTrue(((Label) field(pane[0], "status")).getText().contains("2 accepted · 1 filtered · 1 invalid"));
                FxFuelCurvePane curve = field(pane[0], "curve"); assertNotNull(field(curve, "analysis"));
                javafx.stage.Stage stage = new javafx.stage.Stage();
                try {
                    javafx.scene.Scene scene = new javafx.scene.Scene(pane[0], 1000, 640); FxTheme.apply(stage, scene); stage.setScene(scene); stage.show();
                    rate(pane[0]).setAnimated(false); rate(pane[0]).setExpanded(true); pane[0].applyCss(); pane[0].layout();
                    ScrollPane scroll = (ScrollPane) pane[0].getLeft(); scroll.setVvalue(1); pane[0].layout();
                    assertTrue(rate(pane[0]).getWidth() <= 350, "Expanded rate inputs must fit the compact analysis sidebar");
                    TextField maximum = field(rate(pane[0]), "maximum");
                    assertTrue(maximum.localToScene(maximum.getBoundsInLocal()).getMaxX() <= scene.getWidth());
                    String capture = System.getenv("RR2_RATE_CAPTURE");
                    if (capture != null && !capture.isBlank()) {
                        var snapshot = pane[0].snapshot(null, null);
                        var bitmap = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) bitmap.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        assertTrue(javax.imageio.ImageIO.write(bitmap, "png", new java.io.File(capture)));
                    }
                } finally { stage.close(); }
                ((TextField) field(rate(pane[0]), "gap")).setText("0.1");
                assertNull(field(curve, "analysis")); assertTrue(((TableView<?>) field(pane[0], "results")).getItems().isEmpty());
                assertTrue(((Button) field(curve, "transfer")).isDisabled());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void datasetReplacementClearsRateAndRejectsOldChannelIdentities() throws Exception {
        LogDataset first = data(), second = data();
        FxTestRuntime.run(() -> {
            var pane = pane(first);
            try {
                rate(pane).apply(valid(first)); pane.setDataset(second);
                assertFalse(rate(pane).draft().enabled()); assertNull(rate(pane).draft().time());
                rate(pane).apply(valid(first)); assertThrows(IllegalArgumentException.class, () -> rate(pane).draft().filter(second));
            } finally { pane.close(); }
        });
    }
}
