package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RR2_FX_WINDOW_SMOKE", matches = "1")
class FxSerialPortSelectorTest {
    @Test void discoversWithoutSelectingAndPreservesSelectionAndManualEdits() throws Exception {
        var source = new AtomicReference<>(List.of(new FxSerialPortSelector.Port("COM4", "OBDLink"),
                new FxSerialPortSelector.Port("COM3", "USB serial"), new FxSerialPortSelector.Port("COM4", "Duplicate")));
        FxSerialPortSelector[] selector = new FxSerialPortSelector[1];
        try {
            FxTestRuntime.run(() -> {
                selector[0] = new FxSerialPortSelector(() -> { assertFalse(Platform.isFxApplicationThread()); return source.get(); });
                new javafx.scene.Scene(selector[0]); selector[0].applyCss();
                assertTrue(selector[0].ports.getItems().isEmpty());
                selector[0].scan();
            });
            await(() -> !selector[0].refresh.isDisabled());
            FxTestRuntime.run(() -> {
                assertEquals(List.of("COM3", "COM4"), selector[0].ports.getItems());
                assertEquals("", selector[0].address());
                assertNull(selector[0].ports.getValue());
                selector[0].ports.setValue("COM4");
                assertEquals("COM4", selector[0].address());
                selector[0].refresh.fire();
            });
            await(() -> !selector[0].refresh.isDisabled());
            FxTestRuntime.run(() -> {
                assertEquals("COM4", selector[0].address());
                selector[0].ports.getEditor().setText("/dev/serial/by-id/custom-adapter");
                source.set(List.of());
                selector[0].refresh.fire();
            });
            await(() -> !selector[0].refresh.isDisabled());
            FxTestRuntime.run(() -> {
                assertEquals("/dev/serial/by-id/custom-adapter", selector[0].address());
                assertTrue(selector[0].ports.getItems().isEmpty());
                assertTrue(selector[0].status.getText().contains("No serial ports"));
            });
        } finally { FxTestRuntime.run(() -> { if (selector[0] != null) selector[0].close(); }); }
    }

    @Test void scanIsNonBlockingSingleFlightAndKeepsEditsMadeDuringEnumeration() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        FxSerialPortSelector[] selector = new FxSerialPortSelector[1];
        try {
            FxTestRuntime.run(() -> {
                selector[0] = new FxSerialPortSelector(() -> {
                    calls.incrementAndGet(); entered.countDown(); waitFor(release);
                    return List.of(new FxSerialPortSelector.Port("/dev/ttyUSB0", "ELM327"));
                });
                selector[0].scan(); selector[0].scan();
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            FxTestRuntime.run(() -> {
                assertEquals(1, calls.get()); assertTrue(selector[0].refresh.isDisabled());
                assertFalse(selector[0].ports.isDisabled());
                selector[0].ports.getEditor().setText("/dev/ttyACM0");
            });
            release.countDown(); await(() -> !selector[0].refresh.isDisabled());
            FxTestRuntime.run(() -> assertEquals("/dev/ttyACM0", selector[0].address()));
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (selector[0] != null) selector[0].close(); }); }
    }

    @Test void failedEnumerationKeepsManualEntryAndCanRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FxSerialPortSelector[] selector = new FxSerialPortSelector[1];
        try {
            FxTestRuntime.run(() -> {
                selector[0] = new FxSerialPortSelector(() -> {
                    if (calls.getAndIncrement() == 0) throw new UnsatisfiedLinkError("Synthetic unavailable driver");
                    return List.of(new FxSerialPortSelector.Port("/dev/cu.usbserial-test", "USB serial"));
                });
                selector[0].ports.getEditor().setText("manual"); selector[0].scan();
            });
            await(() -> !selector[0].refresh.isDisabled());
            FxTestRuntime.run(() -> {
                assertTrue(selector[0].status.getText().contains("Could not list"));
                assertEquals("manual", selector[0].address()); selector[0].refresh.fire();
            });
            await(() -> !selector[0].refresh.isDisabled());
            FxTestRuntime.run(() -> {
                assertEquals(List.of("/dev/cu.usbserial-test"), selector[0].ports.getItems());
                assertEquals("manual", selector[0].address());
            });
        } finally { FxTestRuntime.run(() -> { if (selector[0] != null) selector[0].close(); }); }
    }

    @Test void lateCancelledScanCannotReplaceNewResultsOrReviveClosedSelector() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        FxSerialPortSelector[] selector = new FxSerialPortSelector[1];
        try {
            FxTestRuntime.run(() -> {
                selector[0] = new FxSerialPortSelector(() -> {
                    if (calls.getAndIncrement() == 0) {
                        entered.countDown();
                        // Model a native driver that does not honor Java interruption.
                        while (release.getCount() != 0) try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                        exited.countDown();
                        return List.of(new FxSerialPortSelector.Port("old", "Late result"));
                    }
                    return List.of(new FxSerialPortSelector.Port("new", "Current result"));
                });
                selector[0].scan();
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            FxTestRuntime.run(() -> { selector[0].cancelScan(); selector[0].scan(); });
            await(() -> !selector[0].refresh.isDisabled());
            FxTestRuntime.run(() -> { assertEquals(List.of("new"), selector[0].ports.getItems()); selector[0].close(); });
            release.countDown(); assertTrue(exited.await(3, TimeUnit.SECONDS));
            FxTestRuntime.run(() -> {
                selector[0].scan(); assertEquals(2, calls.get());
                assertEquals(List.of("new"), selector[0].ports.getItems());
            });
        } finally { release.countDown(); FxTestRuntime.run(() -> { if (selector[0] != null) selector[0].close(); }); }
    }

    private static void waitFor(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Fixture timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private static void await(BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean[] complete = new boolean[1];
        do {
            FxTestRuntime.run(() -> complete[0] = done.getAsBoolean());
            if (complete[0]) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("Port enumeration did not settle");
    }
}
