/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

/** One platform-neutral logger sample. */
public final class PortableLogSample {
    private final long timestampMillis;
    private final String channelId;
    private final String channelName;
    private final double value;
    private final String units;

    public PortableLogSample(long timestampMillis, String channelId,
            String channelName, double value, String units) {
        if (timestampMillis < 0 || blank(channelId) || blank(channelName)) {
            throw new IllegalArgumentException(
                    "Timestamp, channel ID, and channel name are required");
        }
        this.timestampMillis = timestampMillis;
        this.channelId = channelId.trim();
        this.channelName = channelName.trim();
        this.value = value;
        this.units = units == null ? "" : units.trim();
    }

    public long getTimestampMillis() { return timestampMillis; }
    public String getChannelId() { return channelId; }
    public String getChannelName() { return channelName; }
    public double getValue() { return value; }
    public String getUnits() { return units; }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
