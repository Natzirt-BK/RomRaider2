/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parameter selections from a RomRaider logger profile. */
public final class PortableLoggerProfile {
    public static final class Selection {
        private final String id;
        private final String units;

        public Selection(String id, String units) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Profile parameter ID is required");
            }
            this.id = id.trim();
            this.units = units == null ? "" : units.trim();
        }

        public String getId() { return id; }
        public String getUnits() { return units; }
    }

    private final String protocol;
    private final List<Selection> selections;
    private final List<String> unsupported;

    public PortableLoggerProfile(String protocol, List<Selection> selections,
            List<String> unsupported) {
        this.protocol = protocol == null ? "" : protocol.trim();
        this.selections = Collections.unmodifiableList(
                new ArrayList<Selection>(selections));
        this.unsupported = Collections.unmodifiableList(
                new ArrayList<String>(unsupported));
    }

    public String getProtocol() { return protocol; }
    public List<Selection> selections() { return selections; }
    public List<String> unsupported() { return unsupported; }
    public int size() { return selections.size() + unsupported.size(); }
}
