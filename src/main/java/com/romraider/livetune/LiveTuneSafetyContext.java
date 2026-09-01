/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

import com.romraider.io.transport.EcuIdentity;
import com.romraider.platform.DimeModState;
import com.romraider.platform.VehicleModule;
import com.romraider.platform.VehiclePlatform;

/** Runtime evidence required before a staged live-tuning plan can be ready. */
public final class LiveTuneSafetyContext {
    private final VehiclePlatform platform;
    private final VehicleModule module;
    private final DimeModState dimeModState;
    private final boolean definitionMapped;
    private final boolean runtimeRamTuneAvailable;
    private final EcuIdentity connectedIdentity;

    public LiveTuneSafetyContext(VehiclePlatform platform,
            VehicleModule module, DimeModState dimeModState,
            boolean definitionMapped, boolean runtimeRamTuneAvailable,
            EcuIdentity connectedIdentity) {
        if (platform == null || module == null || dimeModState == null) {
            throw new IllegalArgumentException(
                    "Platform, module, and DimeMod state are required");
        }
        this.platform = platform;
        this.module = module;
        this.dimeModState = dimeModState;
        this.definitionMapped = definitionMapped;
        this.runtimeRamTuneAvailable = runtimeRamTuneAvailable;
        this.connectedIdentity = connectedIdentity;
    }

    public VehiclePlatform getPlatform() {
        return platform;
    }

    public VehicleModule getModule() {
        return module;
    }

    public DimeModState getDimeModState() {
        return dimeModState;
    }

    public boolean isDefinitionMapped() {
        return definitionMapped;
    }

    public boolean isRuntimeRamTuneAvailable() {
        return runtimeRamTuneAvailable;
    }

    public EcuIdentity getConnectedIdentity() {
        return connectedIdentity;
    }
}
