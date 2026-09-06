/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.analysis.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import javafx.collections.ObservableListBase;

/** Immutable sample identities; source order needs no per-row allocation. */
final class FxLogRows extends ObservableListBase<Integer> implements RandomAccess {
    record SortKey(int channel, boolean descending) { } // -1 is the original sample number.
    private final int start, count;
    private final int[] ordered, positions;
    private FxLogRows(int start, int count, int[] ordered, int[] positions) {
        this.start = start; this.count = count; this.ordered = ordered; this.positions = positions;
    }
    static FxLogRows empty() { return new FxLogRows(0, 0, null, null); }
    static FxLogRows sourceOrder(LogRange range) { return new FxLogRows(range.getStartInclusive(), range.size(), null, null); }
    @Override public int size() { return count; }
    @Override public Integer get(int index) {
        Objects.checkIndex(index, count); return ordered == null ? start + index : ordered[index];
    }
    @Override public int indexOf(Object item) {
        if (!(item instanceof Integer row) || row < start || (long) row >= (long) start + count) return -1;
        return positions == null ? row - start : positions[row - start];
    }
    @Override public int lastIndexOf(Object item) { return indexOf(item); }
    @Override public boolean contains(Object item) { return indexOf(item) >= 0; }

    static FxLogRows sorted(LogDataset dataset, LogRange range, List<SortKey> keys) {
        return sorted(dataset, range, keys, 100_000_000L);
    }
    static FxLogRows sorted(LogDataset dataset, LogRange range, List<SortKey> keys, long comparisons) {
        Objects.requireNonNull(dataset); Objects.requireNonNull(range); keys = List.copyOf(keys);
        if (range.getEndExclusive() > dataset.getRowCount() || range.size() > 1_000_000 || comparisons < 1)
            throw new IllegalArgumentException("Table sort exceeds the supported sample range; select at most 1,000,000 samples.");
        Set<Integer> seen = new HashSet<>();
        for (SortKey key : keys) {
            if (key.channel < -1 || key.channel >= dataset.getChannelCount() || !seen.add(key.channel))
                throw new IllegalArgumentException("Sort columns are missing or duplicated.");
        }
        if (keys.size() > 257) throw new IllegalArgumentException("Too many table sort columns.");
        checkCancelled();
        if (keys.isEmpty()) return sourceOrder(range);
        int count = range.size(), start = range.getStartInclusive();
        int[] current = new int[count], scratch = new int[count];
        for (int i = 0; i < count; i++) { if ((i & 1023) == 0) checkCancelled(); current[i] = start + i; }
        Comparison order = new Comparison(dataset, keys, comparisons);
        // Stable bottom-up merge sort over primitive sample IDs, never JavaFX cells.
        for (int width = 1; width < count; width *= 2) {
            for (int begin = 0; begin < count; begin += 2 * width) {
                int middle = Math.min(begin + width, count), end = Math.min(begin + 2 * width, count);
                int left = begin, right = middle;
                for (int out = begin; out < end; out++) {
                    if ((out & 1023) == 0) checkCancelled();
                    if (left < middle && (right >= end || order.compare(current[left], current[right]) <= 0)) scratch[out] = current[left++];
                    else scratch[out] = current[right++];
                }
            }
            int[] swap = current; current = scratch; scratch = swap;
        }
        // Reuse scratch for O(1) source-sample lookup during selection and playback.
        for (int i = 0; i < count; i++) { if ((i & 1023) == 0) checkCancelled(); scratch[current[i] - start] = i; }
        checkCancelled();
        return new FxLogRows(start, count, current, scratch);
    }
    private static final class Comparison {
        final LogDataset dataset; final List<SortKey> keys; long remaining;
        Comparison(LogDataset dataset, List<SortKey> keys, long remaining) { this.dataset = dataset; this.keys = keys; this.remaining = remaining; }
        int compare(int first, int second) {
            for (SortKey key : keys) {
                if (--remaining < 0) throw new IllegalArgumentException("Table sort exceeded its comparison limit; use fewer sort columns or a smaller range. No partial order was applied.");
                int result = key.channel < 0 ? Integer.compare(first, second)
                        : Double.compare(dataset.getValue(first, key.channel), dataset.getValue(second, key.channel));
                if (result != 0) return key.descending ? -result : result;
            }
            return 0;
        }
    }
    private static void checkCancelled() { if (Thread.currentThread().isInterrupted()) throw new CancellationException(); }
}
