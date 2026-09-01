/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.romraider.logger.ecu.comms.manager.QueryManager;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.ui.StatusChangeListener;
import com.romraider.logger.ecu.ui.handler.file.FileLoggerControllerSwitchMonitor;

public class LoggerControllerImplTest {
    @Test
    public void startsANewWorkerAfterThePreviousWorkerExits() throws Exception {
        ExitingQueryManager queryManager = new ExitingQueryManager();
        LoggerControllerImpl controller = new LoggerControllerImpl(queryManager);

        controller.start();
        assertTrue(queryManager.awaitRunCount(1));
        awaitStopped(controller);

        controller.start();
        assertTrue(queryManager.awaitRunCount(2));
        awaitStopped(controller);

        assertEquals(2, queryManager.runCount());
        assertFalse(controller.isStarted());
    }

    @Test
    public void startsANewWorkerAfterAnActiveWorkerIsStopped() throws Exception {
        RestartableQueryManager queryManager = new RestartableQueryManager();
        LoggerControllerImpl controller = new LoggerControllerImpl(queryManager);

        try {
            controller.start();
            assertTrue(queryManager.awaitRunCount(1));
            assertTrue(controller.isStarted());

            controller.stop();
            assertFalse(controller.isStarted());

            queryManager.prepareNextRun();
            controller.start();
            assertTrue(queryManager.awaitRunCount(2));
            assertTrue(controller.isStarted());
        } finally {
            controller.stop();
        }

        assertEquals(2, queryManager.runCount());
        assertEquals(2, queryManager.stopCount());
        assertFalse(controller.isStarted());
    }

    private static void awaitStopped(LoggerControllerImpl controller)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000L;
        while (controller.isStarted() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(5L);
        }
        assertFalse(controller.isStarted());
    }

    private static final class ExitingQueryManager implements QueryManager {
        private final AtomicInteger runCount = new AtomicInteger();

        @Override
        public void run() {
            runCount.incrementAndGet();
        }

        private boolean awaitRunCount(int expected) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 2000L;
            while (runCount.get() < expected
                    && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(5L);
            }
            return runCount.get() >= expected;
        }

        private int runCount() {
            return runCount.get();
        }

        @Override public void stop() { }
        @Override public boolean isRunning() { return false; }
        @Override public Thread getThread() { return null; }
        @Override public void addListener(StatusChangeListener listener) { }
        @Override public void addQuery(String callerId, LoggerData loggerData) { }
        @Override public void removeQuery(String callerId, LoggerData loggerData) { }
        @Override public void setFileLoggerSwitchMonitor(
                FileLoggerControllerSwitchMonitor monitor) { }
    }

    private static final class RestartableQueryManager implements QueryManager {
        private final AtomicInteger runCount = new AtomicInteger();
        private final AtomicInteger stopCount = new AtomicInteger();
        private volatile CountDownLatch stopSignal = new CountDownLatch(1);

        @Override
        public void run() {
            runCount.incrementAndGet();
            try {
                stopSignal.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void stop() {
            stopCount.incrementAndGet();
            stopSignal.countDown();
        }

        private void prepareNextRun() {
            stopSignal = new CountDownLatch(1);
        }

        private boolean awaitRunCount(int expected) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 2000L;
            while (runCount.get() < expected
                    && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(5L);
            }
            return runCount.get() >= expected;
        }

        private int runCount() {
            return runCount.get();
        }

        private int stopCount() {
            return stopCount.get();
        }

        @Override public boolean isRunning() { return stopSignal.getCount() > 0; }
        @Override public Thread getThread() { return null; }
        @Override public void addListener(StatusChangeListener listener) { }
        @Override public void addQuery(String callerId, LoggerData loggerData) { }
        @Override public void removeQuery(String callerId, LoggerData loggerData) { }
        @Override public void setFileLoggerSwitchMonitor(
                FileLoggerControllerSwitchMonitor monitor) { }
    }
}
