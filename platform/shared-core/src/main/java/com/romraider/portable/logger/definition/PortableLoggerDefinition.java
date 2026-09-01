/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, read-only parameter catalog for one logger protocol. */
public final class PortableLoggerDefinition {
    private final String version;
    private final String protocol;
    private final Map<String, PortableLoggerParameter> parameters;

    public PortableLoggerDefinition(String version, String protocol,
            List<PortableLoggerParameter> parameters) {
        this.version = version == null ? "" : version.trim();
        this.protocol = protocol == null ? "" : protocol.trim();
        Map<String, PortableLoggerParameter> byId = new LinkedHashMap<>();
        for (PortableLoggerParameter parameter : parameters) {
            if (byId.put(parameter.getId(), parameter) != null) {
                throw new IllegalArgumentException(
                        "Duplicate logger parameter: " + parameter.getId());
            }
        }
        this.parameters = Collections.unmodifiableMap(byId);
    }

    public String getVersion() { return version; }
    public String getProtocol() { return protocol; }
    public int size() { return parameters.size(); }
    public PortableLoggerParameter parameter(String id) {
        return id == null ? null : parameters.get(id.trim());
    }
    public List<PortableLoggerParameter> parameters() {
        return Collections.unmodifiableList(
                new ArrayList<PortableLoggerParameter>(parameters.values()));
    }
}
