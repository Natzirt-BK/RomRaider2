/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

/** Open transport/interface handle without any Swing dependency. */
public interface FlashDevice extends AutoCloseable {
    String getId();
    String getDisplayName();
    String getTransportName();
    boolean isOpen();
    void close() throws Exception;
}
