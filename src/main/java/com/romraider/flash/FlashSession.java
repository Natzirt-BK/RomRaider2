/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

import java.util.UUID;

/** One owned read, write, verify, or recovery attempt. */
public interface FlashSession {
    UUID getId();
    FlashRequest getRequest();
    FlashState getState();
    FlashResult execute();
    boolean requestCancellation();
}
