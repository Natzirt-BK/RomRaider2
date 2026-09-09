/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile.usb;

import com.romraider.mobile.logger.DimeModDiscoveryTest;
import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.dimemod.DimeModDiscovery;
import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Actual Android transport and packet decoder, with an in-memory USB endpoint. */
public class OpenPortDimeModTest {
    @Test public void fragmentedUsbCarriesAuthorHandshakeAndMetadata() throws Exception {
        Endpoint endpoint = new Endpoint();
        try (OpenPortUsbTransport transport = new OpenPortUsbTransport(endpoint)) {
            assertEquals("0102030405", transport.identifyEcu(PortableLoggerProtocol.SSM));
            assertTrue(transport.discoverDimeMod(() -> false).parameters().stream().anyMatch(p -> p.getId().equals("DM911")));
            assertEquals(2, endpoint.writes);
            assertEquals(1, endpoint.identifies);
        }
        assertEquals(1, endpoint.closes);
    }

    @Test public void stoppedTransportStillSendsConfirmedEntryExit() throws Exception {
        Endpoint endpoint = new Endpoint();
        try (OpenPortUsbTransport transport = new OpenPortUsbTransport(endpoint)) {
            transport.identifyEcu(PortableLoggerProtocol.SSM);
            // Stop after receiving the complete entry acknowledgement, not before it.
            assertThrows(IOException.class, () -> transport.discoverDimeMod(
                    () -> endpoint.writes == 1 && endpoint.pending.isEmpty()));
            assertEquals(2, endpoint.writes);
        }
    }

    @Test public void unavailableOrNonSsmChannelCannotSendDiscovery() throws Exception {
        Endpoint endpoint = new Endpoint();
        try (OpenPortUsbTransport transport = new OpenPortUsbTransport(endpoint)) {
            assertThrows(IOException.class, () -> transport.discoverDimeMod(() -> false));
            assertEquals(0, endpoint.writes);
            transport.openReadOnlyKLine(PortableLoggerProtocol.SSM);
            assertThrows(IOException.class, () -> transport.discoverDimeMod(() -> false));
            assertEquals(0, endpoint.writes);
        }
    }

    static final class Endpoint implements OpenPortUsbTransport.UsbIo {
        final DimeModDiscoveryTest.Wire ecu = new DimeModDiscoveryTest.Wire();
        final ArrayDeque<Byte> pending = new ArrayDeque<>();
        long time;
        int writes, identifies, closes;
        public int write(byte[] request, int timeout) {
            String text = new String(request, StandardCharsets.ISO_8859_1);
            if (text.startsWith("att3")) {
                byte[] frame = Arrays.copyOfRange(request, text.indexOf("\r\n") + 2, request.length);
                byte[] response;
                if ((frame[4] & 255) == 0xBF) {
                    identifies++;
                    response = DimeModDiscoveryTest.response(0xFF, new byte[]{0, 0, 0, 1, 2, 3, 4, 5});
                } else {
                    boolean cleanup = (frame[4] & 255) == 0xB8 && writes > 0;
                    if ((frame[4] & 255) == 0xB8) writes++;
                    try { response = ecu.exchange(frame, cleanup); }
                    catch (IOException ex) { throw new AssertionError(ex); }
                }
                queue(new byte[]{'a', 'r', '3', (byte) (response.length + 1), 0});
                queue(response); queue(new byte[]{'a', 'r', '3', 1, 0x40});
            } else queue((text.startsWith("atf") ? "arf3 0 0\r\n" : "aro\r\n").getBytes(StandardCharsets.US_ASCII));
            return request.length;
        }
        void queue(byte[] bytes) { for (byte value : bytes) pending.add(value); }
        public int read(byte[] buffer, int timeout) {
            if (pending.isEmpty()) { time += timeout; return -1; }
            buffer[0] = pending.remove(); time++; return 1;
        }
        public int packetSize() { return 64; }
        public long elapsedRealtime() { return time; }
        public void close() { closes++; }
    }
}
