/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.logger.ecu.profile.*;
import com.romraider.portable.logger.definition.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real desktop serialization read by the Android/shared profile reader. */
class FxLoggerProfileCompatibilityTest {
    @Test void portableReaderAcceptsUnicodeUnitsAndTheProfilesOwnProtocol() throws Exception {
        Map<String, UserProfileItem> parameters = new LinkedHashMap<>();
        parameters.put("P9", new UserProfileItemImpl("λ & ≤", true, false, false));
        parameters.put("P1", new UserProfileItemImpl("°C", false, true, false));
        UserProfile desktop = new UserProfileImpl(parameters,
                Collections.singletonMap("S1", new UserProfileItemImpl("On/off", false, false, true)),
                Collections.emptyMap(), "MUT2");
        PortableLoggerProfile portable = PortableLoggerProfileReader.read(new ByteArrayInputStream(desktop.getBytes()));
        assertEquals("MUT2", portable.getProtocol());
        assertEquals(List.of("P9", "P1", "S1"), portable.selections().stream().map(PortableLoggerProfile.Selection::getId).toList());
        assertEquals(List.of("λ & ≤", "°C", "On/off"), portable.selections().stream().map(PortableLoggerProfile.Selection::getUnits).toList());
        assertTrue(portable.unsupported().isEmpty());
    }

    @Test void unselectedCatalogEntriesDoNotBecomePortableSelections() throws Exception {
        UserProfile desktop = new UserProfileImpl(Collections.singletonMap("P1", new UserProfileItemImpl("V", false, false, false)),
                Collections.emptyMap(), Collections.emptyMap(), "SSM");
        PortableLoggerProfile portable = PortableLoggerProfileReader.read(new ByteArrayInputStream(desktop.getBytes()));
        assertEquals(0, portable.size());
    }

    @Test void externalEntriesRemainExplicitlyUnsupportedOnPortableRuntime() throws Exception {
        UserProfile desktop = new UserProfileImpl(Collections.emptyMap(), Collections.emptyMap(),
                Collections.singletonMap("E1", new UserProfileItemImpl("λ", true, false, false)), "SSM");
        PortableLoggerProfile portable = PortableLoggerProfileReader.read(new ByteArrayInputStream(desktop.getBytes()));
        assertTrue(portable.selections().isEmpty());
        assertEquals(1, portable.unsupported().size());
        assertTrue(portable.unsupported().get(0).contains("E1"));
    }
}
