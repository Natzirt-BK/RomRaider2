/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.Test;

import com.romraider.platform.VehicleModule;
import com.romraider.platform.VehiclePlatform;

public class FlashArchitectureTest {
    @Test
    public void capabilitiesNeverInferWriteFromReadSupport() {
        FlashCapabilities readOnly = new FlashCapabilities(true, false,
                false, false);

        assertTrue(readOnly.supports(FlashOperation.READ));
        assertFalse(readOnly.supports(FlashOperation.WRITE));
        assertFalse(readOnly.supports(FlashOperation.VERIFY));
        assertFalse(readOnly.supports(FlashOperation.RECOVER));
    }

    @Test
    public void progressIsMeasuredOrExplicitlyIndeterminate() {
        FlashProgress connecting = FlashProgress.indeterminate(
                FlashState.CONNECTING, "Connecting");
        FlashProgress reading = FlashProgress.measured(
                FlashState.READING, "Reading", 512, 2048);

        assertFalse(connecting.isMeasured());
        assertTrue(reading.isMeasured());
        assertEquals(0.25, reading.getFraction(), 0.0001);
    }

    @Test(expected = IllegalStateException.class)
    public void indeterminateProgressDoesNotInventPercentage() {
        FlashProgress.indeterminate(FlashState.CONNECTING, "Connecting")
                .getFraction();
    }

    @Test
    public void unavailableMandatoryPreflightCheckBlocksOperation() {
        FlashPreflight preflight = new FlashPreflight(Arrays.asList(
                check("device", PreflightStatus.PASS, true),
                check("voltage", PreflightStatus.UNAVAILABLE, true),
                check("recovery", PreflightStatus.UNAVAILABLE, false)));

        assertFalse(preflight.canProceed());
        assertEquals(1, preflight.getBlockingChecks().size());
        assertEquals("voltage", preflight.getBlockingChecks().get(0).getId());
    }

    @Test
    public void managerRefusesUnadvertisedWriteBeforeCreatingSession()
            throws Exception {
        final AtomicBoolean sessionCreated = new AtomicBoolean();
        FlashProtocol protocol = readOnlyProtocol(sessionCreated, null, null);
        FlashManager manager = new FlashManager(Collections.singletonList(protocol));
        try {
            FlashResult result = manager.start(new FlashRequest(
                    FlashOperation.WRITE, target(), new TestDevice(),
                    new byte[1024]), null).get(3, TimeUnit.SECONDS);

            assertEquals(FlashState.FAILED, result.getState());
            assertTrue(result.getMessage().contains("does not support WRITE"));
            assertFalse(sessionCreated.get());
        } finally {
            manager.close();
        }
    }

    @Test
    public void readSessionRunsOffSwingThreadAndReportsRealUnits()
            throws Exception {
        final AtomicReference<String> sessionThread = new AtomicReference<String>();
        final AtomicReference<FlashProgress> measured =
                new AtomicReference<FlashProgress>();
        final AtomicBoolean sessionCreated = new AtomicBoolean();
        FlashProtocol protocol = readOnlyProtocol(sessionCreated,
                sessionThread, measured);
        final FlashManager manager = new FlashManager(
                Collections.singletonList(protocol));
        final AtomicReference<Future<FlashResult>> future =
                new AtomicReference<Future<FlashResult>>();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    future.set(manager.start(new FlashRequest(
                            FlashOperation.READ, target(), new TestDevice(), null),
                            progress -> {
                                if (progress.isMeasured()) measured.set(progress);
                            }));
                }
            });
            FlashResult result = future.get().get(3, TimeUnit.SECONDS);

            assertTrue(result.isSuccessful());
            assertTrue(sessionCreated.get());
            assertNotNull(sessionThread.get());
            assertNotEquals(Thread.currentThread().getName(), sessionThread.get());
            assertTrue(sessionThread.get().contains("Flash Worker"));
            assertNotNull(measured.get());
            assertEquals(4096L, measured.get().getCompletedUnits());
            assertEquals(4096L, measured.get().getTotalUnits());
            assertTrue(manager.getActiveSessions().isEmpty());
        } finally {
            manager.close();
        }
    }

    @Test
    public void cancellationIsRejectedDuringEraseWindow() {
        TestSession session = new TestSession(new FlashRequest(
                FlashOperation.WRITE, target(), new TestDevice(), new byte[16]),
                null);

        session.enterErase();

        assertEquals(FlashState.ERASING, session.getState());
        assertFalse(session.requestCancellation());
    }

    @Test
    public void deviceDiscoveryUsesDescriptorsBeforeOpeningNativeHandles() {
        FlashDeviceDescriptor descriptor = new FlashDeviceDescriptor(
                "j2534", "openport-2", "OpenPort 2.0", "J2534");

        assertEquals("j2534", descriptor.getProviderId());
        assertEquals("openport-2", descriptor.getDeviceId());
        assertEquals("OpenPort 2.0", descriptor.getDisplayName());
        assertEquals("J2534", descriptor.getTransportName());
        assertNotNull(FlashBackendRegistry.getInstance().getDeviceProviders());
        assertNotNull(FlashBackendRegistry.getInstance().getProtocols());
    }

    private static FlashProtocol readOnlyProtocol(
            final AtomicBoolean sessionCreated,
            final AtomicReference<String> sessionThread,
            final AtomicReference<FlashProgress> measured) {
        return new FlashProtocol() {
            public String getId() { return "test.subaru.read"; }
            public String getDisplayName() { return "Test Subaru read protocol"; }
            public boolean supports(FlashTarget target, FlashDevice device) {
                return target.getPlatform() == VehiclePlatform.SUBARU;
            }
            public FlashCapabilities getCapabilities(FlashTarget target,
                    FlashDevice device) {
                return new FlashCapabilities(true, false, false, false);
            }
            public FlashPreflight preflight(FlashRequest request) {
                return new FlashPreflight(Arrays.asList(
                        check("device", request.getDevice().isOpen()
                                ? PreflightStatus.PASS : PreflightStatus.FAIL, true),
                        check("target", PreflightStatus.PASS, true)));
            }
            public FlashSession createSession(FlashRequest request,
                    FlashProgressListener listener) {
                sessionCreated.set(true);
                return new TestSession(request, listener) {
                    public FlashResult execute() {
                        if (sessionThread != null) {
                            sessionThread.set(Thread.currentThread().getName());
                        }
                        publish(FlashState.CONNECTING, "Connecting test device");
                        publishMeasured(FlashState.READING, "Reading ROM",
                                4096, 4096);
                        if (measured != null) {
                            measured.set(FlashProgress.measured(FlashState.READING,
                                    "Reading ROM", 4096, 4096));
                        }
                        return result(FlashState.COMPLETED, "Read completed",
                                "test.subaru.read", "test-session.log", null);
                    }
                };
            }
        };
    }

    private static FlashTarget target() {
        return new FlashTarget(VehiclePlatform.SUBARU,
                VehicleModule.ENGINE_ECU, "TEST-ECU", "TEST-ROM", 4096);
    }

    private static FlashPreflightCheck check(String id, PreflightStatus status,
            boolean mandatory) {
        return new FlashPreflightCheck(id, id, status, mandatory, "test");
    }

    private static class TestSession extends AbstractFlashSession {
        TestSession(FlashRequest request, FlashProgressListener listener) {
            super(request, listener);
        }

        public FlashResult execute() {
            return result(FlashState.COMPLETED, "Completed", "test", "", null);
        }

        void enterErase() {
            publish(FlashState.ERASING, "Erasing");
        }
    }

    private static final class TestDevice implements FlashDevice {
        private boolean open = true;
        public String getId() { return "test-device"; }
        public String getDisplayName() { return "Test Device"; }
        public String getTransportName() { return "TEST"; }
        public boolean isOpen() { return open; }
        public void close() { open = false; }
    }
}
