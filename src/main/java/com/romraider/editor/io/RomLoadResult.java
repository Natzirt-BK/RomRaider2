/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.io;

import com.romraider.maps.Rom;

/** Immutable result of a definition-backed ROM load. */
public final class RomLoadResult {
    public enum Outcome {
        LOADED,
        NO_MATCH,
        CANCELLED,
        FAILED
    }

    private final Outcome outcome;
    private final Rom rom;
    private final int validChecksums;
    private final int totalChecksums;

    private RomLoadResult(Outcome outcome, Rom rom, int validChecksums,
            int totalChecksums) {
        this.outcome = outcome;
        this.rom = rom;
        this.validChecksums = validChecksums;
        this.totalChecksums = totalChecksums;
    }

    public static RomLoadResult loaded(Rom rom, int validChecksums,
            int totalChecksums) {
        if (rom == null) throw new IllegalArgumentException("ROM is required");
        return new RomLoadResult(Outcome.LOADED, rom, validChecksums,
                totalChecksums);
    }

    public static RomLoadResult empty(Outcome outcome) {
        if (outcome == null || outcome == Outcome.LOADED) {
            throw new IllegalArgumentException("A non-loaded outcome is required");
        }
        return new RomLoadResult(outcome, null, 0, 0);
    }

    public Outcome getOutcome() { return outcome; }
    public Rom getRom() { return rom; }
    public int getValidChecksums() { return validChecksums; }
    public int getTotalChecksums() { return totalChecksums; }
    public boolean isLoaded() { return outcome == Outcome.LOADED; }
}
