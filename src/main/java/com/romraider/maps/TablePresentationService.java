/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/** Weak, toolkit-neutral presentation event boundary for calibration tables. */
public final class TablePresentationService {
    private static final CopyOnWriteArrayList<Registration> LISTENERS =
            new CopyOnWriteArrayList<Registration>();

    private TablePresentationService() { }

    public static void addListener(Table table,
            TablePresentationListener listener) {
        if (table == null || listener == null) return;
        removeListener(table, listener);
        purge();
        LISTENERS.add(new Registration(table, listener));
    }

    /** Receives events only when no table-specific presentation is attached. */
    public static void addFallbackListener(
            TablePresentationListener listener) {
        if (listener == null) return;
        removeListener(null, listener);
        purge();
        LISTENERS.add(new Registration(null, listener));
    }

    public static void removeListener(Table table,
            TablePresentationListener listener) {
        for (Registration registration : LISTENERS) {
            Table registeredTable = registration.table.get();
            TablePresentationListener registeredListener =
                    registration.listener.get();
            boolean expiredTable = !registration.fallback
                    && registeredTable == null;
            boolean matching = registeredListener == listener
                    && ((registration.fallback && table == null)
                            || (!registration.fallback
                                    && registeredTable == table));
            if (expiredTable || registeredListener == null || matching) {
                LISTENERS.remove(registration);
            }
        }
    }

    public static void changed(Table table) {
        dispatch(table, (listener, target) -> listener.tableChanged(target));
    }

    public static void cellChanged(Table table, DataCell cell) {
        dispatch(table, (listener, target) ->
                listener.cellChanged(target, cell));
    }

    public static void selectionChanged(Table table, int x, int y) {
        dispatch(table, (listener, target) ->
                listener.selectionAnchorChanged(target, x, y));
    }

    public static void invalidScale(Table table, Scale scale) {
        dispatch(table, (listener, target) ->
                listener.invalidScale(target, scale));
    }

    private static void dispatch(Table table, Notification notification) {
        if (table == null) return;
        boolean delivered = false;
        for (Registration registration : LISTENERS) {
            Table registeredTable = registration.table.get();
            TablePresentationListener listener = registration.listener.get();
            if (listener == null || (registeredTable == null
                    && !registration.fallback)) {
                LISTENERS.remove(registration);
            } else if (registeredTable == table) {
                notification.send(listener, table);
                delivered = true;
            }
        }
        if (!delivered) {
            for (Registration registration : LISTENERS) {
                TablePresentationListener listener =
                        registration.listener.get();
                if (registration.fallback && listener != null) {
                    notification.send(listener, table);
                }
            }
        }
    }

    private static void purge() {
        for (Registration registration : LISTENERS) {
            if ((!registration.fallback && registration.table.get() == null)
                    || registration.listener.get() == null) {
                LISTENERS.remove(registration);
            }
        }
    }

    private interface Notification {
        void send(TablePresentationListener listener, Table table);
    }

    private static final class Registration {
        private final WeakReference<Table> table;
        private final WeakReference<TablePresentationListener> listener;
        private final boolean fallback;

        private Registration(Table table,
                TablePresentationListener listener) {
            this.table = new WeakReference<Table>(table);
            this.listener = new WeakReference<TablePresentationListener>(
                    listener);
            this.fallback = table == null;
        }
    }
}
