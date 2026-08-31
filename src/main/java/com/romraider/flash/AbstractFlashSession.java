/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** State/cancellation foundation shared by independently implemented protocols. */
public abstract class AbstractFlashSession implements FlashSession {
    private final UUID id = UUID.randomUUID();
    private final FlashRequest request;
    private final FlashProgressListener listener;
    private final AtomicReference<FlashState> state =
            new AtomicReference<FlashState>(FlashState.CREATED);
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();

    protected AbstractFlashSession(FlashRequest request,
            FlashProgressListener listener) {
        if (request == null) throw new IllegalArgumentException("request is required");
        this.request = request;
        this.listener = listener;
    }

    public final UUID getId() { return id; }
    public final FlashRequest getRequest() { return request; }
    public final FlashState getState() { return state.get(); }

    public final boolean requestCancellation() {
        FlashState current = state.get();
        if (current.isTerminal() || !current.isCancellationSafe()) return false;
        cancellationRequested.set(true);
        return true;
    }

    protected final boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    protected final void publish(FlashState next, String message) {
        transition(next);
        notifyListener(FlashProgress.indeterminate(next, message));
    }

    protected final void publishMeasured(FlashState next, String message,
            long completedUnits, long totalUnits) {
        transition(next);
        notifyListener(FlashProgress.measured(next, message,
                completedUnits, totalUnits));
    }

    protected final FlashResult result(FlashState terminalState, String message,
            String protocolId, String diagnosticReference, Throwable failure) {
        transition(terminalState);
        notifyListener(FlashProgress.indeterminate(terminalState, message));
        return new FlashResult(id, request.getOperation(), terminalState,
                message, protocolId, request.getDevice().getId(),
                diagnosticReference, failure);
    }

    private void transition(FlashState next) {
        if (next == null) throw new IllegalArgumentException("next state is required");
        while (true) {
            FlashState current = state.get();
            if (current.isTerminal()) {
                throw new IllegalStateException("session is already terminal: " + current);
            }
            if (!next.isTerminal() && next.ordinal() < current.ordinal()) {
                throw new IllegalStateException("state cannot move backward from "
                        + current + " to " + next);
            }
            if (state.compareAndSet(current, next)) return;
        }
    }

    private void notifyListener(FlashProgress progress) {
        if (listener != null) listener.progressChanged(progress);
    }
}
