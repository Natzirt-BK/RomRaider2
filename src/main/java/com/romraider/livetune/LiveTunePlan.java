/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

import java.util.List;

import com.romraider.io.transport.EcuIdentity;

/** Immutable set of staged RAM changes for one exact ECU and ROM identity. */
public final class LiveTunePlan {
    public static final int MAX_CHANGE_BYTES = 256;
    public static final int MAX_PLAN_BYTES = 1024;

    private final EcuIdentity expectedIdentity;
    private final List<LiveTuneChange> changes;
    private final int totalBytes;

    public LiveTunePlan(EcuIdentity expectedIdentity,
            List<LiveTuneChange> changes) {
        if (expectedIdentity == null) {
            throw new IllegalArgumentException("Expected ECU identity is required");
        }
        LiveTuneDraft draft = new LiveTuneDraft(changes);
        this.expectedIdentity = expectedIdentity;
        this.changes = draft.getChanges();
        this.totalBytes = draft.getTotalBytes();
    }

    public EcuIdentity getExpectedIdentity() {
        return expectedIdentity;
    }

    public List<LiveTuneChange> getChanges() {
        return changes;
    }

    public int getTotalBytes() {
        return totalBytes;
    }
}
