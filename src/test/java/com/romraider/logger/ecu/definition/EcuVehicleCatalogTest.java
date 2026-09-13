package com.romraider.logger.ecu.definition;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import com.romraider.logger.ecu.comms.query.EcuInit;

public class EcuVehicleCatalogTest {
    private static String conversion(String expression) {
        return "<conversions><conversion units='V' expr='" + expression + "' format='0.00'/></conversions>";
    }
    private static final String XML = "<logger version='1'><protocols><protocol id='SSM' baud='4800' databits='8' stopbits='1' parity='0' connect_timeout='2000' send_timeout='55'>"
            + "<parameter id='P1' name='Supported' desc='' target='1' ecubyteindex='8' ecubit='7'><address>1</address>" + conversion("x") + "</parameter>"
            + "<parameter id='P2' name='Unsupported' desc='' ecubyteindex='8' ecubit='0'><address>2</address>" + conversion("x") + "</parameter>"
            + "<parameter id='U1' name='Unverified' desc=''><address>3</address>" + conversion("x") + "</parameter>"
            + "<parameter id='C1' name='Confirmed calculation' desc=''><depends><ref parameter='P1'/></depends>" + conversion("P1*2") + "</parameter>"
            + "<parameter id='C2' name='Unverified calculation' desc=''><depends><ref parameter='U1'/></depends>" + conversion("U1*2") + "</parameter>"
            + "<ecuparam id='E1' name='Exact ECU' desc='' target='1'><ecu id='AAA'><address>4</address></ecu>" + conversion("x") + "</ecuparam>"
            + "<parameter id='T1' name='Transmission' desc='' target='2' ecubyteindex='8' ecubit='7'><address>5</address>" + conversion("x") + "</parameter>"
            + "<switch id='S1' name='Supported switch' desc='' ecubyteindex='8' ecubit='7' byte='6' bit='7'/>"
            + "<switch id='S2' name='Unverified switch' desc='' byte='7' bit='0'/>"
            + "</protocol></protocols></logger>";

    private EcuDataLoaderImpl load(String id, byte[] payload, int target) {
        EcuInit init = id == null ? null : new EcuInit() {
            public String getEcuId() { return id; }
            public byte[] getEcuInitBytes() { return payload; }
        };
        EcuDataLoaderImpl loader = new EcuDataLoaderImpl();
        loader.loadConfirmedConfigForDesktop("synthetic.xml", XML.getBytes(StandardCharsets.UTF_8), "SSM", "S1", init, target);
        return loader;
    }
    private byte[] payload() { byte[] bytes = new byte[9]; bytes[8] = (byte) 128; return bytes; }

    @Test public void engineUsesFlagsExactMappingsAndConfirmedDependencies() {
        var loader = load("AAA", payload(), 1);
        assertEquals(List.of("P1", "C1", "E1"), loader.getEcuParameters().stream().map(EcuData::getId).toList());
        assertEquals(List.of("S1"), loader.getEcuSwitches().stream().map(EcuData::getId).toList());
        assertNotNull(loader.getFileLoggingControllerSwitch());
    }
    @Test public void moduleAndEcuMappingsCannotLeak() {
        assertEquals(List.of("T1"), load("BBB", payload(), 2).getEcuParameters().stream().map(EcuData::getId).toList());
        assertEquals(List.of("P1", "C1"), load("BBB", payload(), 1).getEcuParameters().stream().map(EcuData::getId).toList());
    }
    @Test public void missingCapabilityBytesDoNotClaimStandardSupport() {
        assertEquals(List.of("E1"), load("AAA", null, 1).getEcuParameters().stream().map(EcuData::getId).toList());
        assertTrue(load("BBB", new byte[8], 1).getEcuParameters().isEmpty());
        assertTrue(load(null, null, 1).getEcuParameters().isEmpty());
        assertTrue(load(null, null, 1).getEcuSwitches().isEmpty());
    }
}
