package com.romraider.logger.ecu.comms.manager;

import static org.junit.Assert.*;
import com.romraider.logger.ecu.comms.query.*;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.ui.handler.DataUpdateHandler;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;

public class AsyncDataUpdateHandlerTest {
    @Test public void stopBeforeThreadStartCannotBeUndoneByRun() throws Exception {
        List<Response> seen = new CopyOnWriteArrayList<>();
        var updater = new AsyncDataUpdateHandler(new DataUpdateHandler[]{handler(seen::add)});
        updater.addResponse(new ResponseImpl()); updater.stopUpdater(); updater.start(); updater.join(2000);
        assertFalse(updater.isAlive()); assertFalse(updater.isRunning()); assertTrue(seen.isEmpty());
    }
    @Test public void boundedQueueRejectsOverflowRatherThanSilentlyLosingSamples() {
        var updater = new AsyncDataUpdateHandler(new DataUpdateHandler[]{handler(r -> {})}, 2);
        updater.addResponse(new ResponseImpl()); updater.addResponse(new ResponseImpl());
        try { updater.addResponse(new ResponseImpl()); fail("Expected queue overflow"); }
        catch (IllegalStateException expected) { assertTrue(expected.getCause().getMessage().contains("queue is full")); }
        finally { updater.stopUpdater(); }
    }
    @Test public void handlerFailureIsObservableAndStopsWorker() throws Exception {
        var updater = new AsyncDataUpdateHandler(new DataUpdateHandler[]{handler(r -> { throw new IllegalArgumentException("Synthetic handler failure"); })});
        updater.addResponse(new ResponseImpl()); updater.start(); updater.join(2000);
        assertFalse(updater.isAlive()); assertFalse(updater.isRunning());
        try { updater.requireHealthy(); fail("Failure must reach polling owner"); }
        catch (IllegalStateException expected) { assertEquals("Synthetic handler failure", expected.getCause().getMessage()); }
    }
    @Test public void acceleratedBurstIsDeliveredInExactOrder() throws Exception {
        List<Response> expected = new ArrayList<>(), actual = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1000);
        var updater = new AsyncDataUpdateHandler(new DataUpdateHandler[]{handler(r -> { actual.add(r); delivered.countDown(); })});
        try {
            for (int i = 0; i < 1000; i++) { var r = new ResponseImpl(); expected.add(r); updater.addResponse(r); }
            updater.start(); assertTrue(delivered.await(3, TimeUnit.SECONDS)); updater.requireHealthy();
            assertEquals(expected, actual);
        } finally { updater.stopUpdater(); updater.join(2000); assertFalse(updater.isAlive()); }
    }
    private static DataUpdateHandler handler(java.util.function.Consumer<Response> consume) {
        return new DataUpdateHandler() {
            public void registerData(LoggerData d) {} public void deregisterData(LoggerData d) {}
            public void handleDataUpdate(Response r) { consume.accept(r); }
            public void cleanUp() {} public void reset() {}
        };
    }
}
