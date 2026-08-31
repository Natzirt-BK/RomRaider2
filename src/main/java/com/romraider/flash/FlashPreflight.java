/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable collection of individual preflight checks. */
public final class FlashPreflight {
    private final List<FlashPreflightCheck> checks;

    public FlashPreflight(List<FlashPreflightCheck> checks) {
        if (checks == null) throw new IllegalArgumentException("checks are required");
        this.checks = Collections.unmodifiableList(
                new ArrayList<FlashPreflightCheck>(checks));
    }

    public List<FlashPreflightCheck> getChecks() { return checks; }

    public boolean canProceed() {
        if (checks.isEmpty()) return false;
        for (FlashPreflightCheck check : checks) {
            if (check == null || check.blocksOperation()) return false;
        }
        return true;
    }

    public List<FlashPreflightCheck> getBlockingChecks() {
        List<FlashPreflightCheck> blocked = new ArrayList<FlashPreflightCheck>();
        for (FlashPreflightCheck check : checks) {
            if (check == null || check.blocksOperation()) {
                if (check != null) blocked.add(check);
            }
        }
        return Collections.unmodifiableList(blocked);
    }
}
