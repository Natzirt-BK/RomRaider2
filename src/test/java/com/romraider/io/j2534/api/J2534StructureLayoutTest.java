/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.io.j2534.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.sun.jna.Native;

/** Guards the native J2534 structure ABI across JNA upgrades and platforms. */
public final class J2534StructureLayoutTest {
    @Test
    public void configLayoutMatchesNativeLongPair() {
        InspectableConfig config = new InspectableConfig();

        assertEquals(0, config.offset("parameter"));
        assertEquals(Native.LONG_SIZE, config.offset("value"));
        assertEquals(Native.LONG_SIZE * 2, config.size());
    }

    @Test
    public void configListAlignsItsPointer() {
        InspectableConfigList list = new InspectableConfigList();
        int pointerOffset = align(Native.LONG_SIZE, Native.POINTER_SIZE);

        assertEquals(0, list.offset("numOfParams"));
        assertEquals(pointerOffset, list.offset("configPtr"));
        assertEquals(pointerOffset + Native.POINTER_SIZE, list.size());
    }

    @Test
    public void passThruMessageKeepsHeaderBeforeFixedDataBuffer() {
        InspectablePassThruMessage message = new InspectablePassThruMessage();

        assertEquals(0, message.offset("protocolID"));
        assertEquals(Native.LONG_SIZE, message.offset("rxStatus"));
        assertEquals(Native.LONG_SIZE * 2, message.offset("txFlags"));
        assertEquals(Native.LONG_SIZE * 3, message.offset("timestamp"));
        assertEquals(Native.LONG_SIZE * 4, message.offset("dataSize"));
        assertEquals(Native.LONG_SIZE * 5, message.offset("extraDataIndex"));
        assertEquals(Native.LONG_SIZE * 6, message.offset("data"));
        assertEquals(Native.LONG_SIZE * 6 + 4128, message.size());
    }

    @Test
    public void byteArrayAlignsItsPointer() {
        InspectableByteArray array = new InspectableByteArray();
        int pointerOffset = align(Native.LONG_SIZE, Native.POINTER_SIZE);

        assertEquals(0, array.offset("numOfBytes"));
        assertEquals(pointerOffset, array.offset("bytePtr"));
        assertEquals(pointerOffset + Native.POINTER_SIZE, array.size());
    }

    private static int align(int value, int boundary) {
        return (value + boundary - 1) / boundary * boundary;
    }

    private static final class InspectableConfig extends J2534_v0404.SCONFIG {
        private int offset(String field) {
            return fieldOffset(field);
        }
    }

    private static final class InspectableConfigList extends J2534_v0404.SCONFIG_LIST {
        private int offset(String field) {
            return fieldOffset(field);
        }
    }

    private static final class InspectablePassThruMessage extends J2534_v0404.PASSTHRU_MSG {
        private int offset(String field) {
            return fieldOffset(field);
        }
    }

    private static final class InspectableByteArray extends J2534_v0404.SBYTE_ARRAY {
        private int offset(String field) {
            return fieldOffset(field);
        }
    }
}
