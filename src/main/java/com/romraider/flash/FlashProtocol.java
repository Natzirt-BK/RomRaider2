/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

/** Replaceable protocol implementation for a specific supported ECU family. */
public interface FlashProtocol {
    String getId();
    String getDisplayName();
    boolean supports(FlashTarget target, FlashDevice device);
    FlashCapabilities getCapabilities(FlashTarget target, FlashDevice device);
    FlashPreflight preflight(FlashRequest request);
    FlashSession createSession(FlashRequest request,
            FlashProgressListener listener);
}
