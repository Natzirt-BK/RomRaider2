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

import static org.apache.log4j.Logger.getLogger;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public final class PlatformContext {
    private static final Logger LOGGER = getLogger(PlatformContext.class);
    private static final PlatformContext INSTANCE = new PlatformContext();

    private final List<PlatformContextListener> listeners =
            new ArrayList<PlatformContextListener>();
    private VehiclePlatform platform = VehiclePlatform.SUBARU;
    private VehicleModule module = VehicleModule.ENGINE_ECU;
    private DimeModState dimeModState = DimeModState.UNKNOWN;

    private PlatformContext() {
    }

    public static PlatformContext getInstance() {
        return INSTANCE;
    }

    public synchronized VehiclePlatform getPlatform() {
        return platform;
    }

    public synchronized VehicleModule getModule() {
        return module;
    }

    public synchronized DimeModState getDimeModState() {
        return dimeModState;
    }

    public void setPlatform(VehiclePlatform platform) {
        if (platform == null) {
            throw new IllegalArgumentException("Vehicle platform is required");
        }
        synchronized (this) {
            if (this.platform == platform) return;
            this.platform = platform;
            PlatformCapabilities capabilities = PlatformRegistry.get(platform);
            if (!capabilities.supports(module)) {
                module = capabilities.getModules().get(0);
            }
        }
        notifyListeners();
    }

    public void setModule(VehicleModule module) {
        if (module == null) {
            throw new IllegalArgumentException("Vehicle module is required");
        }
        synchronized (this) {
            if (!PlatformRegistry.get(platform).supports(module)) {
                throw new IllegalArgumentException(
                        module + " is not available for " + platform);
            }
            if (this.module == module) return;
            this.module = module;
        }
        notifyListeners();
    }

    public void setDimeModState(DimeModState state) {
        if (state == null) {
            throw new IllegalArgumentException("DimeMod state is required");
        }
        synchronized (this) {
            if (dimeModState == state) return;
            dimeModState = state;
        }
        notifyListeners();
    }

    public synchronized void addListener(PlatformContextListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(PlatformContextListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        List<PlatformContextListener> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<PlatformContextListener>(listeners);
        }
        for (PlatformContextListener listener : snapshot) {
            try {
                listener.platformContextChanged(this);
            } catch (RuntimeException exception) {
                LOGGER.warn("Platform-context listener failed", exception);
            }
        }
    }
}
