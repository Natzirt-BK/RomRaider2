/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.Settings;
import com.romraider.logger.ecu.definition.EcuParameterConvertorImpl;
import com.romraider.logger.ecu.ui.handler.dash.GaugeMinMax;
import com.romraider.portable.logger.PortableParameterConverter;
import com.romraider.portable.logger.definition.PortableLoggerConversion;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import org.junit.jupiter.api.Test;

/** The native desktop/JEP converter is the reference, not a second portable decoder. */
class PortableTypedCompatibilityTest {
    @Test void integerTypesMatchDesktopAtEverySupportedWidth() {
        Random random = new Random(20260906);
        for (int width : new int[] {1, 2, 4}) {
            long mask = (1L << (width * 8)) - 1;
            long sign = 1L << (width * 8 - 1);
            for (String storage : List.of("uint", "int", "uint8", "uint16", "uint32", "int8", "int16", "int32", "UINT")) {
                for (String endian : List.of("big", "little", "LiTtLe")) {
                    for (String expression : List.of("x", "x/4-2")) {
                        for (long bits : new long[] {0, 1, sign - 1, sign, mask - 1, mask, random.nextLong() & mask}) {
                            compare(storage, endian, expression, bytes(bits, width, endian));
                        }
                    }
                }
            }
        }
    }

    @Test void floatTypesMatchDesktopIncludingNonfiniteAndSubnormalValues() {
        for (String endian : List.of("big", "little", "LiTtLe")) {
            for (String expression : List.of("x", "x*100", "42")) {
                for (float value : new float[] {0, -0.0f, 1.5f, -13.25f, Float.MIN_VALUE,
                        Float.MIN_NORMAL, Float.MAX_VALUE, -Float.MAX_VALUE,
                        Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                    compare("FlOaT", endian, expression, bytes(Float.floatToRawIntBits(value), 4, endian));
                }
            }
        }
    }

    @Test void omittedStorageAndEndianUseDesktopDefaults() {
        for (int width : new int[] {1, 2, 4}) {
            byte[] bytes = new byte[width];
            Arrays.fill(bytes, (byte) 0xff);
            double portable = new PortableParameterConverter(new PortableLoggerConversion("raw", "x", "0", "", "")).convert(bytes);
            double desktop = new EcuParameterConvertorImpl("raw", "x", "0", -1, null, null, Map.of(), new GaugeMinMax(0, 100, 1)).convert(bytes);
            assertEquals((double) ((1L << (width * 8)) - 1), portable);
            assertEquals(desktop, portable);
        }
    }

    private void compare(String storage, String endian, String expression, byte[] bytes) {
        PortableParameterConverter portable = new PortableParameterConverter(new PortableLoggerConversion("raw", expression, "0.###", storage, endian));
        EcuParameterConvertorImpl desktop = new EcuParameterConvertorImpl("raw", expression, "0.###", -1, storage,
                "little".equalsIgnoreCase(endian) ? Settings.Endian.LITTLE : Settings.Endian.BIG, Map.of(), new GaugeMinMax(0, 100, 1));
        double expected = desktop.convert(bytes), actual = portable.convert(bytes);
        String context = storage + "/" + endian + "/" + expression + "/" + Arrays.toString(bytes);
        if (Double.isNaN(expected)) assertTrue(Double.isNaN(actual), context);
        else assertEquals(expected, actual, 0.0, context);
    }

    private byte[] bytes(long bits, int width, String endian) {
        ByteBuffer buffer = ByteBuffer.allocate(width).order("little".equalsIgnoreCase(endian) ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (width == 1) buffer.put((byte) bits);
        else if (width == 2) buffer.putShort((short) bits);
        else buffer.putInt((int) bits);
        return buffer.array();
    }
}
