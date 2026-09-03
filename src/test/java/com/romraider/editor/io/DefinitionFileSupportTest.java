/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.io;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public final class DefinitionFileSupportTest {
    @Test
    public void acceptsEverySupportedDefinitionExtension() {
        assertTrue(DefinitionFileSupport.isSupported(new File("ecu.xml")));
        assertTrue(DefinitionFileSupport.isSupported(new File("ECU.XML")));
        assertTrue(DefinitionFileSupport.isSupported(new File("ecu.xdf")));
        assertTrue(DefinitionFileSupport.isSupported(new File("ecu.vdf")));
        assertTrue(DefinitionFileSupport.isSupported(new File("ecu.jdf")));
        assertTrue(DefinitionFileSupport.isSupported(new File("E46.C12")));
    }

    @Test
    public void rejectsImagesArchivesAndNearMatches() {
        assertFalse(DefinitionFileSupport.isSupported(new File("windows.iso")));
        assertFalse(DefinitionFileSupport.isSupported(new File("defs.zip")));
        assertFalse(DefinitionFileSupport.isSupported(new File("ecu.xml.old")));
        assertFalse(DefinitionFileSupport.isSupported(new File("E46.c12")));
        assertFalse(DefinitionFileSupport.isSupported(null));
    }
}
