/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.desktop;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Routes requests from the single-instance socket to the active desktop shell.
 */
public final class DesktopApplicationCommands {
    public interface Handler {
        boolean handle(String[] arguments);
    }

    private static final CopyOnWriteArrayList<Handler> ACTIVE =
            new CopyOnWriteArrayList<Handler>();

    private DesktopApplicationCommands() { }

    public static void register(Handler handler) {
        if (handler == null) throw new IllegalArgumentException(
                "Handler is required");
        ACTIVE.addIfAbsent(handler);
    }

    public static void unregister(Handler handler) {
        ACTIVE.remove(handler);
    }

    public static boolean dispatch(String[] arguments) {
        String[] safe = arguments == null ? new String[0] : arguments.clone();
        for (int index = ACTIVE.size() - 1; index >= 0; index--) {
            if (ACTIVE.get(index).handle(safe.clone())) return true;
        }
        return false;
    }
}
