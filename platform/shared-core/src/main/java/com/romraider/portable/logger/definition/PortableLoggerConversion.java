/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

/** One display conversion retained from a logger definition. */
public final class PortableLoggerConversion {
    private final String units;
    private final String expression;
    private final String format;
    private final String storageType;
    private final String endian;

    public PortableLoggerConversion(String units, String expression,
            String format, String storageType, String endian) {
        if (blank(units) || blank(expression) || blank(format)) {
            throw new IllegalArgumentException(
                    "Conversion units, expression, and format are required");
        }
        this.units = units.trim();
        this.expression = expression.trim();
        this.format = format.trim();
        this.storageType = clean(storageType);
        this.endian = clean(endian);
    }

    public String getUnits() { return units; }
    public String getExpression() { return expression; }
    public String getFormat() { return format; }
    public String getStorageType() { return storageType; }
    public String getEndian() { return endian; }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
