/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FxLogStatisticsTaskTest {
    @Test void rowChannelAndCellLimitsIncludeExactBoundariesWithoutIntegerOverflow() {
        assertDoesNotThrow(() -> FxLogStatisticsTask.requireSupported(1_000_000, 8));
        assertDoesNotThrow(() -> FxLogStatisticsTask.requireSupported(31_250, 256));
        assertThrows(IllegalArgumentException.class, () -> FxLogStatisticsTask.requireSupported(1_000_001, 1));
        assertThrows(IllegalArgumentException.class, () -> FxLogStatisticsTask.requireSupported(31_251, 256));
        assertThrows(IllegalArgumentException.class, () -> FxLogStatisticsTask.requireSupported(1, 257));
        assertThrows(IllegalArgumentException.class, () -> FxLogStatisticsTask.requireSupported(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
    private static LogDataset data() throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic", new StringReader("Time,Value\n0,2\n100,\n200,8\n"));
    }
    private static void drain(Queue<Runnable> ui) { Runnable next; while ((next = ui.poll()) != null) next.run(); }

    @Test void actualStatisticsRunOffUiAndRetainExactRangeMissingAndFiniteSemantics() throws Exception {
        var dataset = data(); var ui = new ConcurrentLinkedQueue<Runnable>();
        var results = new ArrayList<List<ChannelStatistics>>(); Thread caller = Thread.currentThread();
        try (var task = new FxLogStatisticsTask((source, range) -> {
            assertNotSame(caller, Thread.currentThread()); return LogStatisticsService.analyze(source, range);
        }, ui::add, results::add, failure -> fail(failure))) {
            task.request(dataset, LogRange.all(dataset)); task.pending().get(5, TimeUnit.SECONDS);
            assertTrue(results.isEmpty()); drain(ui);
            var channel = results.get(0).get(1);
            assertEquals(2, channel.getSampleCount()); assertEquals(1, channel.getMissingCount()); assertEquals(5, channel.getMean());
            task.request(dataset, LogRange.of(1, 3, 3)); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(8, results.get(1).get(1).getMean());
        }
    }
    @Test void completedQueuedResultsAreRejectedAfterNewRangeDraftOrClose() throws Exception {
        var dataset = data(); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<List<ChannelStatistics>>();
        try (var task = new FxLogStatisticsTask(ui::add, results::add, failure -> fail(failure))) {
            task.request(dataset, LogRange.all(dataset)); task.pending().get(5, TimeUnit.SECONDS);
            task.request(dataset, LogRange.of(2, 3, 3)); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(1, results.size()); assertEquals(8, results.get(0).get(1).getMean());
            task.request(dataset, LogRange.all(dataset)); task.pending().get(5, TimeUnit.SECONDS); task.cancel(); drain(ui);
            assertEquals(1, results.size());
            task.request(dataset, LogRange.all(dataset)); task.pending().get(5, TimeUnit.SECONDS); task.close(); drain(ui);
            task.request(dataset, LogRange.all(dataset)); assertNull(task.pending()); assertEquals(1, results.size());
        }
    }
    @Test void cancellationInterruptsWorkAndBurstOnlyComputesNewestQueuedRange() throws Exception {
        var dataset = data(); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<List<ChannelStatistics>>();
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var task = new FxLogStatisticsTask((source, range) -> {
            if (calls.getAndIncrement() == 0) {
                started.countDown();
                try { release.await(); }
                catch (InterruptedException expected) {
                    interrupted.countDown();
                    try { release.await(); } catch (InterruptedException again) { throw new CancellationException(); }
                }
            }
            return LogStatisticsService.analyze(source, range);
        }, ui::add, results::add, failure -> fail(failure))) {
            task.request(dataset, LogRange.all(dataset)); assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) task.request(dataset, LogRange.of(2, 3, 3));
            assertTrue(interrupted.await(5, TimeUnit.SECONDS)); release.countDown();
            task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(2, calls.get()); assertEquals(1, results.size()); assertEquals(8, results.get(0).get(1).getMean());
        } finally { release.countDown(); }
    }
    @Test void currentFailureIsReportedButStaleFailureCannotReplaceNewStatistics() throws Exception {
        var dataset = data(); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<List<ChannelStatistics>>();
        var failures = new ArrayList<Throwable>(); AtomicInteger calls = new AtomicInteger();
        try (var task = new FxLogStatisticsTask((source, range) -> {
            if (calls.getAndIncrement() < 2) throw new IllegalArgumentException("synthetic failure");
            return LogStatisticsService.analyze(source, range);
        }, ui::add, results::add, failures::add)) {
            task.request(dataset, LogRange.all(dataset)); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(1, failures.size());
            task.request(dataset, LogRange.all(dataset)); task.pending().get(5, TimeUnit.SECONDS);
            task.request(dataset, LogRange.all(dataset)); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(1, failures.size()); assertEquals(1, results.size());
        }
    }
    @Test void preflightRejectsUnsupportedColumnCountWithoutSchedulingOrPartialOutput() throws Exception {
        String header = String.join(",", java.util.stream.IntStream.range(0, 257).mapToObj(i -> "C" + i).toList());
        var dataset = new RomRaiderCsvLogParser().parse("wide", new StringReader(header + "\n" + "1,".repeat(256) + "1\n"));
        var failures = new ArrayList<Throwable>();
        try (var task = new FxLogStatisticsTask(Runnable::run, result -> fail("Partial result"), failures::add)) {
            task.request(dataset, LogRange.all(dataset)); assertNull(task.pending()); assertEquals(1, failures.size());
            assertTrue(failures.get(0).getMessage().contains("review limit"));
        }
    }
}
