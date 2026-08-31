/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

/** Receives immutable updates on the flash worker thread. */
public interface FlashProgressListener {
    void progressChanged(FlashProgress progress);
}
