/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.io;

import java.io.File;
import java.util.Locale;

/** Supported ECU definition formats shared by desktop user interfaces. */
public final class DefinitionFileSupport {
    private DefinitionFileSupport() { }

    public static boolean isSupported(File file) {
        if (file == null) return false;
        String name = file.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xml") || lower.endsWith(".xdf")
                || lower.endsWith(".vdf") || lower.endsWith(".jdf")) {
            return true;
        }
        return name.matches("^.*\\.C\\d\\d$");
    }

    public static String supportedTypes() {
        return "XML, XDF, VDF, JDF, or BMW Cxx";
    }
}
