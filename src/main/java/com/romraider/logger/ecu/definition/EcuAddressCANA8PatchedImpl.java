/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2013 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.logger.ecu.definition;

import java.util.LinkedList;
import java.util.List;

import static com.romraider.util.HexUtil.*;
import static com.romraider.util.ParamChecker.*;

public final class EcuAddressCANA8PatchedImpl implements EcuAddress {
    private final String[] addresses;
    private final byte[] bytes;
    private final int bit;

    public EcuAddressCANA8PatchedImpl(EcuAddress address) {
        checkNotNull(address, "address");
        this.addresses = address.getAddresses();
        this.bytes = getAddressBytes(addresses);
        this.bit = address.getBit();
    }

    public String[] getAddresses() {
        return addresses;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int getBit() {
        return bit;
    }

    public int getLength() {
        return addresses.length;
    }

    private byte[] getAddressBytes(String[] addresses) {
        byte[] bytes = new byte[0];
        int prevAddress = -1;
        int seq = 0;
        for (String address : addresses) {
            byte[] tmp1 = asBytes(address);
            int currAddr = Integer.decode(address);
            if (prevAddress != -1) {
                if (currAddr == prevAddress + 1 && (currAddr & 0xFF0000) == 0xFF0000) {
                    seq++;
                    prevAddress = currAddr;
                    if (addresses.length > (seq + 1)) {
                        continue;
                    }
                }
            }
            if (seq != 0) {
                if (seq == 1) {
                    bytes[0] = (byte) 0xF2;
                    continue;
                } else if (seq == 3) {
                    bytes[0] = (byte) 0xF4;
                    continue;
                } else {
                    System.out.println("FUCK!");
                }
            }
            byte[] tmp2 = new byte[bytes.length + tmp1.length];
            System.arraycopy(bytes, 0, tmp2, 0, bytes.length);
            System.arraycopy(tmp1, 0, tmp2, bytes.length, tmp1.length);
            bytes = tmp2;
            prevAddress = currAddr;
        }
        return bytes;
    }
}
