/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

import java.util.concurrent.CopyOnWriteArrayList;

/** Toolkit-neutral application theme state observed by Compose surfaces. */
public final class ApplicationThemeService {
    public interface Listener {
        void themeChanged(ThemeMode mode);
    }

    private static final ApplicationThemeService INSTANCE =
            new ApplicationThemeService();
    private final CopyOnWriteArrayList<Listener> listeners =
            new CopyOnWriteArrayList<Listener>();
    private volatile ThemeMode mode = ThemeMode.LIGHT;

    private ApplicationThemeService() { }

    public static ApplicationThemeService getInstance() {
        return INSTANCE;
    }

    public ThemeMode getCurrentMode() {
        return mode;
    }

    public void apply(ThemeMode requested) {
        if (requested == null) {
            throw new IllegalArgumentException("Theme mode is required");
        }
        ThemeMode next = RuntimeUiProfile.theme(requested);
        if (next == mode) return;
        mode = next;
        for (Listener listener : listeners) listener.themeChanged(next);
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
