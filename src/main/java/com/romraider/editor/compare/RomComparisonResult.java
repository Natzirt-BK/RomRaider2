/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.compare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RomComparisonResult {
    private final List<TableComparison> tables;
    private final int equal;
    private final int different;
    private final int missing;

    RomComparisonResult(List<TableComparison> tables, int equal,
            int different, int missing) {
        this.tables = Collections.unmodifiableList(
                new ArrayList<TableComparison>(tables));
        this.equal = equal;
        this.different = different;
        this.missing = missing;
    }

    public List<TableComparison> getTables() { return tables; }
    public int getEqualCount() { return equal; }
    public int getDifferentCount() { return different; }
    public int getMissingCount() { return missing; }
    public boolean isIdentical() { return different == 0 && missing == 0; }
}
