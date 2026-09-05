/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.util.Arrays;

/** One bounded group of SSM addresses that can be read in a single request. */
public final class PortableLoggerQueryBatch {
    private final int[] addresses;
    private final PortableLoggerProtocol protocol;

    PortableLoggerQueryBatch(int[] addresses) {
        this(addresses, PortableLoggerProtocol.SSM);
    }

    PortableLoggerQueryBatch(int[] addresses, PortableLoggerProtocol protocol) {
        this.addresses = Arrays.copyOf(addresses, addresses.length);
        this.protocol = protocol;
    }

    public PortableLoggerProtocol getProtocol() { return protocol; }

    public int[] getAddresses() {
        return Arrays.copyOf(addresses, addresses.length);
    }

    public byte[] request() {
        if (protocol == PortableLoggerProtocol.MUT2) {
            if (addresses.length != 1) throw new IllegalArgumentException("MUT-II reads one PID at a time");
            return ReadOnlyMut2Protocol.request(addresses[0]);
        }
        return ReadOnlySsmProtocol.readAddressesRequest(addresses);
    }
}
