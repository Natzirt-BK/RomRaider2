/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.romraider.io.transport.EcuIdentity;

/** Validated live-tune changes that are not yet bound to an ECU identity. */
public final class LiveTuneDraft {
    private final List<LiveTuneChange> changes;
    private final int totalBytes;
    private final int tableCount;

    public LiveTuneDraft(List<LiveTuneChange> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one staged change is required");
        }
        List<LiveTuneChange> sorted = new ArrayList<LiveTuneChange>(changes);
        if (sorted.contains(null)) {
            throw new IllegalArgumentException(
                    "Staged changes cannot contain null");
        }
        Collections.sort(sorted, Comparator.comparingLong(
                LiveTuneChange::getAddress));
        int bytes = 0;
        LiveTuneChange previous = null;
        Set<String> tables = new LinkedHashSet<String>();
        for (LiveTuneChange change : sorted) {
            if (previous != null && change.getAddress()
                    <= previous.getEndAddress()) {
                throw new IllegalArgumentException(
                        "Staged live-tune changes cannot overlap");
            }
            bytes = Math.addExact(bytes, change.getLength());
            tables.add(change.getTableName());
            previous = change;
        }
        if (bytes > LiveTunePlan.MAX_PLAN_BYTES) {
            throw new IllegalArgumentException("Staged plan exceeds "
                    + LiveTunePlan.MAX_PLAN_BYTES + " bytes");
        }
        this.changes = Collections.unmodifiableList(sorted);
        this.totalBytes = bytes;
        this.tableCount = tables.size();
    }

    public List<LiveTuneChange> getChanges() {
        return changes;
    }

    public int getTotalBytes() {
        return totalBytes;
    }

    public int getTableCount() {
        return tableCount;
    }

    public long getStartAddress() {
        return changes.get(0).getAddress();
    }

    public long getEndAddress() {
        return changes.get(changes.size() - 1).getEndAddress();
    }

    public LiveTunePlan bindTo(EcuIdentity expectedIdentity) {
        return new LiveTunePlan(expectedIdentity, changes);
    }
}
