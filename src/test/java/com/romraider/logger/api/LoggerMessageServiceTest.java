/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.Test;

public class LoggerMessageServiceTest {
    @Test
    public void retainsMessageStatisticsAndErrorState() {
        LoggerMessageService service = new LoggerMessageService();
        AtomicReference<LoggerMessageSnapshot> observed =
                new AtomicReference<LoggerMessageSnapshot>();
        Consumer<LoggerMessageSnapshot> listener = observed::set;
        service.addListener(listener);
        try {
            assertEquals("Ready", observed.get().getMessage());
            service.statistics("20.0 queries/sec");
            service.message("Reading data");
            assertEquals("20.0 queries/sec",
                    observed.get().getStatistics());
            assertFalse(observed.get().isError());

            service.error("Unable to send ECU init");
            assertTrue(observed.get().isError());
            assertEquals("Unable to send ECU init",
                    observed.get().getMessage());

            service.message("Reconnecting");
            assertFalse(observed.get().isError());
        } finally {
            service.removeListener(listener);
        }
    }
}
