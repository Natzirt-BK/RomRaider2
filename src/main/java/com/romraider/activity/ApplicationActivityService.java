/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe, UI-independent source for current and recent application work. */
public final class ApplicationActivityService {
    private static final int MAX_HISTORY = 12;
    private static final ApplicationActivityService INSTANCE =
            new ApplicationActivityService();
    private final CopyOnWriteArrayList<ApplicationActivityListener> listeners =
            new CopyOnWriteArrayList<ApplicationActivityListener>();
    private final LinkedList<ActivitySnapshot> history =
            new LinkedList<ActivitySnapshot>();
    private ActivitySnapshot current = idleSnapshot("Ready");

    private ApplicationActivityService() {
    }

    public static ApplicationActivityService getInstance() { return INSTANCE; }

    public synchronized ActivitySnapshot getCurrent() { return current; }

    public synchronized List<ActivitySnapshot> getHistory() {
        return Collections.unmodifiableList(
                new ArrayList<ActivitySnapshot>(history));
    }

    public void addListener(ApplicationActivityListener listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        listener.activityChanged(getCurrent());
    }

    public void removeListener(ApplicationActivityListener listener) {
        listeners.remove(listener);
    }

    public void update(String message, int measuredPercent) {
        ActivitySnapshot changed;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long started = current.getState() == ActivityState.RUNNING
                    ? current.getStartedAtMillis() : now;
            int percent = Math.max(0, Math.min(100, measuredPercent));
            changed = new ActivitySnapshot(ActivityState.RUNNING, message,
                    percent, started, now);
            current = changed;
        }
        publish(changed);
    }

    public void updateIndeterminate(String message) {
        ActivitySnapshot changed;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long started = current.getState() == ActivityState.RUNNING
                    ? current.getStartedAtMillis() : now;
            changed = new ActivitySnapshot(ActivityState.RUNNING, message, -1,
                    started, now);
            current = changed;
        }
        publish(changed);
    }

    public void ready(String message) {
        ActivitySnapshot changed;
        synchronized (this) {
            if (current.getState() == ActivityState.RUNNING) {
                record(new ActivitySnapshot(ActivityState.SUCCEEDED,
                        current.getMessage(), current.getProgressPercent(),
                        current.getStartedAtMillis(), System.currentTimeMillis()));
            }
            changed = idleSnapshot(message);
            current = changed;
        }
        publish(changed);
    }

    public void complete(String message) {
        ActivitySnapshot changed;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long started = current.getState() == ActivityState.RUNNING
                    ? current.getStartedAtMillis() : now;
            int progress = current.getState() == ActivityState.RUNNING
                    && current.hasMeasuredProgress() ? 100 : -1;
            changed = new ActivitySnapshot(ActivityState.SUCCEEDED, message,
                    progress, started, now);
            current = changed;
            record(changed);
        }
        publish(changed);
    }

    public void fail(String message) {
        ActivitySnapshot changed;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long started = current.getState() == ActivityState.RUNNING
                    ? current.getStartedAtMillis() : now;
            changed = new ActivitySnapshot(ActivityState.FAILED, message,
                    current.getProgressPercent(), started, now);
            current = changed;
            record(changed);
        }
        publish(changed);
    }

    private synchronized void record(ActivitySnapshot activity) {
        history.addFirst(activity);
        while (history.size() > MAX_HISTORY) history.removeLast();
    }

    synchronized void resetForTesting() {
        history.clear();
        current = idleSnapshot("Ready");
    }

    private void publish(ActivitySnapshot activity) {
        for (ApplicationActivityListener listener : listeners) {
            listener.activityChanged(activity);
        }
    }

    private static ActivitySnapshot idleSnapshot(String message) {
        long now = System.currentTimeMillis();
        return new ActivitySnapshot(ActivityState.IDLE, message, -1, now, now);
    }
}
