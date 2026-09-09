/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Intersection of the loaded catalog, ECU mappings, support flags and engine dependencies. */
public final class PortableVehicleCatalog {
    private PortableVehicleCatalog() { }

    public static PortableLoggerDefinition forEcu(PortableLoggerDefinition definition,
            String ecuId, byte[] ssmInitPayload) {
        if (ecuId == null || ecuId.trim().isEmpty()) {
            throw new IllegalArgumentException("Identify the ECU before filtering vehicle channels");
        }
        List<PortableLoggerParameter> candidates = new ArrayList<>();
        List<PortableLoggerProfile.Selection> choices = new ArrayList<>();
        for (PortableLoggerParameter parameter : definition.parameters()) {
            if ((parameter.getTarget() & 1) == 0) continue;
            if ("SSM".equalsIgnoreCase(definition.getProtocol())
                    && !parameter.supportedBySsm(ssmInitPayload)) continue;
            candidates.add(parameter);
            choices.add(new PortableLoggerProfile.Selection(parameter.getId(), ""));
        }
        PortableLoggerDefinition supported = new PortableLoggerDefinition(
                definition.getVersion(), definition.getProtocol(), candidates);
        PortableLoggerSelection selection = PortableLoggerSelectionService.resolve(supported,
                new PortableLoggerProfile(definition.getProtocol(), choices, Collections.emptyList()), ecuId, 1);
        List<PortableLoggerParameter> available = new ArrayList<>();
        for (PortableSelectedParameter parameter : selection.ready()) available.add(parameter.getParameter());
        return new PortableLoggerDefinition(definition.getVersion(), definition.getProtocol(), available);
    }
}
