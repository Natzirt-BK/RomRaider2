/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import java.util.ArrayList;
import java.util.List;

public final class EditorNavigationHistory {
    private final List<TableLocation> entries = new ArrayList<TableLocation>();
    private int cursor = -1;

    public synchronized void visit(TableLocation location) {
        if (location == null) throw new IllegalArgumentException("Table location is required");
        if (cursor >= 0 && entries.get(cursor).equals(location)) return;
        while (entries.size() > cursor + 1) {
            entries.remove(entries.size() - 1);
        }
        entries.add(location);
        cursor = entries.size() - 1;
    }

    public synchronized boolean canGoBack() {
        return cursor > 0;
    }

    public synchronized boolean canGoForward() {
        return cursor >= 0 && cursor < entries.size() - 1;
    }

    public synchronized TableLocation back() {
        if (!canGoBack()) return null;
        return entries.get(--cursor);
    }

    public synchronized TableLocation forward() {
        if (!canGoForward()) return null;
        return entries.get(++cursor);
    }

    public synchronized TableLocation current() {
        return cursor < 0 ? null : entries.get(cursor);
    }

    public synchronized void clear() {
        entries.clear();
        cursor = -1;
    }
}
