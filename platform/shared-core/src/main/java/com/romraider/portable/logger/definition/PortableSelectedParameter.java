/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.definition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A profile selection resolved to a conversion and concrete byte addresses. */
public final class PortableSelectedParameter {
    private final PortableLoggerParameter parameter;
    private final PortableLoggerConversion conversion;
    private final int[] addresses;

    public PortableSelectedParameter(PortableLoggerParameter parameter,
            PortableLoggerConversion conversion, int[] addresses) {
        if (parameter == null || conversion == null || addresses == null
                || addresses.length == 0) {
            throw new IllegalArgumentException(
                    "Resolved parameter, conversion, and addresses are required");
        }
        this.parameter = parameter;
        this.conversion = conversion;
        this.addresses = Arrays.copyOf(addresses, addresses.length);
    }

    public PortableLoggerParameter getParameter() { return parameter; }
    public PortableLoggerConversion getConversion() { return conversion; }
    public int[] getAddresses() {
        return Arrays.copyOf(addresses, addresses.length);
    }

    public static int[] expand(List<PortableLoggerAddress> ranges) {
        List<Integer> values = new ArrayList<Integer>();
        for (PortableLoggerAddress range : ranges) {
            for (int address : range.expand()) values.add(address);
        }
        int[] expanded = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            expanded[index] = values.get(index);
        }
        return expanded;
    }
}
