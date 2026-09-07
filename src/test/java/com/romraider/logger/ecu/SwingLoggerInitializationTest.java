/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu;

import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.InitializationAttempt;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.platform.DimeModState;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.RamTuneRuntimeMetadata;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Production callback owner with controlled EDT delivery; no JFrame, controller or device. */
public class SwingLoggerInitializationTest {
    @Test(timeout = 10000)
    public void nestedReloadCancelsRemainingStagesForNewIdentityMetadataOwnerOrReload() throws Exception {
        for (int operation = 0; operation < 4; operation++) {
            Fixture f = initialized();
            SwingLoggerInitialization.Reload reload = f.owner.beginReload(f.owner.snapshot());
            List<String> stages = new ArrayList<>();
            final int change = operation;
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(reload.run(() -> {
                    stages.add("definition dialog");
                    java.awt.SecondaryLoop loop = java.awt.Toolkit.getDefaultToolkit()
                            .getSystemEventQueue().createSecondaryLoop();
                    AtomicReference<Throwable> nestedFailure = new AtomicReference<>();
                    SwingUtilities.invokeLater(() -> {
                        try {
                            if (change == 0) f.owner.ecuCallback().callback(ecu("2222222222"));
                            else if (change == 1) f.owner.dimeCallback().callback(dime(), true);
                            else if (change == 2) f.owner.close();
                            else {
                                SwingLoggerInitialization.Reload replacement = f.owner.beginReload(f.owner.snapshot());
                                assertTrue(replacement.run(() -> stages.add("new catalog")));
                            }
                        } catch (Throwable failure) { nestedFailure.set(failure); }
                        finally { loop.exit(); }
                    });
                    assertTrue(loop.enter());
                    if (nestedFailure.get() != null) throw new AssertionError(nestedFailure.get());
                }, () -> fail("Stale external rows loaded"),
                        () -> fail("Stale catalog published"),
                        () -> fail("Stale focus request applied")));
                assertEquals(change == 3 ? java.util.Arrays.asList("definition dialog", "new catalog")
                        : java.util.Collections.singletonList("definition dialog"), stages);
            });
        }
    }

    @Test
    public void reloadKeepsPairedInputsAndDoesNotModifyPreloadedParameters() throws Exception {
        Fixture f = initialized();
        SwingLoggerInitialization.Snapshot state = f.owner.snapshot();
        state.dime.updateRuntimeData(0, 0, new int[4], new int[4]);
        assertFalse(state.dime.getEcuParams().isEmpty());
        SwingLoggerInitialization.Reload reload = f.owner.beginReload(state);
        List<com.romraider.logger.ecu.definition.EcuParameter> original =
                java.util.Collections.singletonList(state.dime.getEcuParams().iterator().next());
        List<com.romraider.logger.ecu.definition.EcuParameter> prepared = reload.parameters(original);
        assertEquals(1, original.size());
        assertEquals(1 + state.dime.getEcuParams().size(), prepared.size());
        assertNotSame(original, prepared);
        assertEquals(prepared.size(), reload.parameters(original).size());
        f.owner.ecuCallback().callback(ecu("2222222222"));
        assertSame(state.ecu, reload.state.ecu);
        assertSame(state.dime, reload.state.dime);
        assertNull(f.owner.getDmInit());
        assertFalse(reload.isCurrent());
        SwingUtilities.invokeAndWait(() -> assertFalse(reload.run(() -> fail("Stale prepared catalog applied"))));
    }

    @Test
    public void reloadRunsCurrentStagesInOrderAndStaleBeginCannotCancelReplacement() throws Exception {
        Fixture f = initialized();
        SwingLoggerInitialization.Snapshot older = f.owner.snapshot();
        f.owner.ecuCallback().callback(ecu("2222222222"));
        SwingLoggerInitialization.Reload current = f.owner.beginReload(f.owner.snapshot());
        assertNull(f.owner.beginReload(older));
        SwingUtilities.invokeAndWait(() -> {
            List<String> stages = new ArrayList<>();
            assertTrue(current.run(() -> stages.add("definition"), () -> stages.add("external"),
                    () -> stages.add("catalog"), () -> stages.add("focus")));
            assertEquals(java.util.Arrays.asList("definition", "external", "catalog", "focus"), stages);
            assertTrue(f.owner.isOpen());
            f.owner.close();
            assertFalse(f.owner.isOpen());
            assertNull(f.owner.beginReload(f.owner.snapshot()));
            assertFalse(current.run(() -> fail("Closed owner published")));
        });
    }

    @Test
    public void lastReloadStageInvalidationDoesNotReportCompletion() throws Exception {
        Fixture f = initialized();
        SwingLoggerInitialization.Reload reload = f.owner.beginReload(f.owner.snapshot());
        SwingUtilities.invokeAndWait(() -> assertFalse(reload.run(f.owner::close)));
    }

    @Test
    public void reviewedProfileRejectsCatalogReplacementEvenWithUnchangedInitialization() throws Exception {
        Fixture f = initialized();
        SwingLoggerInitialization.Snapshot state = f.owner.snapshot();
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(f.owner.applyReviewedUpdate(state, () -> {
                assertNotNull(f.owner.beginReload(state));
                return true;
            }, () -> fail("Profile applied to replaced catalog")));
            assertSame(state, f.owner.snapshot());
            assertTrue(f.owner.applyReviewedUpdate(state, () -> true, () -> { }));
        });
    }

    @Test
    public void reviewedUpdateRejectsSupersededOrClosedStateBeforePrompting() throws Exception {
        Fixture f = initialized();
        SwingLoggerInitialization.Snapshot before = f.owner.snapshot();
        f.owner.ecuCallback().callback(ecu("2222222222"));
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(f.owner.applyReviewedUpdate(before,
                    () -> { fail("Stale review prompted"); return true; },
                    () -> fail("Stale profile applied")));
            SwingLoggerInitialization.Snapshot current = f.owner.snapshot();
            f.owner.close();
            assertFalse(f.owner.applyReviewedUpdate(current,
                    () -> { fail("Closed owner prompted"); return true; },
                    () -> fail("Closed owner applied profile")));
        });
    }

    @Test(timeout = 10000)
    public void nestedReviewCannotApplyAfterIdentityMetadataChangeOrClosure() throws Exception {
        for (int operation = 0; operation < 3; operation++) {
            Fixture f = initialized();
            SwingLoggerInitialization.Snapshot before = f.owner.snapshot();
            final int change = operation;
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(f.owner.applyReviewedUpdate(before, () -> {
                    // Exercise the nested event dispatch used by a modal dialog,
                    // without constructing a logger, adapter or visible dialog.
                    java.awt.SecondaryLoop loop = java.awt.Toolkit.getDefaultToolkit()
                            .getSystemEventQueue().createSecondaryLoop();
                    SwingUtilities.invokeLater(() -> {
                        try {
                            if (change == 0) f.owner.ecuCallback().callback(ecu("2222222222"));
                            else if (change == 1) f.owner.dimeCallback().callback(dime(), true);
                            else f.owner.close();
                        } finally { loop.exit(); }
                    });
                    assertTrue(loop.enter());
                    return true; // User chose Load, but its owner changed while reviewing.
                }, () -> fail("Obsolete profile applied after nested review")));
            });
        }
    }

    @Test
    public void reviewedUpdatePreservesApprovalCancellationAndSameIdCache() throws Exception {
        Fixture f = initialized();
        SwingUtilities.invokeAndWait(() -> {
            SwingLoggerInitialization.Snapshot current = f.owner.snapshot();
            List<String> applications = new ArrayList<>();
            assertTrue(f.owner.applyReviewedUpdate(current, () -> {
                f.owner.ecuCallback().callback(ecu(current.ecu.getEcuId()));
                return true;
            }, () -> applications.add("accepted")));
            assertEquals(java.util.Collections.singletonList("accepted"), applications);
            assertFalse(f.owner.applyReviewedUpdate(current, () -> false,
                    () -> fail("Cancelled profile applied")));
            assertSame(current, f.owner.snapshot());
            assertSame(current.dime, f.owner.dimeCallback().getDmInit());
        });
    }

    @Test(timeout = 10000)
    public void expiredAttemptIsRecheckedAfterWaitingForTheOwnerLock() throws Exception {
        for (int operation = 0; operation < 3; operation++) {
            Fixture f = initialized();
            EcuInit original = f.owner.getEcuInit();
            DmInit metadata = f.owner.getDmInit();
            InitializationAttempt attempt = new InitializationAttempt();
            final int selected = operation;
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread callback = new Thread(() -> {
                try {
                    if (selected == 0) f.owner.ecuCallback().callback(ecu("2222222222"), attempt);
                    else if (selected == 1) f.owner.dimeCallback().callback(null, true, attempt);
                    else {
                        try { f.owner.dimeCallback().getDmInit(attempt); fail("Expired cache lookup succeeded"); }
                        catch (IllegalStateException expected) { }
                    }
                } catch (Throwable error) { failure.set(error); }
            }, "synthetic callback waiting for Swing owner");
            callback.setDaemon(true);
            synchronized (f.owner) {
                callback.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (callback.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield();
                assertEquals(Thread.State.BLOCKED, callback.getState());
                attempt.close();
            }
            callback.join(2000);
            assertFalse(callback.isAlive());
            assertNull(failure.get());
            assertSame(original, f.owner.getEcuInit());
            assertSame(metadata, f.owner.getDmInit());
            assertTrue(f.pending.isEmpty());
        }
    }

    @Test
    public void activeAttemptDeliversStateAndReusesTheExistingCache() throws Exception {
        Fixture f = new Fixture();
        try (InitializationAttempt attempt = new InitializationAttempt()) {
            EcuInit identity = ecu("1111111111");
            DmInit metadata = dime();
            attempt.bind(f.owner.ecuCallback()).callback(identity);
            attempt.bind(f.owner.dimeCallback()).callback(metadata, false);
            assertSame(identity, f.owner.getEcuInit());
            assertSame(metadata, attempt.bind(f.owner.dimeCallback()).getDmInit());
            assertFalse(attempt.bind(f.owner.dimeCallback()).needToInit());
        }
        f.drain(); // Accepted state remains renderable after the transport attempt ends.
        assertEquals(1, f.published.size());
    }

    @Test
    public void dimeCacheIsVisibleBeforeUiDeliveryAndClearsImmediatelyForNewEcu() throws Exception {
        Fixture f = new Fixture();
        EcuInit first = ecu("1111111111");
        DmInit dime = dime();
        f.owner.ecuCallback().callback(first);
        f.owner.dimeCallback().callback(dime, false);
        assertSame(first, f.owner.getEcuInit());
        assertSame(dime, f.owner.getDmInit());
        assertSame(dime, f.owner.dimeCallback().getDmInit());
        assertFalse(f.owner.dimeCallback().needToInit());
        assertTrue(f.published.isEmpty());
        EcuInit second = ecu("2222222222");
        f.owner.ecuCallback().callback(second);
        assertSame(second, f.owner.getEcuInit());
        assertNull(f.owner.getDmInit());
        assertSame(second, f.sharedState.get().ecu);
        assertNull(f.sharedState.get().dime);
        assertFalse(f.sharedState.get().dimeKnown);
        f.drain();
        assertEquals(1, f.published.size());
        assertSame(second, f.published.get(0).ecu);
        assertNull(f.published.get(0).dime);
        assertFalse(f.published.get(0).dimeKnown);
        assertEquals("2222222222/unknown", f.label.getText());
    }

    @Test
    public void coalescedEcuAndDimeUpdatesKeepBothIdentityAndCurrentMetadata() throws Exception {
        Fixture f = new Fixture();
        EcuInit ecu = ecu("1111111111");
        DmInit dime = dime();
        f.owner.ecuCallback().callback(ecu);
        f.owner.dimeCallback().callback(dime, false);
        f.drain();
        assertEquals(1, f.published.size());
        assertSame(ecu, f.published.get(0).ecu);
        assertSame(dime, f.published.get(0).dime);
        assertEquals("1111111111/2.3 build 100", f.label.getText());
    }

    @Test
    public void sameEcuIdRetainsCacheWithoutExtraNotificationsOrDiscoveryDemand() throws Exception {
        Fixture f = initialized();
        EcuInit first = f.owner.getEcuInit();
        DmInit dime = f.owner.getDmInit();
        f.owner.ecuCallback().callback(ecu(first.getEcuId()));
        assertSame(first, f.owner.getEcuInit());
        assertSame(dime, f.owner.getDmInit());
        assertFalse(f.owner.dimeCallback().needToInit());
        assertTrue(f.pending.isEmpty());
    }

    @Test
    public void supersededDimeNotificationsCannotReplaceLatestResultEvenOutOfOrder() throws Exception {
        Fixture f = initialized();
        DmInit older = dime();
        DmInit newer = dime();
        f.owner.dimeCallback().callback(older, false);
        f.owner.dimeCallback().callback(newer, false);
        Runnable oldNotification = f.pending.remove(0);
        f.drain();
        SwingUtilities.invokeAndWait(oldNotification);
        assertSame(newer, f.owner.getDmInit());
        assertEquals(2, f.published.size());
        assertSame(newer, f.published.get(1).dime);
    }

    @Test
    public void unchangedDimeAvoidsReloadButForcedChangesAdvanceChannelRevision() throws Exception {
        Fixture f = initialized();
        DmInit dime = f.owner.getDmInit();
        long previous = f.published.get(0).channelRevision;
        f.owner.dimeCallback().callback(dime, false);
        assertTrue(f.pending.isEmpty());
        f.owner.dimeCallback().callback(dime, true);
        f.drain();
        assertEquals(2, f.published.size());
        assertEquals(previous + 1, f.published.get(1).channelRevision);
    }

    @Test
    public void absentDimePublishesKnownStateWithoutUnnecessaryChannelReload() throws Exception {
        Fixture f = new Fixture();
        f.owner.ecuCallback().callback(ecu("1111111111"));
        f.drain();
        long revision = f.published.get(0).channelRevision;
        f.owner.dimeCallback().callback(null, false);
        f.drain();
        assertTrue(f.published.get(1).dimeKnown);
        assertNull(f.published.get(1).dime);
        assertEquals(revision, f.published.get(1).channelRevision);
        f.owner.dimeCallback().callback(null, true);
        assertTrue(f.pending.isEmpty());
    }

    @Test
    public void closureRejectsQueuedAndLateUpdatesWithoutChangingRetainedState() throws Exception {
        Fixture f = initialized();
        DmInit retained = dime();
        f.owner.dimeCallback().callback(retained, true);
        EcuInit identity = f.owner.getEcuInit();
        SwingUtilities.invokeAndWait(f.owner::close);
        f.owner.ecuCallback().callback(ecu("2222222222"));
        f.owner.dimeCallback().callback(dime(), true);
        f.owner.dimeCallback().callback(null, false);
        f.drain();
        assertSame(identity, f.owner.getEcuInit());
        assertSame(retained, f.owner.getDmInit());
        assertEquals(1, f.published.size());
    }

    @Test
    public void closedOwnerCannotPublishOverReopenedOwner() throws Exception {
        AtomicReference<SwingLoggerInitialization.Snapshot> sharedDisplay = new AtomicReference<>();
        Fixture old = new Fixture(sharedDisplay);
        old.owner.ecuCallback().callback(ecu("1111111111"));
        old.owner.dimeCallback().callback(dime(), false);
        SwingUtilities.invokeAndWait(old.owner::close);
        Fixture reopened = new Fixture(sharedDisplay);
        reopened.owner.ecuCallback().callback(ecu("2222222222"));
        reopened.owner.dimeCallback().callback(dime(), false);
        reopened.drain();
        SwingLoggerInitialization.Snapshot current = sharedDisplay.get();
        old.drain();
        old.owner.ecuCallback().callback(ecu("3333333333"));
        old.owner.dimeCallback().callback(dime(), true);
        old.drain();
        assertSame(current, sharedDisplay.get());
        assertTrue(old.published.isEmpty());
    }

    @Test
    public void closedCacheLookupFailsRatherThanRequestingDiscovery() throws Exception {
        for (Fixture f : new Fixture[] {new Fixture(), initialized()}) {
            SwingUtilities.invokeAndWait(f.owner::close);
            try { f.owner.dimeCallback().getDmInit(); fail("Closed cache lookup succeeded"); }
            catch (IllegalStateException expected) { }
            try { f.owner.dimeCallback().needToInit(); fail("Closed owner answered discovery demand"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test
    public void productionCapabilityPublisherInvalidatesImmediatelyAndRejectsClosedOwner() throws Exception {
        PlatformContext context = PlatformContext.getInstance();
        DimeModState originalState = context.getDimeModState();
        boolean originalAvailable = context.isRamTuneRuntimeAvailable();
        RamTuneRuntimeMetadata originalMetadata = context.getRamTuneRuntimeMetadata().orElse(null);
        List<Runnable> pending = new ArrayList<>();
        SwingLoggerInitialization owner = new SwingLoggerInitialization(pending::add,
                EcuLogger::publishInitialization, state -> { });
        try {
            context.setDimeModRuntime(DimeModState.ACTIVE, true);
            owner.ecuCallback().callback(ecu("1111111111"));
            assertEquals(DimeModState.UNKNOWN, context.getDimeModState());
            assertFalse(context.isRamTuneRuntimeAvailable());
            assertFalse(context.getRamTuneRuntimeMetadata().isPresent());
            owner.dimeCallback().callback(dime(), false);
            assertEquals(DimeModState.ACTIVE, context.getDimeModState());
            owner.ecuCallback().callback(ecu("2222222222"));
            assertEquals(DimeModState.UNKNOWN, context.getDimeModState());
            SwingUtilities.invokeAndWait(owner::close);
            context.setDimeModRuntime(DimeModState.NOT_PRESENT, false);
            owner.ecuCallback().callback(ecu("3333333333"));
            owner.dimeCallback().callback(dime(), true);
            SwingUtilities.invokeAndWait(() -> pending.forEach(Runnable::run));
            assertEquals(DimeModState.NOT_PRESENT, context.getDimeModState());
        } finally {
            SwingUtilities.invokeAndWait(owner::close);
            context.setDimeModRuntime(originalState, originalAvailable, originalMetadata);
        }
    }

    @Test
    public void closureIsIdempotentOnEdt() throws Exception {
        Fixture f = new Fixture();
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(f.owner.close());
            assertFalse(f.owner.close());
        });
    }

    @Test
    public void nullIdentityAndUnboundDimeDoNotPublish() throws Exception {
        Fixture f = new Fixture();
        f.owner.ecuCallback().callback(null);
        f.owner.dimeCallback().callback(dime(), true);
        assertNull(f.owner.getEcuInit());
        assertNull(f.owner.getDmInit());
        assertTrue(f.pending.isEmpty());
    }

    @Test(timeout = 5000)
    public void realEdtQueueDropsUpdatesSupersededWhileDeliveryWasBlocked() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<SwingLoggerInitialization.Snapshot> displayed = new ArrayList<>();
        AtomicReference<SwingLoggerInitialization.Snapshot> capabilities = new AtomicReference<>();
        SwingLoggerInitialization owner = new SwingLoggerInitialization(
                SwingUtilities::invokeLater, capabilities::set, displayed::add);
        SwingUtilities.invokeLater(() -> {
            entered.countDown();
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            owner.ecuCallback().callback(ecu("1111111111"));
            owner.dimeCallback().callback(dime(), false);
            owner.ecuCallback().callback(ecu("2222222222"));
            assertNull(owner.getDmInit());
            assertEquals("2222222222", capabilities.get().ecu.getEcuId());
            assertFalse(capabilities.get().dimeKnown);
            assertNull(capabilities.get().dime);
        } finally { release.countDown(); }
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, displayed.size());
        assertEquals("2222222222", displayed.get(0).ecu.getEcuId());
        assertNull(displayed.get(0).dime);
    }

    private static Fixture initialized() throws Exception {
        Fixture f = new Fixture();
        f.owner.ecuCallback().callback(ecu("1111111111"));
        f.owner.dimeCallback().callback(dime(), false);
        f.drain();
        return f;
    }

    private static EcuInit ecu(String id) {
        return new EcuInit() {
            public String getEcuId() { return id; }
            public byte[] getEcuInitBytes() { return new byte[128]; }
        };
    }

    private static DmInit dime() {
        ByteBuffer bytes = ByteBuffer.allocate(112);
        bytes.put((byte) 2).put((byte) 3).putShort((short) 100)
                .putInt(0x20000).putInt(0).putInt(0xDEAD0001);
        for (int i = 0; i < 24; i++) bytes.putInt(0x2000 + i * 0x10);
        return new DmInit(bytes.array());
    }

    private static final class Fixture {
        final List<Runnable> pending = new ArrayList<>();
        final List<SwingLoggerInitialization.Snapshot> published = new ArrayList<>();
        final JLabel label = new JLabel();
        final AtomicReference<SwingLoggerInitialization.Snapshot> sharedState;
        final SwingLoggerInitialization owner;
        Fixture() { this(new AtomicReference<>()); }
        Fixture(AtomicReference<SwingLoggerInitialization.Snapshot> sharedDisplay) {
            sharedState = sharedDisplay;
            owner = new SwingLoggerInitialization(pending::add, sharedState::set, state -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                published.add(state);
                label.setText(state.ecu.getEcuId() + "/" + (!state.dimeKnown ? "unknown"
                        : state.dime == null ? "absent" : state.dime.getDimeModVersion()));
            });
        }
        void drain() throws Exception {
            List<Runnable> queued = new ArrayList<>(pending);
            pending.clear();
            SwingUtilities.invokeAndWait(() -> queued.forEach(Runnable::run));
        }
    }
}
