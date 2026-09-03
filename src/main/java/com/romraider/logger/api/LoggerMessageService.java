/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Thread-safe, toolkit-neutral Logger message stream. */
public final class LoggerMessageService {
    private final CopyOnWriteArrayList<Consumer<LoggerMessageSnapshot>> listeners =
            new CopyOnWriteArrayList<Consumer<LoggerMessageSnapshot>>();
    private volatile LoggerMessageSnapshot snapshot =
            new LoggerMessageSnapshot("Ready", "", false);

    public LoggerMessageSnapshot getSnapshot() { return snapshot; }

    public void message(String value) {
        publish(new LoggerMessageSnapshot(value,
                snapshot.getStatistics(), false));
    }

    public void statistics(String value) {
        publish(new LoggerMessageSnapshot(snapshot.getMessage(), value,
                snapshot.isError()));
    }

    public void error(String value) {
        publish(new LoggerMessageSnapshot(value,
                snapshot.getStatistics(), true));
    }

    public void addListener(Consumer<LoggerMessageSnapshot> listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        listener.accept(snapshot);
    }

    public void removeListener(Consumer<LoggerMessageSnapshot> listener) {
        listeners.remove(listener);
    }

    private void publish(LoggerMessageSnapshot next) {
        snapshot = next;
        for (Consumer<LoggerMessageSnapshot> listener : listeners) {
            listener.accept(next);
        }
    }
}
