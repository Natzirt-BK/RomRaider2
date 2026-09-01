/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static com.romraider.util.ParamChecker.checkNotNull;

import java.util.function.BiConsumer;

/** Toolkit-neutral state for replacement Logger presentation preferences. */
public final class LoggerWorkspacePreferences {
    private final BiConsumer<LoggerWorkspaceView, Boolean> persistence;
    private volatile LoggerWorkspaceView view;
    private volatile boolean darkTheme;

    public LoggerWorkspacePreferences(LoggerWorkspaceView view,
            boolean darkTheme,
            BiConsumer<LoggerWorkspaceView, Boolean> persistence) {
        checkNotNull(view, persistence);
        this.view = view;
        this.darkTheme = darkTheme;
        this.persistence = persistence;
    }

    public LoggerWorkspaceView getView() {
        return view;
    }

    public boolean isDarkTheme() {
        return darkTheme;
    }

    public void setView(LoggerWorkspaceView next) {
        if (next == null || next == view) return;
        view = next;
        persist();
    }

    public void setDarkTheme(boolean dark) {
        if (dark == darkTheme) return;
        darkTheme = dark;
        persist();
    }

    private void persist() {
        persistence.accept(view, Boolean.valueOf(darkTheme));
    }
}
