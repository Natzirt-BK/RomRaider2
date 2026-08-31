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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;

import com.romraider.Settings;
import com.romraider.xml.DOMSettingsUnmarshaller;

public class PlatformSettingsMigrationTest {
    @Test
    public void legacySettingsDefaultToSubaruEngine() throws Exception {
        Settings settings = load("<settings><options/></settings>");
        assertEquals(VehiclePlatform.SUBARU, settings.getVehiclePlatform());
        assertEquals(VehicleModule.ENGINE_ECU, settings.getVehicleModule());
    }

    @Test
    public void restoresVersionedEvoSelection() throws Exception {
        Settings settings = load("<settings><platform-selection schema=\"1\" "
                + "vehicle=\"EVO_8_9\" module=\"AYC_ACD\"/></settings>");
        assertEquals(VehiclePlatform.EVO_8_9, settings.getVehiclePlatform());
        assertEquals(VehicleModule.AYC_ACD, settings.getVehicleModule());
    }

    @Test
    public void migratesInvalidCrossPlatformModuleToSafeDefault() throws Exception {
        Settings settings = load("<settings><platform-selection schema=\"1\" "
                + "vehicle=\"EVO_8_9\" module=\"TCU\"/></settings>");
        assertEquals(VehiclePlatform.EVO_8_9, settings.getVehiclePlatform());
        assertEquals(VehicleModule.ENGINE_ECU, settings.getVehicleModule());
    }

    @Test
    public void ignoresUnknownFutureSchema() throws Exception {
        Settings settings = load("<settings><platform-selection schema=\"99\" "
                + "vehicle=\"EVO_8_9\" module=\"ABS\"/></settings>");
        assertEquals(VehiclePlatform.SUBARU, settings.getVehiclePlatform());
        assertEquals(VehicleModule.ENGINE_ECU, settings.getVehicleModule());
    }

    private Settings load(String xml) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return new DOMSettingsUnmarshaller().unmarshallSettings(document.getDocumentElement());
    }
}
