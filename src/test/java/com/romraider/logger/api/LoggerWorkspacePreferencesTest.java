/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;

import com.romraider.Settings;
import com.romraider.xml.DOMSettingsUnmarshaller;

public class LoggerWorkspacePreferencesTest {
    @Test
    public void persistsChannelRailVisibilityOnlyWhenItChanges() {
        AtomicInteger updates = new AtomicInteger();
        AtomicReference<Boolean> visible = new AtomicReference<Boolean>();
        LoggerWorkspacePreferences preferences =
                new LoggerWorkspacePreferences(LoggerWorkspaceView.DATA,
                        false, LoggerGaugeTheme.RR2_CLASSIC,
                        (view, dark) -> { }, theme -> { },
                        LoggerGaugeLayout.STANDARD, layout -> { },
                        new LinkedHashMap<String, LoggerGaugeConfiguration>(),
                        (id, configuration) -> { },
                        new LinkedHashMap<String, LoggerDashboardTile>(),
                        (id, tile) -> { }, true, next -> {
                            updates.incrementAndGet();
                            visible.set(next);
                        });

        preferences.setChannelRailVisible(true);
        preferences.setChannelRailVisible(false);

        assertFalse(preferences.isChannelRailVisible());
        assertEquals(1, updates.get());
        assertEquals(Boolean.FALSE, visible.get());
    }

    @Test
    public void publishesOnlyChangedWorkspacePreferences() {
        AtomicInteger updates = new AtomicInteger();
        AtomicReference<LoggerWorkspaceView> view =
                new AtomicReference<LoggerWorkspaceView>();
        AtomicReference<Boolean> dark = new AtomicReference<Boolean>();
        LoggerWorkspacePreferences preferences =
                new LoggerWorkspacePreferences(LoggerWorkspaceView.DATA,
                        false, (nextView, nextDark) -> {
                            updates.incrementAndGet();
                            view.set(nextView);
                            dark.set(nextDark);
                        });

        preferences.setView(LoggerWorkspaceView.DATA);
        preferences.setDarkTheme(false);
        assertEquals(0, updates.get());

        preferences.setView(LoggerWorkspaceView.GRAPH);
        preferences.setDarkTheme(true);

        assertEquals(2, updates.get());
        assertEquals(LoggerWorkspaceView.GRAPH, view.get());
        assertTrue(dark.get().booleanValue());
        assertEquals(LoggerWorkspaceView.GRAPH, preferences.getView());
        assertTrue(preferences.isDarkTheme());
        assertEquals(LoggerGaugeTheme.RR2_CLASSIC,
                preferences.getGaugeTheme());
    }

    @Test
    public void restoresWorkspaceViewAndOptionalTheme() throws Exception {
        Settings saved = load("<settings><logger><tabs workspace=\"DASHBOARD\" "
                + "workspace-dark=\"true\" gauge-theme=\"NEON_CIRCUIT\" "
                + "gauge-layout=\"LARGE\"/>"
                + "</logger></settings>");
        assertEquals(LoggerWorkspaceView.DASHBOARD,
                saved.getLoggerWorkspaceView());
        assertTrue(saved.getLoggerWorkspaceDarkTheme().booleanValue());
        assertEquals(LoggerGaugeTheme.NEON_CIRCUIT,
                saved.getLoggerGaugeTheme());
        assertEquals(LoggerGaugeLayout.LARGE,
                saved.getLoggerGaugeLayout());

        Settings legacy = load("<settings><logger><tabs workspace=\"future\"/>"
                + "</logger></settings>");
        assertEquals(LoggerWorkspaceView.OVERVIEW,
                legacy.getLoggerWorkspaceView());
        assertNull(legacy.getLoggerWorkspaceDarkTheme());
        assertEquals(LoggerGaugeTheme.RR2_CLASSIC,
                legacy.getLoggerGaugeTheme());
        assertEquals(LoggerGaugeLayout.STANDARD,
                legacy.getLoggerGaugeLayout());
    }

    @Test
    public void viewNamesHaveSafeDefaults() {
        assertEquals(LoggerWorkspaceView.OVERVIEW,
                LoggerWorkspaceView.fromName(null));
        assertEquals(LoggerWorkspaceView.OVERVIEW,
                LoggerWorkspaceView.fromName("unknown"));
        assertEquals(LoggerWorkspaceView.GRAPH,
                LoggerWorkspaceView.fromName(" graph "));
        assertFalse(LoggerWorkspaceView.DATA.getDisplayName().isEmpty());
        assertEquals(LoggerGaugeTheme.RR2_CLASSIC,
                LoggerGaugeTheme.fromName("future"));
        assertEquals(LoggerGaugeTheme.AMBER_GT,
                LoggerGaugeTheme.fromName(" amber_gt "));
    }

    @Test
    public void gaugeWarningsUseHysteresisAndNeverAssumeLimits() {
        LoggerGaugeConfiguration configuration =
                new LoggerGaugeConfiguration(Double.valueOf(-15),
                        Double.valueOf(30), Double.valueOf(-10),
                        Double.valueOf(20), 1.5);
        assertEquals(LoggerGaugeConfiguration.AlertState.HIGH,
                configuration.alertState(20,
                        LoggerGaugeConfiguration.AlertState.NORMAL));
        assertEquals(LoggerGaugeConfiguration.AlertState.HIGH,
                configuration.alertState(19,
                        LoggerGaugeConfiguration.AlertState.HIGH));
        assertEquals(LoggerGaugeConfiguration.AlertState.NORMAL,
                configuration.alertState(18.4,
                        LoggerGaugeConfiguration.AlertState.HIGH));
        assertEquals(LoggerGaugeConfiguration.AlertState.LOW,
                configuration.alertState(-10.5,
                        LoggerGaugeConfiguration.AlertState.NORMAL));
        assertTrue(configuration.hasCustomScale());
        assertTrue(configuration.hasWarnings());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsContradictoryGaugeWarningLimits() {
        new LoggerGaugeConfiguration(null, null, Double.valueOf(20),
                Double.valueOf(10), 0);
    }

    @Test
    public void restoresValidGaugeConfigurationAndSkipsCorruptEntries()
            throws Exception {
        Settings settings = load("<settings><logger>"
                + "<gauge-configurations schema=\"1\">"
                + "<channel id=\"P-BOOST\" scale-min=\"-15\" "
                + "scale-max=\"30\" warn-high=\"18.5\" "
                + "hysteresis=\"1.0\"/>"
                + "<channel id=\"BAD\" scale-min=\"20\" "
                + "scale-max=\"10\" hysteresis=\"0\"/>"
                + "</gauge-configurations></logger></settings>");
        LoggerGaugeConfiguration boost = settings
                .getLoggerGaugeConfigurations().get("P-BOOST");
        assertEquals(Double.valueOf(-15), boost.getScaleMinimum());
        assertEquals(Double.valueOf(18.5), boost.getHighWarning());
        assertEquals(1.0, boost.getHysteresis(), 0.0);
        assertFalse(settings.getLoggerGaugeConfigurations().containsKey("BAD"));
    }

    @Test
    public void persistsPerChannelGaugeConfigurationChanges() {
        AtomicReference<String> id = new AtomicReference<String>();
        AtomicReference<LoggerGaugeConfiguration> saved =
                new AtomicReference<LoggerGaugeConfiguration>();
        LoggerWorkspacePreferences preferences =
                new LoggerWorkspacePreferences(LoggerWorkspaceView.DASHBOARD,
                        true, LoggerGaugeTheme.RR2_CLASSIC,
                        (view, dark) -> { }, theme -> { },
                        LoggerGaugeLayout.STANDARD, layout -> { },
                        new LinkedHashMap<String, LoggerGaugeConfiguration>(),
                        (parameterId, configuration) -> {
                            id.set(parameterId);
                            saved.set(configuration);
                        });
        LoggerGaugeConfiguration configuration =
                new LoggerGaugeConfiguration(null, null, null,
                        Double.valueOf(7000), 100);
        preferences.setGaugeConfiguration("P-RPM", configuration);
        assertEquals("P-RPM", id.get());
        assertEquals(configuration, saved.get());
        assertEquals(configuration,
                preferences.getGaugeConfiguration("P-RPM"));
    }

    @Test
    public void gaugeLayoutNamesHaveSafeDefaults() {
        assertEquals(LoggerGaugeLayout.STANDARD,
                LoggerGaugeLayout.fromName("future"));
        assertEquals(LoggerGaugeLayout.COMPACT,
                LoggerGaugeLayout.fromName(" compact "));
    }

    @Test
    public void restoresDashboardTileLayoutAndSkipsCorruptEntries()
            throws Exception {
        Settings settings = load("<settings><logger>"
                + "<dashboard-layout schema=\"1\">"
                + "<tile id=\"P-RPM\" role=\"TREND\" size=\"WIDE\" "
                + "order=\"2\" color=\"#17a2b8\" custom-width=\"460\" "
                + "custom-height=\"280\"/>"
                + "<tile id=\"BAD\" role=\"GAUGE\" size=\"STANDARD\" "
                + "order=\"-1\"/>"
                + "</dashboard-layout></logger></settings>");

        LoggerDashboardTile rpm = settings.getLoggerDashboardTiles()
                .get("P-RPM");
        assertEquals(LoggerDashboardTileRole.TREND, rpm.getRole());
        assertEquals(LoggerDashboardTileSize.WIDE, rpm.getSize());
        assertEquals(2, rpm.getOrder());
        assertEquals("#17a2b8", rpm.getAccentColor());
        assertEquals(Double.valueOf(460), rpm.getCustomWidth());
        assertEquals(Double.valueOf(280), rpm.getCustomHeight());
        LoggerDashboardTile resized = rpm.withRole(
                LoggerDashboardTileRole.ALARM);
        assertEquals("#17a2b8", resized.getAccentColor());
        assertEquals(Double.valueOf(460), resized.getCustomWidth());
        assertFalse(settings.getLoggerDashboardTiles().containsKey("BAD"));
    }

    @Test
    public void persistsDashboardTileChanges() {
        AtomicReference<String> id = new AtomicReference<String>();
        AtomicReference<LoggerDashboardTile> saved =
                new AtomicReference<LoggerDashboardTile>();
        LoggerWorkspacePreferences preferences =
                new LoggerWorkspacePreferences(LoggerWorkspaceView.DASHBOARD,
                        false, LoggerGaugeTheme.RR2_CLASSIC,
                        (view, dark) -> { }, theme -> { },
                        LoggerGaugeLayout.STANDARD, layout -> { },
                        new LinkedHashMap<String, LoggerGaugeConfiguration>(),
                        (parameterId, configuration) -> { },
                        new LinkedHashMap<String, LoggerDashboardTile>(),
                        (parameterId, tile) -> {
                            id.set(parameterId);
                            saved.set(tile);
                        });
        LoggerDashboardTile tile = new LoggerDashboardTile(
                LoggerDashboardTileRole.ALARM,
                LoggerDashboardTileSize.LARGE, 1);

        preferences.setDashboardTile("P-BOOST", tile);

        assertEquals("P-BOOST", id.get());
        assertEquals(tile, saved.get());
        assertEquals(tile, preferences.getDashboardTile("P-BOOST"));
        assertEquals(LoggerDashboardTileRole.GAUGE,
                LoggerDashboardTileRole.fromName("future"));
        assertEquals(LoggerDashboardTileSize.WIDE,
                LoggerDashboardTileSize.fromName(" wide "));
    }

    private Settings load(String xml) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(new ByteArrayInputStream(
                        xml.getBytes(StandardCharsets.UTF_8)));
        return new DOMSettingsUnmarshaller().unmarshallSettings(
                document.getDocumentElement());
    }
}
