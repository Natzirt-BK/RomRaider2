/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.romraider.maps.DataCell;
import com.romraider.maps.Scale;
import com.romraider.maps.Table;
import com.romraider.maps.Table1D;
import com.romraider.maps.Table3D;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** Lightweight interactive map surface that relies only on supported Java2D. */
final class Java2dSurfacePanel extends JPanel implements MapVisualizationControls {
    private static final long serialVersionUID = 1L;
    private static final double DEFAULT_YAW = -0.72;
    private static final double DEFAULT_PITCH = 0.58;
    private static final double EPSILON = 0.0000001;

    private final Table3D table;
    private final Timer changeMonitor;
    private Point dragOrigin;
    private double yaw = DEFAULT_YAW;
    private double pitch = DEFAULT_PITCH;
    private double zoom = 1.0;
    private long valueFingerprint = Long.MIN_VALUE;
    private ProjectedPoint[][] projectedPoints;
    private SurfaceValues renderedValues;
    private int hoverX = -1;
    private int hoverY = -1;

    Java2dSurfacePanel(Table3D table) {
        this.table = table;
        setName("BUILT-IN MAP SURFACE");
        setOpaque(true);
        setPreferredSize(new Dimension(360, 260));
        setMinimumSize(new Dimension(200, 160));
        setToolTipText("Drag to rotate • Mouse wheel to zoom • Double-click to reset");
        getAccessibleContext().setAccessibleName("Interactive 3D map surface");

        MouseAdapter interaction = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragOrigin = event.getPoint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragOrigin = null;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragOrigin == null) return;
                clearHover();
                // Treat the surface as if it is being grabbed: the map follows
                // the pointer on both axes instead of rotating away from it.
                yaw -= (event.getX() - dragOrigin.x) * 0.012;
                pitch = clamp(pitch + (event.getY() - dragOrigin.y) * 0.009,
                        0.18, 1.25);
                dragOrigin = event.getPoint();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                zoom = clamp(zoom * Math.pow(0.90, event.getPreciseWheelRotation()),
                        0.55, 2.25);
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    resetView();
                } else if (SwingUtilities.isLeftMouseButton(event)) {
                    updateHover(event.getPoint());
                    selectCell(hoverX, hoverY);
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                updateHover(event.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                clearHover();
            }
        };
        addMouseListener(interaction);
        addMouseMotionListener(interaction);
        addMouseWheelListener(interaction);

        changeMonitor = new Timer(350, event -> refreshWhenValuesChange());
        changeMonitor.setRepeats(true);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                updateMonitorState();
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        valueFingerprint = fingerprint();
        updateMonitorState();
    }

    @Override
    public void removeNotify() {
        changeMonitor.stop();
        super.removeNotify();
    }

    void disposeSurface() {
        changeMonitor.stop();
    }

    public void resetView() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        zoom = 1.0;
        repaint();
    }

    public String getInteractionHint() {
        return "Drag to rotate  •  Wheel to zoom  •  Click to select";
    }

    boolean isMonitoringForTesting() {
        return changeMonitor.isRunning();
    }

    String cellDescriptionForTesting(int x, int y) {
        return cellDescription(SurfaceValues.read(table), x, y);
    }

    boolean selectCellForTesting(int x, int y) {
        return selectCell(x, y);
    }

    double yawForTesting() {
        return yaw;
    }

    double pitchForTesting() {
        return pitch;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (hoverX >= 0 && hoverY >= 0 && renderedValues != null) {
            return cellDescription(renderedValues, hoverX, hoverY);
        }
        return "Drag to rotate • Mouse wheel to zoom • Hover a point for exact values";
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            paintSurface(g);
        } finally {
            g.dispose();
        }
    }

    private void paintSurface(Graphics2D g) {
        Color background = UiThemeService.getInstance().color(
                ThemeToken.BACKGROUND);
        g.setColor(background);
        g.fillRect(0, 0, getWidth(), getHeight());

        SurfaceValues values = SurfaceValues.read(table);
        if (!values.isRenderable()) {
            paintMessage(g, "Surface data is unavailable");
            return;
        }

        int topInset = 20;
        int bottomInset = 36;
        double scale = Math.max(34.0, Math.min(getWidth() * 0.29,
                (getHeight() - topInset - bottomInset) * 0.42)) * zoom;
        double centerX = getWidth() * 0.50;
        double centerY = topInset + (getHeight() - topInset - bottomInset) * 0.56;

        ProjectedPoint[][] points = new ProjectedPoint[values.width][values.height];
        for (int x = 0; x < values.width; x++) {
            for (int y = 0; y < values.height; y++) {
                double nx = values.width == 1 ? 0.0
                        : -1.0 + (2.0 * x / (values.width - 1));
                double ny = values.height == 1 ? 0.0
                        : -1.0 + (2.0 * y / (values.height - 1));
                double nz = values.normalized(x, y) * 1.20;
                points[x][y] = project(nx, ny, nz, scale, centerX, centerY);
            }
        }
        projectedPoints = points;
        renderedValues = values;

        paintFloorGrid(g, values, scale, centerX, centerY);

        List<SurfaceCell> cells = new ArrayList<SurfaceCell>();
        for (int x = 0; x < values.width - 1; x++) {
            for (int y = 0; y < values.height - 1; y++) {
                ProjectedPoint p00 = points[x][y];
                ProjectedPoint p10 = points[x + 1][y];
                ProjectedPoint p11 = points[x + 1][y + 1];
                ProjectedPoint p01 = points[x][y + 1];
                double level = (values.normalized(x, y)
                        + values.normalized(x + 1, y)
                        + values.normalized(x + 1, y + 1)
                        + values.normalized(x, y + 1)) / 4.0;
                cells.add(new SurfaceCell(new Polygon(
                        new int[] {p00.x, p10.x, p11.x, p01.x},
                        new int[] {p00.y, p10.y, p11.y, p01.y}, 4),
                        (p00.depth + p10.depth + p11.depth + p01.depth) / 4.0,
                        level, values.isSelected(x, y)
                                || values.isSelected(x + 1, y)
                                || values.isSelected(x + 1, y + 1)
                                || values.isSelected(x, y + 1),
                        containsHover(x, y)));
            }
        }
        Collections.sort(cells, Comparator.comparingDouble(cell -> cell.depth));

        g.setStroke(new BasicStroke(1.0f));
        for (SurfaceCell cell : cells) {
            g.setColor(surfaceColor(cell.level));
            g.fillPolygon(cell.polygon);
            g.setStroke(new BasicStroke(cell.hovered ? 3.0f
                    : cell.selected ? 2.4f : 1.0f));
            g.setColor(cell.hovered || cell.selected
                    ? UiThemeService.getInstance().color(ThemeToken.ACCENT)
                    : new Color(15, 24, 34, 118));
            g.drawPolygon(cell.polygon);
        }

        if (values.width == 1 || values.height == 1) {
            g.setColor(UiThemeService.getInstance().color(ThemeToken.ACCENT));
            for (int x = 0; x < values.width; x++) {
                for (int y = 0; y < values.height; y++) {
                    ProjectedPoint point = points[x][y];
                    g.fillOval(point.x - 3, point.y - 3, 6, 6);
                }
            }
        }

        paintAxisTicks(g, values, scale, centerX, centerY);
        paintValueTicks(g, values, scale, centerX, centerY);
        paintLegend(g, values.minimum, values.maximum);
        paintAxisLabels(g);
        paintValueCallout(g, values, points);
    }

    private void paintFloorGrid(Graphics2D g, SurfaceValues values, double scale,
            double centerX, double centerY) {
        g.setColor(withAlpha(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT), 65));
        g.setStroke(new BasicStroke(0.8f));
        for (int x = 0; x < values.width; x++) {
            double position = values.width == 1 ? 0.0
                    : -1.0 + (2.0 * x / (values.width - 1));
            ProjectedPoint start = project(position, -1.0, 0.0,
                    scale, centerX, centerY);
            ProjectedPoint end = project(position, 1.0, 0.0,
                    scale, centerX, centerY);
            g.drawLine(start.x, start.y, end.x, end.y);
        }
        for (int y = 0; y < values.height; y++) {
            double position = values.height == 1 ? 0.0
                    : -1.0 + (2.0 * y / (values.height - 1));
            ProjectedPoint start = project(-1.0, position, 0.0,
                    scale, centerX, centerY);
            ProjectedPoint end = project(1.0, position, 0.0,
                    scale, centerX, centerY);
            g.drawLine(start.x, start.y, end.x, end.y);
        }
    }

    private ProjectedPoint project(double x, double y, double z, double scale,
            double centerX, double centerY) {
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double horizontal = x * cosYaw - y * sinYaw;
        double depth = x * sinYaw + y * cosYaw;
        double screenY = depth * Math.sin(pitch) - z * Math.cos(pitch);
        return new ProjectedPoint(
                (int) Math.round(centerX + horizontal * scale),
                (int) Math.round(centerY + screenY * scale), depth);
    }

    private void paintLegend(Graphics2D g, double minimum, double maximum) {
        String range = format(minimum) + "  —  " + format(maximum)
                + unitSuffix(table);
        FontMetrics metrics = g.getFontMetrics();
        int swatchWidth = Math.min(150, Math.max(80, getWidth() / 3));
        int x = 12;
        int y = getHeight() - 18;
        for (int offset = 0; offset < swatchWidth; offset++) {
            g.setColor(surfaceColor(offset / (double) Math.max(1, swatchWidth - 1)));
            g.drawLine(x + offset, y - 9, x + offset, y);
        }
        g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
        g.drawString(range, x + swatchWidth + 8,
                y - Math.max(0, (9 - metrics.getAscent()) / 2));
    }

    private void paintAxisLabels(Graphics2D g) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
        String xLabel = label(table == null ? null : table.getXAxis(), "X axis");
        String yLabel = label(table == null ? null : table.getYAxis(), "Y axis");
        String valueLabel = label(table, "Value");
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(yLabel, 12, 18);
        g.drawString(xLabel, Math.max(12,
                getWidth() - metrics.stringWidth(xLabel) - 12),
                getHeight() - 18);
        g.drawString(valueLabel, Math.max(12,
                getWidth() - metrics.stringWidth(valueLabel) - 12), 18);
    }

    private void paintAxisTicks(Graphics2D g, SurfaceValues values, double scale,
            double centerX, double centerY) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
        FontMetrics metrics = g.getFontMetrics();
        for (int index : tickIndices(values.width)) {
            double normalized = values.width == 1 ? 0.0
                    : -1.0 + (2.0 * index / (values.width - 1));
            ProjectedPoint point = project(normalized, 1.0, 0.0,
                    scale, centerX, centerY);
            String text = format(values.xAxis[index]);
            g.drawLine(point.x, point.y, point.x, point.y + 4);
            g.drawString(text, point.x - metrics.stringWidth(text) / 2,
                    point.y + metrics.getAscent() + 6);
        }
        for (int index : tickIndices(values.height)) {
            double normalized = values.height == 1 ? 0.0
                    : -1.0 + (2.0 * index / (values.height - 1));
            ProjectedPoint point = project(-1.0, normalized, 0.0,
                    scale, centerX, centerY);
            String text = format(values.yAxis[index]);
            g.drawLine(point.x - 4, point.y, point.x, point.y);
            g.drawString(text, point.x - metrics.stringWidth(text) - 7,
                    point.y + metrics.getAscent() / 2);
        }
    }

    private void paintValueTicks(Graphics2D g, SurfaceValues values,
            double scale, double centerX, double centerY) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
        FontMetrics metrics = g.getFontMetrics();
        for (int step = 0; step <= 2; step++) {
            double normalized = step / 2.0;
            ProjectedPoint point = project(-1.0, 1.0,
                    normalized * 1.20, scale, centerX, centerY);
            double value = values.minimum
                    + (values.maximum - values.minimum) * normalized;
            String text = format(value) + unitSuffix(table);
            g.drawLine(point.x - 4, point.y, point.x + 3, point.y);
            g.drawString(text, point.x - metrics.stringWidth(text) - 7,
                    point.y + metrics.getAscent() / 2);
        }
    }

    private void paintValueCallout(Graphics2D g, SurfaceValues values,
            ProjectedPoint[][] points) {
        int x = hoverX;
        int y = hoverY;
        if (!values.contains(x, y)) {
            int[] selected = values.firstSelected();
            if (selected == null) return;
            x = selected[0];
            y = selected[1];
        }
        String[] lines = cellDescriptionParts(values, x, y);
        FontMetrics metrics = g.getFontMetrics();
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, metrics.stringWidth(line));
        }
        int width = textWidth + 18;
        int height = metrics.getHeight() * lines.length + 10;
        int left = Math.max(10, Math.min(points[x][y].x + 10,
                getWidth() - width - 10));
        int top = Math.max(26, Math.min(points[x][y].y - height - 8,
                getHeight() - height - 28));
        g.setColor(withAlpha(UiThemeService.getInstance().color(
                ThemeToken.RAISED_SURFACE), 235));
        g.fillRoundRect(left, top, width, height, 8, 8);
        g.setColor(UiThemeService.getInstance().color(ThemeToken.ACCENT));
        g.drawRoundRect(left, top, width, height, 8, 8);
        g.setColor(UiThemeService.getInstance().color(ThemeToken.PRIMARY_TEXT));
        for (int line = 0; line < lines.length; line++) {
            g.drawString(lines[line], left + 9,
                    top + 5 + metrics.getAscent() + line * metrics.getHeight());
        }
        ProjectedPoint point = points[x][y];
        g.fillOval(point.x - 4, point.y - 4, 8, 8);
    }

    private boolean containsHover(int x, int y) {
        return hoverX >= x && hoverX <= x + 1
                && hoverY >= y && hoverY <= y + 1;
    }

    private void updateHover(Point pointer) {
        ProjectedPoint[][] points = projectedPoints;
        if (points == null) return;
        int nearestX = -1;
        int nearestY = -1;
        double nearestDistance = 18.0 * 18.0;
        for (int x = 0; x < points.length; x++) {
            for (int y = 0; y < points[x].length; y++) {
                double dx = pointer.x - points[x][y].x;
                double dy = pointer.y - points[x][y].y;
                double distance = dx * dx + dy * dy;
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearestX = x;
                    nearestY = y;
                }
            }
        }
        if (hoverX != nearestX || hoverY != nearestY) {
            hoverX = nearestX;
            hoverY = nearestY;
            setCursor(nearestX >= 0
                    ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                    : Cursor.getDefaultCursor());
            repaint();
        }
    }

    private void clearHover() {
        if (hoverX < 0 && hoverY < 0) return;
        hoverX = -1;
        hoverY = -1;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    private boolean selectCell(int x, int y) {
        SurfaceValues values = SurfaceValues.read(table);
        if (!values.contains(x, y)) return false;
        try {
            table.selectCellAt(x, y);
        } catch (NullPointerException | IndexOutOfBoundsException incompleteAxisData) {
            // Definition previews and partially populated tables can lack axis
            // cells. Surface selection must remain usable and isolated.
            DataCell[][] data = table.get3dData();
            for (DataCell[] column : data) {
                if (column == null) continue;
                for (DataCell cell : column) {
                    if (cell != null) cell.setSelected(false);
                }
            }
            if (data[x][y] == null) return false;
            data[x][y].setSelected(true);
        }
        if (table.getTableView() != null) table.getTableView().drawTable();
        hoverX = x;
        hoverY = y;
        repaint();
        return true;
    }

    private String cellDescription(SurfaceValues values, int x, int y) {
        if (values == null || !values.contains(x, y)) return "No cell value";
        return String.join(" • ", cellDescriptionParts(values, x, y));
    }

    private String[] cellDescriptionParts(SurfaceValues values, int x, int y) {
        return new String[] {
            shortLabel(table == null ? null : table.getXAxis(), "X")
                    + ": " + format(values.xAxis[x])
                    + unitSuffix(table == null ? null : table.getXAxis()),
            shortLabel(table == null ? null : table.getYAxis(), "Y")
                    + ": " + format(values.yAxis[y])
                    + unitSuffix(table == null ? null : table.getYAxis()),
            "Value: " + format(values.values[x][y]) + unitSuffix(table)
        };
    }

    private static int[] tickIndices(int size) {
        if (size <= 1) return new int[] {0};
        if (size == 2) return new int[] {0, 1};
        return new int[] {0, (size - 1) / 2, size - 1};
    }

    private void paintMessage(Graphics2D g, String message) {
        g.setColor(UiThemeService.getInstance().color(ThemeToken.SECONDARY_TEXT));
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(message, Math.max(12, (getWidth() - metrics.stringWidth(message)) / 2),
                Math.max(24, getHeight() / 2));
    }

    private void refreshWhenValuesChange() {
        long current = fingerprint();
        if (current != valueFingerprint) {
            valueFingerprint = current;
            repaint();
        }
    }

    private void updateMonitorState() {
        if (isShowing()) {
            changeMonitor.start();
        } else {
            changeMonitor.stop();
        }
    }

    private long fingerprint() {
        long result = 17L;
        DataCell[][] data = table == null ? null : table.get3dData();
        if (data == null) return result;
        for (DataCell[] column : data) {
            if (column == null) continue;
            for (DataCell cell : column) {
                long bits = cell == null ? 0L
                        : Double.doubleToLongBits(cell.getRealValue());
                result = result * 31L + bits;
                result = result * 31L + (cell != null && cell.isSelected() ? 1L : 0L);
            }
        }
        return result;
    }

    private static Color surfaceColor(double value) {
        double clamped = clamp(value, 0.0, 1.0);
        float hue = (float) (0.62 - clamped * 0.62);
        return Color.getHSBColor(hue, 0.78f, 0.92f);
    }

    private static Color withAlpha(Color color, int alpha) {
        if (color == null) color = Color.GRAY;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static String label(Table axis, String fallback) {
        if (axis == null || axis.getName() == null
                || axis.getName().trim().isEmpty()) return fallback;
        String name = axis.getName().trim();
        Scale scale = axis.getCurrentScale();
        if (scale == null || scale.getUnit() == null
                || scale.getUnit().trim().isEmpty()
                || "raw value".equalsIgnoreCase(scale.getUnit().trim())) {
            return name;
        }
        return name + " (" + scale.getUnit().trim() + ")";
    }

    private static String shortLabel(Table axis, String fallback) {
        if (axis == null || axis.getName() == null
                || axis.getName().trim().isEmpty()) return fallback;
        return axis.getName().trim();
    }

    private static String unitSuffix(Table valueTable) {
        if (valueTable == null) return "";
        Scale scale = valueTable.getCurrentScale();
        if (scale == null || scale.getUnit() == null) return "";
        String unit = scale.getUnit().trim();
        return unit.isEmpty() || "raw value".equalsIgnoreCase(unit)
                ? "" : " " + unit;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String format(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.0001) return Long.toString((long) rounded);
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class SurfaceValues {
        private final double[][] values;
        private final boolean[][] selected;
        private final int width;
        private final int height;
        private final double minimum;
        private final double maximum;
        private final double[] xAxis;
        private final double[] yAxis;

        private SurfaceValues(double[][] values, boolean[][] selected,
                int width, int height,
                double minimum, double maximum,
                double[] xAxis, double[] yAxis) {
            this.values = values;
            this.selected = selected;
            this.width = width;
            this.height = height;
            this.minimum = minimum;
            this.maximum = maximum;
            this.xAxis = xAxis;
            this.yAxis = yAxis;
        }

        static SurfaceValues read(Table3D table) {
            DataCell[][] cells = table == null ? null : table.get3dData();
            if (cells == null || cells.length == 0 || cells[0] == null
                    || cells[0].length == 0) {
                return empty();
            }
            int width = cells.length;
            int height = cells[0].length;
            double[][] values = new double[width][height];
            boolean[][] selected = new boolean[width][height];
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (int x = 0; x < width; x++) {
                if (cells[x] == null || cells[x].length != height) {
                    return empty();
                }
                for (int y = 0; y < height; y++) {
                    double value = cells[x][y] == null ? Double.NaN
                            : cells[x][y].getRealValue();
                    if (Double.isNaN(value) || Double.isInfinite(value)) value = 0.0;
                    values[x][y] = value;
                    selected[x][y] = cells[x][y] != null
                            && cells[x][y].isSelected();
                    minimum = Math.min(minimum, value);
                    maximum = Math.max(maximum, value);
                }
            }
            return new SurfaceValues(values, selected, width, height,
                    minimum, maximum, axisValues(table.getXAxis(), width),
                    axisValues(table.getYAxis(), height));
        }

        private static SurfaceValues empty() {
            return new SurfaceValues(new double[0][0], new boolean[0][0],
                    0, 0, 0.0, 0.0, new double[0], new double[0]);
        }

        private static double[] axisValues(Table1D axis, int size) {
            double[] result = new double[size];
            DataCell[] data = axis == null ? null : axis.getData();
            for (int index = 0; index < size; index++) {
                DataCell cell = data != null && index < data.length
                        ? data[index] : null;
                double value = cell == null ? index : cell.getRealValue();
                result[index] = Double.isNaN(value) || Double.isInfinite(value)
                        ? index : value;
            }
            return result;
        }

        boolean isRenderable() {
            return width > 0 && height > 0;
        }

        double normalized(int x, int y) {
            double range = maximum - minimum;
            return Math.abs(range) < EPSILON ? 0.45
                    : (values[x][y] - minimum) / range;
        }

        boolean isSelected(int x, int y) {
            return selected[x][y];
        }

        boolean contains(int x, int y) {
            return x >= 0 && x < width && y >= 0 && y < height;
        }

        int[] firstSelected() {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (selected[x][y]) return new int[] {x, y};
                }
            }
            return null;
        }
    }

    private static final class ProjectedPoint {
        private final int x;
        private final int y;
        private final double depth;

        private ProjectedPoint(int x, int y, double depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }

    private static final class SurfaceCell {
        private final Polygon polygon;
        private final double depth;
        private final double level;
        private final boolean selected;
        private final boolean hovered;

        private SurfaceCell(Polygon polygon, double depth, double level,
                boolean selected, boolean hovered) {
            this.polygon = polygon;
            this.depth = depth;
            this.level = level;
            this.selected = selected;
            this.hovered = hovered;
        }
    }
}
