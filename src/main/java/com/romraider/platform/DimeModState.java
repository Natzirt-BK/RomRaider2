/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.romraider.platform;

public enum DimeModState {
    ACTIVE("Active"),
    PRESENT("Present"),
    NOT_PRESENT("Not Present"),
    UNKNOWN("Unknown");

    private final String displayName;

    DimeModState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
