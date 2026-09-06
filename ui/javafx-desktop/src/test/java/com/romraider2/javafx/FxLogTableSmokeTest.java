/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.List;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLogTableSmokeTest {
    static void awaitTable(FxLogAnalysisPane pane) throws Exception {
        FxLogTableTask task = field(pane, "tableTask"); FxLogStatisticsSmokeTest.awaitWork(task.pending());
    }
    @Test void nativeSortRetainsCurrentCursorAndRangeAndRestoresSourceOrderWhenCleared() throws Exception {
        var data = new RomRaiderCsvLogParser().parse("synthetic", new StringReader("Time,Value\n0,3\n100,1\n200,2\n300,4\n"));
        FxTestRuntime.run(() -> {
            try (var pane = new FxLogAnalysisPane(null, data)) {
                TableView<Integer> table = field(pane, "values"); LogPlaybackService playback = field(pane, "playback");
                assertInstanceOf(FxLogRows.class, table.getItems()); playback.seek(2);
                table.getSortOrder().setAll(table.getColumns().get(2));
                assertEquals(List.of(0, 1, 2, 3), table.getItems()); awaitTable(pane);
                assertEquals(List.of(1, 2, 0, 3), table.getItems()); assertEquals(2, playback.snapshot().getSampleIndex());
                assertEquals(List.of(table.getColumns().get(2)), table.getSortOrder());
                assertEquals(2, table.getSelectionModel().getSelectedItem());
                pane.selectRange(LogRange.of(1, 3, 4)); awaitTable(pane); assertEquals(List.of(1, 2), table.getItems());
                table.getColumns().get(2).setSortType(TableColumn.SortType.DESCENDING); awaitTable(pane);
                assertEquals(List.of(2, 1), table.getItems());
                table.getSelectionModel().select(1); assertEquals(1, playback.snapshot().getSampleIndex());
                table.getSortOrder().clear(); assertEquals(List.of(1, 2), table.getItems()); assertEquals(1, playback.snapshot().getSampleIndex());
                table.getSortOrder().setAll(table.getColumns().get(2)); pane.invalidateSharedRange();
                assertTrue(table.getItems().isEmpty()); FxLogTableTask task = field(pane, "tableTask"); assertNull(task.pending());
                pane.selectRange(LogRange.of(2, 4, 4)); awaitTable(pane); assertEquals(List.of(3, 2), table.getItems());
                pane.close(); assertNull(task.pending());
            }
        });
    }
    @Test void largerNativeTableSortDoesNotAllocateBoxedSourceRowsOrLoseLastSampleSelection() throws Exception {
        StringBuilder csv = new StringBuilder("Value\n"); for (int i = 20_000; i > 0; i--) csv.append(i).append('\n');
        var data = new RomRaiderCsvLogParser().parse("synthetic", new StringReader(csv.toString()));
        FxTestRuntime.run(() -> {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            try (var pane = new FxLogAnalysisPane(null, data)) {
                TableView<Integer> table = field(pane, "values"); LogPlaybackService playback = field(pane, "playback");
                assertInstanceOf(FxLogRows.class, table.getItems()); assertEquals(20_000, table.getItems().size());
                playback.seek(19_999); table.getSortOrder().setAll(table.getColumns().get(1)); awaitTable(pane);
                assertEquals(19_999, table.getItems().get(0)); assertEquals(0, table.getItems().indexOf(19_999));
                assertEquals(19_999, table.getSelectionModel().getSelectedItem()); assertEquals(19_999, playback.snapshot().getSampleIndex());
                FxLogStatisticsSmokeTest.awaitStatistics(pane);
                javafx.scene.Scene scene = new javafx.scene.Scene(pane, 800, 600);
                FxTheme.apply(stage, scene); stage.setScene(scene); stage.show();
                TabPane tabs = (TabPane) pane.getCenter();
                for (int index : new int[]{0, 3}) {
                    tabs.getSelectionModel().select(index); pane.applyCss(); pane.layout();
                    TableView<?> visible = index == 0 ? table : field(pane, "statistics");
                    assertTrue(visible.getHeight() > 180, "Compact workspace retains table space");
                    String prefix = System.getenv("RR2_LOG_TABLE_CAPTURE");
                    if (prefix != null && !prefix.isBlank()) {
                        var snapshot = pane.snapshot(null, null);
                        var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        assertTrue(javax.imageio.ImageIO.write(image, "png", new java.io.File(prefix + (index == 0 ? "-table.png" : "-statistics.png"))));
                    }
                }
            } finally { stage.close(); }
        });
    }
}
