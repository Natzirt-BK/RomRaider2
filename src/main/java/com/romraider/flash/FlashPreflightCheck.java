/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

/** One auditable safety check and its disposition. */
public final class FlashPreflightCheck {
    private final String id;
    private final String label;
    private final PreflightStatus status;
    private final boolean mandatory;
    private final String detail;

    public FlashPreflightCheck(String id, String label, PreflightStatus status,
            boolean mandatory, String detail) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("check id is required");
        }
        if (status == null) throw new IllegalArgumentException("status is required");
        this.id = id.trim();
        this.label = label == null ? this.id : label.trim();
        this.status = status;
        this.mandatory = mandatory;
        this.detail = detail == null ? "" : detail.trim();
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public PreflightStatus getStatus() { return status; }
    public boolean isMandatory() { return mandatory; }
    public String getDetail() { return detail; }
    public boolean blocksOperation() {
        return mandatory && status != PreflightStatus.PASS;
    }
}
