/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import static org.junit.Assert.*;
import com.romraider.Settings;
import com.romraider.swing.JProgressPane;
import com.romraider.xml.DOMSettingsBuilder;
import com.romraider.xml.DOMSettingsUnmarshaller;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LoggerDashboardStylesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LoggerDashboardTile tile() {
        return new LoggerDashboardTile(LoggerDashboardTileRole.GAUGE, LoggerDashboardTileSize.STANDARD, 2);
    }
    @Test public void optionalOverrideInheritsAndAllLayoutEditsPreserveIt() {
        assertNull(tile().getGaugeTheme());
        assertEquals(LoggerGaugeTheme.ION_OLED, tile().resolveGaugeTheme(LoggerGaugeTheme.ION_OLED));
        LoggerDashboardTile pinned = tile().withGaugeTheme(LoggerGaugeTheme.STI_NIGHT);
        assertEquals(LoggerGaugeTheme.STI_NIGHT, pinned.resolveGaugeTheme(LoggerGaugeTheme.ION_OLED));
        LoggerDashboardTile edited = pinned.withRole(LoggerDashboardTileRole.VALUE)
                .withOrder(3).withSize(LoggerDashboardTileSize.WIDE).withAccentColor("#ff1234").withCustomSize(300, 250);
        assertEquals(LoggerGaugeTheme.STI_NIGHT, edited.getGaugeTheme());
        assertEquals(LoggerGaugeTheme.ION_OLED, edited.withGaugeTheme(null).resolveGaugeTheme(LoggerGaugeTheme.ION_OLED));
        assertNotEquals(tile(), pinned);
        assertEquals(pinned, tile().withGaugeTheme(LoggerGaugeTheme.STI_NIGHT));
        assertEquals(pinned.hashCode(), tile().withGaugeTheme(LoggerGaugeTheme.STI_NIGHT).hashCode());
    }
    @Test public void actualSettingsRoundTripPreservesEveryStyleAndDefault() throws Exception {
        Settings original = new Settings();
        original.setLoggerDashboardTile("default", tile());
        for (LoggerGaugeTheme theme : LoggerGaugeTheme.selectableValues())
            original.setLoggerDashboardTile(theme.name(), tile().withGaugeTheme(theme));
        java.io.File file = temporary.newFile("styles.xml");
        new DOMSettingsBuilder().buildSettings(original, file, new JProgressPane(), "test");
        Settings restored = new DOMSettingsUnmarshaller().unmarshallSettings(DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file).getDocumentElement());
        assertEquals(original.getLoggerDashboardTiles(), restored.getLoggerDashboardTiles());
    }
    @Test public void unknownAndOldOverridesFollowDefaultWithoutDiscardingLayout() throws Exception {
        String xml = "<settings><logger><dashboard-layout schema=\"1\">"
                + "<tile id=\"old\" role=\"TREND\" order=\"3\"/>"
                + "<tile id=\"future\" role=\"VALUE\" gauge-theme=\"future-style\"/>"
                + "<tile id=\"known\" gauge-theme=\" ion_oled \"/>"
                + "</dashboard-layout></logger></settings>";
        Settings restored = new DOMSettingsUnmarshaller().unmarshallSettings(DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement());
        assertEquals(3, restored.getLoggerDashboardTiles().size());
        assertEquals(LoggerDashboardTileRole.TREND, restored.getLoggerDashboardTiles().get("old").getRole());
        assertNull(restored.getLoggerDashboardTiles().get("old").getGaugeTheme());
        assertNull(restored.getLoggerDashboardTiles().get("future").getGaugeTheme());
        assertEquals(LoggerGaugeTheme.ION_OLED, restored.getLoggerDashboardTiles().get("known").getGaugeTheme());
    }
}
