/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

public enum DisplayMode {
    COMPACT("Compact", 0.85, 0.85),
    NORMAL("Normal", 1.0, 1.0),
    TOUCH("Touch", 1.18, 1.45),
    GARAGE("Garage", 1.12, 1.30),
    DYNO("Dyno", 1.08, 1.20),
    IN_CAR("In Car", 1.22, 1.60);

    private final String displayName;
    private final double fontDensity;
    private final double controlDensity;

    DisplayMode(String displayName, double fontDensity, double controlDensity) {
        this.displayName = displayName;
        this.fontDensity = fontDensity;
        this.controlDensity = controlDensity;
    }

    public double getFontDensity() {
        return fontDensity;
    }

    public double getControlDensity() {
        return controlDensity;
    }

    public boolean isTouchOptimized() {
        return this == TOUCH || this == IN_CAR;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
