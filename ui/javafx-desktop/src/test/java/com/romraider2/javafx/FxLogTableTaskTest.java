/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FxLogTableTaskTest {
    private static LogDataset data() throws Exception { return new RomRaiderCsvLogParser().parse("synthetic", new StringReader("Value\n3\n1\n2\n")); }
    private static final List<FxLogRows.SortKey> ASC = List.of(new FxLogRows.SortKey(0, false));
    private static void drain(Queue<Runnable> ui) { Runnable next; while ((next = ui.poll()) != null) next.run(); }
    @Test void queuedSortResultsRespectNewestRangeDraftAndClose() throws Exception {
        var data = data(); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<FxLogRows>();
        Thread caller = Thread.currentThread();
        try (var task = new FxLogTableTask((source, range, keys) -> {
            assertNotSame(caller, Thread.currentThread()); return FxLogRows.sorted(source, range, keys);
        }, ui::add, results::add, failure -> fail(failure))) {
            task.request(data, LogRange.all(data), ASC); task.pending().get(5, TimeUnit.SECONDS);
            task.request(data, LogRange.of(1, 3, 3), ASC); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(List.of(List.of(1, 2)), results);
            task.request(data, LogRange.all(data), ASC); task.pending().get(5, TimeUnit.SECONDS); task.cancel(); drain(ui);
            assertEquals(1, results.size());
            task.request(data, LogRange.all(data), ASC); task.pending().get(5, TimeUnit.SECONDS); task.close(); drain(ui);
            task.request(data, LogRange.all(data), ASC); assertNull(task.pending()); assertEquals(1, results.size());
        }
    }
    @Test void cancellationInterruptsWorkerAndRemovesSupersededQueuedSorts() throws Exception {
        var data = data(); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<FxLogRows>();
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var task = new FxLogTableTask((source, range, keys) -> {
            if (calls.getAndIncrement() == 0) {
                started.countDown();
                try { release.await(); } catch (InterruptedException expected) {
                    interrupted.countDown();
                    try { release.await(); } catch (InterruptedException again) { throw new CancellationException(); }
                }
            }
            return FxLogRows.sorted(source, range, keys);
        }, ui::add, results::add, failure -> fail(failure))) {
            task.request(data, LogRange.all(data), ASC); assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) task.request(data, LogRange.of(1, 3, 3), ASC);
            assertTrue(interrupted.await(5, TimeUnit.SECONDS)); release.countDown(); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(2, calls.get()); assertEquals(List.of(List.of(1, 2)), results);
        } finally { release.countDown(); }
    }
    @Test void currentErrorIsReportedButSupersededErrorCannotReplaceSuccessfulSort() throws Exception {
        var data = data(); var ui = new ConcurrentLinkedQueue<Runnable>(); var results = new ArrayList<FxLogRows>(); var failures = new ArrayList<Throwable>();
        AtomicInteger calls = new AtomicInteger();
        try (var task = new FxLogTableTask((source, range, keys) -> {
            if (calls.getAndIncrement() < 2) throw new IllegalArgumentException("synthetic failure");
            return FxLogRows.sorted(source, range, keys);
        }, ui::add, results::add, failures::add)) {
            task.request(data, LogRange.all(data), ASC); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            task.request(data, LogRange.all(data), ASC); task.pending().get(5, TimeUnit.SECONDS);
            task.request(data, LogRange.all(data), ASC); task.pending().get(5, TimeUnit.SECONDS); drain(ui);
            assertEquals(1, failures.size()); assertEquals(List.of(List.of(1, 2, 0)), results);
        }
    }
}
