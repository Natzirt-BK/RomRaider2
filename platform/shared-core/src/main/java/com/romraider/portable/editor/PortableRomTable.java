/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.editor;

import com.romraider.portable.PortableRomDocument;
import com.romraider.portable.logger.PortableExpression;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** A bounded numeric calibration table backed by a portable ROM document. */
public final class PortableRomTable {
    private final String name;
    private final String category;
    private final String description;
    private final String type;
    private final int address;
    private final int columns;
    private final int rows;
    private final Storage storage;
    private final boolean littleEndian;
    private final String units;
    private final String format;
    private final PortableExpression fromBytes;
    private final PortableExpression toBytes;

    PortableRomTable(String name, String category, String description,
            String type, int address, int columns, int rows, Storage storage,
            boolean littleEndian, String units, String format,
            PortableExpression fromBytes, PortableExpression toBytes) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.type = type;
        this.address = address;
        this.columns = columns;
        this.rows = rows;
        this.storage = storage;
        this.littleEndian = littleEndian;
        this.units = units;
        this.format = format;
        this.fromBytes = fromBytes;
        this.toBytes = toBytes;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public int getAddress() { return address; }
    public int getColumns() { return columns; }
    public int getRows() { return rows; }
    public int cellCount() { return Math.multiplyExact(columns, rows); }
    public String getUnits() { return units; }

    public double valueAt(PortableRomDocument rom, int row, int column) {
        return fromBytes.evaluate(rawValue(rom, cellIndex(row, column)));
    }

    public String formattedValueAt(PortableRomDocument rom, int row, int column) {
        double value = valueAt(rom, row, column);
        if (format == null || format.trim().isEmpty()) {
            String result = String.format(Locale.ROOT, "%.4f", value);
            while (result.contains(".") && result.endsWith("0")) {
                result = result.substring(0, result.length() - 1);
            }
            return result.endsWith(".")
                    ? result.substring(0, result.length() - 1) : result;
        }
        try {
            DecimalFormat formatter = new DecimalFormat(format,
                    DecimalFormatSymbols.getInstance(Locale.US));
            formatter.setGroupingUsed(false);
            return formatter.format(value);
        } catch (IllegalArgumentException ignored) {
            return Double.toString(value);
        }
    }

    public void replaceValue(PortableRomDocument rom, int row, int column,
            double displayedValue) {
        if (!Double.isFinite(displayedValue)) {
            throw new IllegalArgumentException("A finite table value is required");
        }
        int index = cellIndex(row, column);
        double converted = toBytes.evaluate(displayedValue);
        if (!Double.isFinite(converted)) {
            throw new IllegalArgumentException("The table scaling produced an invalid value");
        }
        byte[] encoded = storage.floating
                ? encodeFloat(converted) : encodeInteger(converted);
        rom.replace(byteOffset(index), encoded);
    }

    private int cellIndex(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IndexOutOfBoundsException("Table cell is outside the defined dimensions");
        }
        return Math.addExact(Math.multiplyExact(row, columns), column);
    }

    private int byteOffset(int index) {
        return Math.addExact(address, Math.multiplyExact(index, storage.bytes));
    }

    private double rawValue(PortableRomDocument rom, int index) {
        int offset = byteOffset(index);
        if (storage.floating) {
            int bits = 0;
            for (int indexByte = 0; indexByte < 4; indexByte++) {
                int source = littleEndian ? 3 - indexByte : indexByte;
                bits = (bits << 8) | (rom.byteAt(offset + source) & 0xFF);
            }
            return Float.intBitsToFloat(bits);
        }
        long value = 0;
        for (int indexByte = 0; indexByte < storage.bytes; indexByte++) {
            int source = littleEndian ? storage.bytes - 1 - indexByte : indexByte;
            value = (value << 8) | (rom.byteAt(offset + source) & 0xFFL);
        }
        if (storage.signed) {
            int bits = storage.bytes * 8;
            long sign = 1L << (bits - 1);
            if ((value & sign) != 0) value -= 1L << bits;
        }
        return value;
    }

    private byte[] encodeInteger(double converted) {
        long value = (long) converted;
        int bits = storage.bytes * 8;
        long minimum = storage.signed ? -(1L << (bits - 1)) : 0;
        long maximum = storage.signed ? (1L << (bits - 1)) - 1
                : bits == 32 ? 0xFFFFFFFFL : (1L << bits) - 1;
        if (converted < minimum || converted > maximum) {
            throw new IllegalArgumentException("Value is outside the table's stored range");
        }
        byte[] result = new byte[storage.bytes];
        for (int index = 0; index < result.length; index++) {
            int target = littleEndian ? index : result.length - 1 - index;
            result[target] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return result;
    }

    private byte[] encodeFloat(double converted) {
        int bits = Float.floatToIntBits((float) converted);
        byte[] result = new byte[4];
        for (int index = 0; index < result.length; index++) {
            int target = littleEndian ? index : result.length - 1 - index;
            result[target] = (byte) (bits & 0xFF);
            bits >>>= 8;
        }
        return result;
    }

    static final class Storage {
        final int bytes;
        final boolean signed;
        final boolean floating;

        private Storage(int bytes, boolean signed, boolean floating) {
            this.bytes = bytes;
            this.signed = signed;
            this.floating = floating;
        }

        static Storage parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if ("float".equals(normalized)) return new Storage(4, true, true);
            boolean signed;
            String bits;
            if (normalized.startsWith("uint")) {
                signed = false;
                bits = normalized.substring(4);
            } else if (normalized.startsWith("int")) {
                signed = true;
                bits = normalized.substring(3);
            } else {
                throw new IllegalArgumentException("Unsupported table storage " + value);
            }
            int bitCount;
            try {
                bitCount = Integer.parseInt(bits);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Unsupported table storage " + value);
            }
            if (bitCount != 8 && bitCount != 16 && bitCount != 24
                    && bitCount != 32) {
                throw new IllegalArgumentException("Unsupported table storage " + value);
            }
            return new Storage(bitCount / 8, signed, false);
        }
    }
}
