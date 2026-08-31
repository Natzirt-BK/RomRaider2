/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuntimeArchitectureTest {
    @Test
    public void explicitDataModelWinsOverArchitectureAlias() {
        assertEquals(64, RuntimeArchitecture.bitness("64", "x86"));
        assertEquals(32, RuntimeArchitecture.bitness("32-bit", "amd64"));
    }

    @Test
    public void recognizesCommonModernArchitectureNames() {
        assertEquals(64, RuntimeArchitecture.bitness(null, "amd64"));
        assertEquals(64, RuntimeArchitecture.bitness(null, "aarch64"));
        assertEquals(64, RuntimeArchitecture.bitness(null, "ppc64le"));
        assertEquals(32, RuntimeArchitecture.bitness(null, "x86"));
        assertEquals(-1, RuntimeArchitecture.bitness(null, "unknown"));
    }

    @Test
    public void currentProcessMatchesItsOwnReportedBitness() {
        assertTrue(RuntimeArchitecture.currentBitness() == 32
                || RuntimeArchitecture.currentBitness() == 64);
        assertTrue(RuntimeArchitecture.isCompatible(
                Integer.toString(RuntimeArchitecture.currentBitness())));
    }
}
