package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.List;
import com.romraider.logger.analysis.*;
import javafx.scene.chart.LineChart;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLogAnalysisPaneSmokeTest {
    @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }

    @Test void sortingAndRangesRetainSourceSampleIdentity() throws Exception {
        FxLogAnalysisPane[] pane = new FxLogAnalysisPane[1];
        try {
            FxTestRuntime.run(() -> {
                LogDataset dataset = new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader(
                        "Time (msec),Value\n0,2\n100,10\n200,3\n300,20\n"));
                pane[0] = new FxLogAnalysisPane(null, dataset);
            });
            FxTestRuntime.run(() -> {
                TableView<Integer> table = field(pane[0], "values");
                table.getSortOrder().setAll(table.getColumns().get(2));
                table.sort();
                assertEquals(List.of(0, 2, 1, 3), table.getItems());
                table.getSelectionModel().select(1);
                LogCursorModel cursor = field(pane[0], "cursor");
                assertEquals(2, cursor.getSampleIndex());
                LogPlaybackService playback = field(pane[0], "playback");
                playback.seek(1);
            });
            FxTestRuntime.run(() -> {
                TableView<Integer> table = field(pane[0], "values");
                assertEquals(Integer.valueOf(1), table.getSelectionModel().getSelectedItem());
                pane[0].selectRange(LogRange.of(1, 3, 4));
                assertEquals(List.of(2, 1), table.getItems());
                TableView<ChannelStatistics> stats = field(pane[0], "statistics");
                assertEquals(6.5, stats.getItems().get(1).getMean());
                LineChart<Number, Number> chart = field(pane[0], "timelineChart");
                assertEquals(2, chart.getData().get(0).getData().size());
                LogPlaybackService playback = field(pane[0], "playback");
                playback.seek(99);
                assertEquals(2, playback.snapshot().getSampleIndex());
                TextField first = field(pane[0], "rangeStart");
                first.setText("99"); first.fireEvent(new ActionEvent());
                assertEquals(List.of(2, 1), table.getItems());
                pane[0].selectRange(LogRange.all(new RomRaiderCsvLogParser().parse(
                        "synthetic.csv", new StringReader("Value\n1\n2\n3\n4\n"))));
                assertEquals(List.of(0, 2, 1, 3), table.getItems());
            });
        } finally { FxTestRuntime.run(() -> { if (pane[0] != null) pane[0].close(); }); }
    }
}
