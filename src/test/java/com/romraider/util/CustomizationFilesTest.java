/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class CustomizationFilesTest {
    @Test
    public void packagedDirectoryDoesNotDependOnLaunchDirectory()
            throws Exception {
        Path directory = Files.createTempDirectory("rr2-customize-");
        String previous = System.getProperty(
                CustomizationFiles.DIRECTORY_PROPERTY);
        try {
            System.setProperty(CustomizationFiles.DIRECTORY_PROPERTY,
                    directory.toString());
            assertEquals(new File(directory.toFile(),
                    "nameSequences.properties").getAbsolutePath(),
                    CustomizationFiles.file(
                            "nameSequences.properties").getAbsolutePath());
        } finally {
            if (previous == null) {
                System.clearProperty(CustomizationFiles.DIRECTORY_PROPERTY);
            } else {
                System.setProperty(CustomizationFiles.DIRECTORY_PROPERTY,
                        previous);
            }
            Files.deleteIfExists(directory);
        }
    }
}
