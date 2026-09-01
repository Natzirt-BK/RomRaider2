/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.livetune;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete, inspectable result of live-tuning eligibility checks. */
public final class LiveTunePreflight {
    private final List<LiveTunePreflightCheck> checks;

    public LiveTunePreflight(List<LiveTunePreflightCheck> checks) {
        if (checks == null || checks.isEmpty() || checks.contains(null)) {
            throw new IllegalArgumentException(
                    "At least one preflight check is required");
        }
        this.checks = Collections.unmodifiableList(
                new ArrayList<LiveTunePreflightCheck>(checks));
    }

    public List<LiveTunePreflightCheck> getChecks() {
        return checks;
    }

    public boolean isReady() {
        for (LiveTunePreflightCheck check : checks) {
            if (check.getStatus() != LiveTuneCheckStatus.PASS) return false;
        }
        return true;
    }
}
