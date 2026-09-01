/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.util.Arrays;

/** One bounded group of SSM addresses that can be read in a single request. */
public final class PortableLoggerQueryBatch {
    private final int[] addresses;

    PortableLoggerQueryBatch(int[] addresses) {
        this.addresses = Arrays.copyOf(addresses, addresses.length);
    }

    public int[] getAddresses() {
        return Arrays.copyOf(addresses, addresses.length);
    }

    public byte[] request() {
        return ReadOnlySsmProtocol.readAddressesRequest(addresses);
    }
}
