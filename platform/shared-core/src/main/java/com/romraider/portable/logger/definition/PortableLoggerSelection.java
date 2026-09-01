/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of resolving a logger profile against a definition and ECU ID. */
public final class PortableLoggerSelection {
    private final List<PortableSelectedParameter> ready;
    private final List<String> unavailable;

    PortableLoggerSelection(List<PortableSelectedParameter> ready,
            List<String> unavailable) {
        this.ready = Collections.unmodifiableList(
                new ArrayList<PortableSelectedParameter>(ready));
        this.unavailable = Collections.unmodifiableList(
                new ArrayList<String>(unavailable));
    }

    public List<PortableSelectedParameter> ready() { return ready; }
    public List<String> unavailable() { return unavailable; }
}
