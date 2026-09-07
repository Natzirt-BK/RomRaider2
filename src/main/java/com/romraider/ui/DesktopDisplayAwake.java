/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A window-scoped request, independent of acquisition/recording and UI toolkits. */
public final class DesktopDisplayAwake implements AutoCloseable {
    public enum Status {
        OFF("Screen awake off"), REQUESTING("Requesting screen awake…"),
        ACTIVE("Screen awake requested"), RELEASING("Releasing screen awake…"),
        UNAVAILABLE("Screen awake unavailable");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @FunctionalInterface public interface Backend { Lease acquire() throws Exception; }
    public interface Lease extends AutoCloseable {
        default boolean isValid() throws Exception { return true; }
        @Override void close() throws Exception;
    }
    /** A failed acquisition can still own a native resource whose cleanup failed. */
    static final class AcquisitionFailure extends Exception {
        final Lease cleanup;
        AcquisitionFailure(Throwable cause, Lease cleanup) { super(cause); this.cleanup = Objects.requireNonNull(cleanup); }
    }

    private final Backend backend;
    private final ScheduledThreadPoolExecutor worker;
    private boolean desired, closed, queued;
    private long revision, attempted = -1, leaseRevision = -1;
    private volatile Status status = Status.OFF;
    private volatile String failure = "";
    // These fields are used only by the dedicated worker. Windows execution
    // requests must be acquired and cleared on the same native thread.
    private Lease lease;
    private ScheduledFuture<?> health;

    public DesktopDisplayAwake() { this(DesktopDisplayAwakeNative::acquire); }

    /** Injection boundary for synthetic UI/lifecycle tests; no native request is needed. */
    public DesktopDisplayAwake(Backend backend) {
        this.backend = Objects.requireNonNull(backend);
        worker = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "Gauge display awake");
            thread.setDaemon(true);
            return thread;
        });
        worker.setRemoveOnCancelPolicy(true);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    public Status getStatus() { return status; }
    public String getFailure() { return failure; }

    /** Call with fullScreen && showing && focused && !minimized. Never blocks the UI. */
    public synchronized void setActive(boolean active) {
        if (closed || desired == active) return;
        desired = active;
        revision++;
        status = active ? Status.REQUESTING : Status.RELEASING;
        enqueue();
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        desired = false;
        revision++;
        status = Status.RELEASING;
        enqueue();
    }

    private void enqueue() {
        if (!queued) { queued = true; worker.execute(this::reconcile); }
    }

    private void reconcile() {
        if (health != null) { health.cancel(false); health = null; }
        while (true) {
            final boolean active;
            final long request;
            synchronized (this) { active = desired && !closed; request = revision; }
            if (lease != null && (!active || leaseRevision != request)) {
                if (!release()) {
                    synchronized (this) { queued = false; }
                    health = worker.schedule(() -> {
                        synchronized (this) { enqueue(); }
                    }, 2, TimeUnit.SECONDS);
                    return;
                }
            }
            if (active && lease == null && attempted != request) {
                attempted = request;
                try {
                    lease = Objects.requireNonNull(backend.acquire(), "No screen-awake lease");
                    leaseRevision = request;
                    failure = "";
                } catch (AcquisitionFailure error) {
                    lease = error.cleanup;
                    leaseRevision = -1;
                    fail(error);
                    continue;
                } catch (Exception | LinkageError error) { fail(error); }
            }
            synchronized (this) {
                // A request acquired after focus loss/close must be released before
                // publishing ACTIVE, including a rapid lose/regain-focus cycle.
                if (revision != request) continue;
                status = !active ? Status.OFF : lease != null ? Status.ACTIVE : Status.UNAVAILABLE;
                queued = false;
                if (closed && lease == null) worker.shutdown();
                else if (lease != null) health = worker.schedule(this::checkHealth, 2, TimeUnit.SECONDS);
                return;
            }
        }
    }

    private void checkHealth() {
        if (lease == null) return;
        try {
            if (lease.isValid()) {
                health = worker.schedule(this::checkHealth, 2, TimeUnit.SECONDS);
                return;
            }
            fail(new IllegalStateException("The desktop screen-awake service disconnected"));
        } catch (Exception | LinkageError error) { fail(error); }
        // Do not repeatedly prompt a denying/unavailable service. A new foreground
        // full-screen activation retries. Failed cleanup remains owned and is retried.
        leaseRevision = -1;
        synchronized (this) { enqueue(); }
    }

    private boolean release() {
        try { lease.close(); lease = null; return true; }
        catch (Exception | LinkageError error) { fail(error); return false; }
    }

    private void fail(Throwable error) {
        failure = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        status = Status.UNAVAILABLE;
    }
}
