/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.runtime;

import java.util.Locale;

/** Single tested source of JVM/native process architecture information. */
public final class RuntimeArchitecture {
    private RuntimeArchitecture() {
    }

    public static int currentBitness() {
        return bitness(System.getProperty("sun.arch.data.model"),
                System.getProperty("os.arch"));
    }

    public static boolean isCompatible(String requiredArchitecture) {
        int required = bitness(requiredArchitecture, requiredArchitecture);
        return required > 0 && required == currentBitness();
    }

    static int bitness(String dataModel, String architecture) {
        int explicit = parseBitness(dataModel);
        if (explicit > 0) return explicit;
        String normalized = architecture == null ? ""
                : architecture.trim().toLowerCase(Locale.ENGLISH);
        if (normalized.contains("64") || normalized.equals("s390x")
                || normalized.equals("ppc64le") || normalized.equals("aarch64")) {
            return 64;
        }
        if (normalized.contains("86") || normalized.contains("32")
                || normalized.startsWith("arm") || normalized.equals("i386")) {
            return 32;
        }
        return -1;
    }

    private static int parseBitness(String value) {
        if (value == null) return -1;
        String normalized = value.trim().toLowerCase(Locale.ENGLISH);
        if (normalized.equals("64") || normalized.equals("64-bit")) return 64;
        if (normalized.equals("32") || normalized.equals("32-bit")) return 32;
        return -1;
    }
}
