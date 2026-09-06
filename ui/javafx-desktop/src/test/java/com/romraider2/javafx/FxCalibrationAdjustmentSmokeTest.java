/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;

import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.JProgressPane;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxCalibrationAdjustmentSmokeTest {
    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void selectionMathKeepsSelectionAndUsesOneUndoWithoutChangingDefinitions() throws Exception {
        FxTestRuntime.run(() -> {
            Table1D table = new Table1D();
            table.setName("Synthetic selection math");
            table.setStorageType(1); table.setStorageAddress(0); table.setDataSize(3);
            Rom rom = new Rom(new RomID()); rom.addTableByName(table);
            rom.populateTables(new byte[] {10, 20, 30}, new JProgressPane());
            table.getCurrentScale().setFineIncrement(1);
            table.getCurrentScale().setCoarseIncrement(10);
            FxCalibrationPane pane = new FxCalibrationPane(table);
            Stage stage = new Stage();
            try {
                stage.setScene(new Scene(pane, 900, 600)); stage.show();
                pane.applyCss(); pane.layout();
                TableView grid = (TableView) pane.lookup(".calibration-grid");
                grid.getSelectionModel().clearSelection();
                grid.getSelectionModel().select(0, (javafx.scene.control.TableColumn) grid.getColumns().get(0));
                grid.getSelectionModel().select(0, (javafx.scene.control.TableColumn) grid.getColumns().get(2));
                ((TextField) pane.lookup("#selection-multiplier")).setText("2");
                button(pane, "Multiply selection").fire();
                assertArrayEquals(new byte[] {20, 20, 60}, rom.getBinary());
                assertEquals(2, grid.getSelectionModel().getSelectedCells().size());
                assertEquals(1, RomEditHistory.getInstance().undoDepth(rom));
                ((TextField) pane.lookup("#fine-step")).setText("3");
                button(pane, "+ Fine").fire();
                assertArrayEquals(new byte[] {23, 20, 63}, rom.getBinary());
                assertEquals("3", ((TextField) pane.lookup("#fine-step")).getText());
                assertEquals(2, grid.getSelectionModel().getSelectedCells().size());
                button(pane, "Undo").fire();
                assertArrayEquals(new byte[] {20, 20, 60}, rom.getBinary());
                button(pane, "Undo").fire();
                assertArrayEquals(new byte[] {10, 20, 30}, rom.getBinary());
                button(pane, "Use definition steps").fire();
                assertEquals("", ((TextField) pane.lookup("#fine-step")).getText());
                button(pane, "+ Fine").fire();
                assertArrayEquals(new byte[] {11, 20, 31}, rom.getBinary());
                assertEquals(1, table.getCurrentScale().getFineIncrement());
                ((TextField) pane.lookup("#selection-multiplier")).setText("NaN");
                button(pane, "Multiply selection").fire();
                assertArrayEquals(new byte[] {11, 20, 31}, rom.getBinary());
            } finally { stage.close(); pane.close(); RomEditHistory.getInstance().clear(rom); }
        });
    }

    private static Button button(FxCalibrationPane pane, String text) {
        return pane.lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }
}
