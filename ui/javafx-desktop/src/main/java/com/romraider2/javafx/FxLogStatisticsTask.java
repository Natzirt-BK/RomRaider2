/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;

/** UI-thread-owned requests with bounded background work and latest-request delivery. */
final class FxLogStatisticsTask implements AutoCloseable {
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), task -> {
                Thread thread = new Thread(task, "rr2-log-statistics"); thread.setDaemon(true); return thread;
            });
    private final BiFunction<LogDataset, LogRange, List<ChannelStatistics>> calculate;
    private final Consumer<Runnable> dispatch;
    private final Consumer<List<ChannelStatistics>> loaded;
    private final Consumer<Throwable> failed;
    private Future<?> pending;
    private long generation;
    private boolean closed;

    FxLogStatisticsTask(Consumer<Runnable> dispatch, Consumer<List<ChannelStatistics>> loaded, Consumer<Throwable> failed) {
        this(LogStatisticsService::analyze, dispatch, loaded, failed);
    }
    FxLogStatisticsTask(BiFunction<LogDataset, LogRange, List<ChannelStatistics>> calculate,
            Consumer<Runnable> dispatch, Consumer<List<ChannelStatistics>> loaded, Consumer<Throwable> failed) {
        this.calculate = calculate; this.dispatch = dispatch; this.loaded = loaded; this.failed = failed;
    }

    void request(LogDataset dataset, LogRange range) {
        if (closed) return;
        cancel();
        long ticket = generation;
        try { requireSupported(range.size(), dataset.getChannelCount()); }
        catch (IllegalArgumentException failure) { failed.accept(failure); return; }
        pending = worker.submit(() -> {
            try {
                List<ChannelStatistics> result = List.copyOf(calculate.apply(dataset, range));
                dispatch.accept(() -> { if (!closed && ticket == generation) loaded.accept(result); });
            } catch (CancellationException cancelled) {
                // Superseded and closed requests have no visible result.
            } catch (RuntimeException failure) {
                dispatch.accept(() -> { if (!closed && ticket == generation) failed.accept(failure); });
            }
        });
    }
    static void requireSupported(int samples, int channels) {
        var limits = RomRaiderCsvLogParser.REVIEW_LIMITS;
        if (samples < 1 || channels < 1) throw new IllegalArgumentException("Statistics require samples and channels.");
        if (channels > limits.channels())
            throw new IllegalArgumentException("Statistics exceed the 256-column review limit; no partial statistics were calculated.");
        if (samples > limits.rows() || (long) samples * channels > limits.cells())
            throw new IllegalArgumentException("Statistics exceed the review limit. Select a smaller sample range; no partial statistics were calculated.");
    }
    void cancel() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null;
        worker.purge();
    }
    Future<?> pending() { return pending; }
    @Override public void close() { if (!closed) { closed = true; cancel(); worker.shutdownNow(); } }
}
