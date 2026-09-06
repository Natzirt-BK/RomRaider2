/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import static com.romraider2.javafx.FxEditorControlsSmokeTest.field;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxLogStatisticsSmokeTest {
    /** Let normal UI delivery run while awaiting background work, without blocking JavaFX. */
    static void awaitStatistics(FxLogAnalysisPane pane) throws Exception {
        FxLogStatisticsTask task = field(pane, "statisticsTask"); Future<?> pending = task.pending();
        awaitWork(pending);
    }
    static void awaitWork(Future<?> pending) {
        if (pending == null) return;
        Object key = new Object();
        Thread waiter = new Thread(() -> {
            Throwable failure = null;
            try { pending.get(10, TimeUnit.SECONDS); } catch (Exception problem) { failure = problem; }
            Throwable result = failure;
            Platform.runLater(() -> Platform.exitNestedEventLoop(key, result));
        }, "rr2-statistics-test-wait");
        waiter.setDaemon(true); waiter.start();
        Object failure = Platform.enterNestedEventLoop(key);
        if (failure != null) fail((Throwable) failure);
    }

    @Test void rangeChangesImmediatelyClearStatisticsAndDraftOrCloseRejectsLateDelivery() throws Exception {
        var dataset = new RomRaiderCsvLogParser().parse("synthetic", new StringReader("Time,Value\n0,2\n100,10\n200,3\n"));
        FxTestRuntime.run(() -> {
            try (var pane = new FxLogAnalysisPane(null, dataset)) {
                TableView<ChannelStatistics> statistics = field(pane, "statistics");
                Label status = field(pane, "statisticsStatus");
                assertTrue(statistics.getItems().isEmpty()); assertTrue(status.getText().startsWith("Calculating"));
                awaitStatistics(pane); assertEquals(5, statistics.getItems().get(1).getMean());
                pane.selectRange(LogRange.of(1, 3, 3));
                assertTrue(statistics.getItems().isEmpty()); awaitStatistics(pane);
                assertEquals(6.5, statistics.getItems().get(1).getMean()); assertTrue(status.getText().contains("Samples 2–3"));
                pane.selectRange(LogRange.all(dataset)); pane.invalidateSharedRange();
                assertTrue(statistics.getItems().isEmpty()); assertTrue(status.getText().startsWith("Apply range"));
                pane.selectRange(LogRange.of(2, 3, 3)); awaitStatistics(pane);
                assertEquals(3, statistics.getItems().get(1).getMean());
                pane.selectRange(LogRange.all(dataset)); pane.close();
                FxLogStatisticsTask task = field(pane, "statisticsTask"); assertNull(task.pending());
                assertTrue(statistics.getItems().isEmpty());
            }
        });
    }
}
