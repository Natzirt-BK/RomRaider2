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
        assertFalse(context.isRamTuneRuntimeAvailable());
    }

    @Test
    public void ramTuneAvailabilityRequiresAnActiveRuntime() {
        context.setDimeModRuntime(DimeModState.ACTIVE, true);
        assertTrue(context.isRamTuneRuntimeAvailable());

        context.setDimeModState(DimeModState.UNKNOWN);
        assertFalse(context.isRamTuneRuntimeAvailable());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRamTuneAvailabilityWithoutActiveDimeMod() {
        context.setDimeModRuntime(DimeModState.PRESENT, true);
    }

    @Test
    public void retainsQualifiedRamTuneDiscoveryMetadataForCurrentSession() {
        RamTuneRuntimeMetadata metadata = new RamTuneRuntimeMetadata(
                "2.3 build 100", 0x123456, 128);

        context.setDimeModRuntime(DimeModState.ACTIVE, true, metadata);

        assertTrue(context.isRamTuneRuntimeAvailable());
        assertTrue(context.hasQualifiedRamTuneMetadata());
        assertEquals(0x123456L, context.getRamTuneRuntimeMetadata().get()
                .getSignatureAddress());
        context.setDimeModState(DimeModState.UNKNOWN);
        assertFalse(context.getRamTuneRuntimeMetadata().isPresent());
    }

    @Test
    public void doesNotQualifyOutOfRangeRamTuneMetadata() {
        context.setDimeModRuntime(DimeModState.ACTIVE, true,
                new RamTuneRuntimeMetadata("test", 0x1000000L, 12));

        assertTrue(context.isRamTuneRuntimeAvailable());
        assertFalse(context.hasQualifiedRamTuneMetadata());
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

    @Test
    public void detectsCarBerryAndMerpModFromExplicitRomIdentity() {
        Rom carBerry = new Rom(new RomID());
        carBerry.getRomID().setXmlid("CarBerry");
        Rom merpMod = new Rom(new RomID());
        merpMod.getRomID().setInternalIdString(
                "A2ZJE11J.MeRpMoD.Switch.Testing.v00.60");

        assertEquals(RomModificationEvidence.ROM_IDENTITY,
                RomModificationDetector.detect(carBerry).get(
                        RomModification.CARBERRY));
        assertEquals(RomModificationEvidence.ROM_IDENTITY,
                RomModificationDetector.detect(merpMod).get(
                        RomModification.MERP_MOD));
    }

    @Test
    public void detectsOnlyExplicitlyBrandedModificationTables() {
        Rom rom = new Rom(new RomID());
        Table1D carBerry = new Table1D();
        carBerry.setName("Launch Control - Enable");
        carBerry.setCategory("CarBerry - Launch Control");
        rom.addTableByName(carBerry);
        Table1D generic = new Table1D();
        generic.setName("Flex Fuel Blend");
        generic.setCategory("Custom Features");
        rom.addTableByName(generic);

        java.util.Map<RomModification, RomModificationEvidence> detected =
                RomModificationDetector.detect(rom);

        assertEquals(RomModificationEvidence.BRANDED_TABLES,
                detected.get(RomModification.CARBERRY));
        assertEquals(RomModificationEvidence.NOT_DETECTED,
                detected.get(RomModification.MERP_MOD));
        assertEquals(RomModificationEvidence.NOT_DETECTED,
                detected.get(RomModification.DIME_MOD));
        assertFalse(DimeModFeatureDetector.detect(rom).get(
                DimeModFeature.FLEX_FUEL));
    }

    @Test
    public void detectsCarBerryAndMerpModFeaturesOnlyFromTheirOwnTables() {
        Rom rom = new Rom(new RomID());
        Table1D carBerry = new Table1D();
        carBerry.setName("Flex Fuel - Enable");
        carBerry.setCategory("CarBerry - Flex Fuel");
        rom.addTableByName(carBerry);
        Table1D merp = new Table1D();
        merp.setName("MerpMod SD Mode Switch");
        merp.setCategory("MerpMod - Speed Density");
        rom.addTableByName(merp);
        Table1D generic = new Table1D();
        generic.setName("Launch Control");
        generic.setCategory("Miscellaneous");
        rom.addTableByName(generic);

        java.util.Map<RomModificationFeature, Boolean> features =
                RomModificationFeatureDetector.detect(rom);

        assertTrue(features.get(RomModificationFeature.CARBERRY_FLEX_FUEL));
        assertFalse(features.get(
                RomModificationFeature.CARBERRY_LAUNCH_CONTROL));
        assertTrue(features.get(
                RomModificationFeature.MERPMOD_SPEED_DENSITY));
        assertFalse(features.get(
                RomModificationFeature.MERPMOD_LAUNCH_CONTROL));
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
        evo.setModel("Lancer Evolution GT-A / Wagon family");
        assertEquals(VehiclePlatform.EVO_8_9,
                RomPlatformResolver.resolve(evo).get());

        RomID unknown = new RomID();
        unknown.setMake("Mitsubishi");
        unknown.setModel("Galant");
        assertFalse(RomPlatformResolver.resolve(unknown).isPresent());
        assertFalse(RomPlatformResolver.resolve(null).isPresent());
    }
}
