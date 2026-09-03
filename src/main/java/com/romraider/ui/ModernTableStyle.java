/*
 * RomRaider2 ECU Studio
 * Copyright (C) 2026 RomRaider2 contributors
 * Released under GPL 2.0 or later.
 */
package com.romraider.ui;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/** Shared spacing and row treatment for modern Swing workspaces. */
public final class ModernTableStyle {
    private ModernTableStyle() { }

    public static void apply(JTable table) {
        applyLayout(table);
        table.setDefaultRenderer(Object.class, new ZebraRenderer());
    }

    /** Applies spacing without replacing specialized cell renderers. */
    public static void applyLayout(JTable table) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(Math.max(27, table.getRowHeight()));
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setShowGrid(false);
        table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(10, 30));
    }

    /** Renderer that preserves selection colors and follows the active theme. */
    public static class ZebraRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean selected, boolean focus, int row,
                int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                    value, selected, focus, row, column);
            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            if (!selected) {
                label.setBackground(UiThemeService.getInstance().color(
                        row % 2 == 0 ? ThemeToken.SURFACE
                                : ThemeToken.RAISED_SURFACE));
                label.setForeground(UiThemeService.getInstance().color(
                        ThemeToken.PRIMARY_TEXT));
            }
            return label;
        }
    }

    /** Zebra renderer with one semantic foreground colour. */
    public static final class TokenRenderer extends ZebraRenderer {
        private static final long serialVersionUID = 1L;
        private final ThemeToken token;

        public TokenRenderer(ThemeToken token) {
            if (token == null) {
                throw new IllegalArgumentException("A theme token is required");
            }
            this.token = token;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean selected, boolean focus, int row,
                int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                    value, selected, focus, row, column);
            if (!selected) {
                label.setForeground(UiThemeService.getInstance().color(token));
            }
            return label;
        }
    }
}
