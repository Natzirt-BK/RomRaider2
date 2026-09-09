/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.logger.definition.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoggerProfileWriterTest {
    private PortableLoggerDefinition definition() throws Exception {
        String xml = "<logger><protocol id='SSM'><parameter id='P8' name='RPM'><address>14</address>"
                + "<conversions><conversion units='rpm' expr='x' format='0'/></conversions></parameter>"
                + "<switch id='CUSTOM_SWITCH' name='Switch' byte='20' bit='1'/></protocol></logger>";
        return PortableLoggerDefinitionReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "SSM");
    }
    @Test public void standardXmlPreservesSwitchCategoryOrderUnitsAndPendingDimeChannels() throws Exception {
        PortableLoggerProfile profile = new PortableLoggerProfile("SSM", List.of(
                new PortableLoggerProfile.Selection("CUSTOM_SWITCH", "On/Off"),
                new PortableLoggerProfile.Selection("DM911", "%"),
                new PortableLoggerProfile.Selection("P8", "")), List.of("Wideband: external input transport unavailable"));
        byte[] bytes = PortableLoggerProfileWriter.encode(profile, definition());
        String xml = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(xml.contains("<switch id=\"CUSTOM_SWITCH\""));
        assertFalse(xml.contains("<parameter id=\"CUSTOM_SWITCH\""));
        PortableLoggerProfile restored = PortableLoggerProfileReader.read(new ByteArrayInputStream(bytes));
        assertEquals(List.of("CUSTOM_SWITCH", "DM911", "P8"), restored.selections().stream().map(PortableLoggerProfile.Selection::getId).collect(Collectors.toList()));
        assertEquals("rpm", restored.selections().get(2).getUnits());
        assertEquals(profile.unsupported(), restored.unsupported());
    }
    @Test public void xmlSpecialCharactersAndUnicodeRoundTrip() throws Exception {
        PortableLoggerProfile profile = new PortableLoggerProfile("SSM", List.of(
                new PortableLoggerProfile.Selection("A&<\"'\t日本", "µ & < V")), List.of());
        PortableLoggerProfile restored = PortableLoggerProfileReader.read(new ByteArrayInputStream(PortableLoggerProfileWriter.encode(profile, definition())));
        assertEquals(profile.selections().get(0).getId(), restored.selections().get(0).getId());
        assertEquals(profile.selections().get(0).getUnits(), restored.selections().get(0).getUnits());
    }
    @Test public void invalidProtocolDuplicatesAndUnknownDiagnosticsFailBeforeDestinationOpens() throws Exception {
        PortableLoggerDefinition definition = definition();
        for (PortableLoggerProfile profile : List.of(
                new PortableLoggerProfile("MUT2", List.of(), List.of()),
                new PortableLoggerProfile("SSM", List.of(new PortableLoggerProfile.Selection("P8", "rpm"), new PortableLoggerProfile.Selection("P8", "rpm")), List.of()),
                new PortableLoggerProfile("SSM", List.of(), List.of("Unknown unsupported entry")),
                new PortableLoggerProfile("SSM", List.of(new PortableLoggerProfile.Selection("P\u0001X", "rpm")), List.of()))) {
            assertThrows(IOException.class, () -> PortableLoggerProfileWriter.encode(profile, definition));
        }
    }
    @Test public void boundedAndEmptyProfilesRoundTrip() throws Exception {
        List<PortableLoggerProfile.Selection> selections = new ArrayList<>();
        for (int i = 0; i < 256; i++) selections.add(new PortableLoggerProfile.Selection("P" + i, "rpm"));
        assertEquals(256, PortableLoggerProfileReader.read(new ByteArrayInputStream(PortableLoggerProfileWriter.encode(
                new PortableLoggerProfile("SSM", selections, List.of()), definition()))).size());
        selections.add(new PortableLoggerProfile.Selection("P256", "rpm"));
        assertThrows(IOException.class, () -> PortableLoggerProfileWriter.encode(new PortableLoggerProfile("SSM", selections, List.of()), definition()));
        assertEquals(0, PortableLoggerProfileReader.read(new ByteArrayInputStream(PortableLoggerProfileWriter.encode(
                new PortableLoggerProfile("SSM", List.of(), List.of()), definition()))).size());
    }
}
