/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.compare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;

import org.junit.Test;

import com.romraider.editor.ecu.spi.RomComparisonWorkspaceContext;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;

public final class RomComparisonWorkspaceBoundaryTest {
    @Test
    public void commandBoundaryOnlyOpensTablesAvailableInBothRoms() {
        Rom left = new Rom(new RomID());
        Rom right = new Rom(new RomID());
        final TableComparison[] opened = {null};
        RomComparisonWorkspaceContext context =
                new RomComparisonWorkspaceContext(Arrays.asList(left, right),
                        (first, second, comparison) -> opened[0] = comparison);
        TableComparison missing = new TableComparison("Only here",
                TableComparisonStatus.ONLY_LEFT);
        TableComparison modified = new TableComparison("Fuel Target",
                TableComparisonStatus.DIFFERENT);

        context.openComparison(left, right, missing);
        assertEquals(null, opened[0]);
        context.openComparison(left, right, modified);
        assertSame(modified, opened[0]);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void romListIsImmutableAtTheViewBoundary() {
        RomComparisonWorkspaceContext context =
                new RomComparisonWorkspaceContext(
                        Arrays.asList(new Rom(new RomID())), null);
        context.getRoms().clear();
    }
}
