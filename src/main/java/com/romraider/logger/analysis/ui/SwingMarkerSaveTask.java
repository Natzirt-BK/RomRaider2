/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis.ui;

import com.romraider.logger.analysis.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One accepted edit at a time; closure suppresses delivery but lets accepted writes finish. */
final class SwingMarkerSaveTask implements AutoCloseable {
    @FunctionalInterface interface Writer { LogMarkerStore.Snapshot save(LogMarkerStore.Snapshot expected, List<LogMarker> markers) throws IOException; }
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "rr2-swing-marker-save"); thread.setDaemon(true); return thread;
    });
    private final Writer writer;
    private final Consumer<Runnable> dispatch;
    private final Consumer<LogMarkerStore.Snapshot> saved;
    private final Consumer<Throwable> failed;
    private Future<?> pending;
    private boolean closed, busy;
    SwingMarkerSaveTask(Consumer<Runnable> dispatch, Consumer<LogMarkerStore.Snapshot> saved, Consumer<Throwable> failed) {
        this(new LogMarkerStore()::saveIfUnchanged, dispatch, saved, failed);
    }
    SwingMarkerSaveTask(Writer writer, Consumer<Runnable> dispatch, Consumer<LogMarkerStore.Snapshot> saved, Consumer<Throwable> failed) {
        this.writer = writer; this.dispatch = dispatch; this.saved = saved; this.failed = failed;
    }
    void save(LogMarkerStore.Snapshot expected, List<LogMarker> proposed) {
        if (closed || busy) throw new IllegalStateException("A marker save is already pending or this workspace is closed");
        List<LogMarker> frozen = List.copyOf(proposed); busy = true;
        pending = worker.submit(() -> {
            try {
                var snapshot = writer.save(expected, frozen);
                dispatch.accept(() -> { if (!closed) { busy = false; saved.accept(snapshot); } });
            } catch (IOException | RuntimeException failure) {
                java.util.logging.Logger.getLogger(SwingMarkerSaveTask.class.getName()).log(java.util.logging.Level.WARNING,
                        "Marker save failed; prior file/list retained", failure);
                dispatch.accept(() -> { if (!closed) { busy = false; failed.accept(failure); } });
            }
        });
    }
    Future<?> pending() { return pending; }
    @Override public void close() { if (!closed) { closed = true; worker.shutdown(); } }
}
