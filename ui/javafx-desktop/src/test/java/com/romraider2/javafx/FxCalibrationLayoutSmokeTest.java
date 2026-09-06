/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;

import com.romraider.Settings;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table3D;
import com.romraider.swing.JProgressPane;
import com.romraider.util.SettingsManager;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxCalibrationLayoutSmokeTest {
    @Test void compactCellsAreCenteredAndStillRespectZoomAndCustomWidths() throws Exception {
        FxTestRuntime.run(() -> {
            Settings settings = SettingsManager.getSettings();
            var previousSize = settings.getJavaFxCellSize();
            settings.setJavaFxCellSize(new Settings().getJavaFxCellSize());
            Table3D table = new Table3D();
            table.setName("Synthetic layout surface");
            table.setStorageType(1); table.setStorageAddress(0);
            table.setSizeX(2); table.setSizeY(2);
            table.getXAxis().setStorageType(1);
            table.getXAxis().setStorageAddress(4); table.getXAxis().setDataSize(2);
            table.getYAxis().setStorageType(1);
            table.getYAxis().setStorageAddress(6); table.getYAxis().setDataSize(2);
            Rom rom = new Rom(new RomID()); rom.addTableByName(table);
            rom.populateTables(new byte[] {10, 20, 30, 40, 50, 60, 70, 80}, new JProgressPane());
            byte[] original = rom.getBinary().clone();
            FxCalibrationPane pane = new FxCalibrationPane(table);
            Stage stage = new Stage();
            Scene scene = new Scene(pane, 1000, 600);
            FxTheme.apply(stage, scene); stage.setScene(scene);
            try {
                FxWindowPlacement.show(stage); pane.applyCss(); pane.layout();
                TableView<?> grid = FxEditorControlsSmokeTest.field(pane, "grid");
                TableView<?> headers = FxEditorControlsSmokeTest.field(pane, "rowHeaders");
                assertEquals(84, grid.getColumns().get(0).getWidth(), .01);
                assertEquals(34, grid.getFixedCellSize(), .01);
                assertCenteredCells(grid, true);
                assertCenteredCells(headers, false);

                Button shrink = FxEditorControlsSmokeTest.field(pane, "tableScaleSmaller");
                shrink.fire();
                assertEquals(75.6, grid.getColumns().get(0).getPrefWidth(), .01);
                assertEquals(grid.getFixedCellSize(), headers.getFixedCellSize(), .01);
                Button reset = FxEditorControlsSmokeTest.field(pane, "tableScaleReset");
                reset.fire();
                assertEquals(84, grid.getColumns().get(0).getPrefWidth(), .01);

                settings.setJavaFxCellSize(new java.awt.Dimension(110, 40));
                pane.refreshSettings();
                assertEquals(110, grid.getColumns().get(0).getPrefWidth(), .01);
                assertEquals(40, grid.getFixedCellSize(), .01);
                assertArrayEquals(original, rom.getBinary());
            } finally {
                pane.close(); stage.close(); settings.setJavaFxCellSize(previousSize);
            }
        });
    }

    private static void assertCenteredCells(TableView<?> view, boolean checkTooltip) {
        var cells = view.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance).map(TableCell.class::cast)
                .filter(cell -> !cell.isEmpty()).toList();
        assertFalse(cells.isEmpty());
        for (TableCell<?, ?> cell : cells) {
            assertEquals(Pos.CENTER, cell.getAlignment());
            if (checkTooltip) assertEquals(cell.getText(), cell.getTooltip().getText());
        }
    }
}
