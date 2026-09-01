/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class LoggerSessionServiceTest {
    @Test
    public void commandsFollowTheObservedSessionState() throws Exception {
        LoggerLiveDataBus bus = LoggerLiveDataBus.getInstance();
        bus.stopped();
        AtomicInteger connects = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        LoggerSessionService service = new LoggerSessionService(bus,
                connects::incrementAndGet,
                disconnects::incrementAndGet,
                starts::incrementAndGet,
                stops::incrementAndGet,
                failure -> failures.incrementAndGet());
        try {
            service.startRecording();
            service.connect();
            awaitValue(connects, 1);
            assertEquals(0, starts.get());

            bus.readingData();
            service.startRecording();
            awaitValue(starts, 1);

            bus.loggingData();
            service.disconnect();
            awaitValue(disconnects, 1);
            assertEquals(1, stops.get());
            assertEquals(0, failures.get());
        } finally {
            service.close();
            bus.stopped();
        }
    }

    @Test
    public void commandFailuresAreReportedWithoutKillingTheService()
            throws Exception {
        LoggerLiveDataBus bus = LoggerLiveDataBus.getInstance();
        bus.stopped();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        LoggerSessionService service = new LoggerSessionService(bus,
                () -> { throw new IllegalStateException("connect failed"); },
                disconnects::incrementAndGet,
                () -> { }, () -> { },
                failure -> failures.incrementAndGet());
        try {
            service.connect();
            awaitValue(failures, 1);
            bus.connecting();
            service.disconnect();
            awaitValue(disconnects, 1);
        } finally {
            service.close();
            bus.stopped();
        }
    }

    private static void awaitValue(AtomicInteger value, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (value.get() < expected && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(5L);
        }
        assertEquals(expected, value.get());
    }
}
