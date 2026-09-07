package com.romraider.mobile.usb;

import com.romraider.portable.logger.PortableLoggerProtocol;
import org.junit.Test;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Runs the production transport with a synthetic USB endpoint and clock. */
public class OpenPortUsbTransportStartupTest {
    @Test public void wakesBeforeBatteryProbeAndDoesNotWakeEachRead() throws Exception {
        FakeUsb usb = new FakeUsb();
        OpenPortUsbTransport transport = new OpenPortUsbTransport(usb);
        assertEquals("MUT2_GENERIC", transport.identifyEcu(PortableLoggerProtocol.MUT2));
        transport.openReadOnlyKLine(PortableLoggerProtocol.MUT2);
        assertEquals(1, usb.count("atw3"));
        assertEquals(1, usb.count("att3"));
        assertTrue("Separate wake deadline permits a four-second response", usb.time >= 4000);
        assertTrue(usb.first("atw3") < usb.first("att3"));
        assertTrue(usb.first("ats3 1 15625") < usb.first("att3"));
        transport.close(); transport.close();
        assertEquals(1, usb.count("atv 1 -1"));
        assertEquals(1, usb.closes);
    }

    @Test public void ssmNeverTouchesMitsubishiPinsOrWakeup() throws Exception {
        FakeUsb usb = new FakeUsb();
        OpenPortUsbTransport transport = new OpenPortUsbTransport(usb);
        transport.openReadOnlyKLine(PortableLoggerProtocol.SSM);
        transport.close();
        assertEquals("ato3 512 4800 0\r\n", usb.writes.get(0));
        assertEquals(0, usb.count("atw")); assertEquals(0, usb.count("atv"));
        assertEquals(0, usb.count("ats3 33"));
    }

    @Test public void wakeFailureNeverFallsThroughToPidPolling() throws Exception {
        FakeUsb usb = new FakeUsb(); usb.wakeError = true;
        OpenPortUsbTransport transport = new OpenPortUsbTransport(usb);
        IOException failure = assertThrows(IOException.class, () -> transport.identifyEcu(PortableLoggerProtocol.MUT2));
        assertTrue(failure.getMessage().contains("five-baud"));
        assertEquals(0, usb.count("att3")); assertEquals(1, usb.count("atv 1 -1"));
        transport.close(); assertEquals(1, usb.closes);
    }

    @Test public void staleWakeReplyTimesOutAndReleasesPin() throws Exception {
        FakeUsb usb = new FakeUsb(); usb.staleWake = true;
        OpenPortUsbTransport transport = new OpenPortUsbTransport(usb);
        IOException failure = assertThrows(IOException.class, () -> transport.identifyEcu(PortableLoggerProtocol.MUT2));
        assertTrue(failure.getMessage().contains("matching acknowledgement"));
        assertEquals(0, usb.count("att3")); assertEquals(1, usb.count("atv 1 -1"));
        transport.close();
    }

    @Test public void cancellationDuringWakeStillPerformsCleanup() throws Exception {
        FakeUsb usb = new FakeUsb();
        OpenPortUsbTransport transport = new OpenPortUsbTransport(usb);
        assertThrows(InterruptedIOException.class, () -> transport.identifyEcu(
                PortableLoggerProtocol.MUT2, () -> usb.count("atw3") > 0));
        assertEquals(0, usb.count("att3")); assertEquals(1, usb.count("atv 1 -1"));
        transport.close(); assertEquals(1, usb.closes);
    }

    @Test public void shortGroundWriteStillTriggersRelease() throws Exception {
        FakeUsb usb = new FakeUsb(); usb.shortGround = true;
        OpenPortUsbTransport transport = new OpenPortUsbTransport(usb);
        assertThrows(IOException.class, () -> transport.identifyEcu(PortableLoggerProtocol.MUT2));
        assertEquals(0, usb.count("atw3")); assertEquals(1, usb.count("atv 1 -1"));
        transport.close();
    }

    @Test public void releaseFailureIsVisibleButCannotPreventUsbClose() throws Exception {
        FakeUsb usb = new FakeUsb();
        OpenPortUsbTransport transport = new OpenPortUsbTransport(usb);
        transport.identifyEcu(PortableLoggerProtocol.MUT2);
        usb.failRelease = true;
        IllegalStateException failure = assertThrows(IllegalStateException.class, transport::close);
        assertTrue(failure.getMessage().contains("pin release"));
        assertEquals(1, usb.count("atz")); assertEquals(1, usb.closes);
    }

    @Test public void switchingToSsmReleasesMitsubishiPinFirst() throws Exception {
        FakeUsb usb = new FakeUsb();
        OpenPortUsbTransport transport = new OpenPortUsbTransport(usb);
        transport.identifyEcu(PortableLoggerProtocol.MUT2);
        transport.openReadOnlyKLine(PortableLoggerProtocol.SSM);
        assertTrue(usb.first("atv 1 -1") < usb.first("ato3 512 4800"));
        transport.close();
    }

    private static class FakeUsb implements OpenPortUsbTransport.UsbIo {
        final List<String> writes = new ArrayList<>();
        final ArrayDeque<Byte> pending = new ArrayDeque<>();
        long time; int closes; int wakeDelay;
        boolean wakeError, staleWake, shortGround, failRelease;
        @Override public int write(byte[] bytes, int timeout) {
            String command = new String(bytes, StandardCharsets.ISO_8859_1);
            writes.add(command);
            if (shortGround && command.startsWith("atv 1 -2")) return bytes.length - 1;
            String[] fields = command.trim().split(" ");
            String id = fields[fields.length - 1];
            if (command.startsWith("atw")) {
                // Four seconds of firmware work must fit the separate init deadline.
                wakeDelay = 4000;
                queue((wakeError ? "are9 " + id : "arw3 239 133 " + (staleWake ? "65535" : id)) + "\r\n");
            } else if (command.startsWith("atv")) {
                if (!(failRelease && command.startsWith("atv 1 -1"))) queue("aro " + id + "\r\n");
            } else if (command.startsWith("ats") && fields.length == 4) {
                queue("aro " + id + "\r\n");
            } else if (command.startsWith("atf")) {
                queue("arf3 0 0\r\n");
            } else if (command.startsWith("att")) {
                for (byte value : new byte[]{'a','r','3',2,0,(byte)180,'a','r','3',1,0x40}) pending.add(value);
            } else queue("aro\r\n");
            return bytes.length;
        }
        void queue(String value) { for (byte b : value.getBytes(StandardCharsets.US_ASCII)) pending.add(b); }
        @Override public int read(byte[] buffer, int timeout) {
            if (wakeDelay > 0 && writes.get(writes.size()-1).startsWith("atw")) {
                int elapsed = Math.min(timeout, wakeDelay);
                wakeDelay -= elapsed; time += elapsed; return -1;
            }
            if (pending.isEmpty()) { time += timeout; return -1; }
            // Fragment every control/vehicle packet down to one USB byte.
            buffer[0] = pending.remove(); time++; return 1;
        }
        int count(String prefix) { return (int)writes.stream().filter(s -> s.startsWith(prefix)).count(); }
        int first(String prefix) { for (int i=0;i<writes.size();i++) if(writes.get(i).startsWith(prefix)) return i; return -1; }
        @Override public int packetSize() { return 64; }
        @Override public long elapsedRealtime() { return time; }
        @Override public void close() { closes++; }
    }
}
