/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.romraider.maps.Table;
import com.romraider.maps.TableView;

/** Swing-only lookup for compatibility calibration views. */
public final class SwingTableViewRegistry {
    private static final List<Entry> VIEWS = new ArrayList<Entry>();

    private SwingTableViewRegistry() { }

    public static synchronized void register(Table table, TableView view) {
        if (table == null || view == null) {
            throw new IllegalArgumentException("Table and view are required");
        }
        unregister(table, null);
        VIEWS.add(new Entry(table, view));
    }

    public static synchronized TableView find(Table table) {
        if (table == null) return null;
        Iterator<Entry> entries = VIEWS.iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            Table candidate = entry.table.get();
            TableView view = entry.view.get();
            if (candidate == null || view == null) {
                entries.remove();
            } else if (candidate == table) {
                return view;
            }
        }
        return null;
    }

    public static synchronized void unregister(Table table, TableView view) {
        if (table == null) return;
        Iterator<Entry> entries = VIEWS.iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            Table candidate = entry.table.get();
            TableView registered = entry.view.get();
            if (candidate == null || registered == null
                    || (candidate == table
                            && (view == null || registered == view))) {
                entries.remove();
            }
        }
    }

    private static final class Entry {
        private final WeakReference<Table> table;
        private final WeakReference<TableView> view;

        private Entry(Table table, TableView view) {
            this.table = new WeakReference<Table>(table);
            this.view = new WeakReference<TableView>(view);
        }
    }
}
