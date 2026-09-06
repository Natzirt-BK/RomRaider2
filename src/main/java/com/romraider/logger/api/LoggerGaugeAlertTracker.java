/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Consumes every reading, independently of rendering frequency or detached views. */
public final class LoggerGaugeAlertTracker {
    private final Map<String, Entry> entries = new HashMap<String, Entry>();
    private LoggerSessionState session;

    public synchronized LoggerGaugeConfiguration.AlertState update(LiveDataSample sample,
            LoggerGaugeConfiguration configuration) {
        Entry previous = entries.get(sample.getParameterId());
        LoggerGaugeConfiguration.AlertState prior = previous != null
                && Objects.equals(previous.configuration, configuration)
                && previous.identity.equals(sample.getConversionIdentity())
                ? previous.state : LoggerGaugeConfiguration.AlertState.NORMAL;
        LoggerGaugeConfiguration.AlertState next = configuration == null
                || !configuration.matchesConversion(sample.getConversionIdentity())
                ? LoggerGaugeConfiguration.AlertState.UNAVAILABLE
                : configuration.alertState(sample.getRawValue(), prior);
        entries.put(sample.getParameterId(), new Entry(configuration,
                sample.getConversionIdentity(), next));
        return next;
    }

    public synchronized LoggerGaugeConfiguration.AlertState state(String id,
            LoggerGaugeConfiguration configuration) {
        Entry entry = entries.get(id);
        return entry != null && Objects.equals(entry.configuration, configuration)
                ? entry.state : LoggerGaugeConfiguration.AlertState.UNAVAILABLE;
    }

    public synchronized void remove(String id) { entries.remove(id); }
    public synchronized void clear() { entries.clear(); }

    public synchronized void retainChannels(java.util.Collection<LoggerChannel> channels) {
        entries.entrySet().removeIf(entry -> channels.stream().noneMatch(channel ->
                channel.isSelected() && channel.getParameterId().equals(entry.getKey())
                && channel.getConversionIdentity().equals(entry.getValue().identity)));
    }

    public synchronized void sessionChanged(LoggerSessionState next) {
        if (next == session) return;
        if (next == LoggerSessionState.STOPPED || next == LoggerSessionState.CONNECTING
                || next == LoggerSessionState.RECONNECTING) clear();
        session = next;
    }

    private static final class Entry {
        private final LoggerGaugeConfiguration configuration;
        private final String identity;
        private final LoggerGaugeConfiguration.AlertState state;
        private Entry(LoggerGaugeConfiguration configuration, String identity,
                LoggerGaugeConfiguration.AlertState state) {
            this.configuration = configuration;
            this.identity = identity;
            this.state = state;
        }
    }
}
