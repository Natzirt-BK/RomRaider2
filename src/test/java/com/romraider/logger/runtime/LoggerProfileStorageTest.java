/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import static org.junit.Assert.assertEquals;

import java.nio.file.Paths;
import java.nio.file.Path;

import org.junit.Test;

public class LoggerProfileStorageTest {
    @Test
    public void backupProfileBelongsToPackagedSettingsDirectory() {
        Path settingsDirectory = Paths.get("romraider2", "config", "user")
                .toAbsolutePath();
        assertEquals(settingsDirectory.resolve("profiles")
                        .resolve("profile_backup.xml"),
                LoggerProfileStorage.backupPath(settingsDirectory));
    }

    @Test(expected = IllegalArgumentException.class)
    public void backupProfileRequiresSettingsDirectory() {
        LoggerProfileStorage.backupPath(null);
    }
}
