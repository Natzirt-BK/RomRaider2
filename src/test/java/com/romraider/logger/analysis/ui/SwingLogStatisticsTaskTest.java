/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import static org.junit.Assert.*;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class SwingLogStatisticsTaskTest {
    private LogDataset data() throws Exception { return new RomRaiderCsvLogParser().parse("synthetic", new StringReader("Value\n2\nNaN\n8\n")); }
    private void drain(Queue<Runnable> ui) { Runnable next; while ((next = ui.poll()) != null) next.run(); }
    @Test public void realCalculationUsesRequestedRangeAndRunsOffCallerThread() throws Exception {
        var data = data(); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<SwingLogStatisticsTask.Result>(); Thread caller = Thread.currentThread();
        try (var task = new SwingLogStatisticsTask((source, range) -> { assertNotSame(caller, Thread.currentThread()); return LogStatisticsService.analyze(source, range); },
                ui::add, results::add, failure -> fail(failure.toString()))) {
            task.request(data, LogRange.all(data)); task.pending().get(5, TimeUnit.SECONDS); assertTrue(results.isEmpty()); drain(ui);
            assertEquals(5, results.get(0).statistics().get(0).getMean(), 0); assertEquals(1, results.get(0).statistics().get(0).getMissingCount());
            task.request(data, LogRange.of(1, 3, 3)); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(1, results.get(1).range().getStartInclusive()); assertEquals(8, results.get(1).statistics().get(0).getMean(), 0);
        }
    }
    @Test public void queuedResultsAndErrorsCannotReplaceNewerRangeOrClosedView() throws Exception {
        var data = data(); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<SwingLogStatisticsTask.Result>(); var failures = new ArrayList<Throwable>();
        try (var task = new SwingLogStatisticsTask((source, range) -> {
            if (range.getStartInclusive() == 0) throw new IllegalArgumentException("synthetic failure");
            return LogStatisticsService.analyze(source, range);
        }, ui::add, results::add, failures::add)) {
            task.request(data, LogRange.all(data)); task.pending().get(5, TimeUnit.SECONDS);
            task.request(data, LogRange.of(1, 3, 3)); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertTrue(failures.isEmpty()); assertEquals(1, results.size());
            task.request(data, LogRange.all(data)); task.pending().get(5, TimeUnit.SECONDS); drain(ui); assertEquals(1, failures.size());
            task.request(data, LogRange.of(2, 3, 3)); task.pending().get(5, TimeUnit.SECONDS); task.close(); drain(ui); assertEquals(1, results.size());
        }
    }
    @Test public void cancellationInterruptsRunningWorkAndBurstDoesNotAccumulateQueuedCalculations() throws Exception {
        var data = data(); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<SwingLogStatisticsTask.Result>();
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1), release = new CountDownLatch(1); AtomicInteger calls = new AtomicInteger();
        try (var task = new SwingLogStatisticsTask((source, range) -> {
            if (calls.getAndIncrement() == 0) { started.countDown();
                try { release.await(); } catch (InterruptedException expected) { interrupted.countDown(); try { release.await(); } catch (InterruptedException again) { throw new CancellationException(); } }
            }
            return LogStatisticsService.analyze(source, range);
        }, ui::add, results::add, failure -> fail(failure.toString()))) {
            task.request(data, LogRange.all(data)); assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) task.request(data, LogRange.of(2, 3, 3));
            assertTrue(interrupted.await(5, TimeUnit.SECONDS)); release.countDown(); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(2, calls.get()); assertEquals(1, results.size()); assertEquals(8, results.get(0).statistics().get(0).getMean(), 0);
        } finally { release.countDown(); }
    }
    @Test public void tooManyColumnsRejectWithoutCallingTheCalculator() throws Exception {
        String header = String.join(",", java.util.stream.IntStream.range(0, 257).mapToObj(i -> "C" + i).toList());
        var data = new RomRaiderCsvLogParser().parse("wide", new StringReader(header + "\n" + "1,".repeat(256) + "1\n"));
        var ui = new ConcurrentLinkedQueue<Runnable>(); var failures = new ArrayList<Throwable>();
        try (var task = new SwingLogStatisticsTask((source, range) -> { fail("Unbounded work"); return List.of(); }, ui::add,
                result -> fail("Partial result"), failures::add)) {
            task.request(data, LogRange.all(data)); task.pending().get(5, TimeUnit.SECONDS); drain(ui); assertEquals(1, failures.size());
        }
    }
}
