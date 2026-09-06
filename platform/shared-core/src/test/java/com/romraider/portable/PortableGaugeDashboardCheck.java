/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.gauge.GaugeDashboardLayout;
import java.util.List;

public final class PortableGaugeDashboardCheck {
    public static void main(String[] args) {
        int cases = 0;
        for (int[] size : new int[][]{{320, 480}, {1080, 1800}, {1920, 920}, {800, 420},
                {640, 640}, {1, 1}, {0, 0}, {0, 500}, {Integer.MAX_VALUE, 1000}}) {
            for (int count = 1; count <= 6; count++) for (double aspect : new double[]{320.0 / 250, 320.0 / 205}) {
                List<GaugeDashboardLayout.Tile> tiles = GaugeDashboardLayout.fit(size[0], size[1], count, aspect, 2);
                check(tiles.size() == count, "Wrong gauge count");
                double drawnArea = 0;
                for (int i = 0; i < count; i++) {
                    GaugeDashboardLayout.Tile t = tiles.get(i);
                    check(t.left >= 0 && t.top >= 0 && t.width >= 0 && t.height >= 0
                            && (long) t.left + t.width <= size[0] && (long) t.top + t.height <= size[1], "Gauge overflows viewport");
                    double h = Math.min(t.height, t.width / aspect);
                    drawnArea += h * h * aspect;
                    for (int j = 0; j < i; j++) {
                        GaugeDashboardLayout.Tile other = tiles.get(j);
                        check((long) t.left + t.width <= other.left || (long) other.left + other.width <= t.left
                                || (long) t.top + t.height <= other.top || (long) other.top + other.height <= t.top,
                                "Gauge tiles overlap");
                    }
                }
                // Independently compare every balanced-row candidate's fitted face area.
                int gap = Math.min(2, Math.min(size[0], size[1]) / (count * 2));
                for (int rows = 1; rows <= count; rows++) {
                    double alternative = 0;
                    for (int row = 0; row < rows; row++) {
                        int columns = count / rows + (row < count % rows ? 1 : 0);
                        long h = (long) (row + 1) * size[1] / rows - (long) row * size[1] / rows - gap * 2;
                        for (int col = 0; col < columns; col++) {
                            long w = (long) (col + 1) * size[0] / columns - (long) col * size[0] / columns - gap * 2;
                            double fitted = Math.min(Math.max(0, h), Math.max(0, w) / aspect);
                            alternative += fitted * fitted * aspect;
                        }
                    }
                    check(drawnArea + .0001 >= alternative, "Layout did not maximize candidate face area");
                }
                cases++;
            }
        }
        check(GaugeDashboardLayout.fit(100, 100, 0, 1.28, 2).isEmpty(), "Empty data should not invent gauges");
        for (int count : new int[]{-1, 7}) {
            try { GaugeDashboardLayout.fit(100, 100, count, 1.28, 2); throw new AssertionError("Invalid count accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try { GaugeDashboardLayout.fit(100, 100, 1, Double.NaN, 2); throw new AssertionError("Invalid aspect accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("Mounted gauge layout checks passed: " + cases + " viewports/counts/styles; no overlap, crop or empty slots");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
