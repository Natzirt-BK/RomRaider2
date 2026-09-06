/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.maps.Table;
import java.util.function.BooleanSupplier;

record FxMapTraceTarget(Table table, String documentName, BooleanSupplier stillCurrent) { }
