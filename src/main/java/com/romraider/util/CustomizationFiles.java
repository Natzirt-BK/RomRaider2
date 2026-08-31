/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/** Resolves editable runtime assets independently of the launch directory. */
public final class CustomizationFiles {
    public static final String DIRECTORY_PROPERTY =
            "romraider2.customize.dir";

    private CustomizationFiles() { }

    public static File file(String name) {
        String configuredDirectory = System.getProperty(DIRECTORY_PROPERTY);
        if (configuredDirectory != null
                && !configuredDirectory.trim().isEmpty()) {
            return new File(configuredDirectory.trim(), name);
        }
        return new File(new File(System.getProperty("user.dir", "."),
                "customize"), name);
    }

    public static FileInputStream open(String name)
            throws FileNotFoundException {
        return new FileInputStream(file(name));
    }
}
