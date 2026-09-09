/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.Settings;
import com.romraider.io.connection.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticConnectionSelectionTest {
    @Test public void connectionTimingIsCopiedWithoutLosingKwpValues() {
        KwpConnectionProperties original = new KwpSerialConnectionProperties(10400, 8, 1, 0, 1000, 2000, 1, 3, 4);
        ConnectionProperties captured = DiagnosticConnectionSelection.snapshot(original);
        assertNotSame(original, captured);
        assertEquals(10400, captured.getBaudRate());
        assertEquals(1000, captured.getConnectTimeout());
        assertEquals(2000, captured.getSendTimeout());
        assertEquals(1, ((KwpConnectionProperties) captured).getP1Max());
        assertEquals(3, ((KwpConnectionProperties) captured).getP3Min());
        assertEquals(4, ((KwpConnectionProperties) captured).getP4Min());
    }

    @Test public void changedTimingOrAdapterKindInvalidatesTheFingerprint() {
        Settings settings = new Settings();
        var original = DiagnosticConnectionSelection.fingerprint(settings);
        settings.setLoggerConnectionProperties(new SerialConnectionProperties(1, 8, 1, 0, 2000, 3000));
        assertFalse(original.equals(DiagnosticConnectionSelection.fingerprint(settings)));
    }
}
