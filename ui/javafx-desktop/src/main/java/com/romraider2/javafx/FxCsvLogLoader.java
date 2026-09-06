/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.RomRaiderCsvLogParser;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.*;

/** One bounded parse at a time; cancellation interrupts actual work, not just delivery. */
final class FxCsvLogLoader implements AutoCloseable {
    @FunctionalInterface interface Parser { LogDataset parse(File file) throws Exception; }
    private final Parser parser;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), task -> {
                Thread thread = new Thread(task, "rr2-csv-import");
                thread.setDaemon(true); return thread;
            });
    private CompletableFuture<LogDataset> pending;
    private boolean closed;

    FxCsvLogLoader() {
        this(file -> new RomRaiderCsvLogParser().parse(file, RomRaiderCsvLogParser.REVIEW_LIMITS));
    }
    FxCsvLogLoader(Parser parser) { this.parser = Objects.requireNonNull(parser); }

    synchronized CompletableFuture<LogDataset> load(File file) {
        Objects.requireNonNull(file);
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("CSV loader is closed"));
        if (pending != null) pending.cancel(true);
        // Cancelled queued requests must not accumulate behind slow filesystem I/O.
        worker.purge();
        CompletableFuture<LogDataset> result = new CompletableFuture<>();
        FutureTask<LogDataset> task = new FutureTask<>(() -> parser.parse(file)) {
            @Override protected void done() {
                try { result.complete(get()); }
                catch (CancellationException cancelled) { result.cancel(false); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); result.completeExceptionally(interrupted);
                } catch (ExecutionException failure) { result.completeExceptionally(failure.getCause()); }
            }
        };
        result.whenComplete((value, failure) -> { if (result.isCancelled()) task.cancel(true); });
        pending = result;
        worker.execute(task);
        return result;
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (pending != null) pending.cancel(true);
        worker.shutdownNow();
    }
}
