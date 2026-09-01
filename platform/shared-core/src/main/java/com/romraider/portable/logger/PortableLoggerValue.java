/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import com.romraider.portable.logger.definition.PortableSelectedParameter;

/** One converted value from a logger query cycle. */
public final class PortableLoggerValue {
    private final PortableSelectedParameter selection;
    private final double value;

    PortableLoggerValue(PortableSelectedParameter selection, double value) {
        this.selection = selection;
        this.value = value;
    }

    public PortableSelectedParameter getSelection() { return selection; }
    public double getValue() { return value; }
}
