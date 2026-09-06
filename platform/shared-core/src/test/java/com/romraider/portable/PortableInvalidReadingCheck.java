/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.romraider.portable.logger.PortableParameterConverter;
import com.romraider.portable.logger.definition.PortableLoggerConversion;

/** Invalid conversions must survive recording as gaps, not fabricated zeroes. */
public final class PortableInvalidReadingCheck {
    public static void main(String[] args) throws Exception {
        for (String expression : new String[] {"x/0", "0/0", "-x/0", "x*1e999"}) {
            double value = converter(expression, "uint8", "big").convert(new byte[] {1});
            require(Double.isNaN(value), "Invalid expression became a numeric reading");
        }
        for (String endian : new String[] {"big", "little"}) {
            for (float raw : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                byte[] bytes = ByteBuffer.allocate(4).order("little".equals(endian)
                        ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).putFloat(raw).array();
                for (String expression : new String[] {"x", "42"}) {
                    require(Double.isNaN(converter(expression, "float", endian).convert(bytes)),
                            "Non-finite raw float was masked by its conversion");
                }
            }
        }
        PortableParameterConverter converter = converter("10/x", "uint8", "big");
        PortableLogSession session = new PortableLogSession();
        session.append(new PortableLogSample(0, "p", "Fixture", converter.convert(new byte[] {2}), "V"));
        session.append(new PortableLogSample(1, "p", "Fixture", converter.convert(new byte[] {0}), "V"));
        session.append(new PortableLogSample(2, "p", "Fixture", converter.convert(new byte[] {5}), "V"));
        session.append(new PortableLogSample(3, "p", "Fixture", converter("x", "uint8", "big").convert(new byte[] {0}), "V"));
        StringWriter csv = new StringWriter();
        session.writeRomRaiderCsv(csv);
        require(csv.toString().equals("Time (msec),Fixture (V)\n0,5\n1,\n2,2\n3,0\n"),
                "CSV lost a gap, recovery or legitimate zero: " + csv);
        require(Double.isNaN(PortableLogCsvReader.read(new StringReader(csv.toString()))
                .snapshot().get(1).getValue()), "CSV import fabricated a zero");
        System.out.println("Portable invalid-reading checks passed (18 assertions)");
    }

    private static PortableParameterConverter converter(String expression, String storage, String endian) {
        return new PortableParameterConverter(new PortableLoggerConversion("V", expression, "0.###", storage, endian));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
