/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import static org.junit.Assert.assertEquals;

import java.nio.file.Paths;
import java.nio.file.Path;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class LoggerProfileStorageTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void managedBackupWinsWithoutOverwritingLegacyCopy() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path legacy = root.resolve(".RomRaider/profile_backup.xml");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "legacy backup");
        Path settings = root.resolve("portable/config/user");
        assertEquals(legacy, LoggerProfileStorage.recoveryPath(settings, root));
        LoggerProfileStorage.saveBackup(emptyProfile(), settings);
        Path managed = LoggerProfileStorage.backupPath(settings);
        assertTrue(Files.size(managed) > 0);
        assertEquals(managed, LoggerProfileStorage.recoveryPath(settings, root));
        assertEquals("legacy backup", Files.readString(legacy));
    }

    @Test public void backupCreatesOnlyTheSettingsOwnedDirectory() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path settings = root.resolve("portable/config/user");
        LoggerProfileStorage.saveBackup(emptyProfile(), settings);
        assertTrue(Files.isRegularFile(LoggerProfileStorage.backupPath(settings)));
        assertFalse(Files.exists(root.resolve(".RomRaider")));
    }

    @Test public void missingBackupsResolveToManagedLocationWithoutCreatingFiles() {
        Path root = temporary.getRoot().toPath();
        Path settings = root.resolve("settings");
        assertEquals(LoggerProfileStorage.backupPath(settings), LoggerProfileStorage.recoveryPath(settings, root));
        assertFalse(Files.exists(settings));
    }

    private static com.romraider.logger.ecu.profile.UserProfile emptyProfile() {
        return new com.romraider.logger.ecu.profile.UserProfileImpl(
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), "SSM");
    }
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
