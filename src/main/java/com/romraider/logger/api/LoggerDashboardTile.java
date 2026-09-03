/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

/** Saved presentation and position of one dashboard parameter. */
public final class LoggerDashboardTile {
    private final LoggerDashboardTileRole role;
    private final LoggerDashboardTileSize size;
    private final int order;

    public LoggerDashboardTile(LoggerDashboardTileRole role,
            LoggerDashboardTileSize size, int order) {
        if (role == null || size == null) {
            throw new IllegalArgumentException("Dashboard role and size are required");
        }
        if (order < 0) {
            throw new IllegalArgumentException("Dashboard order cannot be negative");
        }
        this.role = role;
        this.size = size;
        this.order = order;
    }

    public LoggerDashboardTileRole getRole() { return role; }
    public LoggerDashboardTileSize getSize() { return size; }
    public int getOrder() { return order; }

    public LoggerDashboardTile withRole(LoggerDashboardTileRole next) {
        return new LoggerDashboardTile(next, size, order);
    }

    public LoggerDashboardTile withSize(LoggerDashboardTileSize next) {
        return new LoggerDashboardTile(role, next, order);
    }

    public LoggerDashboardTile withOrder(int next) {
        return new LoggerDashboardTile(role, size, next);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LoggerDashboardTile)) return false;
        LoggerDashboardTile tile = (LoggerDashboardTile) other;
        return role == tile.role && size == tile.size && order == tile.order;
    }

    @Override public int hashCode() {
        int result = role.hashCode();
        result = 31 * result + size.hashCode();
        return 31 * result + order;
    }
}
