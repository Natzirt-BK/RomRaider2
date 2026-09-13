/* RomRaider2 - GPL 2.0 or later. */
package com.romraider.portable;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Numeric presentation at the CSV boundary only; source samples retain full precision. */
public final class PortableCsvNumbers {
    private PortableCsvNumbers() { }

    public static String twoDecimals(double value) {
        return Double.isFinite(value)
                ? BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString() : "";
    }
}
