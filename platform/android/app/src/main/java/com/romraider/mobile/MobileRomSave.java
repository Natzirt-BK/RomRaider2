/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import com.romraider.portable.PortableRomDocument;
import java.io.IOException;
import java.io.OutputStream;

/** Provider I/O must finish, including close, before saved state is published. */
final class MobileRomSave {
    interface Destination {
        OutputStream open() throws IOException;
    }

    private MobileRomSave() { }

    static boolean save(PortableRomDocument document, byte[] snapshot,
            Destination destination) throws IOException {
        if (document == null || snapshot == null || destination == null) {
            throw new IllegalArgumentException("Document, snapshot and destination are required");
        }
        // Own the exact bytes that will be written and subsequently marked saved.
        byte[] written = snapshot.clone();
        try (OutputStream output = destination.open()) {
            if (output == null) throw new IOException("ROM destination is unavailable");
            output.write(written);
            output.flush();
        }
        // In particular, a provider close failure must leave recovery/dirty state intact.
        return document.markSavedIfCurrent(written);
    }
}
