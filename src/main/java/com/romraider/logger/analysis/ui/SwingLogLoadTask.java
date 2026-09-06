/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import com.romraider.logger.analysis.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** UI-owned latest-request delivery; CSV, initial statistics and sidecar reads are background work. */
final class SwingLogLoadTask implements AutoCloseable {
    record PreparedLog(File source, LogDataset dataset, LogMarkerStore.Snapshot markers,
            String markerProblem, List<ChannelStatistics> statistics) {
        PreparedLog { statistics = List.copyOf(statistics); }
    }
    @FunctionalInterface interface Loader { PreparedLog prepare(File source) throws Exception; }
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), task -> { Thread thread = new Thread(task, "rr2-swing-log-import"); thread.setDaemon(true); return thread; });
    private final Loader loader;
    private final Consumer<Runnable> dispatch;
    private final Consumer<PreparedLog> loaded;
    private final BiConsumer<File, Throwable> failed;
    private Future<?> pending;
    private long generation;
    private boolean closed;

    SwingLogLoadTask(Consumer<Runnable> dispatch, Consumer<PreparedLog> loaded, BiConsumer<File, Throwable> failed) {
        this(SwingLogLoadTask::prepare, dispatch, loaded, failed);
    }
    SwingLogLoadTask(Loader loader, Consumer<Runnable> dispatch, Consumer<PreparedLog> loaded, BiConsumer<File, Throwable> failed) {
        this.loader = loader; this.dispatch = dispatch; this.loaded = loaded; this.failed = failed;
    }
    static PreparedLog prepare(File source) throws IOException {
        File file = source.getAbsoluteFile();
        LogDataset dataset = new RomRaiderCsvLogParser().parse(file, RomRaiderCsvLogParser.REVIEW_LIMITS);
        List<ChannelStatistics> statistics = LogStatisticsService.analyze(dataset, LogRange.all(dataset));
        LogMarkerStore.Snapshot markers = null; String problem = null;
        try { markers = new LogMarkerStore().loadSnapshot(file, dataset.getRowCount()); }
        catch (IOException failure) {
            if (Thread.currentThread().isInterrupted()) throw failure;
            problem = "Markers could not be read. Reload the log before editing: " + failure.getMessage();
        }
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        return new PreparedLog(file, dataset, markers, problem, statistics);
    }
    void load(File source) {
        if (closed || source == null) return;
        cancel(); long ticket = generation; File file = source.getAbsoluteFile();
        pending = worker.submit(() -> {
            try {
                PreparedLog result = Objects.requireNonNull(loader.prepare(file));
                dispatch.accept(() -> { if (!closed && ticket == generation) loaded.accept(result); });
            } catch (CancellationException cancelled) {
                // Superseded work must not alter the current workspace or report a late error.
            } catch (Exception failure) {
                dispatch.accept(() -> { if (!closed && ticket == generation) failed.accept(file, failure); });
            }
        });
    }
    void cancel() { generation++; if (pending != null) pending.cancel(true); pending = null; worker.purge(); }
    Future<?> pending() { return pending; }
    @Override public void close() { if (!closed) { closed = true; cancel(); worker.shutdownNow(); } }
}
