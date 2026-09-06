/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.PortableLogSession;
import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.PortableLoggerValue;
import com.romraider.portable.logger.ReadOnlyLoggerTransport;
import com.romraider.portable.logger.definition.PortableLoggerDefinition;
import com.romraider.portable.logger.definition.PortableLoggerProfile;

import java.io.Closeable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Screen-independent ownership of one explicitly started read-only recording.
 * This is not an Android Service and does not grant permission to run in the background.
 * A host must establish its execution/USB authority before calling start().
 * Polling immutable snapshots never queues UI callbacks or retains a screen.
 */
public final class ReadOnlyRecording implements AutoCloseable {
    public enum Phase { NEW, PREPARING, CONNECTING, RECORDING, STOPPING, STOPPED }

    /**
     * Called once on the recording worker, never by snapshot() or stop().
     * The factory owns partial acquisitions until it successfully returns Resources.
     * It must clean up partial failures and use finite, cancellable device operations.
     */
    public interface ResourceFactory {
        Resources open(java.util.function.BooleanSupplier cancelled) throws Exception;
    }

    /** Exclusive ownership transfers to this recording when the factory returns. */
    public static final class Resources {
        private final ReadOnlyLoggerTransport transport;
        private final PortableLogSession log;
        private final Closeable releaseTransport;

        public Resources(ReadOnlyLoggerTransport transport, PortableLogSession log,
                Closeable releaseTransport) {
            this.transport = Objects.requireNonNull(transport, "transport");
            this.log = Objects.requireNonNull(log, "log");
            this.releaseTransport = Objects.requireNonNull(releaseTransport, "releaseTransport");
        }
    }

    /**
     * Values are only the latest completed cycle, not a second recording buffer.
     * receivedAtNanos is the original monotonic receipt time, NOT a view-attachment time;
     * callers must use the same clock and phase when deciding whether readings are live.
     */
    public static final class Snapshot {
        private final Phase phase;
        private final String ecuId;
        private final int ready;
        private final int unavailable;
        private final long timestampMillis;
        private final long receivedAtNanos;
        private final List<PortableLoggerValue> values;
        private final int samples;
        private final String message;

        private Snapshot(Phase phase, String ecuId, int ready, int unavailable,
                long timestampMillis, long receivedAtNanos, List<PortableLoggerValue> values,
                int samples, String message) {
            this.phase = phase;
            this.ecuId = ecuId;
            this.ready = ready;
            this.unavailable = unavailable;
            this.timestampMillis = timestampMillis;
            this.receivedAtNanos = receivedAtNanos;
            this.values = values;
            this.samples = samples;
            this.message = message;
        }

        public Phase phase() { return phase; }
        public boolean active() { return phase != Phase.NEW && phase != Phase.STOPPED; }
        public String ecuId() { return ecuId; }
        public int ready() { return ready; }
        public int unavailable() { return unavailable; }
        public long timestampMillis() { return timestampMillis; }
        public long receivedAtNanos() { return receivedAtNanos; }
        public List<PortableLoggerValue> values() { return values; }
        /** Recorded values, not CSV rows; one completed cycle may contain several values. */
        public int samples() { return samples; }
        public String message() { return message; }
    }

    private final Object lock = new Object();
    private final ResourceFactory factory;
    private final PortableLoggerDefinition definition;
    private final PortableLoggerProfile profile;
    private final Executor executor;
    private final LongSupplier nanoTime;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile boolean stopRequested;
    private volatile Snapshot snapshot = new Snapshot(Phase.NEW, "", 0, 0,
            0, 0, List.of(), 0, "Not started.");
    private ReadOnlyLoggerSession session;
    private PortableLogSession completedLog;

    public ReadOnlyRecording(ResourceFactory factory, PortableLoggerDefinition definition,
            PortableLoggerProfile profile) {
        this(factory, definition, profile, System::nanoTime);
    }

    /** Android hosts should supply SystemClock::elapsedRealtimeNanos to include deep sleep. */
    public ReadOnlyRecording(ResourceFactory factory, PortableLoggerDefinition definition,
            PortableLoggerProfile profile, LongSupplier nanoTime) {
        this(factory, definition, profile, command -> {
            Thread worker = new Thread(command, "rr2-read-only-recording");
            worker.setDaemon(true);
            worker.start();
        }, nanoTime);
    }

    // Deterministic scheduling/clock injection; production hosts use the owned worker above.
    ReadOnlyRecording(ResourceFactory factory, PortableLoggerDefinition definition,
            PortableLoggerProfile profile, Executor executor, LongSupplier nanoTime) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        PortableLoggerProtocol protocol = PortableLoggerProtocol.fromId(definition.getProtocol());
        if (!profile.getProtocol().isEmpty()
                && PortableLoggerProtocol.fromId(profile.getProtocol()) != protocol) {
            throw new IllegalArgumentException("Logger profile protocol does not match the definition");
        }
    }

    /** One-shot and explicit. A stopped/closed recording cannot restart or reconnect. */
    public void start() {
        synchronized (lock) {
            if (snapshot.phase != Phase.NEW) {
                throw new IllegalStateException("Recordings can only be started once");
            }
            changePhase(Phase.PREPARING, "Preparing read-only recording...");
        }
        try {
            executor.execute(this::run);
        } catch (RuntimeException failure) {
            finish(null, "Recording worker could not start: " + detail(failure));
        }
    }

    public Snapshot snapshot() { return snapshot; }

    /** Returns no writer while acquisition, reading, flushing or transport cleanup is pending. */
    public PortableLogSession completedLog() {
        synchronized (lock) { return completedLog; }
    }

    /** Cooperative and nonblocking, including during preparation and a device read. */
    public void stop() {
        synchronized (lock) {
            if (snapshot.phase == Phase.STOPPED) return;
            stopRequested = true;
            if (snapshot.phase == Phase.NEW) {
                changePhase(Phase.STOPPED, "Read-only logger stopped before starting.");
                stopped.countDown();
                return;
            }
            changePhase(Phase.STOPPING, "Stopping after the current operation...");
            if (session != null) session.stop();
        }
    }

    /** Closing never deletes the recording and does not block a UI/lifecycle thread. */
    @Override public void close() { stop(); }

    /** Worker/test helper only: never wait on an Android main/lifecycle thread. */
    public boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
        return stopped.await(timeout, unit);
    }

    private void run() {
        Resources resources = null;
        boolean sessionOwnsFinish = false;
        final String[] message = {"Read-only logger stopped."};
        try {
            if (stopRequested) return;
            resources = Objects.requireNonNull(factory.open(() -> stopRequested), "resources");
            ReadOnlyLoggerSession prepared = new ReadOnlyLoggerSession(resources.transport,
                    definition, profile, resources.log, new ReadOnlyLoggerSession.Listener() {
                @Override public void onIdentified(String ecuId, int ready, int unavailable) {
                    synchronized (lock) {
                        if (stopRequested) return;
                        snapshot = new Snapshot(Phase.CONNECTING, ecuId, ready, unavailable,
                                0, 0, List.of(), 0, "ECU identified; waiting for a complete cycle.");
                    }
                }

                @Override public void onValues(String ecuId, long timestamp,
                        List<PortableLoggerValue> values, int samples) {
                    synchronized (lock) {
                        if (stopRequested) return;
                        Snapshot previous = snapshot;
                        snapshot = new Snapshot(Phase.RECORDING, ecuId, previous.ready,
                                previous.unavailable, timestamp, nanoTime.getAsLong(),
                                List.copyOf(values), samples, "Read-only recording.");
                    }
                }

                @Override public void onStopped(String reason) {
                    message[0] = reason;
                    synchronized (lock) { changePhase(Phase.STOPPING, "Releasing the adapter..."); }
                }
            });
            synchronized (lock) {
                session = prepared;
                if (stopRequested) prepared.stop();
                else changePhase(Phase.CONNECTING, "Identifying the ECU read-only...");
            }
            sessionOwnsFinish = true;
            prepared.run();
        } catch (Exception failure) {
            message[0] = "Read-only recording failed: " + detail(failure);
        } finally {
            synchronized (lock) { changePhase(Phase.STOPPING, "Finishing read-only recording..."); }
            if (resources != null) {
                if (!sessionOwnsFinish) {
                    try { resources.log.finish(); }
                    catch (Exception failure) {
                        message[0] += " Log cleanup failed: " + detail(failure);
                    }
                }
                try { resources.releaseTransport.close(); }
                catch (Exception failure) {
                    message[0] += " USB release failed: " + detail(failure);
                }
            }
            finish(resources == null ? null : resources.log, message[0]);
        }
    }

    private void finish(PortableLogSession log, String message) {
        synchronized (lock) {
            session = null;
            completedLog = log;
            Snapshot previous = snapshot;
            snapshot = new Snapshot(Phase.STOPPED, previous.ecuId, previous.ready,
                    previous.unavailable, previous.timestampMillis, previous.receivedAtNanos,
                    previous.values, log == null ? 0 : log.size(), message);
            stopped.countDown();
        }
    }

    // Caller holds lock. Retain actual last readings/time while changing availability.
    private void changePhase(Phase phase, String message) {
        Snapshot previous = snapshot;
        snapshot = new Snapshot(phase, previous.ecuId, previous.ready, previous.unavailable,
                previous.timestampMillis, previous.receivedAtNanos, previous.values,
                previous.samples, message);
    }

    private static String detail(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
