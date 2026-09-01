/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.mobile.usb.OpenPortUsbTransport;
import com.romraider.portable.PortableLogSample;
import com.romraider.portable.PortableLogSession;
import com.romraider.portable.logger.PortableLoggerCycle;
import com.romraider.portable.logger.PortableLoggerQueryPlan;
import com.romraider.portable.logger.PortableLoggerValue;
import com.romraider.portable.logger.definition.PortableLoggerDefinition;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import com.romraider.portable.logger.definition.PortableLoggerSelection;
import com.romraider.portable.logger.definition.PortableLoggerSelectionService;
import com.romraider.portable.logger.definition.PortableSelectedParameter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** One foreground-only, read-only Android logger session. */
public final class ReadOnlyLoggerSession {
    public interface Listener {
        void onIdentified(String ecuId, int readyParameters,
                int unavailableParameters);
        void onValues(String ecuId, long timestampMillis,
                List<PortableLoggerValue> values, int samples);
        void onStopped(String message);
    }

    private final OpenPortUsbTransport transport;
    private final PortableLoggerDefinition definition;
    private final PortableLoggerProfile profile;
    private final Listener listener;
    private final PortableLogSession log = new PortableLogSession();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean stopRequested;

    public ReadOnlyLoggerSession(OpenPortUsbTransport transport,
            PortableLoggerDefinition definition, PortableLoggerProfile profile,
            Listener listener) {
        if (transport == null || definition == null || profile == null
                || listener == null) {
            throw new IllegalArgumentException(
                    "Logger transport, definition, profile, and listener are required");
        }
        this.transport = transport;
        this.definition = definition;
        this.profile = profile;
        this.listener = listener;
    }

    /** Blocks on the calling worker thread until stopped or a read fails. */
    public void run() {
        if (stopRequested) {
            listener.onStopped("Read-only logger stopped.");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Logger session is already running");
        }
        String stopMessage = "Read-only logger stopped.";
        try {
            if (stopRequested) return;
            String ecuId = transport.identifyEcu();
            PortableLoggerSelection selection =
                    PortableLoggerSelectionService.resolve(
                            definition, profile, ecuId, 1);
            if (selection.ready().isEmpty()) {
                throw new IllegalStateException(
                        "The profile has no parameters for ECU " + ecuId + ".");
            }
            PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(
                    selection.ready());
            PortableLoggerCycle cycle = new PortableLoggerCycle(plan);
            listener.onIdentified(ecuId, selection.ready().size(),
                    selection.unavailable().size());
            if (stopRequested) return;
            long started = android.os.SystemClock.elapsedRealtime();
            while (running.get() && !stopRequested) {
                List<PortableLoggerValue> values = cycle.read(transport);
                long timestamp = android.os.SystemClock.elapsedRealtime()
                        - started;
                for (PortableLoggerValue value : values) {
                    PortableSelectedParameter selected = value.getSelection();
                    log.append(new PortableLogSample(timestamp,
                            selected.getParameter().getId(),
                            selected.getParameter().getName(), value.getValue(),
                            selected.getConversion().getUnits()));
                }
                listener.onValues(ecuId, timestamp, values, log.size());
            }
        } catch (Exception ex) {
            stopMessage = ex.getMessage() == null
                    ? "Read-only logger stopped after a connection error."
                    : ex.getMessage();
        } finally {
            running.set(false);
            transport.closeReadOnlyKLine();
            listener.onStopped(stopMessage);
        }
    }

    public void stop() {
        stopRequested = true;
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public PortableLogSession getLog() {
        return log;
    }
}
