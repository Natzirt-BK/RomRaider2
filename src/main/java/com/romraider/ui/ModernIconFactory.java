/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;

import javax.swing.Icon;
import javax.swing.UIManager;

/** Crisp, theme-aware action glyphs that scale with the selected display mode. */
public final class ModernIconFactory {
    public enum Action {
        OPEN, SAVE, SAVE_AS, REFRESH, CLOSE, EXIT, EXPORT, SETTINGS,
        COMPARE, DEFINITION, CATEGORY, DOWNLOAD, PROPERTIES, LOGGER, CONNECT, TOOLS, HELP, USER,
        FAVORITE, BACK, FORWARD, COLOR, VIEW_3D,
        TABLE_1D, TABLE_2D, TABLE_3D, SWITCH,
        HIDE_LEFT_PANEL, SHOW_LEFT_PANEL, HIDE_RIGHT_PANEL, SHOW_RIGHT_PANEL,
        HIDE_BOTTOM_PANEL, SHOW_BOTTOM_PANEL, SEARCH, DASHBOARD, UNDO, REDO
    }

    private ModernIconFactory() {
    }

    public static Icon icon(Action action) {
        return new ActionIcon(action);
    }

    private static final class ActionIcon implements Icon {
        private final Action action;

        private ActionIcon(Action action) {
            this.action = action;
        }

        public int getIconWidth() {
            return Math.max(18, UiScaleService.getInstance().control(18));
        }

        public int getIconHeight() {
            return getIconWidth();
        }

        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            int size = getIconWidth();
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.scale(size / 20.0, size / 20.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = component == null ? UIManager.getColor("MenuItem.foreground")
                        : component.getForeground();
                if (component != null && !component.isEnabled()) {
                    Color disabled = UIManager.getColor("Button.disabledText");
                    if (disabled != null) color = disabled;
                }
                if (color == null) color = Color.DARK_GRAY;
                g.setColor(color);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                draw(g);
            } finally {
                g.dispose();
            }
        }

        private void draw(Graphics2D g) {
            switch (action) {
                case OPEN:
                    path(g, 2,8, 7,8, 9,5, 17,5, 17,8);
                    path(g, 2,8, 4,17, 16,17, 19,8, 2,8);
                    break;
                case SAVE:
                case SAVE_AS:
                    g.drawRect(3, 2, 14, 16); g.drawRect(6, 3, 7, 5);
                    g.drawRect(6, 12, 8, 6);
                    if (action == Action.SAVE_AS) { g.drawLine(13, 10, 18, 15); g.drawLine(15, 17, 18, 15); }
                    break;
                case REFRESH:
                    g.draw(new Arc2D.Double(3, 3, 14, 14, 35, 285, Arc2D.OPEN));
                    path(g, 15,3, 18,3, 18,7);
                    break;
                case CLOSE:
                case EXIT:
                    g.drawLine(4, 4, 16, 16); g.drawLine(16, 4, 4, 16);
                    if (action == Action.EXIT) g.drawRect(2, 2, 16, 16);
                    break;
                case EXPORT:
                case DOWNLOAD:
                    g.drawRect(3, 12, 14, 5); g.drawLine(10, 2, 10, 13);
                    if (action == Action.DOWNLOAD) path(g, 6,9, 10,13, 14,9);
                    else path(g, 6,6, 10,2, 14,6);
                    break;
                case SETTINGS:
                    g.drawOval(6, 6, 8, 8); g.drawOval(3, 3, 14, 14);
                    g.drawLine(10, 1, 10, 4); g.drawLine(10, 16, 10, 19);
                    g.drawLine(1, 10, 4, 10); g.drawLine(16, 10, 19, 10);
                    break;
                case COMPARE:
                    g.drawRect(2, 3, 7, 13); g.drawRect(11, 4, 7, 13);
                    g.drawLine(6, 7, 6, 12); g.drawLine(14, 8, 14, 13);
                    break;
                case DEFINITION:
                    path(g, 4,2, 13,2, 17,6, 17,18, 4,18, 4,2);
                    g.drawLine(13, 2, 13, 7); g.drawLine(13, 7, 17, 7);
                    g.drawLine(7, 11, 14, 11); g.drawLine(7, 14, 13, 14);
                    break;
                case CATEGORY:
                    path(g, 2,6, 8,6, 10,3, 18,3, 18,17, 2,17, 2,6);
                    g.drawLine(2, 8, 18, 8);
                    break;
                case PROPERTIES:
                    g.drawRect(3, 3, 14, 14); g.drawLine(7, 7, 14, 7);
                    g.drawLine(7, 10, 14, 10); g.drawLine(7, 13, 12, 13);
                    break;
                case LOGGER:
                    path(g, 2,13, 5,13, 7,6, 10,16, 13,9, 15,12, 18,12);
                    break;
                case CONNECT:
                    path(g, 7,3, 7,8, 13,8, 13,3);
                    g.drawLine(5, 8, 15, 8);
                    path(g, 6,8, 6,11, 9,14, 11,14, 14,11, 14,8);
                    g.drawLine(10, 14, 10, 18);
                    break;
                case TOOLS:
                    g.drawLine(4, 16, 15, 5); g.drawOval(2, 14, 4, 4);
                    g.drawOval(13, 2, 5, 5);
                    break;
                case HELP:
                    g.drawOval(2, 2, 16, 16); g.draw(new Arc2D.Double(7, 5, 6, 6, 0, 210, Arc2D.OPEN));
                    g.drawLine(10, 11, 10, 13); g.fillOval(9, 15, 2, 2);
                    break;
                case USER:
                    g.drawOval(7, 3, 6, 6); g.draw(new Arc2D.Double(4, 9, 12, 9, 0, 180, Arc2D.OPEN));
                    break;
                case FAVORITE:
                    path(g, 10,2, 12,7, 18,7, 13,11, 15,17, 10,14,
                            5,17, 7,11, 2,7, 8,7, 10,2);
                    break;
                case BACK:
                    path(g, 9,4, 3,10, 9,16); g.drawLine(3,10,17,10);
                    break;
                case FORWARD:
                    path(g, 11,4, 17,10, 11,16); g.drawLine(3,10,17,10);
                    break;
                case COLOR:
                    g.drawOval(2, 3, 16, 14);
                    g.fillOval(5, 7, 2, 2); g.fillOval(9, 5, 2, 2);
                    g.fillOval(13, 8, 2, 2); g.drawOval(8, 12, 4, 3);
                    break;
                case VIEW_3D:
                    path(g, 3,7, 10,3, 17,7, 10,11, 3,7);
                    path(g, 3,7, 3,14, 10,18, 10,11);
                    path(g, 17,7, 17,14, 10,18);
                    break;
                case TABLE_1D:
                    g.drawLine(3, 16, 3, 4); g.drawLine(3, 16, 17, 16);
                    path(g, 4,14, 7,11, 10,12, 14,6, 17,8);
                    break;
                case TABLE_2D:
                    g.drawRect(3, 4, 14, 12);
                    g.drawLine(3, 10, 17, 10);
                    g.drawLine(8, 4, 8, 16); g.drawLine(13, 4, 13, 16);
                    break;
                case TABLE_3D:
                    path(g, 3,7, 10,3, 17,7, 10,11, 3,7);
                    path(g, 3,7, 3,14, 10,18, 10,11);
                    path(g, 17,7, 17,14, 10,18);
                    g.drawLine(6, 5, 13, 9); g.drawLine(7, 16, 14, 12);
                    break;
                case SWITCH:
                    g.drawRoundRect(2, 6, 16, 9, 8, 8);
                    g.fillOval(4, 8, 5, 5);
                    break;
                case HIDE_RIGHT_PANEL:
                    g.drawLine(17, 3, 17, 17);
                    path(g, 8,5, 13,10, 8,15);
                    break;
                case SHOW_RIGHT_PANEL:
                    g.drawLine(17, 3, 17, 17);
                    path(g, 13,5, 8,10, 13,15);
                    break;
                case HIDE_LEFT_PANEL:
                    g.drawLine(3, 3, 3, 17);
                    path(g, 12,5, 7,10, 12,15);
                    break;
                case SHOW_LEFT_PANEL:
                    g.drawLine(3, 3, 3, 17);
                    path(g, 7,5, 12,10, 7,15);
                    break;
                case HIDE_BOTTOM_PANEL:
                    g.drawLine(3, 17, 17, 17);
                    path(g, 5,8, 10,13, 15,8);
                    break;
                case SHOW_BOTTOM_PANEL:
                    g.drawLine(3, 17, 17, 17);
                    path(g, 5,13, 10,8, 15,13);
                    break;
                case SEARCH:
                    g.drawOval(3, 3, 10, 10);
                    g.drawLine(12, 12, 18, 18);
                    break;
                case DASHBOARD:
                    g.drawRect(2, 3, 7, 6); g.drawRect(11, 3, 7, 6);
                    g.drawRect(2, 11, 7, 6); g.drawRect(11, 11, 7, 6);
                    break;
                case UNDO:
                    g.draw(new Arc2D.Double(4, 5, 13, 11, 35, 235, Arc2D.OPEN));
                    path(g, 7,3, 3,7, 8,10);
                    break;
                case REDO:
                    g.draw(new Arc2D.Double(3, 5, 13, 11, -90, 235, Arc2D.OPEN));
                    path(g, 13,3, 17,7, 12,10);
                    break;
                default:
                    break;
            }
        }

        private static void path(Graphics2D g, int... points) {
            Path2D path = new Path2D.Double();
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
            g.draw(path);
        }
    }
}
