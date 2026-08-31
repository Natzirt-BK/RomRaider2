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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlatformCapabilities {
    private final VehiclePlatform platform;
    private final List<VehicleModule> modules;
    private final boolean dimeModSupported;
    private final boolean mut2Supported;
    private final boolean diagnosticsSupported;

    PlatformCapabilities(VehiclePlatform platform,
            List<VehicleModule> modules,
            boolean dimeModSupported,
            boolean mut2Supported,
            boolean diagnosticsSupported) {
        this.platform = platform;
        this.modules = Collections.unmodifiableList(
                new ArrayList<VehicleModule>(modules));
        this.dimeModSupported = dimeModSupported;
        this.mut2Supported = mut2Supported;
        this.diagnosticsSupported = diagnosticsSupported;
    }

    public VehiclePlatform getPlatform() {
        return platform;
    }

    public List<VehicleModule> getModules() {
        return modules;
    }

    public boolean supports(VehicleModule module) {
        return modules.contains(module);
    }

    public boolean isDimeModSupported() {
        return dimeModSupported;
    }

    public boolean isMut2Supported() {
        return mut2Supported;
    }

    public boolean isDiagnosticsSupported() {
        return diagnosticsSupported;
    }
}
