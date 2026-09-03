/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.desktop;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class DesktopApplicationCommandsTest {
    @Test
    public void routesArgumentsOnlyWhileHandlerIsRegistered() {
        AtomicReference<String[]> received = new AtomicReference<String[]>();
        DesktopApplicationCommands.Handler handler = arguments -> {
            received.set(arguments);
            arguments[0] = "changed by handler";
            return true;
        };
        String[] request = {"example.bin"};

        DesktopApplicationCommands.register(handler);
        try {
            assertTrue(DesktopApplicationCommands.dispatch(request));
            assertArrayEquals(new String[] {"example.bin"}, request);
            assertArrayEquals(new String[] {"changed by handler"},
                    received.get());
        } finally {
            DesktopApplicationCommands.unregister(handler);
        }

        assertFalse(DesktopApplicationCommands.dispatch(request));
    }

    @Test
    public void dispatchesAcrossIndependentActiveHandlers() {
        DesktopApplicationCommands.Handler first = arguments -> false;
        DesktopApplicationCommands.Handler second = arguments -> true;
        DesktopApplicationCommands.register(first);
        DesktopApplicationCommands.register(second);
        try {
            assertTrue(DesktopApplicationCommands.dispatch(null));
        } finally {
            DesktopApplicationCommands.unregister(second);
            DesktopApplicationCommands.unregister(first);
        }
    }
}
