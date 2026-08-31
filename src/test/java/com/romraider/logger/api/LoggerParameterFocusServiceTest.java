/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LoggerParameterFocusServiceTest {
    private final LoggerParameterFocusService service =
            LoggerParameterFocusService.getInstance();

    @Before
    public void resetService() {
        service.clearForTesting();
    }

    @After
    public void cleanUpService() {
        service.clearForTesting();
    }

    @Test
    public void queuesRequestUntilLoggerCanAcceptIt() {
        final AtomicReference<String> focused = new AtomicReference<String>();
        service.requestFocus("  P-BOOST  ");

        assertEquals("P-BOOST", service.getPendingParameterId());

        service.addListener(parameterId -> false);
        assertEquals("P-BOOST", service.getPendingParameterId());
        service.addListener(parameterId -> {
            focused.set(parameterId);
            return true;
        });

        assertEquals("P-BOOST", focused.get());
        assertNull(service.getPendingParameterId());
    }

    @Test
    public void ignoresBlankRequestsWithoutReplacingPendingNavigation() {
        service.requestFocus("RPM");
        service.requestFocus("  ");
        service.requestFocus(null);

        assertEquals("RPM", service.getPendingParameterId());
    }
}
