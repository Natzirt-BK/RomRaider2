/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.swing.menubar.action;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AboutActionTest {
    @Test
    public void missingDefinitionVersionHasAReadableState() {
        assertEquals("Not installed", AboutAction.displayDefinitionVersion(null));
        assertEquals("Not installed", AboutAction.displayDefinitionVersion("  "));
    }

    @Test
    public void installedDefinitionVersionIsPreserved() {
        assertEquals("370", AboutAction.displayDefinitionVersion("370"));
    }
}
