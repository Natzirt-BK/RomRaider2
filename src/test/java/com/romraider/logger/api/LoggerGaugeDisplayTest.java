/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.romraider.Settings;
import com.romraider.swing.JProgressPane;
import com.romraider.xml.DOMSettingsBuilder;
import com.romraider.xml.DOMSettingsUnmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class LoggerGaugeDisplayTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void assignmentsAreIndependentImmutableAndSwapWithoutDuplicates() {
        LoggerGaugeDisplay empty = new LoggerGaugeDisplay();
        assertTrue(empty.getVisibleChannels().isEmpty());
        LoggerGaugeDisplay assigned = empty.withChannel(0, "A").withChannel(5, "B");
        assertEquals(Arrays.asList("A", "B"), assigned.getVisibleChannels());
        LoggerGaugeDisplay reduced = assigned.withCount(1);
        assertEquals(Arrays.asList("A"), reduced.getVisibleChannels());
        assertEquals(assigned, reduced.withCount(6));
        assertEquals(Arrays.asList("B", "", "", "", "", "A"), assigned.withChannel(0, "B").getSlots());
        assertTrue(empty.getVisibleChannels().isEmpty());
        try { assigned.getSlots().set(0, "changed"); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void explicitLoggerCopyIsOrderedBoundedAndNeverChangesSelection() {
        List<LoggerChannel> channels = new ArrayList<>();
        channels.add(new LoggerChannel("unselected", "Unselected", "V", LoggerChannelKind.PARAMETER, false));
        for (int i = 0; i < 8; i++) channels.add(new LoggerChannel("P" + i, "Channel " + i, "V", LoggerChannelKind.PARAMETER, true));
        LoggerGaugeDisplay display = new LoggerGaugeDisplay().useLoggerChannels(channels);
        assertEquals(Arrays.asList("P0", "P1", "P2", "P3", "P4", "P5"), display.getSlots());
        assertEquals(6, display.getCount());
        assertEquals(8, channels.stream().filter(LoggerChannel::isSelected).count());
        assertTrue(display.useLoggerChannels(Collections.emptyList()).getVisibleChannels().isEmpty());
    }
    @Test public void rejectsInvalidSettingsAndRoundTripsHiddenSlots() throws Exception {
        for (int count : new int[]{0, 7}) try { new LoggerGaugeDisplay().withCount(count); fail(); }
            catch (IllegalArgumentException expected) { }
        for (String id : Arrays.asList(null, "bad\nchannel", String.join("", Collections.nCopies(241, "x"))))
            try { new LoggerGaugeDisplay().withChannel(0, id); fail(); } catch (IllegalArgumentException expected) { }
        try { new LoggerGaugeDisplay(6, Arrays.asList("A", "A", "", "", "", "")); fail(); }
            catch (IllegalArgumentException expected) { }
        Settings settings = new Settings();
        settings.setLoggerGaugeDisplay(new LoggerGaugeDisplay().withChannel(0, "P1").withChannel(5, "P6").withCount(2));
        java.io.File file = temporary.newFile("display.xml");
        new DOMSettingsBuilder().buildSettings(settings, file, new JProgressPane(), "test");
        Settings restored = new DOMSettingsUnmarshaller().unmarshallSettings(DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file).getDocumentElement());
        assertEquals(settings.getLoggerGaugeDisplay(), restored.getLoggerGaugeDisplay());
        assertEquals("P6", restored.getLoggerGaugeDisplay().getSlots().get(5));
    }
    @Test public void preferencesPersistOnlyChangedDisplayValues() {
        AtomicReference<LoggerGaugeDisplay> saved = new AtomicReference<>();
        LoggerWorkspacePreferences preferences = new LoggerWorkspacePreferences(LoggerWorkspaceView.DASHBOARD,
                false, LoggerGaugeTheme.RR2_CLASSIC, (view, dark) -> {}, theme -> {}, LoggerGaugeLayout.STANDARD,
                layout -> {}, new LinkedHashMap<>(), (id, limits) -> {}, new LinkedHashMap<>(), (id, tile) -> {},
                true, visible -> {}, new LoggerGaugeDisplay(), saved::set);
        preferences.setGaugeDisplay(new LoggerGaugeDisplay()); assertNull(saved.get());
        LoggerGaugeDisplay display = new LoggerGaugeDisplay().withChannel(0, "unqueried");
        preferences.setGaugeDisplay(display); assertSame(display, saved.get());
        assertSame(display, preferences.getGaugeDisplay());
    }
}
