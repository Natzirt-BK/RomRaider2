/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.flash;

/** Operations that a protocol must advertise independently. */
public enum FlashOperation {
    READ,
    WRITE,
    VERIFY,
    RECOVER
}
