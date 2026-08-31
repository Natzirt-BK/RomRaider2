/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JDialog;
import javax.swing.JRootPane;

import org.junit.Test;

public class IntegratedOptionDialogTest {
    @Test
    public void definitionPromptUsesIntegratedApplicationChrome() {
        IntegratedOptionDialog.DialogParts parts = IntegratedOptionDialog.create(
                null, "Definitions are not configured.",
                "Editor Configuration", javax.swing.JOptionPane.WARNING_MESSAGE,
                new Object[] {"Open SubaruDefs", "Not now"},
                "Open SubaruDefs");
        JDialog dialog = parts.dialog;
        try {
            assertTrue(dialog.isUndecorated());
            assertTrue(dialog.getRootPane().getWindowDecorationStyle()
                    == JRootPane.NONE);
            assertNotNull(findNamed(dialog, "INTEGRATED DIALOG TITLE BAR"));
            assertNotNull(findNamed(dialog, "INTEGRATED DIALOG TITLE"));
            assertNotNull(findNamed(dialog, "DIALOG CLOSE"));
            assertNotNull(findNamed(dialog, "INTEGRATED OPTION CONTENT"));
        } finally {
            dialog.dispose();
        }
    }

    private static Component findNamed(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container) {
                Component match = findNamed((Container) component, name);
                if (match != null) return match;
            }
        }
        return null;
    }
}
