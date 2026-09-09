/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.logger.ReadOnlySsmProtocol;
import com.romraider.portable.logger.definition.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleCatalogTest {
    private static final String XML = "<logger><protocol id='SSM'>"
            + "<parameter id='P1' name='Supported' ecubyteindex='8' ecubit='7'><address>1</address>" + conversion("x") + "</parameter>"
            + "<parameter id='P2' name='Unsupported' ecubyteindex='8' ecubit='0'><address>2</address>" + conversion("x") + "</parameter>"
            + "<switch id='S1' name='Switch' ecubyteindex='9' bit='2' byte='3'/>"
            + "<parameter id='C1' name='Calculated'><depends><ref parameter='P1'/></depends>" + conversion("P1*2") + "</parameter>"
            + "<parameter id='C2' name='Unavailable calculation'><depends><ref parameter='P2'/></depends>" + conversion("P2*2") + "</parameter>"
            + "<ecuparam id='E1' name='Variant'><ecu id='AAA'><address>4</address></ecu>" + conversion("x") + "</ecuparam>"
            + "<ecuparam id='E2' name='Variant'><ecu id='BBB'><address>5</address></ecu>" + conversion("x") + "</ecuparam>"
            + "<ecuparam id='DM911' name='DimeMod: FlexFuel Ethanol Content'><ecu id='AAA'><address length='4'>0xFF1000</address></ecu>"
            + "<conversions><conversion units='%' expr='x' format='0.00' storagetype='float'/></conversions></ecuparam>"
            + "<parameter id='T1' name='Transmission' target='2'><address>6</address>" + conversion("x") + "</parameter>"
            + "</protocol></logger>";

    private static String conversion(String expr) {
        return "<conversions><conversion units='V' expr='" + expr + "' format='0.00'/></conversions>";
    }
    private static PortableLoggerDefinition read(String xml) throws IOException {
        return PortableLoggerDefinitionReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "SSM");
    }
    private static List<String> ids(PortableLoggerDefinition definition) {
        return definition.parameters().stream().map(PortableLoggerParameter::getId).collect(Collectors.toList());
    }
    private static byte[] payload() {
        byte[] init = new byte[10]; init[8] = (byte) 0x80; init[9] = 4; return init;
    }

    @Test public void flagsMappingsModuleAndDependenciesAllApply() throws Exception {
        PortableLoggerDefinition base = read(XML);
        assertEquals(List.of("P1", "S1", "C1", "E1", "DM911"), ids(PortableVehicleCatalog.forEcu(base, "AAA", payload())));
        assertEquals(List.of("P1", "S1", "C1", "E2"), ids(PortableVehicleCatalog.forEcu(base, "BBB", payload())));
        assertEquals(9, base.size()); // Filtering never mutates the loaded file/catalog.
    }
    @Test public void missingAndTruncatedSupportFlagsCannotClaimSupport() throws Exception {
        PortableLoggerDefinition base = read(XML);
        assertEquals(List.of("E1", "DM911"), ids(PortableVehicleCatalog.forEcu(base, "AAA", null)));
        assertEquals(List.of("E1", "DM911"), ids(PortableVehicleCatalog.forEcu(base, "AAA", new byte[8])));
        assertEquals(List.of("P1", "C1", "E1", "DM911"), ids(PortableVehicleCatalog.forEcu(base, "AAA", Arrays.copyOf(payload(), 9))));
    }
    @Test public void malformedSupportAttributesAreRejected() {
        for (String xml : List.of(XML.replace("ecubyteindex='8'", "ecubyteindex='-1'"),
                XML.replace("ecubyteindex='8'", "ecubyteindex='255'"),
                XML.replace("ecubit='7'", "ecubit='8'"), XML.replace("ecubit='7'", ""))) {
            assertThrows(IOException.class, () -> read(xml));
        }
    }
    @Test public void payloadExtractionMatchesDesktopOffsetAndRejectsBadFrames() {
        byte[] payload = payload();
        byte[] frame = DimeModDiscoveryTest.response(0xFF, payload);
        assertArrayEquals(payload, ReadOnlySsmProtocol.ecuInitPayload(frame));
        frame[frame.length - 1]++;
        assertThrows(IllegalArgumentException.class, () -> ReadOnlySsmProtocol.ecuInitPayload(frame));
        assertThrows(IllegalArgumentException.class, () -> ReadOnlySsmProtocol.ecuInitPayload(
                DimeModDiscoveryTest.response(0xFF, new byte[7])));
    }
    @Test public void definitionOnlyChannelsDoNotBecomeInventedEcuChannels() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> PortableVehicleCatalog.forEcu(read(XML), "", payload()));
        assertNull(PortableVehicleCatalog.forEcu(read(XML), "AAA", payload()).parameter("P99"));
    }

    @Test public void importedUnsupportedSelectionsAreNeverPolledOrWrittenToCsv() throws Exception {
        PortableLoggerProfile profile = new PortableLoggerProfile("SSM", List.of(
                new PortableLoggerProfile.Selection("P1", "V"),
                new PortableLoggerProfile.Selection("P2", "V"),
                new PortableLoggerProfile.Selection("C2", "V")), List.of());
        com.romraider.portable.PortableLogSession log = new com.romraider.portable.PortableLogSession();
        int[] reads = {0};
        ReadOnlyLoggerSession[] owner = new ReadOnlyLoggerSession[1];
        com.romraider.portable.logger.ReadOnlyLoggerTransport transport = new com.romraider.portable.logger.ReadOnlyLoggerTransport() {
            public String identifyEcu(com.romraider.portable.logger.PortableLoggerProtocol protocol) { return "AAA"; }
            public byte[] ssmInitPayload() { return payload(); }
            public byte[] read(com.romraider.portable.logger.PortableLoggerQueryBatch batch) {
                assertArrayEquals(new int[]{1}, batch.getAddresses());
                reads[0]++;
                return new byte[]{12};
            }
            public void closeReadOnlyKLine() { }
        };
        owner[0] = new ReadOnlyLoggerSession(transport, read(XML), profile, log, new ReadOnlyLoggerSession.Listener() {
            public void onCatalog(String ecuId, PortableLoggerDefinition catalog, String status) {
                assertNull(catalog.parameter("P2"));
                assertNotNull(catalog.parameter("DM911"));
            }
            public void onIdentified(String ecuId, int ready, int unavailable) {
                assertEquals(1, ready); assertEquals(2, unavailable);
            }
            public void onValues(String ecuId, long timestamp, List<com.romraider.portable.logger.PortableLoggerValue> values, int count) {
                owner[0].stop();
            }
            public void onStopped(String message) { assertEquals("Read-only logger stopped.", message); }
        });
        owner[0].run();
        assertEquals(1, reads[0]);
        StringWriter csv = new StringWriter(); log.writeRomRaiderCsv(csv);
        assertEquals("Time (msec),Supported (V)\n0,12\n", csv.toString());
        assertEquals(3, profile.size());
    }
}
