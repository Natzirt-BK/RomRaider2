/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static com.romraider.util.ParamChecker.checkNotNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.Map;
import java.util.LinkedHashMap;

/** Toolkit-neutral state for replacement Logger presentation preferences. */
public final class LoggerWorkspacePreferences {
    private final BiConsumer<LoggerWorkspaceView, Boolean> persistence;
    private final Consumer<LoggerGaugeTheme> gaugePersistence;
    private final Consumer<LoggerGaugeLayout> gaugeLayoutPersistence;
    private final BiConsumer<String, LoggerGaugeConfiguration>
            gaugeConfigurationPersistence;
    private final BiConsumer<String, LoggerDashboardTile>
            dashboardTilePersistence;
    private final Consumer<Boolean> channelRailPersistence;
    private final Map<String, LoggerGaugeConfiguration> gaugeConfigurations;
    private final Map<String, LoggerDashboardTile> dashboardTiles;
    private volatile LoggerWorkspaceView view;
    private volatile boolean darkTheme;
    private volatile LoggerGaugeTheme gaugeTheme;
    private volatile LoggerGaugeLayout gaugeLayout;
    private volatile boolean channelRailVisible;

    public LoggerWorkspacePreferences(LoggerWorkspaceView view,
            boolean darkTheme,
            BiConsumer<LoggerWorkspaceView, Boolean> persistence) {
        this(view, darkTheme, LoggerGaugeTheme.RR2_CLASSIC, persistence,
                theme -> { });
    }

    public LoggerWorkspacePreferences(LoggerWorkspaceView view,
            boolean darkTheme, LoggerGaugeTheme gaugeTheme,
            BiConsumer<LoggerWorkspaceView, Boolean> persistence,
            Consumer<LoggerGaugeTheme> gaugePersistence) {
        this(view, darkTheme, gaugeTheme, persistence, gaugePersistence,
                LoggerGaugeLayout.STANDARD, layout -> { },
                new LinkedHashMap<String, LoggerGaugeConfiguration>(),
                (parameterId, configuration) -> { },
                new LinkedHashMap<String, LoggerDashboardTile>(),
                (parameterId, tile) -> { }, true, visible -> { });
    }

    public LoggerWorkspacePreferences(LoggerWorkspaceView view,
            boolean darkTheme, LoggerGaugeTheme gaugeTheme,
            BiConsumer<LoggerWorkspaceView, Boolean> persistence,
            Consumer<LoggerGaugeTheme> gaugePersistence,
            LoggerGaugeLayout gaugeLayout,
            Consumer<LoggerGaugeLayout> gaugeLayoutPersistence,
            Map<String, LoggerGaugeConfiguration> gaugeConfigurations,
            BiConsumer<String, LoggerGaugeConfiguration>
                    gaugeConfigurationPersistence) {
        this(view, darkTheme, gaugeTheme, persistence, gaugePersistence,
                gaugeLayout, gaugeLayoutPersistence, gaugeConfigurations,
                gaugeConfigurationPersistence,
                new LinkedHashMap<String, LoggerDashboardTile>(),
                (parameterId, tile) -> { }, true, visible -> { });
    }

    public LoggerWorkspacePreferences(LoggerWorkspaceView view,
            boolean darkTheme, LoggerGaugeTheme gaugeTheme,
            BiConsumer<LoggerWorkspaceView, Boolean> persistence,
            Consumer<LoggerGaugeTheme> gaugePersistence,
            LoggerGaugeLayout gaugeLayout,
            Consumer<LoggerGaugeLayout> gaugeLayoutPersistence,
            Map<String, LoggerGaugeConfiguration> gaugeConfigurations,
            BiConsumer<String, LoggerGaugeConfiguration>
                    gaugeConfigurationPersistence,
            Map<String, LoggerDashboardTile> dashboardTiles,
            BiConsumer<String, LoggerDashboardTile> dashboardTilePersistence) {
        this(view, darkTheme, gaugeTheme, persistence, gaugePersistence,
                gaugeLayout, gaugeLayoutPersistence, gaugeConfigurations,
                gaugeConfigurationPersistence, dashboardTiles,
                dashboardTilePersistence, true, visible -> { });
    }

    public LoggerWorkspacePreferences(LoggerWorkspaceView view,
            boolean darkTheme, LoggerGaugeTheme gaugeTheme,
            BiConsumer<LoggerWorkspaceView, Boolean> persistence,
            Consumer<LoggerGaugeTheme> gaugePersistence,
            LoggerGaugeLayout gaugeLayout,
            Consumer<LoggerGaugeLayout> gaugeLayoutPersistence,
            Map<String, LoggerGaugeConfiguration> gaugeConfigurations,
            BiConsumer<String, LoggerGaugeConfiguration>
                    gaugeConfigurationPersistence,
            Map<String, LoggerDashboardTile> dashboardTiles,
            BiConsumer<String, LoggerDashboardTile> dashboardTilePersistence,
            boolean channelRailVisible,
            Consumer<Boolean> channelRailPersistence) {
        checkNotNull(view, gaugeTheme, gaugeLayout, persistence,
                gaugePersistence, gaugeLayoutPersistence);
        checkNotNull(gaugeConfigurations, gaugeConfigurationPersistence);
        checkNotNull(dashboardTiles, dashboardTilePersistence);
        checkNotNull(channelRailPersistence);
        this.view = view;
        this.darkTheme = darkTheme;
        this.gaugeTheme = gaugeTheme;
        this.gaugeLayout = gaugeLayout;
        this.persistence = persistence;
        this.gaugePersistence = gaugePersistence;
        this.gaugeLayoutPersistence = gaugeLayoutPersistence;
        this.gaugeConfigurations = new LinkedHashMap<
                String, LoggerGaugeConfiguration>(gaugeConfigurations);
        this.gaugeConfigurationPersistence = gaugeConfigurationPersistence;
        this.dashboardTiles = new LinkedHashMap<String, LoggerDashboardTile>(
                dashboardTiles);
        this.dashboardTilePersistence = dashboardTilePersistence;
        this.channelRailVisible = channelRailVisible;
        this.channelRailPersistence = channelRailPersistence;
    }

    public LoggerWorkspaceView getView() {
        return view;
    }

    public boolean isDarkTheme() {
        return darkTheme;
    }

    public LoggerGaugeTheme getGaugeTheme() {
        return gaugeTheme;
    }

    public LoggerGaugeLayout getGaugeLayout() {
        return gaugeLayout;
    }

    public boolean isChannelRailVisible() {
        return channelRailVisible;
    }

    public void setView(LoggerWorkspaceView next) {
        if (next == null || next == view) return;
        view = next;
        persist();
    }

    public void setDarkTheme(boolean dark) {
        if (dark == darkTheme) return;
        darkTheme = dark;
        persist();
    }

    public void setGaugeTheme(LoggerGaugeTheme next) {
        if (next == null || next == gaugeTheme) return;
        gaugeTheme = next;
        gaugePersistence.accept(next);
    }

    public void setGaugeLayout(LoggerGaugeLayout next) {
        if (next == null || next == gaugeLayout) return;
        gaugeLayout = next;
        gaugeLayoutPersistence.accept(next);
    }

    public void setChannelRailVisible(boolean visible) {
        if (visible == channelRailVisible) return;
        channelRailVisible = visible;
        channelRailPersistence.accept(Boolean.valueOf(visible));
    }

    public synchronized LoggerGaugeConfiguration getGaugeConfiguration(
            String parameterId) {
        return gaugeConfigurations.get(parameterId);
    }

    public synchronized void setGaugeConfiguration(String parameterId,
            LoggerGaugeConfiguration configuration) {
        if (parameterId == null || parameterId.trim().isEmpty()) {
            throw new IllegalArgumentException("Logger parameter ID is required");
        }
        if (configuration == null) {
            gaugeConfigurations.remove(parameterId);
        } else {
            gaugeConfigurations.put(parameterId, configuration);
        }
        gaugeConfigurationPersistence.accept(parameterId, configuration);
    }

    public synchronized LoggerDashboardTile getDashboardTile(
            String parameterId) {
        return dashboardTiles.get(parameterId);
    }

    public synchronized Map<String, LoggerDashboardTile> getDashboardTiles() {
        return new LinkedHashMap<String, LoggerDashboardTile>(dashboardTiles);
    }

    public synchronized void setDashboardTile(String parameterId,
            LoggerDashboardTile tile) {
        if (parameterId == null || parameterId.trim().isEmpty()) {
            throw new IllegalArgumentException("Logger parameter ID is required");
        }
        if (tile == null) dashboardTiles.remove(parameterId);
        else dashboardTiles.put(parameterId, tile);
        dashboardTilePersistence.accept(parameterId, tile);
    }

    private void persist() {
        persistence.accept(view, Boolean.valueOf(darkTheme));
    }
}
