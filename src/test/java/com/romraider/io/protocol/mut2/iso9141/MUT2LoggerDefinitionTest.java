/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.romraider.io.protocol.mut2.iso9141;

import com.romraider.logger.ecu.definition.xml.LoggerDefinitionHandler;
import com.romraider.util.SaxParserFactory;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MUT2LoggerDefinitionTest {
    @Test
    public void loadsMinimalDefinitionThroughRomRaiderParser() throws Exception {
        String definition = "<logger version=\"MUT2-test-1\">"
                + "<protocols><protocol id=\"MUT2\" baud=\"15625\" databits=\"8\" "
                + "stopbits=\"1\" parity=\"0\" connect_timeout=\"250\" send_timeout=\"100\">"
                + "<transports><transport id=\"iso9141\" name=\"K-Line\" desc=\"Test transport\">"
                + "<module id=\"ecu\" address=\"0x10\" tester=\"0xF0\" "
                + "desc=\"Test module\" fastpoll=\"false\"/></transport></transports>"
                + "<parameters><parameter id=\"M01\" name=\"Test byte\" "
                + "desc=\"Synthetic parser fixture\" target=\"1\"><address>0x01</address>"
                + "<conversions><conversion units=\"raw\" storagetype=\"uint8\" "
                + "expr=\"x\" format=\"0\"/></conversions></parameter></parameters>"
                + "</protocol></protocols></logger>";
        LoggerDefinitionHandler handler = new LoggerDefinitionHandler("MUT2", "S20", null);
        ByteArrayInputStream input = new ByteArrayInputStream(
                definition.getBytes(StandardCharsets.UTF_8));
        SaxParserFactory.getSaxParser().parse(input, handler, "inline-mut2-test.xml");

        assertEquals("MUT2-test-1", handler.getVersion());
        assertEquals(1, handler.getEcuParameters().size());
        assertEquals(15625, handler.getConnectionProperties().getBaudRate());
        assertTrue(handler.getProtocols().containsKey("MUT2"));
    }
}
