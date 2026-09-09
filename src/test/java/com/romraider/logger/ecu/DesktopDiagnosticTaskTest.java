/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class DesktopDiagnosticTaskTest {
    @Test public void slowReadDoesNotBlockEdtAndCompletionReturnsToEdt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicReference<DesktopDiagnosticTask.Completion<String>> result = new AtomicReference<>();
        AtomicBoolean deliveredOnEdt = new AtomicBoolean();
        DesktopDiagnosticTask<String> task = new DesktopDiagnosticTask<>(() -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            entered.countDown(); release.await(); return "complete";
        }, () -> true, value -> { deliveredOnEdt.set(SwingUtilities.isEventDispatchThread()); result.set(value); done.countDown(); });
        try {
            SwingUtilities.invokeAndWait(task::start);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            CountDownLatch heartbeat = new CountDownLatch(1);
            SwingUtilities.invokeLater(heartbeat::countDown);
            assertTrue(heartbeat.await(2, TimeUnit.SECONDS));
            assertNull(result.get());
        } finally { release.countDown(); }
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue(deliveredOnEdt.get());
        assertTrue(result.get().succeeded());
        assertEquals("complete", result.get().result());
    }

    @Test public void cancellationDoesNotReleaseAnUninterruptibleOperationEarly() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
        AtomicReference<DesktopDiagnosticTask.Completion<String>> result = new AtomicReference<>();
        DesktopDiagnosticTask<String> task = new DesktopDiagnosticTask<>(() -> {
            entered.countDown(); awaitIgnoringInterrupts(release); return "late";
        }, () -> true, result::set, ui::add);
        try {
            task.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
            task.cancel(); task.cancel();
            assertNull(ui.poll(100, TimeUnit.MILLISECONDS));
        } finally { release.countDown(); }
        runDelivery(ui);
        assertTrue(result.get().cancelled());
        assertFalse(result.get().succeeded());
        assertNull(result.get().result());
    }

    @Test public void cancellationAfterWorkBeforeDeliverySuppressesResults() throws Exception {
        BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
        AtomicReference<DesktopDiagnosticTask.Completion<String>> result = new AtomicReference<>();
        DesktopDiagnosticTask<String> task = new DesktopDiagnosticTask<>(() -> "late", () -> true, result::set, ui::add);
        task.start();
        Runnable delivery = ui.poll(2, TimeUnit.SECONDS); assertNotNull(delivery);
        task.cancel(); delivery.run();
        assertTrue(result.get().cancelled()); assertNull(result.get().result());
    }

    @Test public void closedOwnerOrFailedIdentityLookupSuppressesQueuedDelivery() throws Exception {
        for (boolean throwsFailure : new boolean[] {false, true}) {
            AtomicBoolean open = new AtomicBoolean(true);
            BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
            AtomicReference<DesktopDiagnosticTask.Completion<String>> result = new AtomicReference<>();
            DesktopDiagnosticTask<String> task = new DesktopDiagnosticTask<>(() -> "late", () -> {
                if (!open.get() && throwsFailure) throw new IllegalStateException("closed");
                return open.get();
            }, result::set, ui::add);
            task.start();
            Runnable delivery = ui.poll(2, TimeUnit.SECONDS); assertNotNull(delivery);
            open.set(false); delivery.run();
            assertTrue(result.get().stale()); assertNull(result.get().result());
        }
    }

    @Test public void preCancelledOrClosedTaskDoesNotStartWork() throws Exception {
        for (boolean cancel : new boolean[] {false, true}) {
            BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
            AtomicReference<DesktopDiagnosticTask.Completion<String>> result = new AtomicReference<>();
            DesktopDiagnosticTask<String> task = new DesktopDiagnosticTask<>(() -> { throw new AssertionError("work"); },
                    () -> cancel, result::set, ui::add);
            if (cancel) task.cancel();
            task.start(); runDelivery(ui);
            assertFalse(result.get().succeeded()); assertNull(result.get().failure());
        }
    }

    @Test public void failureAndInterruptionRemainDistinctFromSuccess() throws Exception {
        for (Exception failure : new Exception[] {new IllegalArgumentException("fixture"), new InterruptedException("cancel")}) {
            BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
            AtomicReference<DesktopDiagnosticTask.Completion<String>> result = new AtomicReference<>();
            DesktopDiagnosticTask<String> task = new DesktopDiagnosticTask<>(() -> { throw failure; }, () -> true, result::set, ui::add);
            task.start(); runDelivery(ui);
            assertSame(failure, result.get().failure());
            assertEquals(failure instanceof InterruptedException, result.get().cancelled());
            assertFalse(result.get().succeeded());
            try { task.start(); fail("restarted"); } catch (IllegalStateException expected) { }
        }
    }

    private static void runDelivery(BlockingQueue<Runnable> ui) throws Exception {
        Runnable delivery = ui.poll(2, TimeUnit.SECONDS); assertNotNull(delivery); delivery.run();
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try { if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("release timeout"); break; }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
