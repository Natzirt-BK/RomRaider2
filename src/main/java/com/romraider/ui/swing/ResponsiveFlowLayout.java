/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * Flow layout whose preferred height includes rows created by a narrow host.
 * Standard {@link FlowLayout} wraps during layout but reports a single-row
 * preferred size, which lets BorderLayout clip the wrapped controls.
 */
public final class ResponsiveFlowLayout extends FlowLayout {
    private static final long serialVersionUID = 1L;

    public ResponsiveFlowLayout(int alignment, int horizontalGap,
            int verticalGap) {
        super(alignment, horizontalGap, verticalGap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int availableWidth = target.getWidth();
            if (availableWidth <= 0 && target.getParent() != null) {
                availableWidth = target.getParent().getWidth();
            }
            if (availableWidth <= 0) return super.preferredLayoutSize(target);

            Insets insets = target.getInsets();
            int usableWidth = Math.max(1, availableWidth - insets.left
                    - insets.right - getHgap() * 2);
            int rowWidth = 0;
            int rowHeight = 0;
            int widestRow = 0;
            int totalHeight = 0;
            int rows = 0;

            for (Component component : target.getComponents()) {
                if (!component.isVisible()) continue;
                Dimension size = component.getPreferredSize();
                int nextWidth = rowWidth == 0 ? size.width
                        : rowWidth + getHgap() + size.width;
                if (rowWidth > 0 && nextWidth > usableWidth) {
                    widestRow = Math.max(widestRow, rowWidth);
                    totalHeight += rowHeight;
                    rows++;
                    rowWidth = size.width;
                    rowHeight = size.height;
                } else {
                    rowWidth = nextWidth;
                    rowHeight = Math.max(rowHeight, size.height);
                }
            }
            if (rowWidth > 0) {
                widestRow = Math.max(widestRow, rowWidth);
                totalHeight += rowHeight;
                rows++;
            }
            if (rows > 1) totalHeight += (rows - 1) * getVgap();
            return new Dimension(widestRow + insets.left + insets.right
                    + getHgap() * 2,
                    totalHeight + insets.top + insets.bottom
                            + getVgap() * 2);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension preferred = preferredLayoutSize(target);
        return new Dimension(0, preferred.height);
    }
}
