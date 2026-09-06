/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Independent display slots. Never modifies logger acquisition or a profile. */
public final class LoggerGaugeDisplay {
    public static final int MAX_GAUGES = 6;
    private final int count;
    private final List<String> slots;

    public LoggerGaugeDisplay() { this(6, Collections.nCopies(6, "")); }
    public LoggerGaugeDisplay(int count, List<String> slots) {
        if (count < 1 || count > MAX_GAUGES || slots == null || slots.size() != MAX_GAUGES)
            throw new IllegalArgumentException("Display requires one to six gauges and six saved slots");
        List<String> copy = new ArrayList<>();
        HashSet<String> assigned = new HashSet<>();
        for (String value : slots) {
            String id = normalize(value);
            if (!id.isEmpty() && !assigned.add(id)) throw new IllegalArgumentException("Duplicate display channel");
            copy.add(id);
        }
        this.count = count;
        this.slots = Collections.unmodifiableList(copy);
    }
    private static String normalize(String value) {
        if (value == null || value.length() > 240) throw new IllegalArgumentException("Invalid display channel ID");
        String id = value.trim();
        for (int i = 0; i < id.length(); i++) if (Character.isISOControl(id.charAt(i)))
            throw new IllegalArgumentException("Invalid display channel ID");
        return id;
    }
    public int getCount() { return count; }
    public List<String> getSlots() { return slots; }
    public List<String> getVisibleChannels() {
        List<String> ids = new ArrayList<>();
        for (String id : slots.subList(0, count)) if (!id.isEmpty()) ids.add(id);
        return Collections.unmodifiableList(ids);
    }
    public LoggerGaugeDisplay withCount(int next) { return new LoggerGaugeDisplay(next, slots); }
    public LoggerGaugeDisplay withChannel(int slot, String channelId) {
        if (slot < 0 || slot >= MAX_GAUGES) throw new IllegalArgumentException("Invalid gauge slot");
        String id = normalize(channelId);
        List<String> next = new ArrayList<>(slots);
        int occupied = id.isEmpty() ? -1 : next.indexOf(id);
        if (occupied >= 0 && occupied != slot) next.set(occupied, next.get(slot));
        next.set(slot, id);
        return new LoggerGaugeDisplay(count, next);
    }
    public LoggerGaugeDisplay useLoggerChannels(List<LoggerChannel> channels) {
        List<String> next = new ArrayList<>(Collections.nCopies(MAX_GAUGES, ""));
        int index = 0;
        for (LoggerChannel channel : channels) {
            if (!channel.isSelected() || next.contains(channel.getParameterId())) continue;
            next.set(index++, channel.getParameterId());
            if (index == MAX_GAUGES) break;
        }
        return new LoggerGaugeDisplay(Math.max(1, index), next);
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof LoggerGaugeDisplay)) return false;
        LoggerGaugeDisplay display = (LoggerGaugeDisplay) other;
        return count == display.count && slots.equals(display.slots);
    }
    @Override public int hashCode() { return Objects.hash(count, slots); }
}
