/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.analysis.*;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class FxLogRowsTest {
    private static LogDataset data(String rows) throws Exception {
        return new RomRaiderCsvLogParser().parse("synthetic", new StringReader("A,B\n" + rows));
    }
    @Test void millionRowSourceOrderAllocatesNoIndexArraysAndHasDirectSampleLookup() throws Exception {
        var rows = FxLogRows.sourceOrder(LogRange.of(10, 1_000_010, 1_000_010));
        assertEquals(1_000_000, rows.size()); assertEquals(10, rows.get(0)); assertEquals(1_000_009, rows.get(999_999));
        assertEquals(999_999, rows.indexOf(1_000_009)); assertEquals(-1, rows.indexOf(9)); assertEquals(-1, rows.indexOf(1_000_010));
        assertEquals(-1, rows.indexOf(10L)); assertEquals(-1, rows.indexOf(null));
        for (String name : List.of("ordered", "positions")) {
            var field = FxLogRows.class.getDeclaredField(name); field.setAccessible(true); assertNull(field.get(rows));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> rows.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> rows.get(rows.size()));
        assertTrue(FxLogRows.empty().isEmpty());
    }
    @Test void numericSortPreservesMissingNonfiniteSignedZeroAndStableSampleTies() throws Exception {
        var data = data("2,1\nNaN,2\n-0.0,3\n0.0,4\nInfinity,5\n-Infinity,6\n2,7\n");
        var rows = FxLogRows.sorted(data, LogRange.all(data), List.of(new FxLogRows.SortKey(0, false)));
        assertEquals(List.of(5, 2, 3, 0, 6, 4, 1), rows);
        for (int i = 0; i < rows.size(); i++) assertEquals(i, rows.indexOf(rows.get(i)));
        assertEquals(List.of(1, 4, 0, 6, 3, 2, 5), FxLogRows.sorted(data, LogRange.all(data), List.of(new FxLogRows.SortKey(0, true))));
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(data.getValue(2, 0)));
    }
    @Test void multipleKeysAndRestrictedRangeUseOriginalSampleNumbers() throws Exception {
        var data = data("3,0\n2,1\n1,4\n2,9\n1,2\n");
        var range = LogRange.of(1, 5, 5);
        var rows = FxLogRows.sorted(data, range, List.of(new FxLogRows.SortKey(0, false), new FxLogRows.SortKey(1, true)));
        assertEquals(List.of(2, 4, 3, 1), rows); assertEquals(-1, rows.indexOf(0));
        assertEquals(List.of(4, 3, 2, 1), FxLogRows.sorted(data, range, List.of(new FxLogRows.SortKey(-1, true))));
        assertEquals(List.of(1, 2, 3, 4), FxLogRows.sorted(data, range, List.of()));
    }
    @Test void randomPrimitiveSortMatchesStableReferenceAcrossMergeBoundaries() throws Exception {
        Random random = new Random(7193);
        for (int size : new int[]{1, 2, 3, 7, 16, 31, 64, 129, 1003}) {
            StringBuilder csv = new StringBuilder();
            for (int i = 0; i < size; i++) csv.append(random.nextInt(7)).append(',').append(random.nextInt(11)).append('\n');
            var data = data(csv.toString()); var expected = new ArrayList<Integer>();
            for (int i = 0; i < size; i++) expected.add(i);
            expected.sort(Comparator.<Integer>comparingDouble(i -> data.getValue(i, 0))
                    .thenComparing(Comparator.<Integer>comparingDouble(i -> data.getValue(i, 1)).reversed()));
            var actual = FxLogRows.sorted(data, LogRange.all(data), List.of(new FxLogRows.SortKey(0, false), new FxLogRows.SortKey(1, true)));
            assertEquals(expected, actual);
            for (int i = 0; i < size; i++) assertEquals(i, actual.indexOf(actual.get(i)));
        }
    }
    @Test void immutableListsCannotBeEditedAndInvalidKeysOrWorkLimitsRejectWholeSort() throws Exception {
        var data = data("3,1\n2,2\n1,3\n"); var range = LogRange.all(data);
        var rows = FxLogRows.sorted(data, range, List.of(new FxLogRows.SortKey(0, false)));
        assertThrows(UnsupportedOperationException.class, () -> rows.set(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> rows.add(0));
        assertThrows(UnsupportedOperationException.class, () -> rows.remove(0));
        assertThrows(IllegalArgumentException.class, () -> FxLogRows.sorted(data, range, List.of(new FxLogRows.SortKey(2, false))));
        assertThrows(IllegalArgumentException.class, () -> FxLogRows.sorted(data, range, List.of(new FxLogRows.SortKey(0, false), new FxLogRows.SortKey(0, true))));
        assertThrows(IllegalArgumentException.class, () -> FxLogRows.sorted(data, range, List.of(new FxLogRows.SortKey(0, false)), 1));
        assertEquals(List.of(2, 1, 0), rows);
    }
    @Test void interruptedSortDoesNotPublishAPartialOrder() throws Exception {
        var data = data("3,1\n2,2\n1,3\n");
        try {
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class, () -> FxLogRows.sorted(data, LogRange.all(data), List.of(new FxLogRows.SortKey(0, false))));
        } finally { Thread.interrupted(); }
    }
}
