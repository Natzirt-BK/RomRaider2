/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

/** UI-neutral messages and decisions requested by ROM operations. */
public interface RomUserInteraction {
    default void definitionError(Rom rom, Table table, String title,
            String message, Throwable failure) { }
    default boolean confirmChecksumFix(Rom rom, Table table, String title,
            String message) { return false; }
    default void checksumValidationFailed(Rom rom, String title,
            String message) { }
    default void checksumUpdated(Rom rom, String message) { }
}
