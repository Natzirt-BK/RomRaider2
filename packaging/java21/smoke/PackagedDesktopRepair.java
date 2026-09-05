/* RomRaider2 ECU Studio - GPL 2.0 or later. Synthetic diagnostics, never vehicle data. */
package com.romraider2.javafx;

import static com.romraider2.javafx.PackagedDocumentSafety.*;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.romraider.editor.calibration.CalibrationEditController;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.logger.analysis.*;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table2D;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.JProgressPane;
import com.romraider.util.SettingsManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public final class PackagedDesktopRepair {
    static Button button(Node node, String text) {
        if (node instanceof Button button && text.equals(button.getText())) return button;
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) {
            Button found = button(child, text); if (found != null) return found;
        }
        return null;
    }
    static void capture(Stage stage, Path target) throws Exception {
        var root = stage.getScene().getRoot(); root.applyCss(); root.layout();
        var image = stage.getScene().snapshot(null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        var png = new java.awt.image.BufferedImage(width, height, 2);
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        png.setRGB(0, 0, width, height, pixels, 0, width);
        javax.imageio.ImageIO.write(png, "png", target.toFile());
    }
    static Stage show(Parent root) {
        Stage stage = new Stage(); Scene scene = new Scene(root, 853, 500);
        FxTheme.apply(stage, scene); stage.setScene(scene); FxWindowPlacement.show(stage);
        root.applyCss(); root.layout(); return stage;
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath(); Files.createDirectories(output);
        System.setProperty("romraider2.settings.dir", output.resolve("settings").toString());
        System.setProperty("romraider2.log.dir", output.resolve("logs").toString());
        SettingsManager.setTesting(true);
        SettingsManager.getSettings().setAutoConnectOnStartup(false);
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            verify(output);
            controls(output);
            analysis(output);
            presentation(output);
            System.out.println("PACKAGED_DESKTOP_REPAIR_PASS");
        } finally { Platform.exit(); }
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) static void controls(Path output) throws Exception {
        fx(() -> {
            Table2D table = new Table2D(); table.setName("Synthetic curve");
            table.setStorageType(1); table.setStorageAddress(0); table.setDataSize(3);
            table.getAxis().setStorageType(1); table.getAxis().setStorageAddress(3); table.getAxis().setDataSize(3);
            Rom rom = new Rom(new RomID()); rom.addTableByName(table);
            rom.populateTables(new byte[] {0, 0, 20, 10, 20, 30}, new JProgressPane());
            RomChangeService.rememberSavedBinary(rom);
            FxCalibrationPane pane = new FxCalibrationPane(table); Stage stage = show(pane);
            try {
                TableView grid = field(pane, "grid");
                grid.getSelectionModel().selectRange(0, (TableColumn) grid.getColumns().get(0), 0, (TableColumn) grid.getColumns().get(2));
                button(pane, "Interpolate selected rectangle").fire();
                check(rom.getBinary()[1] == 10, "interpolation UI updates synthetic ROM");
                CalibrationEditController edits = field(pane, "controller"); edits.undo();
                grid.getSelectionModel().clearAndSelect(0, (TableColumn) grid.getColumns().get(0));
                Button apply = button(pane, "Apply X axis");
                ((TextField) ((HBox) apply.getParent()).getChildren().get(0)).setText("15"); apply.fire();
                check(rom.getBinary()[1] == 0 && rom.getBinary()[3] == 15, "axis UI and interpolation undo");
                check(FxLiveTunePreview.project(rom, table, false).getTotalBytes() == 1, "offline Live Tune projects axis byte");
                ScrollPane scroll = (ScrollPane) ((BorderPane) pane.getCenter()).getRight();
                pane.applyCss(); pane.layout();
                check(scroll.getContent().getBoundsInLocal().getHeight() > scroll.getViewportBounds().getHeight(), "small inspector has scroll recovery");
                scroll.setVvalue(1); capture(stage, output.resolve("calibration-bottom.png"));
            } finally { pane.close(); stage.close(); RomEditHistory.getInstance().clear(rom); RomChangeService.forget(rom); }
            FxDynoPane dyno = new FxDynoPane(null); stage = show(dyno);
            try {
                ScrollPane setup = (ScrollPane) dyno.getLeft(); setup.setVvalue(1);
                check(button(setup, "Calculate current run") != null && setup.isFitToWidth(), "Dyno calculation is in scrollable setup");
                capture(stage, output.resolve("dyno-bottom.png"));
            } finally { stage.close(); }
            stage = FxSettingsWindow.show(null, () -> { throw new AssertionError("Cancel applied settings"); });
            try {
                capture(stage, output.resolve("settings.png"));
                button(stage.getScene().getRoot(), "Cancel").fire();
                check(!stage.isShowing(), "settings cancel closes without applying");
            } finally { stage.close(); }
        });
    }
    static void analysis(Path output) throws Exception {
        FxLogAnalysisPane[] pane = new FxLogAnalysisPane[1]; Stage[] stage = new Stage[1];
        try {
            fx(() -> {
                var dataset = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader("Time (msec),Value\n0,2\n100,10\n200,3\n300,20\n"));
                pane[0] = new FxLogAnalysisPane(null, dataset); stage[0] = show(pane[0]);
                TableView<Integer> table = field(pane[0], "values");
                table.getSortOrder().setAll(table.getColumns().get(2)); table.sort();
                table.getSelectionModel().select(1);
                LogCursorModel cursor = field(pane[0], "cursor");
                check(table.getItems().equals(List.of(0, 2, 1, 3)) && cursor.getSampleIndex() == 2, "numeric sorting seeks the selected source sample");
                pane[0].selectRange(LogRange.of(1, 3, 4));
            });
            fx(() -> {
                TableView<ChannelStatistics> stats = field(pane[0], "statistics");
                TableView<Integer> table = field(pane[0], "values");
                check(stats.getItems().get(1).getMean() == 6.5 && table.getItems().equals(List.of(2, 1)), "range links table and statistics");
                capture(stage[0], output.resolve("analysis-range.png"));
            });
        } finally { fx(() -> { if (pane[0] != null) pane[0].close(); if (stage[0] != null) stage[0].close(); }); }
    }

    static void presentation(Path output) throws Exception {
        fx(() -> {
            FxLoggerWindow window = new FxLoggerWindow(() -> {});
            Stage stage = field(window, "stage");
            try {
                FxWindowPlacement.show(stage); // Bypass setup/startup, never connect.
                window.setTouchMode();
                check(stage.getScene().getRoot().getStyleClass().contains("touch-controls"), "Logger touch launch profile applies");
                window.enterFullScreen(); check(stage.isFullScreen(), "Logger actual full-screen mode");
                stage.setFullScreen(false);
            } finally { window.close(); }
        });
        fx(() -> {
            Throwable[] failure = {null};
            Platform.runLater(() -> Platform.runLater(() -> {
                Stage dialog = (Stage) javafx.stage.Window.getWindows().stream()
                        .filter(window -> window instanceof Stage candidate && "ECU Definitions Manager".equals(candidate.getTitle()))
                        .findFirst().orElseThrow();
                try {
                    var bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
                    check(dialog.getX() >= bounds.getMinX() - 1 && dialog.getY() >= bounds.getMinY() - 1
                            && dialog.getX() + dialog.getWidth() <= bounds.getMaxX() + 1
                            && dialog.getY() + dialog.getHeight() <= bounds.getMaxY() + 1,
                            "owned Definition Manager fits native work area");
                    capture(dialog, output.resolve("definitions.png"));
                } catch (Throwable problem) { failure[0] = problem; }
                finally { dialog.close(); }
            }));
            FxDefinitionManager.show(null, () -> { throw new AssertionError("Diagnostic must not save definitions"); });
            if (failure[0] != null) throw new AssertionError(failure[0]);
        });
    }
}
