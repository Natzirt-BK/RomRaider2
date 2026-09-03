/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.platform;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.romraider.maps.Rom;
import com.romraider.maps.Table;

/** Definition-only CarBerry and MerpMod feature detection. */
public final class RomModificationFeatureDetector {
    private RomModificationFeatureDetector() { }

    public static Map<RomModificationFeature, Boolean> detect(Rom rom) {
        EnumMap<RomModificationFeature, Boolean> result =
                new EnumMap<RomModificationFeature, Boolean>(
                        RomModificationFeature.class);
        Map<RomModification, RomModificationEvidence> families =
                RomModificationDetector.detect(rom);
        EnumMap<RomModification, String> searchable =
                new EnumMap<RomModification, String>(RomModification.class);
        for (RomModification modification : RomModification.values()) {
            searchable.put(modification, families.get(modification).isDetected()
                    ? tableText(rom, modification) : "");
        }
        for (RomModificationFeature feature
                : RomModificationFeature.values()) {
            String text = searchable.get(feature.getModification());
            boolean found = false;
            for (String term : feature.getSearchTerms()) {
                if (text.contains(term)) {
                    found = true;
                    break;
                }
            }
            result.put(feature, Boolean.valueOf(found));
        }
        return Collections.unmodifiableMap(result);
    }

    private static String tableText(Rom rom, RomModification modification) {
        if (rom == null) return "";
        StringBuilder text = new StringBuilder();
        for (Table table : rom.getTables()) {
            if (!branded(table, modification)) continue;
            append(text, table.getName());
            append(text, table.getCategory());
            append(text, table.getDescription());
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean branded(Table table,
            RomModification modification) {
        String category = table.getCategory();
        String name = table.getName();
        for (String identifier : modification.getIdentifiers()) {
            String term = identifier.toLowerCase(Locale.ROOT);
            if (contains(category, term) || contains(name, term)) return true;
        }
        return false;
    }

    private static boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private static void append(StringBuilder text, String value) {
        if (value != null && !value.trim().isEmpty()) {
            text.append(' ').append(value);
        }
    }
}
