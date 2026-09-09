/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.logger.definition.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoggerChannelCatalogTest {
    private PortableLoggerDefinition definition() throws Exception {
        String xml = "<logger><protocol id='SSM'>"
                + "<parameter id='P8' name='RPM' target='3'><address>0x0E</address>"
                + conversion("rpm", "x") + "</parameter>"
                + "<ecuparam id='E5' name='Boost Error' target='1'><ecu id='AAA'><address>0x1000</address></ecu>"
                + conversion("psi", "x") + "</ecuparam>"
                + "<ecuparam id='E35' name='Boost Error' target='1'><ecu id='BBB'><address>0x2000</address></ecu>"
                + conversion("psi", "x") + "</ecuparam>"
                + "<parameter id='T1' name='Transmission' target='2'><address>0x20</address>"
                + conversion("V", "x") + "</parameter>"
                + "<parameter id='C1' name='Calculated Boost' target='1'><depends><ref ecuparam='E5'/></depends>"
                + conversion("psi", "E5*2") + "</parameter></protocol></logger>";
        return PortableLoggerDefinitionReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "SSM");
    }

    private static String conversion(String units, String expr) {
        return "<conversions><conversion units='" + units + "' expr='" + expr + "' format='0'/></conversions>";
    }

    private static List<String> ids(List<PortableLoggerParameter> parameters) {
        return parameters.stream().map(PortableLoggerParameter::getId).collect(Collectors.toList());
    }

    @Test public void offlineKeepsSameNameVariantsButOmitsUnselectedTransmission() throws Exception {
        PortableLoggerDefinition definition = definition();
        assertEquals(List.of("P8", "E5", "E35", "C1"), ids(LoggerChannelCatalog.channels(definition, null, null)));
        assertEquals("Boost Error [E5]\nECU-specific · psi", LoggerChannelCatalog.label(definition.parameter("E5")));
        assertNotEquals(LoggerChannelCatalog.label(definition.parameter("E5")), LoggerChannelCatalog.label(definition.parameter("E35")));
    }

    @Test public void exactEcuMappingIncludesOnlyResolvableDependencies() throws Exception {
        PortableLoggerDefinition definition = definition();
        assertEquals(List.of("P8", "E5", "C1"), ids(LoggerChannelCatalog.channels(definition, null, "AAA")));
        assertEquals(List.of("P8", "E35"), ids(LoggerChannelCatalog.channels(definition, null, "BBB")));
        assertEquals(List.of("P8"), ids(LoggerChannelCatalog.channels(definition, null, "CCC")));
    }

    @Test public void filterRetainsPreviouslySelectedUnavailableChannels() throws Exception {
        PortableLoggerProfile profile = new PortableLoggerProfile("SSM", List.of(
                new PortableLoggerProfile.Selection("E35", "psi"),
                new PortableLoggerProfile.Selection("T1", "V")), List.of());
        assertEquals(List.of("P8", "E5", "E35", "T1", "C1"),
                ids(LoggerChannelCatalog.channels(definition(), profile, "AAA")));
    }

    @Test public void labelsDistinguishCalculatedAndModuleScope() throws Exception {
        PortableLoggerDefinition definition = definition();
        assertTrue(LoggerChannelCatalog.label(definition.parameter("C1")).contains("Calculated · psi"));
        assertTrue(LoggerChannelCatalog.label(definition.parameter("T1")).contains("Transmission · V"));
    }

    @Test public void editingBeforeDiscoveryRetainsPendingDimeIdsAndOriginalColumnOrder() throws Exception {
        PortableLoggerProfile previous = new PortableLoggerProfile("SSM", List.of(
                new PortableLoggerProfile.Selection("DM911", "%"),
                new PortableLoggerProfile.Selection("E5", "psi"),
                new PortableLoggerProfile.Selection("P8", "rpm")), List.of("External input unavailable"));
        List<PortableLoggerParameter> visible = LoggerChannelCatalog.channels(definition(), previous, null);
        PortableLoggerProfile result = LoggerChannelCatalog.select("SSM", previous, visible,
                new boolean[]{true, false, true, false});
        assertEquals(List.of("DM911", "P8", "E35"), result.selections().stream()
                .map(PortableLoggerProfile.Selection::getId).collect(Collectors.toList()));
        assertEquals("%", result.selections().get(0).getUnits());
        assertEquals(previous.unsupported(), result.unsupported());
    }
}
