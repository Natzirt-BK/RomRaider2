/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps.history;

/** Scope used to combine a multi-cell operation into one undo step. */
public final class EditTransaction implements AutoCloseable {
    private final RomEditHistory history;
    private boolean closed;

    EditTransaction(RomEditHistory history) {
        this.history = history;
    }

    public void close() {
        if (closed) return;
        closed = true;
        history.endTransaction();
    }
}
