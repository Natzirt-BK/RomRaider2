/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

/** One explicit live-tuning preflight result suitable for a future UI. */
public final class LiveTunePreflightCheck {
    private final String id;
    private final String label;
    private final LiveTuneCheckStatus status;
    private final String detail;

    public LiveTunePreflightCheck(String id, String label,
            LiveTuneCheckStatus status, String detail) {
        if (id == null || id.trim().isEmpty() || label == null
                || label.trim().isEmpty() || status == null || detail == null) {
            throw new IllegalArgumentException(
                    "Preflight check fields are required");
        }
        this.id = id;
        this.label = label;
        this.status = status;
        this.detail = detail;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public LiveTuneCheckStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
