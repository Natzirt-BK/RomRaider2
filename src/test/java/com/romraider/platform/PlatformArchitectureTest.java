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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;

public class PlatformArchitectureTest {
    private final PlatformContext context = PlatformContext.getInstance();

    @After
    public void restoreDefaults() {
        context.setPlatform(VehiclePlatform.SUBARU);
        context.setModule(VehicleModule.ENGINE_ECU);
        context.setDimeModState(DimeModState.UNKNOWN);
    }

    @Test
    public void subaruCapabilitiesRemainIndependentFromDimeModState() {
        PlatformCapabilities subaru = PlatformRegistry.get(VehiclePlatform.SUBARU);
        assertTrue(subaru.supports(VehicleModule.ENGINE_ECU));
        assertTrue(subaru.supports(VehicleModule.TCU));
        assertTrue(subaru.isDimeModSupported());
        assertFalse(subaru.isMut2Supported());

        context.setDimeModState(DimeModState.NOT_PRESENT);
        assertEquals(VehiclePlatform.SUBARU, context.getPlatform());
        assertEquals(DimeModState.NOT_PRESENT, context.getDimeModState());
    }

    @Test
    public void evoExposesOnlyEvoModulesAndMut2Capability() {
        context.setPlatform(VehiclePlatform.EVO_8_9);
        PlatformCapabilities evo = PlatformRegistry.get(context.getPlatform());

        assertTrue(evo.supports(VehicleModule.ENGINE_ECU));
        assertTrue(evo.supports(VehicleModule.AYC_ACD));
        assertTrue(evo.supports(VehicleModule.ABS));
        assertFalse(evo.supports(VehicleModule.TCU));
        assertFalse(evo.isDimeModSupported());
        assertTrue(evo.isMut2Supported());
        assertFalse(evo.isDiagnosticsSupported());
    }

    @Test
    public void detectsMappedDimeModFeaturesWithoutClaimingRuntimeSupport() {
        Rom rom = new Rom(new RomID());
        Table1D speedDensity = new Table1D();
        speedDensity.setName("Speed Density VE Table");
        speedDensity.setCategory("Dime Mod: Speed Density");
        rom.addTableByName(speedDensity);
        Table1D sensors = new Table1D();
        sensors.setName("Flex Fuel Ethanol Content Sensor Scaling");
        sensors.setCategory("Dime Mod: Custom Sensors");
        rom.addTableByName(sensors);

        java.util.Map<DimeModFeature, Boolean> features =
                DimeModFeatureDetector.detect(rom);

        assertTrue(features.get(DimeModFeature.SPEED_DENSITY));
        assertTrue(features.get(DimeModFeature.FLEX_FUEL));
        assertTrue(features.get(DimeModFeature.EXTERNAL_INPUTS));
        assertFalse(features.get(DimeModFeature.VALET_MODE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsModuleFromAnotherPlatform() {
        context.setPlatform(VehiclePlatform.EVO_8_9);
        context.setModule(VehicleModule.TCU);
    }

    @Test
    public void resolvesDefinitionMetadataWithoutSwingOrFilenameGuessing() {
        RomID subaru = new RomID();
        subaru.setMake("Subaru");
        subaru.setModel("Impreza");
        assertEquals(VehiclePlatform.SUBARU,
                RomPlatformResolver.resolve(subaru).get());

        RomID evo = new RomID();
        evo.setMake("MITSUBISHI");
        evo.setModel("Lancer Evolution IX GT-A / Wagon family");
        assertEquals(VehiclePlatform.EVO_8_9,
                RomPlatformResolver.resolve(evo).get());

        RomID unknown = new RomID();
        unknown.setMake("Mitsubishi");
        unknown.setModel("Galant");
        assertFalse(RomPlatformResolver.resolve(unknown).isPresent());
        assertFalse(RomPlatformResolver.resolve(null).isPresent());
    }
}
