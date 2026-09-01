/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable logger parameter, including ECU-specific address mappings. */
public final class PortableLoggerParameter {
    public static final String ALL_ECUS = "";

    private final String id;
    private final String name;
    private final String description;
    private final int target;
    private final Map<String, List<PortableLoggerAddress>> addresses;
    private final List<String> dependencies;
    private final List<PortableLoggerConversion> conversions;

    public PortableLoggerParameter(String id, String name, String description,
            int target,
            Map<String, List<PortableLoggerAddress>> addresses,
            List<String> dependencies,
            List<PortableLoggerConversion> conversions) {
        if (blank(id) || blank(name)) {
            throw new IllegalArgumentException("Logger parameter ID and name are required");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
        if (target < 1 || target > 3) {
            throw new IllegalArgumentException("Logger parameter target is invalid");
        }
        this.target = target;
        this.addresses = immutableAddressMap(addresses);
        this.dependencies = immutableCopy(dependencies);
        this.conversions = Collections.unmodifiableList(
                new ArrayList<PortableLoggerConversion>(conversions));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getTarget() { return target; }
    public List<String> getDependencies() { return dependencies; }
    public List<PortableLoggerConversion> getConversions() { return conversions; }

    public List<PortableLoggerAddress> addressesFor(String ecuId) {
        List<PortableLoggerAddress> common = addresses.get(ALL_ECUS);
        if (common != null) return common;
        if (ecuId == null) return Collections.emptyList();
        List<PortableLoggerAddress> specific = addresses.get(ecuId.trim());
        return specific == null ? Collections.emptyList() : specific;
    }

    public PortableLoggerConversion conversionFor(String units) {
        if (conversions.isEmpty()) return null;
        if (units != null) {
            for (PortableLoggerConversion conversion : conversions) {
                if (units.trim().equalsIgnoreCase(conversion.getUnits())) {
                    return conversion;
                }
            }
        }
        return conversions.get(0);
    }

    private static Map<String, List<PortableLoggerAddress>> immutableAddressMap(
            Map<String, List<PortableLoggerAddress>> source) {
        Map<String, List<PortableLoggerAddress>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<PortableLoggerAddress>> entry
                : source.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableList(
                    new ArrayList<PortableLoggerAddress>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<String> immutableCopy(List<String> source) {
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
