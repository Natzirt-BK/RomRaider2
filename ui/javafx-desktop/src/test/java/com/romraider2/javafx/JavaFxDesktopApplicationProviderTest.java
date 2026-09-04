/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JavaFxDesktopApplicationProviderTest {
    @AfterEach
    void clearShellSelection() {
        System.clearProperty("romraider2.desktop.shell");
    }

    @Test
    void javafxIsTheDefaultDesktopProvider() {
        JavaFxDesktopApplicationProvider provider =
                new JavaFxDesktopApplicationProvider();

        assertTrue(provider.supports(new String[0]));
        assertEquals("JavaFX Desktop ECU Studio", provider.getName());
    }

    @Test
    void compatibilityShellSelectionsDoNotMatchJavafx() {
        JavaFxDesktopApplicationProvider provider =
                new JavaFxDesktopApplicationProvider();

        System.setProperty("romraider2.desktop.shell", "compose");
        assertFalse(provider.supports(new String[0]));
        System.setProperty("romraider2.desktop.shell", "swing");
        assertFalse(provider.supports(new String[0]));
    }
}
