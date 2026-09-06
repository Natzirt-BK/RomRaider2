/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxFuelOperatingConditionsSmokeTest {
    private LogDataset data() throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time (msec),MAF (V),Learning (%),Correction (%),State,RPM,IAT (C),ECT (C),Tip\n"
                + "0,1,2,0,8,1000,30,80,0\n500,2,4,0,0,1000,30,80,0\n1000,3,6,0,8,1000,30,80,0\n1500,4,8,0,8,1000,30,80,0\n"));
    }
    private FxFuelAnalysisPane pane(LogDataset data) throws Exception {
        var pane = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.MAF, () -> {}); pane.setDataset(data);
        for (int i = 0; i < 3; i++) ((ComboBox<LogChannel>) field(pane, List.of("x", "y", "correction").get(i))).setValue(data.getChannels().get(i + 1));
        return pane;
    }
    private FxFuelOperatingConditionsPane operating(FxFuelAnalysisPane pane) throws Exception { return field(pane, "operating"); }
    private void configure(FxFuelOperatingConditionsPane pane, FuelOperatingCondition kind, LogChannel channel, String minimum, String maximum) {
        pane.apply(new FxFuelOperatingConditionsPane.Draft(pane.draft().conditions().stream().map(value -> value.kind() == kind
                ? new FxFuelOperatingConditionsPane.ConditionDraft(kind, true, channel, minimum, maximum) : value).toList()));
    }
    @Test void allEightNamedGatesStartOffAndEnabledInputsAreRequired() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            var controls = new FxFuelOperatingConditionsPane(() -> {}); controls.setDataset(data);
            assertEquals(8, controls.draft().conditions().size()); assertTrue(controls.draft().filters(data).isEmpty()); assertFalse(controls.isExpanded());
            configure(controls, FuelOperatingCondition.LOOP_STATE, null, "", "");
            assertThrows(IllegalArgumentException.class, () -> controls.draft().filters(data));
            configure(controls, FuelOperatingCondition.LOOP_STATE, data.getChannels().get(4), "8", "");
            configure(controls, FuelOperatingCondition.INTAKE_TEMPERATURE, data.getChannels().get(6), "", "45");
            assertEquals(2, controls.draft().filters(data).size()); assertTrue(controls.getText().contains("2 enabled"));
            assertNull(controls.draft().setup(data).get(1).minimum());
        });
    }
    @Test void savedNamedConditionsRestoreAndOldSetupsExplicitlyClearThem() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            var pane = pane(data);
            try {
                var base = pane.snapshotSetup(); configure(operating(pane), FuelOperatingCondition.LOOP_STATE, data.getChannels().get(4), "8", "");
                var saved = pane.snapshotSetup(); assertEquals(1, saved.conditions().size());
                pane.applySetup(saved); assertEquals(saved.conditions(), pane.snapshotSetup().conditions());
                assertFalse(((CheckBox) field(pane, "confirmed")).isSelected()); assertTrue(operating(pane).isExpanded());
                pane.applySetup(base); assertTrue(pane.snapshotSetup().conditions().isEmpty()); assertTrue(operating(pane).draft().filters(data).isEmpty());
            } finally { pane.close(); }
        });
    }
    @Test void missingImportedConditionStaysEnabledAndBlocksInsteadOfDisappearing() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            var pane = pane(data);
            try {
                var base = pane.snapshotSetup(); var saved = new FuelAnalysisSetup.Condition(FuelOperatingCondition.AFR,
                        new FuelAnalysisSetup.Channel("Missing AFR", "AFR"), 13.0, 16.0);
                pane.applySetup(new FuelAnalysisSetup(base.kind(), base.x(), base.y(), base.correction(), base.binWidth(), base.stoichAfr(), base.fuelDensity(), base.filters(), null, List.of(saved)));
                var draft = operating(pane).draft().conditions().stream().filter(c -> c.kind() == FuelOperatingCondition.AFR).findFirst().orElseThrow();
                assertTrue(draft.enabled()); assertNull(draft.channel()); assertEquals("13.0", draft.minimum()); assertEquals("16.0", draft.maximum());
                assertThrows(IllegalArgumentException.class, pane::snapshotSetup);
                assertTrue(((Label) field(pane, "status")).getText().contains("Missing AFR"));
                ((CheckBox) field(pane, "confirmed")).setSelected(true); ((Button) field(pane, "calculate")).fire();
                assertTrue(((Label) field(pane, "status")).getText().contains("Map Air/fuel ratio")); assertNull(field(pane, "pending"));
            } finally { pane.close(); }
        });
    }
    @Test void linksShareNamedConditionsAndInvalidDraftsClearBothTabs() throws Exception {
        LogDataset data = data();
        FxTestRuntime.run(() -> {
            var maf = pane(data); var injector = new FxFuelAnalysisPane(FxFuelAnalysisPane.Mode.INJECTOR, () -> {}); injector.setDataset(data);
            try {
                var link = new FxFuelAnalysisLink(maf, injector); configure(operating(maf), FuelOperatingCondition.LOOP_STATE, data.getChannels().get(4), "8", "");
                link.enable(maf, () -> true); assertEquals(operating(maf).draft(), operating(injector).draft());
                assertTrue(maf.conditionsDraft().summary().contains("column 5 State = 8"));
                ((CheckBox) field(maf, "confirmed")).setSelected(true); ((CheckBox) field(injector, "confirmed")).setSelected(true);
                configure(operating(injector), FuelOperatingCondition.LOOP_STATE, data.getChannels().get(4), "unfinished", "");
                assertEquals(operating(maf).draft(), operating(injector).draft()); assertThrows(IllegalArgumentException.class, () -> maf.conditionsDraft().validate());
                assertFalse(((CheckBox) field(maf, "confirmed")).isSelected()); assertFalse(((CheckBox) field(injector, "confirmed")).isSelected());
                link.disconnect(); assertThrows(IllegalArgumentException.class, () -> link.enable(maf, () -> true)); assertFalse(link.isLinked());
            } finally { maf.close(); injector.close(); }
        });
    }
    @Test void namedCustomAndRateFiltersCombineAndEditingInvalidatesCurveTransfer() throws Exception {
        LogDataset data = data(); FxFuelAnalysisPane[] pane = {null}; Future<?>[] job = {null};
        try {
            FxTestRuntime.run(() -> {
                pane[0] = pane(data); configure(operating(pane[0]), FuelOperatingCondition.LOOP_STATE, data.getChannels().get(4), "8", "");
                FxFuelRateFilterPane rate = field(pane[0], "rateFilter"); rate.apply(new FxFuelRateFilterPane.Draft(true, data.getChannels().get(1), data.getChannels().get(0), ".001", "2", "1"));
                List<?> filters = field(pane[0], "filters"); Object first = filters.get(0);
                ((ComboBox<LogChannel>) field(first, "channel")).setValue(data.getChannels().get(2));
                ((TextField) field(first, "minimum")).setText("0"); ((TextField) field(first, "maximum")).setText("7");
                ((CheckBox) field(pane[0], "confirmed")).setSelected(true); ((Button) field(pane[0], "calculate")).fire(); job[0] = field(pane[0], "pending");
            });
            job[0].get(10, TimeUnit.SECONDS);
            FxTestRuntime.run(() -> {
                assertTrue(((Label) field(pane[0], "status")).getText().contains("1 accepted · 2 filtered · 1 invalid"));
                FxFuelCurvePane curve = field(pane[0], "curve"); assertNotNull(field(curve, "analysis"));
                javafx.stage.Stage stage = new javafx.stage.Stage();
                try {
                    javafx.scene.Scene scene = new javafx.scene.Scene(pane[0], 1000, 640); FxTheme.apply(stage, scene); stage.setScene(scene); stage.show();
                    operating(pane[0]).setAnimated(false); operating(pane[0]).setExpanded(true); pane[0].applyCss(); pane[0].layout();
                    ((ScrollPane) pane[0].getLeft()).setVvalue(.75); pane[0].layout();
                    assertTrue(operating(pane[0]).getWidth() <= 350);
                    String capture = System.getenv("RR2_CONDITIONS_CAPTURE");
                    if (capture != null && !capture.isBlank()) {
                        var snapshot = pane[0].snapshot(null, null);
                        var bitmap = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) bitmap.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        assertTrue(javax.imageio.ImageIO.write(bitmap, "png", new java.io.File(capture)));
                    }
                } finally { stage.close(); }
                configure(operating(pane[0]), FuelOperatingCondition.LOOP_STATE, data.getChannels().get(4), "0", "");
                assertNull(field(curve, "analysis")); assertTrue(((Button) field(curve, "transfer")).isDisabled());
                assertTrue(((TableView<?>) field(pane[0], "results")).getItems().isEmpty());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
    @Test void replacementClearsNamedConditionsAndOldChannelObjectsCannotBeReused() throws Exception {
        LogDataset first = data(), second = data();
        FxTestRuntime.run(() -> {
            var pane = pane(first);
            try {
                configure(operating(pane), FuelOperatingCondition.LOOP_STATE, first.getChannels().get(4), "8", ""); var old = operating(pane).draft();
                pane.setDataset(second); assertTrue(operating(pane).draft().filters(second).isEmpty());
                operating(pane).apply(old); assertThrows(IllegalArgumentException.class, () -> operating(pane).draft().filters(second));
            } finally { pane.close(); }
        });
    }
    @Test void longConditionReviewRemainsScrollableAndDefaultsToCancel() throws Exception {
        FxTestRuntime.run(() -> {
            Throwable[] error = {null}; boolean[] answered = {false};
            javafx.application.Platform.runLater(() -> {
                javafx.stage.Stage stage = javafx.stage.Window.getWindows().stream().filter(javafx.stage.Stage.class::isInstance)
                        .map(javafx.stage.Stage.class::cast).filter(window -> "Synthetic long review".equals(window.getTitle())).findFirst().orElseThrow();
                DialogPane pane = (DialogPane) stage.getScene().lookup(".dialog-pane");
                ButtonType cancel = pane.getButtonTypes().stream().filter(type -> type.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE).findFirst().orElseThrow();
                try {
                    assertInstanceOf(ScrollPane.class, pane.getContent()); assertTrue(stage.getHeight() <= 600); assertTrue(stage.getWidth() <= 900);
                    assertTrue(((Button) pane.lookupButton(cancel)).isDefaultButton()); answered[0] = true;
                } catch (Throwable failure) { error[0] = failure; }
                finally { ((Button) pane.lookupButton(cancel)).fire(); }
            });
            assertFalse(FxDialogs.confirmScrollable(null, "Synthetic long review", "Mapped condition with an explicit limit and reviewed units.\n".repeat(100), "Link conditions"));
            assertTrue(answered[0]); if (error[0] != null) throw new AssertionError(error[0]);
        });
    }
}
