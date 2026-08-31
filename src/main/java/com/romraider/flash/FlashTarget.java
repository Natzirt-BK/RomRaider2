/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import com.romraider.platform.VehicleModule;
import com.romraider.platform.VehiclePlatform;

/** ECU identity known before a session; unknown fields remain explicit. */
public final class FlashTarget {
    private final VehiclePlatform platform;
    private final VehicleModule module;
    private final String ecuIdentifier;
    private final String romIdentifier;
    private final long expectedRomSize;

    public FlashTarget(VehiclePlatform platform, VehicleModule module,
            String ecuIdentifier, String romIdentifier, long expectedRomSize) {
        if (platform == null) throw new IllegalArgumentException("platform is required");
        if (module == null) throw new IllegalArgumentException("module is required");
        if (expectedRomSize < -1L || expectedRomSize == 0L) {
            throw new IllegalArgumentException("expected ROM size must be unknown or positive");
        }
        this.platform = platform;
        this.module = module;
        this.ecuIdentifier = normalize(ecuIdentifier);
        this.romIdentifier = normalize(romIdentifier);
        this.expectedRomSize = expectedRomSize;
    }

    public VehiclePlatform getPlatform() { return platform; }
    public VehicleModule getModule() { return module; }
    public String getEcuIdentifier() { return ecuIdentifier; }
    public String getRomIdentifier() { return romIdentifier; }
    public long getExpectedRomSize() { return expectedRomSize; }
    public boolean hasEcuIdentifier() { return !ecuIdentifier.isEmpty(); }
    public boolean hasRomIdentifier() { return !romIdentifier.isEmpty(); }
    public boolean hasExpectedRomSize() { return expectedRomSize >= 0L; }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
