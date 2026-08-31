/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SettingsManagerTest {
    @Test
    public void explicitRomRaider2DirectoryIsIndependentFromRomRaider() {
        assertEquals("/tmp/romraider2-config",
                SettingsManager.resolveSettingsDirectory(
                        " /tmp/romraider2-config ", "/home/tester"));
    }

    @Test
    public void legacyDirectoryRemainsTheStandaloneFallback() {
        assertEquals("/home/tester/.RomRaider",
                SettingsManager.resolveSettingsDirectory(null, "/home/tester"));
        assertEquals("/home/tester/.RomRaider",
                SettingsManager.resolveSettingsDirectory("  ", "/home/tester"));
    }
}
