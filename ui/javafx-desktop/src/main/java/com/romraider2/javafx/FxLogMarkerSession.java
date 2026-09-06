/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Serialized asynchronous marker I/O. UI changes follow successful persistence. */
final class FxLogMarkerSession implements AutoCloseable {
    record State(List<LogMarker> markers, boolean busy, boolean editable, String message) { }
    interface Persistence {
        LogMarkerStore.Snapshot load(File source, int samples) throws IOException;
        LogMarkerStore.Snapshot save(LogMarkerStore.Snapshot expected, List<LogMarker> markers) throws IOException;
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "rr2-log-markers"); thread.setDaemon(true); return thread;
    });
    private final File source;
    private final int samples;
    private final Persistence persistence;
    private final Consumer<Runnable> dispatch;
    private final Consumer<State> changed;
    private LogMarkerStore.Snapshot snapshot;
    private List<LogMarker> markers = List.of();
    private Future<?> pending;
    private boolean busy, editable, saving;
    private volatile boolean closed;
    private long generation;

    FxLogMarkerSession(File source, int samples, Consumer<Runnable> dispatch, Consumer<State> changed) {
        this(source, samples, new Persistence() {
            private final LogMarkerStore store = new LogMarkerStore();
            public LogMarkerStore.Snapshot load(File file, int count) throws IOException { return store.loadSnapshot(file, count); }
            public LogMarkerStore.Snapshot save(LogMarkerStore.Snapshot expected, List<LogMarker> next) throws IOException { return store.saveIfUnchanged(expected, next); }
        }, dispatch, changed);
    }
    FxLogMarkerSession(File source, int samples, Persistence persistence, Consumer<Runnable> dispatch, Consumer<State> changed) {
        this.source = source; this.samples = samples; this.persistence = persistence; this.dispatch = dispatch; this.changed = changed;
    }
    void load() {
        if (closed || busy) return;
        long ticket = ++generation; editable = false;
        if (source == null) { editable = true; publish("Session-only markers; no source CSV is associated with this view."); return; }
        busy = true; saving = false; publish("Loading markers… Editing is unavailable until the sidecar is validated.");
        pending = worker.submit(() -> {
            try {
                var loaded = persistence.load(source, samples);
                dispatch.accept(() -> {
                    if (!current(ticket)) return;
                    snapshot = loaded; markers = loaded.getMarkers(); busy = false; editable = true;
                    publish(markers.size() + (markers.size() == 1 ? " marker loaded." : " markers loaded.") + " Changes are saved separately from the CSV.");
                });
            } catch (IOException | RuntimeException failure) { failed(ticket, failure, false); }
        });
    }
    void replace(List<LogMarker> proposed) {
        if (closed || busy || !editable) throw new IllegalStateException("Wait for marker loading/saving, or reload after an error, before editing");
        List<LogMarker> frozen = List.copyOf(proposed);
        LogMarkerStore.Snapshot expected = snapshot;
        long ticket = ++generation; busy = true; saving = true; editable = false;
        publish("Saving markers… The previous saved list remains visible until this finishes.");
        pending = worker.submit(() -> {
            try {
                var checked = LogMarkerStore.validateMarkers(frozen);
                for (LogMarker marker : checked) if (marker.getSampleIndex() >= samples) throw new IOException("Marker is outside this log's sample range");
                var saved = source == null ? null : persistence.save(expected, checked);
                dispatch.accept(() -> {
                    if (!current(ticket)) return;
                    snapshot = saved; markers = saved == null ? checked : saved.getMarkers();
                    busy = false; saving = false; editable = true;
                    publish(source == null ? "Session-only markers updated; no file written." : "Marker sidecar saved. CSV unchanged.");
                });
            } catch (IOException | RuntimeException failure) { failed(ticket, failure, true); }
        });
    }
    private void failed(long ticket, Exception failure, boolean write) {
        if (write) java.util.logging.Logger.getLogger(FxLogMarkerSession.class.getName()).log(java.util.logging.Level.WARNING, "Marker save failed; prior file/list retained", failure);
        dispatch.accept(() -> {
            if (!current(ticket)) return;
            busy = false; saving = false; editable = false;
            String detail = FxDialogs.rootMessage(failure);
            publish((write ? "Markers were not saved; prior list retained. " : "Markers could not be loaded. ")
                    + detail + (detail.endsWith(".") ? " " : ". ") + "Reload markers before editing.");
        });
    }
    private boolean current(long ticket) { return !closed && ticket == generation; }
    private void publish(String message) { changed.accept(new State(markers, busy, editable, message)); }
    Future<?> pending() { return pending; }
    @Override public void close() {
        if (closed) return;
        closed = true; generation++;
        if (!saving && pending != null) pending.cancel(true);
        // An accepted edit is allowed to finish its atomic save after the view closes.
        // Late callbacks cannot touch the closed view; failures are recorded in the application log.
        worker.shutdown();
    }
}
