/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.platform;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table;

/**
 * Finds explicit modification-family markers in the loaded ROM definition.
 * This detector does not claim that a connected ECU is running the ROM.
 */
public final class RomModificationDetector {
    private RomModificationDetector() {
    }

    public static Map<RomModification, RomModificationEvidence> detect(
            Rom rom) {
        EnumMap<RomModification, RomModificationEvidence> result =
                new EnumMap<RomModification, RomModificationEvidence>(
                        RomModification.class);
        for (RomModification modification : RomModification.values()) {
            result.put(modification, evidence(rom, modification));
        }
        return Collections.unmodifiableMap(result);
    }

    private static RomModificationEvidence evidence(Rom rom,
            RomModification modification) {
        if (rom == null) return RomModificationEvidence.NOT_DETECTED;
        RomID identity = rom.getRomID();
        if (identity != null && (matches(identity.getXmlid(), modification)
                || matches(identity.getInternalIdString(), modification))) {
            return RomModificationEvidence.ROM_IDENTITY;
        }
        for (Table table : rom.getTables()) {
            if (matches(table.getName(), modification)
                    || matches(table.getCategory(), modification)
                    || matches(table.getDescription(), modification)) {
                return RomModificationEvidence.BRANDED_TABLES;
            }
        }
        return RomModificationEvidence.NOT_DETECTED;
    }

    private static boolean matches(String value,
            RomModification modification) {
        if (value == null || value.trim().isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String identifier : modification.getIdentifiers()) {
            if (normalized.contains(identifier)) return true;
        }
        return false;
    }
}
