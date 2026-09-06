/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.editor.calibration.ReviewedMafTransfer;
import com.romraider.editor.document.EditorDocumentController;
import com.romraider.logger.analysis.*;
import com.romraider.maps.*;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.JProgressPane;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableView;
import javafx.stage.*;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxMafTransferSmokeTest {
    private static Table2D target() throws Exception {
        Table2D table = new Table2D(); table.setName("Synthetic MAF"); table.setStorageType(1); table.setStorageAddress(0); table.setDataSize(3);
        table.getAxis().setStorageAddress(3); table.getAxis().setStorageType(1); table.getAxis().setDataSize(3);
        Rom rom = new Rom(new RomID()); rom.setFileName("synthetic-transfer.bin"); rom.addTableByName(table);
        rom.populateTables(new byte[] {10, 20, 30, 1, 2, 3}, new JProgressPane());
        table.getCurrentScale().setUnit("g/s"); table.getAxis().getCurrentScale().setUnit("V"); return table;
    }
    private static FxFuelCurvePane completedCurve() throws Exception {
        LogDataset log = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                "Time,Input (V),Learning (%),Correction (%)\n0,1,10,0\n1,2,10,0\n2,3,10,0\n"));
        FuelLogAnalysis.Result data = FuelLogAnalysis.maf(log, LogRange.all(log), 1, 2, 3, 1, List.of());
        FxFuelCurvePane[] pane = {null}; Future<?>[] job = {null};
        FxTestRuntime.run(() -> {
            pane[0] = new FxFuelCurvePane(false); pane[0].setAnalysis(data);
            ((Button) field(pane[0], "interpolate")).fire(); job[0] = field(pane[0], "pending");
        });
        job[0].get(10, TimeUnit.SECONDS); FxTestRuntime.run(() -> assertFalse(((Button) field(pane[0], "transfer")).isDisabled()));
        return pane[0];
    }
    @Test void dialogRequiresAcknowledgementAndCancellingDoesNotEdit() throws Exception {
        Table2D table = target();
        FxTestRuntime.run(() -> {
            var proposal = ReviewedMafTransfer.preview(table, v -> v == 2 ? Double.NaN : 15, "Synthetic interpolation");
            var dialog = FxMafTransferDialog.create(null, "synthetic-transfer.bin", proposal);
            try {
                dialog.show(); dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                Button apply = (Button) dialog.getDialogPane().lookupButton(FxMafTransferDialog.APPLY);
                assertTrue(apply.isDisabled()); assertFalse(apply.isDefaultButton());
                ((CheckBox) dialog.getDialogPane().lookup("#maf-transfer-confirmed")).setSelected(true);
                assertFalse(apply.isDisabled());
                TableView<?> values = (TableView<?>) dialog.getDialogPane().lookup(".table-view");
                assertEquals(3, values.getItems().size()); assertEquals(7, values.getColumns().size());
                assertEquals(12, proposal.getRows().get(0).stored()); assertEquals(1, proposal.getUncoveredCount());
                assertTrue(dialog.getWidth() <= 1024, "Review should fit a small desktop display");
                String capture = System.getenv("RR2_TRANSFER_CAPTURE");
                if (capture != null && !capture.isBlank()) {
                    var snapshot = dialog.getDialogPane().snapshot(null, null);
                    var bitmap = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) bitmap.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    assertTrue(javax.imageio.ImageIO.write(bitmap, "png", new java.io.File(capture)));
                }
                ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
                assertEquals(ButtonType.CANCEL, dialog.getResult());
                assertEquals(10, table.getDataCell(0).getRealValue()); assertFalse(RomEditHistory.getInstance().canUndo(table.getRom()));
            } finally { dialog.close(); RomEditHistory.getInstance().clear(table.getRom()); }
        });
    }
    @Test void completedCurveAppliesOnlyAfterNativeReviewAndClearsRepeatAction() throws Exception { transfer(false, false, false); }
    @Test void cancelledNativeReviewPreservesCurveAndRom() throws Exception { transfer(true, false, false); }
    @Test void analysisChangeDuringModalReviewRejectsApply() throws Exception { transfer(false, true, false); }
    @Test void editorChangeDuringModalReviewRejectsApply() throws Exception { transfer(false, false, true); }

    private void transfer(boolean cancel, boolean staleAnalysis, boolean staleEditor) throws Exception {
        Table2D table = target(); FxFuelCurvePane pane = completedCurve(); AtomicBoolean current = new AtomicBoolean(true);
        Stage[] owner = {null};
        try {
            FxTestRuntime.run(() -> {
                owner[0] = new Stage(); owner[0].setScene(new Scene(pane, 1000, 600)); owner[0].show();
                pane.setTransferTarget(() -> new FxMafTransferTarget(table, "synthetic-transfer.bin", current::get));
                AtomicBoolean answered = new AtomicBoolean(); Timeline timer = new Timeline(); int[] polls = {0};
                timer.getKeyFrames().add(new KeyFrame(Duration.millis(50), event -> {
                    Stage review = Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                            .filter(stage -> "Review MAF table changes".equals(stage.getTitle())).findFirst().orElse(null);
                    if (review == null) { if (++polls[0] >= 100) timer.stop(); return; }
                    timer.stop(); DialogPane dialog = (DialogPane) review.getScene().getRoot();
                    if (staleAnalysis) pane.setAnalysis(null);
                    if (staleEditor) current.set(false);
                    ((CheckBox) dialog.lookup("#maf-transfer-confirmed")).setSelected(true); answered.set(true);
                    ((Button) dialog.lookupButton(cancel ? ButtonType.CANCEL : FxMafTransferDialog.APPLY)).fire();
                }));
                timer.setCycleCount(Animation.INDEFINITE); timer.play();
                try { ((Button) field(pane, "transfer")).fire(); }
                finally { timer.stop(); }
                assertTrue(answered.get(), "Native review must be shown and answered");
                if (cancel || staleAnalysis || staleEditor) {
                    assertEquals(10, table.getDataCell(0).getRealValue()); assertFalse(RomEditHistory.getInstance().canUndo(table.getRom()));
                    if (!cancel) assertTrue(((Label) field(pane, "status")).getText().contains("context changed"));
                    else assertFalse(((Button) field(pane, "transfer")).isDisabled());
                } else {
                    assertEquals(11, table.getDataCell(0).getRealValue()); assertEquals(33, table.getDataCell(2).getRealValue());
                    assertEquals(1, RomEditHistory.getInstance().undoDepth(table.getRom()));
                    assertTrue(((Button) field(pane, "transfer")).isDisabled());
                    assertTrue(((Label) field(pane, "status")).getText().contains("No file was saved"));
                    RomEditHistory.getInstance().undo(table.getRom()); assertEquals(10, table.getDataCell(0).getRealValue());
                }
            });
        } finally { FxTestRuntime.run(() -> { pane.close(); if (owner[0] != null) owner[0].close(); RomEditHistory.getInstance().clear(table.getRom()); }); }
    }
    @Test void editorTargetRequiresAnOpenSelectedTableAndTracksSessionRevision() throws Exception {
        Table2D table = target(); FxEditorWindow[] editor = {null};
        try {
            FxTestRuntime.run(() -> {
                editor[0] = new FxEditorWindow(() -> {}, () -> {}); assertNull(editor[0].mafTransferTarget());
                EditorDocumentController controller = field(editor[0], "controller"); controller.getSession().openRom(table.getRom());
                assertNull(editor[0].mafTransferTarget()); controller.openTable(table.getRom(), table);
                FxMafTransferTarget selected = editor[0].mafTransferTarget(); assertNotNull(selected);
                assertSame(table, selected.table()); assertTrue(selected.stillCurrent().getAsBoolean());
                controller.closeTable(table.getRom(), table); assertFalse(selected.stillCurrent().getAsBoolean());
                assertNull(editor[0].mafTransferTarget()); controller.openTable(table.getRom(), table);
                assertFalse(selected.stillCurrent().getAsBoolean()); assertNotNull(editor[0].mafTransferTarget());
                FxWindowPlacement.show(field(editor[0], "stage"));
            });
        } finally { FxTestRuntime.run(() -> { if (editor[0] != null) editor[0].close(); }); }
    }
    @Test void missingEditorReportsActionableStatusWithoutEditing() throws Exception {
        FxFuelCurvePane pane = completedCurve();
        try { FxTestRuntime.run(() -> {
            ((Button) field(pane, "transfer")).fire();
            assertTrue(((Label) field(pane, "status")).getText().contains("Open the matching ROM"));
            assertFalse(((Button) field(pane, "transfer")).isDisabled());
        }); } finally { FxTestRuntime.run(pane::close); }
    }
}
