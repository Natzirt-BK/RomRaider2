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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class PlatformRegistry {
    private static final Map<VehiclePlatform, PlatformCapabilities> PLATFORMS =
            new EnumMap<VehiclePlatform, PlatformCapabilities>(VehiclePlatform.class);

    static {
        PLATFORMS.put(VehiclePlatform.SUBARU, new PlatformCapabilities(
                VehiclePlatform.SUBARU,
                Arrays.asList(VehicleModule.ENGINE_ECU, VehicleModule.TCU),
                true, false, true));
        PLATFORMS.put(VehiclePlatform.EVO_8_9, new PlatformCapabilities(
                VehiclePlatform.EVO_8_9,
                Arrays.asList(VehicleModule.ENGINE_ECU, VehicleModule.AYC_ACD,
                        VehicleModule.ABS),
                false, true, false));
    }

    private PlatformRegistry() {
    }

    public static PlatformCapabilities get(VehiclePlatform platform) {
        if (platform == null) {
            throw new IllegalArgumentException("Vehicle platform is required");
        }
        return PLATFORMS.get(platform);
    }
}
