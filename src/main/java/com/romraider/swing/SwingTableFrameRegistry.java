/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.romraider.maps.Table;

/**
 * Swing-only ownership of open calibration frames.
 *
 * <p>The calibration model must not retain a Swing frame. Compatibility views
 * register here while they are open, allowing replacement workspaces to use
 * the same {@link Table} without joining the Swing object graph.</p>
 */
public final class SwingTableFrameRegistry {
    private static final List<Entry> FRAMES = new ArrayList<Entry>();

    private SwingTableFrameRegistry() { }

    public static synchronized void register(Table table, TableFrame frame) {
        if (table == null || frame == null) {
            throw new IllegalArgumentException("Table and frame are required");
        }
        unregister(table, null);
        FRAMES.add(new Entry(table, frame));
    }

    public static synchronized TableFrame find(Table table) {
        if (table == null) return null;
        Iterator<Entry> entries = FRAMES.iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            Table candidate = entry.table.get();
            TableFrame frame = entry.frame.get();
            if (candidate == null || frame == null) {
                entries.remove();
            } else if (candidate == table) {
                return frame;
            }
        }
        return null;
    }

    public static synchronized void unregister(Table table, TableFrame frame) {
        if (table == null) return;
        Iterator<Entry> entries = FRAMES.iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            Table candidate = entry.table.get();
            TableFrame registered = entry.frame.get();
            if (candidate == null || registered == null
                    || (candidate == table
                            && (frame == null || registered == frame))) {
                entries.remove();
            }
        }
    }

    private static final class Entry {
        private final WeakReference<Table> table;
        private final WeakReference<TableFrame> frame;

        private Entry(Table table, TableFrame frame) {
            this.table = new WeakReference<Table>(table);
            this.frame = new WeakReference<TableFrame>(frame);
        }
    }
}
