/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One sort worker per table; all requests and callback delivery belong to the UI thread. */
final class FxLogTableTask implements AutoCloseable {
    @FunctionalInterface interface Sorter { FxLogRows sort(LogDataset dataset, LogRange range, List<FxLogRows.SortKey> keys); }
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), task -> { Thread thread = new Thread(task, "rr2-log-table-sort"); thread.setDaemon(true); return thread; });
    private final Sorter sorter;
    private final Consumer<Runnable> dispatch;
    private final Consumer<FxLogRows> loaded;
    private final Consumer<Throwable> failed;
    private Future<?> pending;
    private long generation;
    private boolean closed;
    FxLogTableTask(Consumer<Runnable> dispatch, Consumer<FxLogRows> loaded, Consumer<Throwable> failed) {
        this(FxLogRows::sorted, dispatch, loaded, failed);
    }
    FxLogTableTask(Sorter sorter, Consumer<Runnable> dispatch, Consumer<FxLogRows> loaded, Consumer<Throwable> failed) {
        this.sorter = sorter; this.dispatch = dispatch; this.loaded = loaded; this.failed = failed;
    }
    void request(LogDataset dataset, LogRange range, List<FxLogRows.SortKey> requested) {
        if (closed) return;
        cancel(); long ticket = generation; List<FxLogRows.SortKey> keys = List.copyOf(requested);
        pending = worker.submit(() -> {
            try {
                FxLogRows rows = sorter.sort(dataset, range, keys);
                dispatch.accept(() -> { if (!closed && ticket == generation) loaded.accept(rows); });
            } catch (CancellationException cancelled) {
                // No late result from cancelled work.
            } catch (RuntimeException failure) {
                dispatch.accept(() -> { if (!closed && ticket == generation) failed.accept(failure); });
            }
        });
    }
    void cancel() { generation++; if (pending != null) pending.cancel(true); pending = null; worker.purge(); }
    Future<?> pending() { return pending; }
    @Override public void close() { if (!closed) { closed = true; cancel(); worker.shutdownNow(); } }
}
