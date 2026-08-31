/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

public enum UiScale {
    AUTOMATIC("Automatic", 0.0),
    PERCENT_75("75%", 0.75),
    PERCENT_85("85%", 0.85),
    PERCENT_100("100%", 1.0),
    PERCENT_110("110%", 1.10),
    PERCENT_125("125%", 1.25),
    PERCENT_150("150%", 1.50),
    PERCENT_175("175%", 1.75),
    PERCENT_200("200%", 2.0),
    PERCENT_250("250%", 2.5),
    PERCENT_300("300%", 3.0);

    private final String displayName;
    private final double factor;

    UiScale(String displayName, double factor) {
        this.displayName = displayName;
        this.factor = factor;
    }

    public double getConfiguredFactor() {
        return factor;
    }

    public boolean isAutomatic() {
        return this == AUTOMATIC;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
