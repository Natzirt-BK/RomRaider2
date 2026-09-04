/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import java.util.Objects;

/** Saved presentation and position of one dashboard parameter. */
public final class LoggerDashboardTile {
    private final LoggerDashboardTileRole role;
    private final LoggerDashboardTileSize size;
    private final int order;
    private final String accentColor;
    private final Double customWidth;
    private final Double customHeight;

    public LoggerDashboardTile(LoggerDashboardTileRole role,
            LoggerDashboardTileSize size, int order) {
        this(role, size, order, "", null, null);
    }

    public LoggerDashboardTile(LoggerDashboardTileRole role,
            LoggerDashboardTileSize size, int order, String accentColor,
            Double customWidth, Double customHeight) {
        if (role == null || size == null) {
            throw new IllegalArgumentException("Dashboard role and size are required");
        }
        if (order < 0) {
            throw new IllegalArgumentException("Dashboard order cannot be negative");
        }
        if ((customWidth == null) != (customHeight == null)) {
            throw new IllegalArgumentException(
                    "Both custom dashboard dimensions are required");
        }
        if (customWidth != null && (!Double.isFinite(customWidth)
                || !Double.isFinite(customHeight)
                || customWidth < 210.0 || customWidth > 700.0
                || customHeight < 190.0 || customHeight > 520.0)) {
            throw new IllegalArgumentException(
                    "Custom dashboard dimensions are outside the supported range");
        }
        this.role = role;
        this.size = size;
        this.order = order;
        this.accentColor = accentColor == null ? "" : accentColor.trim();
        this.customWidth = customWidth;
        this.customHeight = customHeight;
    }

    public LoggerDashboardTileRole getRole() { return role; }
    public LoggerDashboardTileSize getSize() { return size; }
    public int getOrder() { return order; }
    public String getAccentColor() { return accentColor; }
    public Double getCustomWidth() { return customWidth; }
    public Double getCustomHeight() { return customHeight; }
    public boolean hasCustomSize() {
        return customWidth != null && customHeight != null;
    }

    public LoggerDashboardTile withRole(LoggerDashboardTileRole next) {
        return new LoggerDashboardTile(next, size, order, accentColor,
                customWidth, customHeight);
    }

    public LoggerDashboardTile withSize(LoggerDashboardTileSize next) {
        return new LoggerDashboardTile(role, next, order, accentColor,
                customWidth, customHeight);
    }

    public LoggerDashboardTile withOrder(int next) {
        return new LoggerDashboardTile(role, size, next, accentColor,
                customWidth, customHeight);
    }

    public LoggerDashboardTile withAccentColor(String next) {
        return new LoggerDashboardTile(role, size, order, next,
                customWidth, customHeight);
    }

    public LoggerDashboardTile withCustomSize(double width, double height) {
        return new LoggerDashboardTile(role, size, order, accentColor,
                Double.valueOf(width), Double.valueOf(height));
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LoggerDashboardTile)) return false;
        LoggerDashboardTile tile = (LoggerDashboardTile) other;
        return role == tile.role && size == tile.size && order == tile.order
                && Objects.equals(accentColor, tile.accentColor)
                && Objects.equals(customWidth, tile.customWidth)
                && Objects.equals(customHeight, tile.customHeight);
    }

    @Override public int hashCode() {
        int result = role.hashCode();
        result = 31 * result + size.hashCode();
        result = 31 * result + order;
        result = 31 * result + accentColor.hashCode();
        result = 31 * result + Objects.hashCode(customWidth);
        return 31 * result + Objects.hashCode(customHeight);
    }
}
