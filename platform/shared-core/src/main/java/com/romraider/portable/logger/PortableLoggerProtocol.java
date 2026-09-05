/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

/** Explicit protocol selection; never probe a vehicle with guessed protocols. */
public enum PortableLoggerProtocol {
    SSM(4800), MUT2(15625);

    private final int baud;
    PortableLoggerProtocol(int baud) { this.baud = baud; }
    public int baud() { return baud; }
    public static PortableLoggerProtocol fromId(String id) {
        if ("SSM".equalsIgnoreCase(id)) return SSM;
        if ("MUT2".equalsIgnoreCase(id) || "MUT-II".equalsIgnoreCase(id)) return MUT2;
        throw new IllegalArgumentException("Unsupported read-only logger protocol: " + id);
    }
}
