/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Completion is queued only after work (including connection cleanup) exits. */
public final class DesktopDiagnosticTask<T> {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Callable<T> work;
    private final BooleanSupplier current;
    private final Consumer<Completion<T>> completion;
    private final Consumer<Runnable> ui;
    private Thread worker;

    public DesktopDiagnosticTask(Callable<T> work, BooleanSupplier current, Consumer<Completion<T>> completion) {
        this(work, current, completion, SwingUtilities::invokeLater);
    }

    DesktopDiagnosticTask(Callable<T> work, BooleanSupplier current, Consumer<Completion<T>> completion, Consumer<Runnable> ui) {
        this.work = work;
        this.current = current;
        this.completion = completion;
        this.ui = ui;
    }

    public synchronized void start() {
        if (worker != null) throw new IllegalStateException("Diagnostic task already started");
        worker = new Thread(this::run, "Diagnostic read");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void cancel() {
        cancelled.set(true);
        if (worker != null) worker.interrupt();
    }

    private void run() {
        T result = null;
        Throwable failure = null;
        try {
            if (!cancelled.get() && ownerIsCurrent()) result = work.call();
        } catch (Throwable e) { failure = e; }
        T completedResult = result;
        Throwable completedFailure = failure;
        ui.accept(() -> {
            boolean stale = !ownerIsCurrent();
            boolean stopped = cancelled.get() || completedFailure instanceof InterruptedException;
            completion.accept(new Completion<>(stale || stopped ? null : completedResult,
                    completedFailure, stopped, stale));
        });
    }

    private boolean ownerIsCurrent() {
        try { return current.getAsBoolean(); }
        catch (RuntimeException unavailable) { return false; }
    }

    public record Completion<T>(T result, Throwable failure, boolean cancelled, boolean stale) {
        public boolean succeeded() { return !cancelled && !stale && failure == null && result != null; }
    }
}
