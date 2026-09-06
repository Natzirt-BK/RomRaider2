/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.handler.file;

import com.romraider.logger.ecu.comms.query.ResponseImpl;
import com.romraider.logger.ecu.definition.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InvalidCsvReadingTest {
    @Test public void invalidCellsStillCompleteRowsAndDoNotOverwriteRealZero() {
        List<String> lines = new ArrayList<>();
        FileLogger sink = new FileLogger() {
            public void start() { }
            public void stop() { }
            public boolean isStarted() { return true; }
            public void writeHeaders(String headers) { }
            public void writeLine(String line, long timestamp) { lines.add(line); }
        };
        FileUpdateHandlerImpl handler = new FileUpdateHandlerImpl(sink);
        EcuParameterImpl data = new EcuParameterImpl("fixture", "Fixture", "Synthetic",
                new EcuAddressImpl("000001", 1, -1), null, null, null,
                new EcuDataConvertor[] {new EcuParameterConvertorImpl()});
        handler.registerData(data);
        for (double value : new double[] {12, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, 14}) {
            ResponseImpl response = new ResponseImpl();
            response.setDataValue(data, value);
            handler.handleDataUpdate(response);
        }
        assertEquals(6, lines.size());
        String delimiter = lines.get(0).substring(0, 1);
        assertEquals(Arrays.asList(delimiter + "12", delimiter, delimiter, delimiter,
                delimiter + "0", delimiter + "14"), lines);
    }
}
