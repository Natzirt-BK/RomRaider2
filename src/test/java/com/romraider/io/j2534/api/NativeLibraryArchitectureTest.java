package com.romraider.io.j2534.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import org.junit.Test;

import com.romraider.io.j2534.api.NativeLibraryArchitecture.Architecture;

public final class NativeLibraryArchitectureTest {
    @Test
    public void identifiesX86AndRoutesOnlyMismatchedWindowsProcessesToBridge()
            throws Exception {
        File library = peLibrary(0x014C);
        try {
            assertEquals(Architecture.X86, NativeLibraryArchitecture.inspect(library));
            assertFalse(J2534BackendFactory.shouldBridge(library, 32, true));
            assertTrue(J2534BackendFactory.shouldBridge(library, 64, true));
            assertFalse(J2534BackendFactory.shouldBridge(library, 64, false));
        } finally {
            Files.deleteIfExists(library.toPath());
        }
    }

    @Test
    public void identifiesX64AndRoutesOnlyMismatchedWindowsProcessesToBridge()
            throws Exception {
        File library = peLibrary(0x8664);
        try {
            assertEquals(Architecture.X64, NativeLibraryArchitecture.inspect(library));
            assertTrue(J2534BackendFactory.shouldBridge(library, 32, true));
            assertFalse(J2534BackendFactory.shouldBridge(library, 64, true));
        } finally {
            Files.deleteIfExists(library.toPath());
        }
    }

    @Test
    public void leavesUnknownFormatsForTheNormalNativeLoaderToDiagnose()
            throws Exception {
        File library = Files.createTempFile("romraider2-not-pe", ".dll").toFile();
        try {
            Files.write(library.toPath(), new byte[] {'n', 'o', 't', 'p', 'e'});
            assertEquals(Architecture.UNKNOWN, NativeLibraryArchitecture.inspect(library));
            assertFalse(J2534BackendFactory.shouldBridge(library, 64, true));
        } finally {
            Files.deleteIfExists(library.toPath());
        }
    }

    private static File peLibrary(int machine) throws Exception {
        byte[] image = new byte[256];
        image[0] = 'M';
        image[1] = 'Z';
        ByteBuffer buffer = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x3C, 0x80);
        image[0x80] = 'P';
        image[0x81] = 'E';
        buffer.putShort(0x84, (short) machine);
        File library = Files.createTempFile("romraider2-pe", ".dll").toFile();
        Files.write(library.toPath(), image);
        return library;
    }
}
