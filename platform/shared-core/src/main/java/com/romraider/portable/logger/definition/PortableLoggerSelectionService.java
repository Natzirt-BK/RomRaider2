/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.util.ArrayList;
import java.util.List;

/** Resolves profile choices without silently substituting IDs, units, or ECUs. */
public final class PortableLoggerSelectionService {
    private PortableLoggerSelectionService() { }

    public static PortableLoggerSelection resolve(PortableLoggerDefinition definition,
            PortableLoggerProfile profile, String ecuId) {
        return resolve(definition, profile, ecuId, 3);
    }

    /** Resolves only parameters whose target bit includes the requested module. */
    public static PortableLoggerSelection resolve(PortableLoggerDefinition definition,
            PortableLoggerProfile profile, String ecuId, int moduleTarget) {
        if (definition == null || profile == null) {
            throw new IllegalArgumentException("Definition and profile are required");
        }
        if (moduleTarget != 1 && moduleTarget != 2 && moduleTarget != 3) {
            throw new IllegalArgumentException("Logger module target is invalid");
        }
        if (!profile.getProtocol().isEmpty()
                && !definition.getProtocol().equalsIgnoreCase(profile.getProtocol())) {
            throw new IllegalArgumentException("Profile protocol does not match definition");
        }
        List<PortableSelectedParameter> ready = new ArrayList<>();
        List<String> unavailable = new ArrayList<>(profile.unsupported());
        for (PortableLoggerProfile.Selection choice : profile.selections()) {
            PortableLoggerParameter parameter = definition.parameter(choice.getId());
            if (parameter == null) {
                unavailable.add(choice.getId() + ": not in definition");
                continue;
            }
            if ((parameter.getTarget() & moduleTarget) == 0) {
                unavailable.add(choice.getId() + ": not available for this module");
                continue;
            }
            PortableLoggerConversion conversion = parameter.conversionFor(
                    choice.getUnits());
            if (conversion == null) {
                unavailable.add(choice.getId() + ": no conversion");
                continue;
            }
            if (!choice.getUnits().isEmpty()
                    && !choice.getUnits().equalsIgnoreCase(conversion.getUnits())) {
                unavailable.add(choice.getId() + ": units " + choice.getUnits()
                        + " unavailable");
                continue;
            }
            int[] addresses = PortableSelectedParameter.expand(
                    parameter.addressesFor(ecuId));
            if (addresses.length == 0) {
                unavailable.add(choice.getId() + ": ECU address unavailable");
                continue;
            }
            ready.add(new PortableSelectedParameter(parameter, conversion, addresses));
        }
        return new PortableLoggerSelection(ready, unavailable);
    }
}
