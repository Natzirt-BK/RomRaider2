/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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

    @Test
    public void packagedDirectoryWinsOverWorkingDirectorySettings() {
        assertEquals("/opt/romraider2/config/user",
                SettingsManager.resolveLoadSettingsDirectory(
                        " /opt/romraider2/config/user ", "/home/tester",
                        "/worktree", true));
    }

    @Test
    public void standaloneLaunchStillUsesWorkingDirectorySettings() {
        assertEquals("/worktree",
                SettingsManager.resolveLoadSettingsDirectory(
                        null, "/home/tester", "/worktree", true));
        assertEquals("/home/tester/.RomRaider",
                SettingsManager.resolveLoadSettingsDirectory(
                        null, "/home/tester", "/worktree", false));
    }

    @Test
    public void macPackagesUseApplicationSupportByDefault() {
        assertEquals("/Users/tester/Library/Application Support/RomRaider2",
                SettingsManager.resolveSettingsDirectory(null,
                        "/Users/tester", "Mac OS X"));
        assertEquals("/portable/config",
                SettingsManager.resolveSettingsDirectory(
                        "/portable/config", "/Users/tester", "Mac OS X"));
        assertEquals("/Users/tester/Library/Application Support/RomRaider2",
                SettingsManager.resolveLoadSettingsDirectory(null,
                        "/Users/tester", "/Applications", false,
                        "Mac OS X"));
    }

    @Test
    public void packagedDefaultsCanBootstrapAUserSettingsFile()
            throws Exception {
        java.nio.file.Path folder = Files.createTempDirectory(
                "romraider2-settings-test");
        File packaged = folder.resolve("default.xml").toFile();
        File settings = folder.resolve("user/settings.xml").toFile();
        Files.write(packaged.toPath(), "<settings/>".getBytes(
                StandardCharsets.UTF_8));

        SettingsManager.installPackagedDefaults(settings,
                packaged.getAbsolutePath());

        assertEquals("<settings/>", new String(Files.readAllBytes(
                settings.toPath()), StandardCharsets.UTF_8));
    }
}
