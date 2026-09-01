/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.junit.Test;

public class IntegratedFileChooserTest {
    @Test
    public void fileChooserUsesIntegratedApplicationChrome() {
        IntegratedFileChooser chooser = new IntegratedFileChooser();
        chooser.setDialogTitle("Open ROM");
        JDialog dialog = chooser.createDialog(null);
        try {
            assertTrue(dialog.isUndecorated());
            assertTrue(dialog.getRootPane().getWindowDecorationStyle()
                    == JRootPane.NONE);
            assertNotNull(findNamed(dialog,
                    "INTEGRATED FILE CHOOSER DIALOG"));
            assertNotNull(findNamed(dialog,
                    "INTEGRATED DIALOG TITLE BAR"));
            assertNotNull(findNamed(dialog, "INTEGRATED DIALOG TITLE"));
            assertNotNull(findNamed(dialog, "DIALOG CLOSE"));
        } finally {
            dialog.dispose();
        }
    }

    @Test
    public void kdeDialogCommandPreservesPlacesStartFilterAndMultipleMode() {
        File temporaryDirectory = new File(System.getProperty(
                "java.io.tmpdir"));
        IntegratedFileChooser chooser = new IntegratedFileChooser(
                temporaryDirectory);
        chooser.setDialogTitle("Open ROM");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "ROM images", "bin", "hex"));

        List<String> command = chooser.buildKDialogCommand(
                "/usr/bin/kdialog", false);

        assertEquals(Arrays.asList(
                "/usr/bin/kdialog",
                "--getopenfilename",
                chooser.getCurrentDirectory().getAbsolutePath(),
                "*.bin *.hex|ROM images",
                "--multiple",
                "--separate-output",
                "--title",
                "Open ROM"), command);
    }

    @Test
    public void nativeDialogOverrideAppliesOnEveryOperatingSystem() {
        String previous = System.getProperty("romraider2.nativeFileDialogs");
        try {
            System.setProperty("romraider2.nativeFileDialogs", "false");
            assertTrue(!IntegratedFileChooser.nativeDialogsEnabled());
            System.setProperty("romraider2.nativeFileDialogs", "true");
            assertTrue(IntegratedFileChooser.nativeDialogsEnabled());
        } finally {
            if (previous == null) {
                System.clearProperty("romraider2.nativeFileDialogs");
            } else {
                System.setProperty("romraider2.nativeFileDialogs", previous);
            }
        }
    }

    private static Component findNamed(Container root, String name) {
        if (name.equals(root.getName())) return root;
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
