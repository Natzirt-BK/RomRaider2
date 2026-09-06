/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.maps.Table2D;
import java.util.function.BooleanSupplier;

/** A selected, open editor table plus a session/busy-state lifetime check. */
record FxMafTransferTarget(Table2D table, String documentName, BooleanSupplier stillCurrent) { }
