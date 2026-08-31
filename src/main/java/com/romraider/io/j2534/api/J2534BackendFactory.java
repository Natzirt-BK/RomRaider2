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

import static com.romraider.util.Platform.WINDOWS;
import static com.romraider.util.Platform.isPlatform;

import java.io.File;

import org.apache.log4j.Logger;

import com.romraider.io.j2534.api.J2534Impl.Protocol;
import com.sun.jna.Native;

/** Selects an in-process or isolated J2534 implementation by DLL bitness. */
final class J2534BackendFactory {
    private static final Logger LOGGER = Logger.getLogger(J2534BackendFactory.class);

    private J2534BackendFactory() {
    }

    static J2534 create(Protocol protocol, String library) {
        File file = new File(library);
        NativeLibraryArchitecture.Architecture architecture =
                NativeLibraryArchitecture.inspect(file);
        int processBits = Native.POINTER_SIZE * Byte.SIZE;
        if (shouldBridge(file, processBits, isPlatform(WINDOWS))) {
            LOGGER.info("J2534 DLL is " + architecture.bits() + "-bit while RomRaider2 is "
                    + processBits + "-bit; using the isolated cross-bitness bridge: "
                    + file.getAbsolutePath());
            return new BridgedJ2534(protocol, file, architecture.bits());
        }
        if (isPlatform(WINDOWS) && architecture.bits() != 0) {
            LOGGER.info("J2534 DLL matches the " + processBits
                    + "-bit RomRaider2 process; loading it directly: "
                    + file.getAbsolutePath());
        }
        return new J2534Impl(protocol, library);
    }

    static boolean shouldBridge(File library, int processBits, boolean windows) {
        return windows && NativeLibraryArchitecture.requiresBridge(library, processBits);
    }
}
