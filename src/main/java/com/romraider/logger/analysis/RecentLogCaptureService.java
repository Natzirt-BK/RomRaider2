/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.apache.log4j.Logger.getLogger;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.apache.log4j.Logger;

/** Publishes the last completed logger capture for immediate offline replay. */
public final class RecentLogCaptureService {
    private static final Logger LOGGER = getLogger(RecentLogCaptureService.class);
    private static final RecentLogCaptureService INSTANCE =
            new RecentLogCaptureService();
    private final List<Consumer<File>> listeners =
            new CopyOnWriteArrayList<Consumer<File>>();
    private volatile File lastCompleted;

    public static RecentLogCaptureService getInstance() { return INSTANCE; }

    private RecentLogCaptureService() { }

    public void completed(File file) {
        if (file == null || !file.isFile() || file.length() == 0L) return;
        lastCompleted = file.getAbsoluteFile();
        for (Consumer<File> listener : listeners) {
            try {
                listener.accept(lastCompleted);
            } catch (RuntimeException exception) {
                LOGGER.warn("Recent-log listener failed", exception);
            }
        }
    }

    public File getLastCompleted() { return lastCompleted; }

    public void addListener(Consumer<File> listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Consumer<File> listener) {
        listeners.remove(listener);
    }
}
