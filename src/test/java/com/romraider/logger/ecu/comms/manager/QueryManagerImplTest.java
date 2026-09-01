/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QueryManagerImplTest {
    @Test
    public void serialFallbackRequiresAConfiguredPort() {
        assertFalse(QueryManagerImpl.shouldTrySerialConnection(null));
        assertFalse(QueryManagerImpl.shouldTrySerialConnection(""));
        assertTrue(QueryManagerImpl.shouldTrySerialConnection("/dev/ttyUSB0"));
    }

    @Test
    public void reconnectDelayBacksOffAndStopsAtFiveSeconds() {
        assertEquals(1000L, QueryManagerImpl.nextRetryDelay(0L));
        assertEquals(2000L, QueryManagerImpl.nextRetryDelay(1000L));
        assertEquals(4000L, QueryManagerImpl.nextRetryDelay(2000L));
        assertEquals(5000L, QueryManagerImpl.nextRetryDelay(4000L));
        assertEquals(5000L, QueryManagerImpl.nextRetryDelay(5000L));
    }
}
