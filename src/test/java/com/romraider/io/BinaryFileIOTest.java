/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.io;

import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public final class BinaryFileIOTest {
    @Test
    public void readsAFileWithinTheCallerLimit() throws Exception {
        Path file = Files.createTempFile("romraider2-binary", ".bin");
        try {
            Files.write(file, new byte[] {1, 2, 3});
            assertArrayEquals(new byte[] {1, 2, 3},
                    BinaryFileIO.read(file.toFile(), 3));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test(expected = IOException.class)
    public void rejectsAFileBeforeAllocatingPastTheCallerLimit()
            throws Exception {
        Path file = Files.createTempFile("romraider2-binary", ".bin");
        try {
            Files.write(file, new byte[] {1, 2, 3, 4});
            BinaryFileIO.read(file.toFile(), 3);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
