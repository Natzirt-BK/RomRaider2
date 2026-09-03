/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/** Weak application-shell boundary for ROM messages and confirmations. */
public final class RomUserInteractionService {
    private static final CopyOnWriteArrayList<WeakReference<RomUserInteraction>>
            HANDLERS = new CopyOnWriteArrayList<WeakReference<RomUserInteraction>>();

    private RomUserInteractionService() { }

    public static void addHandler(RomUserInteraction handler) {
        if (handler == null) return;
        removeHandler(handler);
        purge();
        HANDLERS.add(new WeakReference<RomUserInteraction>(handler));
    }

    public static void removeHandler(RomUserInteraction handler) {
        for (WeakReference<RomUserInteraction> reference : HANDLERS) {
            RomUserInteraction candidate = reference.get();
            if (candidate == null || candidate == handler) {
                HANDLERS.remove(reference);
            }
        }
    }

    public static void definitionError(Rom rom, Table table, String title,
            String message, Throwable failure) {
        for (RomUserInteraction handler : handlers()) {
            handler.definitionError(rom, table, title, message, failure);
        }
    }

    public static boolean confirmChecksumFix(Rom rom, Table table,
            String title, String message) {
        for (RomUserInteraction handler : handlers()) {
            if (handler.confirmChecksumFix(rom, table, title, message)) {
                return true;
            }
        }
        return false;
    }

    public static void checksumValidationFailed(Rom rom, String title,
            String message) {
        for (RomUserInteraction handler : handlers()) {
            handler.checksumValidationFailed(rom, title, message);
        }
    }

    public static void checksumUpdated(Rom rom, String message) {
        for (RomUserInteraction handler : handlers()) {
            handler.checksumUpdated(rom, message);
        }
    }

    private static java.util.List<RomUserInteraction> handlers() {
        java.util.ArrayList<RomUserInteraction> result =
                new java.util.ArrayList<RomUserInteraction>();
        for (WeakReference<RomUserInteraction> reference : HANDLERS) {
            RomUserInteraction handler = reference.get();
            if (handler == null) HANDLERS.remove(reference);
            else result.add(handler);
        }
        return result;
    }

    private static void purge() {
        handlers();
    }
}
