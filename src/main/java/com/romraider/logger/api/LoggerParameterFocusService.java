/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UI-independent handoff for navigating from integrated workspaces to a
 * logger data item. Requests remain pending while the Logger is starting or
 * until its active definition contains the requested item.
 */
public final class LoggerParameterFocusService {
    public interface Listener {
        /** @return true when the request was accepted by the listener. */
        boolean focusRequested(String parameterId);
    }

    private static final LoggerParameterFocusService INSTANCE =
            new LoggerParameterFocusService();
    private final CopyOnWriteArrayList<Listener> listeners =
            new CopyOnWriteArrayList<Listener>();
    private String pendingParameterId;

    private LoggerParameterFocusService() {
    }

    public static LoggerParameterFocusService getInstance() { return INSTANCE; }

    public void requestFocus(String parameterId) {
        String normalized = normalize(parameterId);
        if (normalized == null) return;
        synchronized (this) {
            pendingParameterId = normalized;
        }
        dispatchPending();
    }

    public void addListener(Listener listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        dispatchPending();
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Retries a request after logger definitions or parameter lists change. */
    public void retryPending() {
        dispatchPending();
    }

    synchronized String getPendingParameterId() {
        return pendingParameterId;
    }

    synchronized void clearForTesting() {
        pendingParameterId = null;
        listeners.clear();
    }

    private void dispatchPending() {
        String requested;
        synchronized (this) {
            requested = pendingParameterId;
        }
        if (requested == null) return;
        for (Listener listener : listeners) {
            boolean accepted = false;
            try {
                accepted = listener.focusRequested(requested);
            } catch (RuntimeException ignored) {
                // A stale UI listener must not prevent a later listener retry.
            }
            if (accepted) {
                synchronized (this) {
                    if (requested.equals(pendingParameterId)) {
                        pendingParameterId = null;
                    }
                }
                return;
            }
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
