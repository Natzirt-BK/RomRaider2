package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table2D;
import com.romraider.editor.calibration.CalibrationEditController;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.JProgressPane;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxEditorControlsSmokeTest {
    @SuppressWarnings("unchecked") static <T> T field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(owner);
    }
    static Button button(Node node, String text) {
        if (node instanceof Button button && text.equals(button.getText())) return button;
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) {
            Button found = button(child, text); if (found != null) return found;
        }
        return null;
    }
    @Test @SuppressWarnings({"rawtypes", "unchecked"}) void axesInterpolationAndOfflinePreviewUseRealHistory() throws Exception {
        FxTestRuntime.run(() -> {
            Table2D table = new Table2D(); table.setName("Synthetic curve");
            table.setStorageType(1); table.setStorageAddress(0); table.setDataSize(3);
            table.getAxis().setStorageType(1); table.getAxis().setStorageAddress(3); table.getAxis().setDataSize(3);
            Rom rom = new Rom(new RomID()); rom.addTableByName(table);
            rom.populateTables(new byte[] {0, 0, 20, 10, 20, 30}, new JProgressPane());
            RomChangeService.rememberSavedBinary(rom);
            FxCalibrationPane pane = new FxCalibrationPane(table);
            Stage stage = new Stage(); stage.setScene(new Scene(pane, 853, 400));
            try {
                FxWindowPlacement.show(stage); pane.applyCss(); pane.layout();
                TableView grid = field(pane, "grid");
                grid.getSelectionModel().selectRange(0, (TableColumn) grid.getColumns().get(0), 0, (TableColumn) grid.getColumns().get(2));
                button(pane, "Interpolate selected rectangle").fire();
                assertEquals(10, rom.getBinary()[1]);
                CalibrationEditController edits = field(pane, "controller");
                edits.undo(); assertEquals(0, rom.getBinary()[1]);
                grid.getSelectionModel().clearAndSelect(0, (TableColumn) grid.getColumns().get(0));
                Button applyAxis = button(pane, "Apply X axis");
                TextField axisValue = (TextField) ((javafx.scene.layout.HBox) applyAxis.getParent()).getChildren().get(0);
                axisValue.setText("15"); applyAxis.fire();
                assertEquals(15, rom.getBinary()[3]);
                byte[] beforePreview = rom.getBinary().clone();
                var draft = FxLiveTunePreview.project(rom, table, false);
                assertEquals(1, draft.getTotalBytes());
                assertArrayEquals(beforePreview, rom.getBinary());
                var content = (javafx.scene.layout.BorderPane) pane.getCenter();
                ScrollPane inspector = (ScrollPane) content.getRight();
                assertTrue(inspector.isFitToWidth());
                assertTrue(inspector.getContent().getBoundsInLocal().getHeight() > inspector.getViewportBounds().getHeight());
            } finally { pane.close(); stage.close(); RomEditHistory.getInstance().clear(rom); RomChangeService.forget(rom); }
        });
    }
    @Test void dynoSetupRemainsScrollableAtSmallHeight() throws Exception {
        FxTestRuntime.run(() -> {
            FxDynoPane pane = new FxDynoPane(null);
            Stage stage = new Stage(); stage.setScene(new Scene(pane, 853, 400));
            try {
                FxWindowPlacement.show(stage); pane.applyCss(); pane.layout();
                ScrollPane setup = (ScrollPane) pane.getLeft();
                assertTrue(setup.isFitToWidth());
                assertNotNull(button(setup, "Calculate current run"));
                assertTrue(setup.getContent().getBoundsInLocal().getHeight() > setup.getViewportBounds().getHeight());
            } finally { stage.close(); }
        });
    }
    @Test void displayProfilesApplyReversibleTouchTargets() throws Exception {
        FxTestRuntime.run(() -> {
            var settings = com.romraider.util.SettingsManager.getSettings();
            var previous = settings.getDisplayMode();
            Stage stage = new Stage(); Button button = new Button("Target");
            Scene scene = new Scene(new javafx.scene.layout.StackPane(button), 400, 200);
            scene.getRoot().setStyle("-rr-owned-color: #123456;");
            try {
                settings.setDisplayMode(com.romraider.ui.DisplayMode.TOUCH);
                FxTheme.apply(stage, scene); stage.setScene(scene); stage.show(); scene.getRoot().applyCss();
                assertTrue(button.minHeight(-1) >= 48);
                assertTrue(scene.getRoot().getStyle().startsWith("-rr-owned-color: #123456;"));
                settings.setDisplayMode(com.romraider.ui.DisplayMode.NORMAL);
                FxTheme.refresh(scene); scene.getRoot().applyCss();
                assertFalse(scene.getRoot().getStyleClass().contains("touch-controls"));
                assertEquals(1, scene.getRoot().getStyle().split("-fx-font-size:", -1).length - 1);
            } finally { settings.setDisplayMode(previous); stage.close(); }
        });
    }
}
