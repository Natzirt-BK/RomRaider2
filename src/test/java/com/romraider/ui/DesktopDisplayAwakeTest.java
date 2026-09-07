/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

import static org.junit.Assert.*;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;

public class DesktopDisplayAwakeTest {
    @Test public void lifecycleIsIdempotentAndUsesOneNonUiThread() throws Exception {
        List<Thread> calls = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger acquired = new AtomicInteger(), released = new AtomicInteger();
        DesktopDisplayAwake awake = new DesktopDisplayAwake(() -> {
            calls.add(Thread.currentThread()); acquired.incrementAndGet();
            return () -> { calls.add(Thread.currentThread()); released.incrementAndGet(); };
        });
        try {
            awake.setActive(false); assertEquals(0, acquired.get());
            awake.setActive(true); await(() -> awake.getStatus() == DesktopDisplayAwake.Status.ACTIVE);
            awake.setActive(true); assertEquals(1, acquired.get());
            awake.setActive(false); await(() -> awake.getStatus() == DesktopDisplayAwake.Status.OFF);
            assertEquals(1, released.get());
            awake.setActive(true); await(() -> awake.getStatus() == DesktopDisplayAwake.Status.ACTIVE);
        } finally { awake.close(); }
        await(() -> awake.getStatus() == DesktopDisplayAwake.Status.OFF);
        awake.close(); awake.setActive(true);
        assertEquals(2, acquired.get()); assertEquals(2, released.get());
        assertTrue(calls.stream().allMatch(thread -> thread == calls.get(0)));
        assertNotSame(Thread.currentThread(), calls.get(0));
    }

    @Test public void lateAcquisitionAfterCloseIsReleasedWithoutPublishingActive() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicInteger released = new AtomicInteger();
        DesktopDisplayAwake awake = new DesktopDisplayAwake(() -> {
            entered.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS));
            return () -> released.incrementAndGet();
        });
        try {
            awake.setActive(true); assertTrue(entered.await(2, TimeUnit.SECONDS));
            awake.setActive(false); awake.close();
            assertEquals(DesktopDisplayAwake.Status.RELEASING, awake.getStatus());
        } finally { finish.countDown(); awake.close(); }
        await(() -> awake.getStatus() == DesktopDisplayAwake.Status.OFF);
        assertEquals(1, released.get());
    }

    @Test public void rapidFocusCycleReleasesTheStaleLeaseBeforeAcquiringAgain() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicInteger acquired = new AtomicInteger(), released = new AtomicInteger();
        DesktopDisplayAwake awake = new DesktopDisplayAwake(() -> {
            if (acquired.incrementAndGet() == 1) {
                entered.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS));
            } else assertEquals(1, released.get());
            return () -> released.incrementAndGet();
        });
        try {
            awake.setActive(true); assertTrue(entered.await(2, TimeUnit.SECONDS));
            awake.setActive(false); awake.setActive(true); finish.countDown();
            await(() -> awake.getStatus() == DesktopDisplayAwake.Status.ACTIVE);
            assertEquals(2, acquired.get()); assertEquals(1, released.get());
        } finally { finish.countDown(); awake.close(); }
    }

    @Test public void missingProviderIsHonestAndRetriesOnlyOnNewActivation() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        DesktopDisplayAwake awake = new DesktopDisplayAwake(() -> {
            attempts.incrementAndGet(); throw new UnsatisfiedLinkError("synthetic missing library");
        });
        try {
            awake.setActive(true); await(() -> awake.getStatus() == DesktopDisplayAwake.Status.UNAVAILABLE);
            awake.setActive(true); assertEquals(1, attempts.get());
            assertTrue(awake.getFailure().contains("synthetic missing library"));
            awake.setActive(false); await(() -> awake.getStatus() == DesktopDisplayAwake.Status.OFF);
            awake.setActive(true); await(() -> attempts.get() == 2);
        } finally { awake.close(); }
    }

    @Test public void failedReleaseRemainsOwnedAndIsRetriedEvenAfterClose() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        DesktopDisplayAwake awake = new DesktopDisplayAwake(() -> () -> {
            if (releases.incrementAndGet() == 1) throw new IllegalStateException("temporary release failure");
        });
        awake.setActive(true); await(() -> awake.getStatus() == DesktopDisplayAwake.Status.ACTIVE);
        awake.close();
        await(() -> awake.getStatus() == DesktopDisplayAwake.Status.UNAVAILABLE);
        await(() -> awake.getStatus() == DesktopDisplayAwake.Status.OFF);
        assertEquals(2, releases.get());
    }

    @Test public void lostServiceCannotContinueToReportActive() throws Exception {
        AtomicInteger released = new AtomicInteger();
        DesktopDisplayAwake awake = new DesktopDisplayAwake(() -> new DesktopDisplayAwake.Lease() {
            @Override public boolean isValid() { return false; }
            @Override public void close() { released.incrementAndGet(); }
        });
        try {
            awake.setActive(true); await(() -> awake.getStatus() == DesktopDisplayAwake.Status.ACTIVE);
            await(() -> awake.getStatus() == DesktopDisplayAwake.Status.UNAVAILABLE && released.get() == 1);
        } finally { awake.close(); }
    }

    @Test public void failedAcquisitionRetainsItsIncompleteNativeCleanup() throws Exception {
        AtomicInteger attempts = new AtomicInteger(), releases = new AtomicInteger();
        DesktopDisplayAwake awake = new DesktopDisplayAwake(() -> {
            attempts.incrementAndGet();
            throw new DesktopDisplayAwake.AcquisitionFailure(new IllegalStateException("synthetic acquire failure"), () -> {
                if (releases.incrementAndGet() == 1) throw new IllegalStateException("synthetic close failure");
            });
        });
        try {
            awake.setActive(true);
            await(() -> releases.get() == 2 && awake.getStatus() == DesktopDisplayAwake.Status.UNAVAILABLE);
            assertEquals(1, attempts.get());
        } finally { awake.close(); }
        await(() -> awake.getStatus() == DesktopDisplayAwake.Status.OFF);
    }

    @Test public void windowsFlagsRestorePreviousStateAndRejectFailure() throws Exception {
        List<Integer> flags = new ArrayList<>();
        DesktopDisplayAwake.Lease lease = DesktopDisplayAwakeNative.windows(value -> {
            flags.add(value); return 0x80000000;
        });
        lease.close(); lease.close();
        assertEquals(java.util.Arrays.asList(0x80000003, 0x80000000), flags);
        try { DesktopDisplayAwakeNative.windows(value -> 0); fail(); } catch (IllegalStateException expected) { }
        AtomicInteger attempts = new AtomicInteger();
        lease = DesktopDisplayAwakeNative.windows(value -> attempts.incrementAndGet() == 2 ? 0 : 0x80000000);
        try { lease.close(); fail(); } catch (IllegalStateException expected) { }
        lease.close(); assertEquals(3, attempts.get());
    }

    @Test public void macOwnsAndReleasesTheAssertionAndTemporaryStrings() throws Exception {
        List<String> strings = new ArrayList<>();
        List<Pointer> freed = new ArrayList<>();
        AtomicInteger released = new AtomicInteger();
        DesktopDisplayAwakeNative.CoreFoundation cf = new DesktopDisplayAwakeNative.CoreFoundation() {
            public Pointer CFStringCreateWithCString(Pointer allocator, String text, int encoding) {
                assertNull(allocator); assertEquals(0x08000100, encoding);
                strings.add(text); return Pointer.createConstant(strings.size());
            }
            public void CFRelease(Pointer value) { freed.add(value); }
        };
        DesktopDisplayAwakeNative.IOKit io = new DesktopDisplayAwakeNative.IOKit() {
            public int IOPMAssertionCreateWithName(Pointer type, int level, Pointer name, IntByReference id) {
                assertEquals(255, level); assertEquals(Pointer.createConstant(1), type);
                assertEquals(Pointer.createConstant(2), name); id.setValue(123); return 0;
            }
            public int IOPMAssertionRelease(int id) { assertEquals(123, id); released.incrementAndGet(); return 0; }
        };
        DesktopDisplayAwake.Lease lease = DesktopDisplayAwakeNative.mac(cf, io);
        assertEquals("PreventUserIdleDisplaySleep", strings.get(0));
        assertEquals(2, freed.size()); assertEquals(0, released.get());
        lease.close(); lease.close(); assertEquals(1, released.get());
    }

    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (!condition.getAsBoolean()) {
            assertTrue("Timed out waiting for display-awake lifecycle", System.nanoTime() < deadline);
            Thread.sleep(10);
        }
    }
}
