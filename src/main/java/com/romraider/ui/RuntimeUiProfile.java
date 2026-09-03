/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

import java.util.Locale;

/** Runtime-only presentation target selected by a platform bundle. */
public final class RuntimeUiProfile {
    public static final String PROPERTY = "romraider2.ui.profile";
    private static final String STEAMOS = "steamos";

    private RuntimeUiProfile() {
    }

    public static boolean isSteamOs() {
        String profile = System.getProperty(PROPERTY, "");
        return STEAMOS.equals(profile.trim().toLowerCase(Locale.ROOT));
    }

    public static ThemeMode theme(ThemeMode configured) {
        return isSteamOs() ? ThemeMode.DARK : configured;
    }

    public static DisplayMode displayMode(DisplayMode configured) {
        return isSteamOs() ? DisplayMode.TOUCH : configured;
    }
}
