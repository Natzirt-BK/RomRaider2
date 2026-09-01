/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.romraider.portable.logger.definition.PortableLoggerConversion;

/** Converts raw ECU bytes with the same numeric rules as desktop RomRaider. */
public final class PortableParameterConverter {
    private final PortableLoggerConversion conversion;
    private final PortableExpression expression;

    public PortableParameterConverter(PortableLoggerConversion conversion) {
        if (conversion == null) {
            throw new IllegalArgumentException("Logger conversion is required");
        }
        this.conversion = conversion;
        this.expression = PortableExpression.compile(conversion.getExpression());
    }

    public double convert(byte[] bytes) {
        if (bytes == null || (bytes.length != 1 && bytes.length != 2
                && bytes.length != 4)) {
            throw new IllegalArgumentException(
                    "Logger values must contain 1, 2, or 4 bytes");
        }
        String storage = conversion.getStorageType().isEmpty()
                ? "uint8" : conversion.getStorageType();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        if ("little".equalsIgnoreCase(conversion.getEndian())) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        double raw;
        if ("float".equalsIgnoreCase(storage)) {
            if (bytes.length != 4) {
                throw new IllegalArgumentException(
                        "Floating-point logger values require 4 bytes");
            }
            raw = buffer.getFloat();
        } else {
            long value;
            if (bytes.length == 1) value = buffer.get();
            else if (bytes.length == 2) value = buffer.getShort();
            else value = buffer.getInt();
            if (storage.toLowerCase(java.util.Locale.ROOT).startsWith("uint")) {
                if (bytes.length == 1) value &= 0xFFL;
                else if (bytes.length == 2) value &= 0xFFFFL;
                else value &= 0xFFFFFFFFL;
            }
            raw = value;
        }
        double result = expression.evaluate(raw);
        return Double.isFinite(result) ? result : 0.0;
    }
}
