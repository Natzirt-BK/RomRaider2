/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.logger.*;
import com.romraider.portable.logger.definition.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic definition-to-query checks; no adapter, discovery or ECU writes. */
public final class PortableTypedChannelCheck {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        integerDefinitions();
        floatDefinitions();
        invalidDefinitions();
        splitBatches();
        System.out.println("Portable typed-channel checks passed: " + assertions);
    }

    private static void integerDefinitions() throws Exception {
        for (String endian : List.of("", "big", "little", "LiTtLe")) {
            for (int width : new int[] {1, 2, 4}) {
                long mask = (1L << (width * 8)) - 1;
                long sign = 1L << (width * 8 - 1);
                for (String storage : List.of("", "uint", "int", "uint8", "uint16", "uint32", "int8", "int16", "int32", "UINT")) {
                    PortableLoggerSelection selection = resolve(leaf("Input", width, storage, endian, "x/4-2", 16), "Input");
                    require(selection.unavailable().isEmpty(), "Valid integer definition resolves");
                    PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(selection.ready());
                    for (long bits : new long[] {0, 1, sign - 1, sign, mask - 1, mask}) {
                        byte[] raw = bytes(bits, width, endian);
                        // Logger integer suffixes are historical type labels: the
                        // address width controls decoding, as on desktop.
                        boolean unsigned = storage.isEmpty() || storage.toLowerCase(Locale.ROOT).startsWith("uint");
                        long value = unsigned || bits < sign ? bits : bits - mask - 1;
                        equal(value / 4.0 - 2, plan.decode(Collections.singletonList(raw)).get(0).getValue(),
                                storage + "/" + endian + "/" + width + "/" + bits);
                    }
                }
            }
        }
    }

    private static void floatDefinitions() throws Exception {
        for (String endian : List.of("", "big", "little", "LiTtLe")) {
            String xml = leaf("Input", 4, "FlOaT", endian, "x", 16)
                    + "<parameter id='Result' name='Result'><depends><ref parameter='Input'/></depends>"
                    + "<conversions><conversion units='raw' expr='Input*2' format='0.###'/></conversions></parameter>";
            PortableLoggerSelection selection = resolve(xml, "Result");
            require(selection.unavailable().isEmpty(), "Float hidden dependency resolves");
            PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(selection.ready());
            require(plan.readParameters().size() == 1 && plan.batches().get(0).getAddresses().length == 4,
                    "Hidden float requires exactly four deduplicated bytes");
            for (float value : new float[] {0, -0.0f, 1.5f, -13.25f, Float.MIN_VALUE,
                    Float.MIN_NORMAL, Float.MAX_VALUE, -Float.MAX_VALUE,
                    Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 3.25f}) {
                byte[] raw = bytes(Float.floatToRawIntBits(value), 4, endian);
                double actual = plan.decode(Collections.singletonList(raw)).get(0).getValue();
                if (Float.isFinite(value)) equal((double) value * 2, actual, "Finite float/calculated recovery");
                else require(Double.isNaN(actual), "Invalid float propagates unavailable, never stale data");
            }
        }
    }

    private static void invalidDefinitions() throws Exception {
        for (String[] invalid : new String[][] {{"float", "big", "1"}, {"float", "little", "2"},
                {"int64", "big", "4"}, {"uint", "middle", "2"}, {"int", "big", "3"}}) {
            String leaf = leaf("Input", Integer.parseInt(invalid[2]), invalid[0], invalid[1], "x", 16);
            for (boolean hidden : new boolean[] {false, true}) {
                String xml = leaf + (hidden ? "<parameter id='Result' name='Result'><depends><ref parameter='Input'/></depends>"
                        + "<conversions><conversion units='raw' expr='Input*2' format='0'/></conversions></parameter>" : "");
                PortableLoggerSelection selection = resolve(xml, hidden ? "Result" : "Input");
                require(selection.ready().isEmpty() && selection.unavailable().size() == 1,
                        "Invalid direct/hidden storage rejected before a query exists");
            }
        }
    }

    private static void splitBatches() throws Exception {
        StringBuilder xml = new StringBuilder();
        String[] ids = new String[20];
        Map<Integer, Byte> memory = new HashMap<>();
        for (int i = 0; i < ids.length; i++) {
            ids[i] = "F" + i;
            String endian = i % 2 == 0 ? "big" : "little";
            int address = 16 + i * 4;
            xml.append(leaf(ids[i], 4, "float", endian, "x", address));
            byte[] raw = bytes(Float.floatToRawIntBits(i + 0.25f), 4, endian);
            for (int j = 0; j < raw.length; j++) memory.put(address + j, raw[j]);
        }
        PortableLoggerSelection selection = resolve(xml.toString(), ids);
        require(selection.ready().size() == 20 && selection.unavailable().isEmpty(), "All mixed-endian channels resolve");
        PortableLoggerQueryPlan plan = PortableLoggerQueryPlan.create(selection.ready());
        require(plan.batches().size() == 2, "Eighty bytes split across bounded SSM batches");
        List<byte[]> replies = new ArrayList<>();
        for (PortableLoggerQueryBatch batch : plan.batches()) {
            int[] addresses = batch.getAddresses();
            require(addresses.length <= 64, "SSM batch bound retained");
            byte[] reply = new byte[addresses.length];
            for (int j = 0; j < addresses.length; j++) reply[j] = memory.get(addresses[j]);
            replies.add(reply);
        }
        List<PortableLoggerValue> values = plan.decode(replies);
        for (int i = 0; i < ids.length; i++) equal(i + 0.25, values.get(i).getValue(), "Batch reconstruction preserves byte order and output order");
        try {
            plan.decode(replies.subList(0, 1));
            throw new AssertionError("Incomplete typed cycle accepted");
        } catch (IllegalArgumentException expected) { assertions++; }
    }

    private static PortableLoggerSelection resolve(String parameters, String... ids) throws Exception {
        byte[] xml = ("<logger><protocol id='SSM'>" + parameters + "</protocol></logger>").getBytes(StandardCharsets.UTF_8);
        PortableLoggerDefinition definition = PortableLoggerDefinitionReader.read(new ByteArrayInputStream(xml), "SSM");
        List<PortableLoggerProfile.Selection> choices = new ArrayList<>();
        for (String id : ids) choices.add(new PortableLoggerProfile.Selection(id, "raw"));
        return PortableLoggerSelectionService.resolve(definition,
                new PortableLoggerProfile("SSM", choices, Collections.emptyList()), "SYNTHETIC", 1);
    }

    private static String leaf(String id, int width, String storage, String endian, String expression, int address) {
        return "<parameter id='" + id + "' name='" + id + "'><address length='" + width + "'>0x"
                + Integer.toHexString(address) + "</address><conversions><conversion units='raw' expr='" + expression
                + "' format='0.###' storagetype='" + storage + "' endian='" + endian + "'/></conversions></parameter>";
    }

    private static byte[] bytes(long bits, int width, String endian) {
        ByteBuffer buffer = ByteBuffer.allocate(width).order("little".equalsIgnoreCase(endian) ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (width == 1) buffer.put((byte) bits);
        else if (width == 2) buffer.putShort((short) bits);
        else buffer.putInt((int) bits);
        return buffer.array();
    }

    private static void equal(double expected, double actual, String message) {
        require(expected == actual, message + ": expected " + expected + ", actual " + actual);
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
