/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.logger;

import com.romraider.portable.logger.definition.*;
import java.util.*;

/** Presentation-only catalog. Names are never used as channel identity. */
public final class LoggerChannelCatalog {
    private LoggerChannelCatalog() { }

    public static List<PortableLoggerParameter> channels(PortableLoggerDefinition definition,
            PortableLoggerProfile profile, String ecuId) {
        Set<String> selected = new HashSet<>();
        if (profile != null) for (PortableLoggerProfile.Selection choice : profile.selections()) {
            selected.add(choice.getId());
        }
        Set<String> mapped = new HashSet<>();
        boolean filter = ecuId != null && !ecuId.isEmpty();
        if (filter) {
            List<PortableLoggerProfile.Selection> choices = new ArrayList<>();
            for (PortableLoggerParameter parameter : definition.parameters()) {
                choices.add(new PortableLoggerProfile.Selection(parameter.getId(), ""));
            }
            PortableLoggerSelection resolved = PortableLoggerSelectionService.resolve(definition,
                    new PortableLoggerProfile(definition.getProtocol(), choices, Collections.emptyList()), ecuId, 1);
            for (PortableSelectedParameter parameter : resolved.ready()) {
                mapped.add(parameter.getParameter().getId());
            }
        }
        List<PortableLoggerParameter> result = new ArrayList<>();
        for (PortableLoggerParameter parameter : definition.parameters()) {
            // Keep selected unavailable entries visible so a filter cannot silently erase them.
            if (selected.contains(parameter.getId()) || ((parameter.getTarget() & 1) != 0
                    && (!filter || mapped.contains(parameter.getId())))) result.add(parameter);
        }
        return Collections.unmodifiableList(result);
    }

    public static String label(PortableLoggerParameter parameter) {
        Set<String> units = new LinkedHashSet<>();
        for (PortableLoggerConversion conversion : parameter.getConversions()) {
            if (!conversion.getUnits().isEmpty()) units.add(conversion.getUnits());
        }
        String kind = parameter.getTarget() == 2 ? "Transmission"
                : parameter.getId().startsWith("DM") && parameter.getName().startsWith("DimeMod:") ? "DimeMod"
                : !parameter.getDependencies().isEmpty() ? "Calculated"
                : parameter.addressesFor(null).isEmpty() ? "ECU-specific" : "Standard";
        return parameter.getName() + " [" + parameter.getId() + "]\n"
                + kind + (units.isEmpty() ? "" : " · " + String.join(" / ", units));
    }

    /** Editing the visible catalog must not erase undiscovered profile IDs or reorder existing CSV columns. */
    public static PortableLoggerProfile select(String protocol, PortableLoggerProfile previous,
            List<PortableLoggerParameter> visible, boolean[] checked) {
        if (checked.length != visible.size()) throw new IllegalArgumentException("Channel selection size mismatch");
        Map<String, Boolean> choices = new LinkedHashMap<>();
        for (int i = 0; i < visible.size(); i++) choices.put(visible.get(i).getId(), checked[i]);
        Map<String, PortableLoggerProfile.Selection> selected = new LinkedHashMap<>();
        if (previous != null) for (PortableLoggerProfile.Selection choice : previous.selections()) {
            if (choices.getOrDefault(choice.getId(), true)) selected.put(choice.getId(), choice);
        }
        for (PortableLoggerParameter parameter : visible) {
            if (choices.get(parameter.getId()) && !parameter.getConversions().isEmpty()) {
                selected.putIfAbsent(parameter.getId(), new PortableLoggerProfile.Selection(parameter.getId(),
                        parameter.getConversions().get(0).getUnits()));
            }
        }
        return new PortableLoggerProfile(protocol, new ArrayList<>(selected.values()),
                previous == null ? Collections.emptyList() : previous.unsupported());
    }
}
