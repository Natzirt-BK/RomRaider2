/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.io.j2534.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Assume;
import org.junit.Test;

import com.fazecast.jSerialComm.SerialPort;
import com.sun.jna.Native;

/** Hardware-free runtime checks for the native communication bridge layer. */
public final class RuntimeNativeBridgeSmokeTest {
    @Test
    public void jnaCoreLoadsForCurrentVmArchitecture() {
        assertTrue(Native.POINTER_SIZE == 4 || Native.POINTER_SIZE == 8);
    }

    @Test
    public void serialProviderLoadsAndEnumeratesWithoutOpeningADevice() {
        assertEquals("2.11.4", SerialPort.getVersion());
        assertNotNull(SerialPort.getCommPorts());
    }

    @Test
    public void configuredJ2534LibraryLinksWithoutOpeningAnEcuSession() {
        String configured = System.getProperty(
                "romraider2.j2534.library", "").trim();
        Assume.assumeTrue(!configured.isEmpty());
        File library = new File(configured);
        Assume.assumeTrue(library.isFile());

        assertNotNull(new J2534_v0404(library.getAbsolutePath()));
    }
}
