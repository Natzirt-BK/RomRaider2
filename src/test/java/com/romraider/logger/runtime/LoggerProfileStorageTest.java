/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import static org.junit.Assert.assertEquals;

import java.nio.file.Paths;

import org.junit.Test;

public class LoggerProfileStorageTest {
    @Test
    public void backupProfileBelongsToPackagedSettingsDirectory() {
        assertEquals(Paths.get("/opt/romraider2/config/user/profiles/"
                        + "profile_backup.xml"),
                LoggerProfileStorage.backupPath(
                        Paths.get("/opt/romraider2/config/user")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void backupProfileRequiresSettingsDirectory() {
        LoggerProfileStorage.backupPath(null);
    }
}
