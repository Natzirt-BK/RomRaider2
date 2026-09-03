/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps;

/** UI-neutral notifications emitted when a table presentation must refresh. */
public interface TablePresentationListener {
    default void tableChanged(Table table) { }
    default void cellChanged(Table table, DataCell cell) { }
    default void selectionAnchorChanged(Table table, int x, int y) { }
    default void invalidScale(Table table, Scale scale) { }
}
