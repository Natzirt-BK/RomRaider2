/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import com.romraider.logger.analysis.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Event-thread-owned requests; numeric work and cancellation belong to one worker. */
final class SwingLogStatisticsTask implements AutoCloseable {
    record Result(LogRange range, List<ChannelStatistics> statistics) { Result { statistics = List.copyOf(statistics); } }
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), task -> { Thread thread = new Thread(task, "rr2-swing-log-statistics"); thread.setDaemon(true); return thread; });
    private final BiFunction<LogDataset, LogRange, List<ChannelStatistics>> calculate;
    private final Consumer<Runnable> dispatch;
    private final Consumer<Result> loaded;
    private final Consumer<Throwable> failed;
    private Future<?> pending;
    private boolean closed;
    private long generation;
    SwingLogStatisticsTask(Consumer<Runnable> dispatch, Consumer<Result> loaded, Consumer<Throwable> failed) {
        this(LogStatisticsService::analyze, dispatch, loaded, failed);
    }
    SwingLogStatisticsTask(BiFunction<LogDataset, LogRange, List<ChannelStatistics>> calculate,
            Consumer<Runnable> dispatch, Consumer<Result> loaded, Consumer<Throwable> failed) {
        this.calculate = calculate; this.dispatch = dispatch; this.loaded = loaded; this.failed = failed;
    }
    void request(LogDataset dataset, LogRange range) {
        if (closed) return;
        if (pending != null) pending.cancel(true); worker.purge(); long ticket = ++generation;
        pending = worker.submit(() -> {
            try {
                var limits = RomRaiderCsvLogParser.REVIEW_LIMITS;
                if (range.size() > limits.rows() || dataset.getChannelCount() > limits.channels()
                        || (long) range.size() * dataset.getChannelCount() > limits.cells())
                    throw new IllegalArgumentException("Statistics exceed the review limit; no partial statistics were calculated");
                Result result = new Result(range, calculate.apply(dataset, range));
                dispatch.accept(() -> { if (!closed && ticket == generation) loaded.accept(result); });
            } catch (CancellationException cancelled) {
                // Superseded work has no visible result.
            } catch (RuntimeException failure) {
                dispatch.accept(() -> { if (!closed && ticket == generation) failed.accept(failure); });
            }
        });
    }
    Future<?> pending() { return pending; }
    @Override public void close() {
        if (closed) return; closed = true; generation++;
        if (pending != null) pending.cancel(true); worker.shutdownNow();
    }
}
