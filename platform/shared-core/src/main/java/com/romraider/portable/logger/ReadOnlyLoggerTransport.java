/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.io.IOException;

/** Injectable read-only session boundary shared by USB and deterministic tests. */
public interface ReadOnlyLoggerTransport extends PortableLoggerDataSource {
    /** Support flags from this connection's validated SSM identity, or null if unavailable. */
    default byte[] ssmInitPayload() { return null; }
    String identifyEcu(PortableLoggerProtocol protocol) throws IOException;
    default String identifyEcu(PortableLoggerProtocol protocol,
            java.util.function.BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("Logger stopped");
        return identifyEcu(protocol);
    }
    void closeReadOnlyKLine();
}
