/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.romraider.io.j2534.api;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Reads the machine type from a Windows PE DLL without loading it. */
final class NativeLibraryArchitecture {
    enum Architecture {
        X86(32),
        X64(64),
        UNKNOWN(0);

        private final int bits;

        Architecture(int bits) {
            this.bits = bits;
        }

        int bits() {
            return bits;
        }
    }

    private static final int IMAGE_FILE_MACHINE_I386 = 0x014C;
    private static final int IMAGE_FILE_MACHINE_AMD64 = 0x8664;

    private NativeLibraryArchitecture() {
    }

    static Architecture inspect(File library) {
        if (library == null || !library.isFile()) return Architecture.UNKNOWN;
        try (RandomAccessFile input = new RandomAccessFile(library, "r")) {
            if (input.length() < 64 || input.readUnsignedByte() != 'M'
                    || input.readUnsignedByte() != 'Z') {
                return Architecture.UNKNOWN;
            }
            input.seek(0x3C);
            long peOffset = Integer.toUnsignedLong(readLittleEndianInt(input));
            if (peOffset < 0x40 || peOffset + 6 > input.length()) {
                return Architecture.UNKNOWN;
            }
            input.seek(peOffset);
            if (input.readUnsignedByte() != 'P' || input.readUnsignedByte() != 'E'
                    || input.readUnsignedByte() != 0 || input.readUnsignedByte() != 0) {
                return Architecture.UNKNOWN;
            }
            int machine = readLittleEndianShort(input);
            if (machine == IMAGE_FILE_MACHINE_I386) return Architecture.X86;
            if (machine == IMAGE_FILE_MACHINE_AMD64) return Architecture.X64;
        } catch (IOException ignored) {
            // The normal native loader will provide the actionable error when
            // the format cannot be identified safely here.
        }
        return Architecture.UNKNOWN;
    }

    static boolean requiresBridge(File library, int processBits) {
        Architecture architecture = inspect(library);
        return architecture.bits() != 0 && architecture.bits() != processBits;
    }

    private static int readLittleEndianShort(RandomAccessFile input)
            throws IOException {
        return input.readUnsignedByte() | input.readUnsignedByte() << 8;
    }

    private static int readLittleEndianInt(RandomAccessFile input)
            throws IOException {
        return input.readUnsignedByte()
                | input.readUnsignedByte() << 8
                | input.readUnsignedByte() << 16
                | input.readUnsignedByte() << 24;
    }
}
