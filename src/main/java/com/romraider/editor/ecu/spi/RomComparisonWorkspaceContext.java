/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.romraider.editor.compare.TableComparison;
import com.romraider.maps.Rom;

/** Immutable input and command boundary for a ROM comparison workspace. */
public final class RomComparisonWorkspaceContext {
    public interface Listener {
        void openComparison(Rom left, Rom right,
                TableComparison comparison);
    }

    private final List<Rom> roms;
    private final Listener listener;

    public RomComparisonWorkspaceContext(List<Rom> roms, Listener listener) {
        this.roms = Collections.unmodifiableList(new ArrayList<Rom>(
                roms == null ? Collections.<Rom>emptyList() : roms));
        this.listener = listener;
    }

    public List<Rom> getRoms() {
        return roms;
    }

    public boolean canOpenComparisons() {
        return listener != null;
    }

    public void openComparison(Rom left, Rom right,
            TableComparison comparison) {
        if (listener == null || left == null || right == null
                || comparison == null || !comparison.isAvailableInBoth()) {
            return;
        }
        listener.openComparison(left, right, comparison);
    }
}
