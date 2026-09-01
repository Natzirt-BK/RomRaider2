/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.platform;

import java.util.Locale;
import java.util.Optional;

import com.romraider.maps.RomID;

/** Resolves editor definition metadata without coupling ROM parsing to Swing. */
public final class RomPlatformResolver {
    private RomPlatformResolver() {
    }

    public static Optional<VehiclePlatform> resolve(RomID romId) {
        if (romId == null) return Optional.empty();
        String make = normalize(romId.getMake());
        String model = normalize(romId.getModel());

        if ("subaru".equals(make)) {
            return Optional.of(VehiclePlatform.SUBARU);
        }
        boolean mitsubishi = "mitsubishi".equals(make);
        boolean lancerEvolution = model.contains("lancer evolution");
        if (mitsubishi && lancerEvolution) {
            return Optional.of(VehiclePlatform.EVO_8_9);
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }
}
