/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import com.romraider.logger.ecu.profile.UserProfile;
import com.romraider.logger.ecu.profile.UserProfileWriter;

/** Settings-owned profile recovery shared by the desktop Logger workspaces. */
public final class LoggerProfileStorage {
    private static final String PROFILE_DIRECTORY = "profiles";
    private static final String BACKUP_PROFILE = "profile_backup.xml";

    private LoggerProfileStorage() {
    }

    public static Path backupPath(Path settingsDirectory) {
        if (settingsDirectory == null) {
            throw new IllegalArgumentException("settingsDirectory is required");
        }
        return settingsDirectory.resolve(PROFILE_DIRECTORY)
                .resolve(BACKUP_PROFILE).toAbsolutePath().normalize();
    }

    /** Retain read access to the old Swing backup without continuing to write there. */
    public static Path recoveryPath(Path settingsDirectory, Path userHome) {
        Path managed = backupPath(settingsDirectory);
        if (Files.exists(managed)) return managed;
        Path legacy = userHome.resolve(".RomRaider").resolve(BACKUP_PROFILE);
        return Files.isRegularFile(legacy) ? legacy : managed;
    }

    public static void saveBackup(UserProfile profile, Path settingsDirectory) throws IOException {
        Path target = backupPath(settingsDirectory);
        Files.createDirectories(target.getParent());
        UserProfileWriter.save(profile, target);
    }
}
