/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import java.nio.file.Path;

/** Package-owned persistence paths for the replacement desktop Logger. */
final class LoggerProfileStorage {
    private static final String PROFILE_DIRECTORY = "profiles";
    private static final String BACKUP_PROFILE = "profile_backup.xml";

    private LoggerProfileStorage() {
    }

    static Path backupPath(Path settingsDirectory) {
        if (settingsDirectory == null) {
            throw new IllegalArgumentException("settingsDirectory is required");
        }
        return settingsDirectory.resolve(PROFILE_DIRECTORY)
                .resolve(BACKUP_PROFILE).toAbsolutePath().normalize();
    }
}
