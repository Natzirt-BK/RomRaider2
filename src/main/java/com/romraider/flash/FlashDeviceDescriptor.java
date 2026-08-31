/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

/** Safe device identity returned by discovery before a native handle is opened. */
public final class FlashDeviceDescriptor {
    private final String providerId;
    private final String deviceId;
    private final String displayName;
    private final String transportName;

    public FlashDeviceDescriptor(String providerId, String deviceId,
            String displayName, String transportName) {
        this.providerId = required(providerId, "provider id");
        this.deviceId = required(deviceId, "device id");
        this.displayName = required(displayName, "display name");
        this.transportName = required(transportName, "transport name");
    }

    public String getProviderId() { return providerId; }
    public String getDeviceId() { return deviceId; }
    public String getDisplayName() { return displayName; }
    public String getTransportName() { return transportName; }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
