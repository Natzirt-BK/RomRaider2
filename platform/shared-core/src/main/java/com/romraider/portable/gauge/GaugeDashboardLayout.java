/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.gauge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fits one to six instruments without scrolling, cropping or distorting their faces. */
public final class GaugeDashboardLayout {
    private GaugeDashboardLayout() { }

    public static final class Tile {
        public final int left, top, width, height;
        private Tile(int left, int top, int width, int height) {
            this.left = left; this.top = top; this.width = width; this.height = height;
        }
    }

    public static List<Tile> fit(int width, int height, int count, double faceAspect, int gap) {
        if (width < 0 || height < 0 || count < 0 || count > 6 || gap < 0
                || !Double.isFinite(faceAspect) || faceAspect <= 0)
            throw new IllegalArgumentException("Invalid gauge viewport");
        if (count == 0) return Collections.emptyList();
        gap = Math.min(gap, Math.min(width, height) / (count * 2));
        List<Tile> best = Collections.emptyList();
        double bestArea = -1;
        // Balanced row candidates fill incomplete rows, rather than reserving empty grid slots.
        for (int rows = 1; rows <= count; rows++) {
            List<Tile> candidate = new ArrayList<>();
            double area = 0;
            for (int row = 0; row < rows; row++) {
                int columns = count / rows + (row < count % rows ? 1 : 0);
                for (int column = 0; column < columns; column++) {
                    int left = (int) ((long) column * width / columns) + gap;
                    int top = (int) ((long) row * height / rows) + gap;
                    int right = (int) ((long) (column + 1) * width / columns) - gap;
                    int bottom = (int) ((long) (row + 1) * height / rows) - gap;
                    Tile tile = new Tile(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
                    candidate.add(tile);
                    double fittedHeight = Math.min(tile.height, tile.width / faceAspect);
                    area += fittedHeight * fittedHeight * faceAspect;
                }
            }
            if (area > bestArea) { bestArea = area; best = candidate; }
        }
        return Collections.unmodifiableList(best);
    }
}
