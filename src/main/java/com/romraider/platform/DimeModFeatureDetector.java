/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.platform;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.romraider.maps.Rom;
import com.romraider.maps.Table;

/** Definition-only feature detection; it never claims an ECU runtime state. */
public final class DimeModFeatureDetector {
    private DimeModFeatureDetector() {
    }

    public static Map<DimeModFeature, Boolean> detect(Rom rom) {
        EnumMap<DimeModFeature, Boolean> detected =
                new EnumMap<DimeModFeature, Boolean>(DimeModFeature.class);
        boolean dimeModIdentified = RomModificationDetector.detect(rom)
                .get(RomModification.DIME_MOD).isDetected();
        StringBuilder searchable = new StringBuilder();
        if (rom != null && dimeModIdentified) {
            for (Table table : rom.getTables()) {
                append(searchable, table.getName());
                append(searchable, table.getCategory());
                append(searchable, table.getDescription());
            }
        }
        String text = searchable.toString().toLowerCase(Locale.ROOT);
        for (DimeModFeature feature : DimeModFeature.values()) {
            boolean found = false;
            for (String term : feature.getSearchTerms()) {
                if (text.contains(term)) {
                    found = true;
                    break;
                }
            }
            detected.put(feature, Boolean.valueOf(found));
        }
        return Collections.unmodifiableMap(detected);
    }

    private static void append(StringBuilder destination, String value) {
        if (value != null && !value.trim().isEmpty()) {
            destination.append(' ').append(value);
        }
    }
}
