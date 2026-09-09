/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.EcuQueryImpl;
import com.romraider.logger.ecu.definition.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticReportCsvTest {
    private static EcuQuery code(String name, byte current, byte memorized) {
        EcuQuery result = new EcuQueryImpl(new EcuSwitchImpl("D1", name, "Synthetic", new EcuAddressImpl("0x000100", 2, 0),
                null, null, null, new EcuDataConvertor[] {new EcuDtcConvertorImpl(0)}));
        result.setResponse(new byte[] {current, memorized}); return result;
    }
    private static String csv(List<EcuQuery> standard, Set<String> current, Set<String> memorized) {
        return DiagnosticReportCsv.format(standard, current, memorized, "Code,Temporary,Memorized", "true", "false", "\r\n");
    }

    @Test public void standardAndDimeRowsRetainIndependentFlags() {
        assertEquals("Code,Temporary,Memorized\r\nP0001,true,false\r\nDM1,false,true\r\nDM2,true,true\r\n",
                csv(List.of(code("P0001", (byte) 1, (byte) 0)), Set.of("DM2"), Set.of("DM2", "DM1")));
    }

    @Test public void dimeOnlyReportIsNotAnEmptyHeader() {
        assertEquals("Code,Temporary,Memorized\r\nDM1,true,false\r\n", csv(List.of(), Set.of("DM1"), Set.of()));
    }

    @Test public void commasQuotesAndNewlinesAreEscapedWithoutLosingNames() {
        assertEquals("Code,Temporary,Memorized\r\n\"Sensor, \"\"A\"\"\n故障\",true,true\r\n",
                csv(List.of(code("Sensor, \"A\"\n故障", (byte) 1, (byte) 1)), null, null));
    }

    @Test public void emptyLegacyReportAndNullDimeSetsAreSupported() {
        assertEquals("Code,Temporary,Memorized\r\n", csv(List.of(), null, null));
    }

    @Test public void utf8SaveNeverOverwritesAnExistingReport() throws Exception {
        Path directory = Files.createTempDirectory("rr2-diagnostic-csv-");
        Path target = directory.resolve("report.csv");
        try {
            DiagnosticReportCsv.writeNew(target, "故障\r\n");
            assertArrayEquals("故障\r\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
            try { DiagnosticReportCsv.writeNew(target, "replacement"); fail("overwritten"); }
            catch (FileAlreadyExistsException expected) { }
            assertEquals("故障\r\n", Files.readString(target));
        } finally { Files.deleteIfExists(target); Files.deleteIfExists(directory); }
    }
}
