/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.api;

import com.romraider.logger.ecu.definition.EcuDataConvertor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable conversion identity; neither a unit label nor an option index is sufficient. */
public final class LoggerConversionIdentity {
    private LoggerConversionIdentity() { }

    public static String of(EcuDataConvertor conversion) {
        if (conversion == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : new String[] {conversion.getClass().getName(),
                    conversion.getUnits(), conversion.getExpression(),
                    conversion.getFormat(), conversion.getDataType()}) {
                String value = field == null ? "" : field;
                digest.update((value.length() + ":" + value).getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder identity = new StringBuilder("conversion-v1:");
            for (byte value : digest.digest()) {
                identity.append("0123456789abcdef".charAt((value & 0xff) >>> 4));
                identity.append("0123456789abcdef".charAt(value & 15));
            }
            return identity.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
