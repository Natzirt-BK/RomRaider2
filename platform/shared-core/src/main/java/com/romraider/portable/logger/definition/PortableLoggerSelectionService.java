/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import com.romraider.portable.logger.PortableExpression;
import com.romraider.portable.logger.PortableParameterConverter;

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
        Resolver resolver = new Resolver(definition, ecuId, moduleTarget);
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
            try { ready.add(resolver.resolve(parameter, conversion, new LinkedHashSet<>())); }
            catch (IllegalArgumentException failure) { unavailable.add(choice.getId() + ": " + failure.getMessage()); }
        }
        return new PortableLoggerSelection(ready, unavailable);
    }

    private static final class Resolver {
        private final PortableLoggerDefinition definition;
        private final String ecuId;
        private final int module;
        private final Map<String, PortableSelectedParameter> cache = new LinkedHashMap<>();
        private int visited;
        Resolver(PortableLoggerDefinition definition, String ecuId, int module) {
            this.definition = definition; this.ecuId = ecuId; this.module = module;
        }

        PortableSelectedParameter resolve(PortableLoggerParameter parameter,
                PortableLoggerConversion conversion, Set<String> path) {
            String id = parameter.getId();
            if (path.contains(id)) throw new IllegalArgumentException("Calculated dependency cycle at " + id);
            if (path.size() >= 32) throw new IllegalArgumentException("Calculated dependency depth exceeds 32");
            if ((parameter.getTarget() & module) == 0) throw new IllegalArgumentException(id + ": dependency not available for this module");
            String key = id + '\0' + conversion.getUnits();
            if (cache.containsKey(key)) return cache.get(key);
            if (++visited > 4096) throw new IllegalArgumentException("Logger dependency graph exceeds 4096 nodes");
            path.add(id);
            try {
                PortableSelectedParameter selected;
                if (parameter.getDependencies().isEmpty()) {
                    int[] addresses = PortableSelectedParameter.expand(parameter.addressesFor(ecuId));
                    if (addresses.length == 0) throw new IllegalArgumentException(id + ": ECU address unavailable");
                    if (addresses.length != 1 && addresses.length != 2 && addresses.length != 4) {
                        throw new IllegalArgumentException(id + ": values require 1, 2 or 4 bytes");
                    }
                    if ("float".equalsIgnoreCase(conversion.getStorageType()) && addresses.length != 4) {
                        throw new IllegalArgumentException(id + ": floating-point values require 4 bytes");
                    }
                    new PortableParameterConverter(conversion); // Validate expressions before requesting any values.
                    selected = new PortableSelectedParameter(parameter, conversion, addresses);
                } else {
                    if (!parameter.addressesFor(ecuId).isEmpty()) throw new IllegalArgumentException(id + ": mixed address/calculated definitions are unsupported");
                    Set<String> declared = new LinkedHashSet<>(parameter.getDependencies());
                    PortableExpression expression = PortableExpression.compile(conversion.getExpression(), declared);
                    Map<String, PortableSelectedParameter> inputs = new LinkedHashMap<>();
                    // Bare IDs always bind to the definition's first conversion,
                    // independently of optional display-unit choices in the profile.
                    for (String dependency : declared) {
                        PortableLoggerParameter declaredParameter = definition.parameter(dependency);
                        if (declaredParameter == null) throw new IllegalArgumentException("Missing dependency " + dependency);
                        if ((declaredParameter.getTarget() & module) == 0) throw new IllegalArgumentException(dependency + ": dependency not available for this module");
                    }
                    for (String reference : expression.getVariables()) {
                        if (reference.startsWith("[")) {
                            int colon = reference.indexOf(':');
                            inputs.put(reference, dependency(reference.substring(1, colon),
                                    reference.substring(colon + 1, reference.length() - 1), path));
                        } else inputs.put(reference, dependency(reference, "", path));
                    }
                    selected = PortableSelectedParameter.calculated(parameter, conversion, inputs, expression);
                }
                cache.put(key, selected);
                return selected;
            } finally { path.remove(id); }
        }

        private PortableSelectedParameter dependency(String id, String units, Set<String> path) {
            PortableLoggerParameter parameter = definition.parameter(id);
            if (parameter == null) throw new IllegalArgumentException("Missing dependency " + id);
            // Bind the first conversion explicitly rather than performing a unit lookup.
            PortableLoggerConversion conversion = units.isEmpty()
                    ? (parameter.getConversions().isEmpty() ? null : parameter.getConversions().get(0))
                    : parameter.conversionFor(units);
            if (conversion == null || (!units.isEmpty() && !units.equalsIgnoreCase(conversion.getUnits()))) {
                throw new IllegalArgumentException(id + ": dependency units " + units + " unavailable");
            }
            return resolve(parameter, conversion, path);
        }
    }
}
