/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.*;
import static com.romraider.logger.api.LoggerGaugeConfiguration.AlertState.*;
import com.romraider.Settings;
import com.romraider.logger.ecu.definition.EcuDataConvertor;
import com.romraider.logger.ecu.ui.handler.dash.GaugeMinMax;
import com.romraider.swing.JProgressPane;
import com.romraider.xml.DOMSettingsBuilder;
import com.romraider.xml.DOMSettingsUnmarshaller;
import java.util.Collections;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LoggerGaugeSafetyTest {
    @Test public void currentCollectionHas25ChoicesAndRetainsLegacyPreferenceCompatibility() {
        assertEquals(25, LoggerGaugeTheme.selectableValues().length);
        assertEquals(25, new java.util.HashSet<>(java.util.Arrays.asList(LoggerGaugeTheme.selectableValues())).size());
        assertFalse(java.util.Arrays.asList(LoggerGaugeTheme.selectableValues()).contains(LoggerGaugeTheme.HANDHELD));
        assertSame(LoggerGaugeTheme.HANDHELD, LoggerGaugeTheme.fromName("HANDHELD"));
        for (LoggerGaugeTheme theme : LoggerGaugeTheme.selectableValues()) assertSame(theme, LoggerGaugeTheme.fromName(theme.name()));
    }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final LoggerGaugeConfiguration LIMITS = new LoggerGaugeConfiguration(
            0.0, 120.0, 10.0, 100.0, 5).forConversion("C");

    @Test public void retainsEveryReadingAcrossCoalescedAndRepeatedRenders() {
        LoggerGaugeAlertTracker tracker = new LoggerGaugeAlertTracker();
        assertEquals(HIGH, tracker.update(sample(101, "C"), LIMITS));
        assertEquals(HIGH, tracker.update(sample(98, "C"), LIMITS));
        assertEquals(HIGH, tracker.state("test", LIMITS));
        assertEquals(HIGH, tracker.state("test", LIMITS)); // Detached render has no side effects.
        assertEquals(NORMAL, tracker.update(sample(95, "C"), LIMITS));
        assertEquals(LOW, tracker.update(sample(9, "C"), LIMITS));
        assertEquals(LOW, tracker.update(sample(12, "C"), LIMITS));
        assertEquals(NORMAL, tracker.update(sample(15, "C"), LIMITS));
    }

    @Test public void warningBoundaryOverridesOppositeWideHysteresis() {
        LoggerGaugeConfiguration wide = new LoggerGaugeConfiguration(null, null, 10.0, 20.0, 100);
        assertEquals(LOW, wide.alertState(5, HIGH));
        assertEquals(HIGH, wide.alertState(25, LOW));
    }

    @Test public void invalidSamplesAreUnavailableAndResetWarningMemory() {
        LoggerGaugeAlertTracker tracker = new LoggerGaugeAlertTracker();
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            tracker.update(sample(101, "C"), LIMITS);
            assertEquals(UNAVAILABLE, tracker.update(sample(invalid, "C"), LIMITS));
            assertEquals(NORMAL, tracker.update(sample(98, "C"), LIMITS));
        }
    }

    @Test public void conversionAndConfigurationChangesResetState() {
        LoggerGaugeAlertTracker tracker = new LoggerGaugeAlertTracker();
        tracker.update(sample(101, "C"), LIMITS);
        assertEquals(UNAVAILABLE, tracker.update(sample(176, "F"), LIMITS));
        assertEquals(NORMAL, tracker.update(sample(98, "C"), LIMITS));
        tracker.update(sample(101, "C"), LIMITS);
        LoggerGaugeConfiguration changed = new LoggerGaugeConfiguration(null, null, null, 102.0, 5).forConversion("C");
        assertEquals(UNAVAILABLE, tracker.state("test", changed));
        assertEquals(NORMAL, tracker.update(sample(99, "C"), changed));
    }

    @Test public void sessionsAndChannelRemovalResetButRecordingDoesNot() {
        LoggerGaugeAlertTracker tracker = new LoggerGaugeAlertTracker();
        tracker.sessionChanged(LoggerSessionState.LIVE_ECU);
        tracker.update(sample(101, "C"), LIMITS);
        tracker.sessionChanged(LoggerSessionState.RECORDING);
        assertEquals(HIGH, tracker.update(sample(98, "C"), LIMITS));
        tracker.sessionChanged(LoggerSessionState.LIVE_ECU);
        assertEquals(HIGH, tracker.state("test", LIMITS));
        for (LoggerSessionState state : new LoggerSessionState[] {LoggerSessionState.STOPPED,
                LoggerSessionState.CONNECTING, LoggerSessionState.RECONNECTING}) {
            tracker.update(sample(101, "C"), LIMITS);
            tracker.sessionChanged(state);
            assertEquals(UNAVAILABLE, tracker.state("test", LIMITS));
            assertEquals(NORMAL, tracker.update(sample(98, "C"), LIMITS));
        }
        tracker.update(sample(101, "C"), LIMITS);
        tracker.remove("test");
        assertEquals(NORMAL, tracker.update(sample(98, "C"), LIMITS));
        tracker.update(sample(101, "C"), LIMITS);
        tracker.retainChannels(Collections.singleton(channel("F")));
        assertEquals(UNAVAILABLE, tracker.state("test", LIMITS));
        tracker.update(sample(101, "C"), LIMITS);
        tracker.retainChannels(Collections.singleton(channel("C").withSelected(false)));
        assertEquals(UNAVAILABLE, tracker.state("test", LIMITS));
    }

    @Test public void legacyAndDifferentConversionLimitsAreRetainedButInactive() {
        LoggerWorkspacePreferences preferences = new LoggerWorkspacePreferences(LoggerWorkspaceView.DASHBOARD,
                false, (view, dark) -> { });
        LoggerGaugeConfiguration legacy = new LoggerGaugeConfiguration(0.0, 120.0, null, 100.0, 5);
        preferences.setGaugeConfiguration("test", legacy);
        assertSame(legacy, preferences.getGaugeConfiguration("test"));
        assertNull(preferences.getGaugeConfiguration("test", "C"));
        assertNull(preferences.getGaugeConfiguration("test", ""));
        preferences.setGaugeConfiguration("test", LIMITS);
        assertSame(LIMITS, preferences.getGaugeConfiguration("test", "C"));
        assertNull(preferences.getGaugeConfiguration("test", "F"));
        assertSame(LIMITS, preferences.getGaugeConfiguration("test"));
        assertNotEquals(LIMITS, LIMITS.forConversion("F"));
    }

    @Test public void scopedAndLegacyLimitsSurviveActualSettingsRoundTrip() throws Exception {
        Settings original = new Settings();
        original.setLoggerGaugeConfiguration("test", LIMITS);
        original.setLoggerGaugeConfiguration("legacy", new LoggerGaugeConfiguration(null, null, null, 100.0, 5));
        java.io.File file = temporary.newFile("settings.xml");
        new DOMSettingsBuilder().buildSettings(original, file, new JProgressPane(), "test");
        Settings restored = new DOMSettingsUnmarshaller().unmarshallSettings(DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file).getDocumentElement());
        assertEquals(LIMITS, restored.getLoggerGaugeConfigurations().get("test"));
        assertTrue(restored.getLoggerGaugeConfigurations().get("test").matchesConversion("C"));
        assertFalse(restored.getLoggerGaugeConfigurations().get("test").matchesConversion("F"));
        assertFalse(restored.getLoggerGaugeConfigurations().get("legacy").matchesConversion("C"));
    }

    @Test public void identityUsesConversionSemanticsNotObjectOrOptionIndex() {
        String first = LoggerConversionIdentity.of(new Conversion("°C", "x-40"));
        assertEquals(first, LoggerConversionIdentity.of(new Conversion("°C", "x-40")));
        assertNotEquals(first, LoggerConversionIdentity.of(new Conversion("°C", "x-50")));
        assertNotEquals(first, LoggerConversionIdentity.of(new Conversion("°F", "x*1.8+32")));
        assertEquals("", LoggerConversionIdentity.of(null));
        assertEquals("C", channel("C").withSelected(false).getConversionIdentity());
    }

    @Test(expected = IllegalArgumentException.class) public void cannotBindUnknownConversion() {
        LIMITS.forConversion("");
    }

    @Test public void profileReloadAndReorderedOptionsCannotReinterpretStoredLimits() throws Exception {
        EcuDataConvertor celsius = new Conversion("C", "x-40");
        EcuDataConvertor fahrenheit = new Conversion("F", "x*1.8+32");
        String identity = LoggerConversionIdentity.of(celsius);
        LoggerGaugeConfiguration limits = LIMITS.forConversion(identity);
        java.io.File file = temporary.newFile("profile.xml");
        for (String units : new String[] {"F", "C"}) {
            com.romraider.logger.ecu.profile.UserProfile profile = new com.romraider.logger.ecu.profile.UserProfileImpl(
                    Collections.singletonMap("test", new com.romraider.logger.ecu.profile.UserProfileItemImpl(
                            units, true, false, true)), Collections.emptyMap(), Collections.emptyMap(), "SSM");
            java.nio.file.Files.write(file.toPath(), profile.getBytes());
            com.romraider.logger.ecu.profile.UserProfile restored =
                    new com.romraider.logger.ecu.profile.UserProfileLoaderImpl().loadProfile(file.getAbsolutePath());
            assertNotNull(restored);
            com.romraider.logger.ecu.definition.LoggerData data = new com.romraider.logger.ecu.definition.EcuParameterImpl(
                    "test", "Synthetic", "", new com.romraider.logger.ecu.definition.EcuAddressImpl("0x10", 1, -1),
                    null, null, null, new EcuDataConvertor[] {fahrenheit, celsius});
            // Invoke the exact profile-application path without constructing a runtime or connecting.
            java.lang.reflect.Method apply = com.romraider.logger.runtime.LoggerDesktopRuntime.class.getDeclaredMethod(
                    "applyUnits", com.romraider.logger.ecu.profile.UserProfile.class,
                    com.romraider.logger.ecu.definition.LoggerData.class);
            apply.setAccessible(true);
            apply.invoke(null, restored, data);
            assertEquals(units, data.getSelectedConvertor().getUnits());
            assertEquals(units.equals("C"), limits.matchesConversion(LoggerConversionIdentity.of(data.getSelectedConvertor())));
        }
    }

    private static LiveDataSample sample(double value, String identity) {
        return new LiveDataSample("test", "Synthetic", value, Double.toString(value), identity, 1, identity);
    }
    private static LoggerChannel channel(String identity) {
        return new LoggerChannel("test", "Synthetic", identity, LoggerChannelKind.PARAMETER,
                true, Collections.emptyList(), identity);
    }
    private static final class Conversion implements EcuDataConvertor {
        private final String units, expression;
        Conversion(String units, String expression) { this.units = units; this.expression = expression; }
        public double convert(byte[] bytes) { return 0; }
        public String format(double value) { return Double.toString(value); }
        public String getUnits() { return units; }
        public GaugeMinMax getGaugeMinMax() { return null; }
        public String getFormat() { return "0.0"; }
        public String getExpression() { return expression; }
        public String getDataType() { return "uint8"; }
    }
}
