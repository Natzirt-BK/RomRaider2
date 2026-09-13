/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.maps.RomChecksum;
import com.romraider.maps.TableSwitch;
import com.romraider.portable.PortableRomDocument;
import com.romraider.portable.editor.PortableEcuDefinitionReader;
import com.romraider.portable.editor.PortableRomChecksum;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortableChecksumCompatibilityTest {
    @Test void matchesDesktopAlgorithmByteForByteAcrossRandomizedRoms() throws Exception {
        Random random = new Random(1112);
        for (int sample = 0; sample < 100; sample++) {
            byte[] bytes = new byte[4096];
            random.nextBytes(bytes);
            System.arraycopy("TEST".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            for (int i = 0; i < 14; i++) {
                int start = i * 256;
                buffer.putInt(3840 + 12 * i, start).putInt(3844 + 12 * i, start + 253 + sample % 4);
            }
            String xml = "<roms><rom><romid><xmlid>TEST</xmlid><make>Subaru</make>"
                    + "<internalidaddress>0</internalidaddress><internalidstring>TEST</internalidstring>"
                    + "<filesize>4096</filesize></romid><table name='Checksum Fix' type='Switch'"
                    + " storageaddress='f00' sizey='168'/><table name='Value' type='2D' sizey='1'"
                    + " storageaddress='40' storagetype='uint8'><scaling expression='x' to_byte='x'/>"
                    + "</table></rom></roms>";
            PortableRomChecksum portable = PortableEcuDefinitionReader.read(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                    new PortableRomDocument("Synthetic ROM", bytes)).getChecksum();
            byte[] original = bytes.clone();
            byte[] corrected = portable.prepareCopy(bytes);
            TableSwitch table = new TableSwitch();
            table.setStorageAddress(3840);
            table.setDataSize(168);
            RomChecksum.calculateRomChecksum(bytes, table);
            assertArrayEquals(bytes, corrected, "desktop parity sample " + sample);
            assertEquals(0, RomChecksum.validateRomChecksum(corrected, table));
            assertEquals(PortableRomChecksum.Status.VALID, portable.validate(corrected));
            assertEquals(PortableRomChecksum.Status.NEEDS_CORRECTION, portable.validate(original));
        }
    }
}
