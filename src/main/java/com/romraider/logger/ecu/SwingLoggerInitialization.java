/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu;

import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.InitializationAttempt;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.logger.ecu.definition.EcuParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Owner-scoped initialization state. Transport callbacks update the cache
 * synchronously; queued UI notifications never assign cache state. Notifications
 * and close run on the EDT so an old queued notification cannot outlive closure.
 * This retains the existing ECU-ID cache key, not a transport/session identity.
 */
final class SwingLoggerInitialization {
    static final class Snapshot {
        final EcuInit ecu;
        final DmInit dime;
        final boolean dimeKnown;
        final long channelRevision;

        Snapshot(EcuInit ecu, DmInit dime, boolean dimeKnown, long channelRevision) {
            this.ecu = ecu;
            this.dime = dime;
            this.dimeKnown = dimeKnown;
            this.channelRevision = channelRevision;
        }
    }

    private final Consumer<Runnable> uiExecutor;
    private final Consumer<Snapshot> statePublisher;
    private final Consumer<Snapshot> listener;
    private Snapshot current = new Snapshot(null, null, false, 0);
    private Reload currentReload;
    private boolean closed;
    private final EcuInitCallback ecuCallback = new EcuInitCallback() {
        public void callback(EcuInit next) { acceptEcu(next); }
        public void callback(EcuInit next, InitializationAttempt attempt) {
            synchronized (SwingLoggerInitialization.this) {
                if (attempt.isActive()) acceptEcu(next);
            }
        }
    };
    private final DmInitCallback dimeCallback = new DmInitCallback() {
        public void callback(DmInit next, boolean forceUpdate) { acceptDime(next, forceUpdate); }
        public boolean needToInit() { return cacheForConnection() == null; }
        public DmInit getDmInit() { return cacheForConnection(); }
        public void callback(DmInit next, boolean forceUpdate, InitializationAttempt attempt) {
            synchronized (SwingLoggerInitialization.this) {
                if (attempt.isActive()) acceptDime(next, forceUpdate);
            }
        }
        public boolean needToInit(InitializationAttempt attempt) { return getDmInit(attempt) == null; }
        public DmInit getDmInit(InitializationAttempt attempt) {
            synchronized (SwingLoggerInitialization.this) {
                attempt.requireActive();
                return cacheForConnection();
            }
        }
    };

    SwingLoggerInitialization(Consumer<Runnable> uiExecutor,
            Consumer<Snapshot> statePublisher, Consumer<Snapshot> listener) {
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
        this.statePublisher = Objects.requireNonNull(statePublisher, "statePublisher");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    EcuInitCallback ecuCallback() { return ecuCallback; }
    DmInitCallback dimeCallback() { return dimeCallback; }
    synchronized EcuInit getEcuInit() { return current.ecu; }
    synchronized DmInit getDmInit() { return current.dime; }
    synchronized Snapshot snapshot() { return current; }
    synchronized boolean isOpen() { return !closed; }
    synchronized boolean isCurrent(Snapshot snapshot) { return !closed && current == snapshot; }

    /** Captures both initialization inputs and supersedes an earlier catalog reload. */
    synchronized Reload beginReload(Snapshot snapshot) {
        if (!isCurrent(snapshot)) return null;
        currentReload = new Reload(snapshot);
        return currentReload;
    }

    final class Reload {
        final Snapshot state;

        private Reload(Snapshot state) { this.state = state; }

        List<EcuParameter> parameters(List<EcuParameter> definitionParameters) {
            List<EcuParameter> result = new ArrayList<>(definitionParameters);
            if (state.dime != null) result.addAll(state.dime.getEcuParams());
            return result;
        }

        boolean isCurrent() {
            synchronized (SwingLoggerInitialization.this) {
                return currentReload == this && SwingLoggerInitialization.this.isCurrent(state);
            }
        }

        /** Stages can pump nested event loops. Never hold the owner lock across them. */
        boolean run(Runnable... stages) {
            for (Runnable stage : stages) {
                if (!isCurrent()) return false;
                stage.run();
            }
            return isCurrent();
        }
    }

    /**
     * A confirmation can pump a nested Swing event loop. Recheck ownership after
     * it returns, without holding the owner monitor across a modal dialog.
     * This guards entry to an update; it does not roll back an update in flight.
     */
    boolean applyReviewedUpdate(Snapshot snapshot, BooleanSupplier confirm, Runnable update) {
        final Reload reviewedCatalog;
        synchronized (this) {
            if (!isCurrent(snapshot)) return false;
            reviewedCatalog = currentReload;
        }
        if (!confirm.getAsBoolean()) return false;
        synchronized (this) {
            if (!isCurrent(snapshot) || currentReload != reviewedCatalog) return false;
        }
        update.run();
        return true;
    }

    private synchronized DmInit cacheForConnection() {
        // Returning null here after closure could select legacy discovery writes.
        if (closed) throw new IllegalStateException("Logger initialization owner is closed");
        return current.dime;
    }

    private void acceptEcu(EcuInit next) {
        final Snapshot snapshot;
        synchronized (this) {
            if (closed || next == null || current.ecu != null
                    && Objects.equals(current.ecu.getEcuId(), next.getEcuId())) return;
            snapshot = new Snapshot(next, null, false, current.channelRevision + 1);
            current = snapshot;
            // Invalidate shared capabilities immediately, even while the EDT is
            // busy. The publisher must not wait for UI work or perform device I/O.
            statePublisher.accept(snapshot);
        }
        notifyUi(snapshot);
    }

    private void acceptDime(DmInit next, boolean forceUpdate) {
        final Snapshot snapshot;
        synchronized (this) {
            if (closed || current.ecu == null) return;
            boolean channelsChanged = current.dime != next || next != null && forceUpdate;
            if (current.dimeKnown && !channelsChanged) return;
            snapshot = new Snapshot(current.ecu, next, true,
                    current.channelRevision + (channelsChanged ? 1 : 0));
            current = snapshot;
            statePublisher.accept(snapshot);
        }
        notifyUi(snapshot);
    }

    private void notifyUi(Snapshot snapshot) {
        uiExecutor.accept(() -> {
            requireEdt();
            if (isCurrent(snapshot)) listener.accept(snapshot);
        });
    }

    /** Call before stopping the controller or disposing the Swing owner. */
    boolean close() {
        requireEdt();
        synchronized (this) {
            if (closed) return false;
            closed = true;
            return true;
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Initialization UI notifications and closure require the EDT");
    }
}
