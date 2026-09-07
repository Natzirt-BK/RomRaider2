package com.romraider.mobile.logger;

import com.romraider.portable.openport.OpenPortMut2Startup;
import com.romraider.portable.openport.OpenPortStartupResponse;
import org.junit.Test;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Synthetic USB responses only; no adapter or ECU is opened. */
public class OpenPortMut2StartupTest {
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.US_ASCII); }

    @Test public void handshakeRequiresMatchingChannelRequestAndTwoKeywords() throws Exception {
        OpenPortStartupResponse reply = OpenPortStartupResponse.wake(7);
        for (String invalid : new String[] {"aro 7\r\n", "arw3 239 133 6\r\n",
                "arw4 239 133 7\r\n", "arw3 239 7\r\n", "arw3 256 133 7\r\n",
                "arw3 -1 133 7\r\n", "arw3 239 133 7 extra\r\n"}) {
            assertFalse(reply.accept(bytes(invalid), invalid.length()));
        }
        byte[] good = bytes("arw3 239 133 7\r\n");
        for (int i = 0; i < good.length - 1; i++) assertFalse(reply.accept(new byte[]{good[i]}, 1));
        assertTrue(reply.accept(new byte[]{good[good.length - 1]}, 1));
        assertArrayEquals(new byte[]{(byte)239, (byte)133}, reply.keywords());
    }

    @Test public void errorMustMatchOutstandingRequest() throws Exception {
        OpenPortStartupResponse reply = OpenPortStartupResponse.wake(7);
        assertFalse(reply.accept(bytes("are9 6\r\n"), 8));
        IOException error = assertThrows(IOException.class,
                () -> reply.accept(bytes("are9 7\r\n"), 8));
        assertTrue(error.getMessage().contains("five-baud"));
        assertTrue(error.getMessage().contains("9"));
    }

    @Test public void ignoresCommandTextEmbeddedInBinaryPackets() throws Exception {
        for (String embedded : new String[]{"arw3 239 133 7\r\n", "are9 7\r\n"}) {
            OpenPortStartupResponse reply = OpenPortStartupResponse.wake(7);
            byte[] data = bytes(embedded);
            byte[] packet = new byte[data.length + 5];
            packet[0] = 'a'; packet[1] = 'r'; packet[2] = '3';
            packet[3] = (byte)(data.length + 1); packet[4] = 0;
            System.arraycopy(data, 0, packet, 5, data.length);
            for (byte value : packet) assertFalse(reply.accept(new byte[]{value}, 1));
            assertTrue(reply.accept(bytes("arw3 239 133 7\r\n"), 16));
        }
    }

    @Test public void boundsAndIncompleteAcknowledgements() throws Exception {
        OpenPortStartupResponse reply = OpenPortStartupResponse.ack("pin release", 1);
        assertFalse(reply.accept(bytes("aro 1\r"), 6));
        assertTrue(reply.accept(bytes("\n"), 1));
        assertThrows(IllegalArgumentException.class, () -> OpenPortStartupResponse.wake(0));
        assertThrows(IllegalArgumentException.class, () -> OpenPortStartupResponse.wake(65536));
        assertThrows(IOException.class, () -> OpenPortStartupResponse.wake(1).accept(new byte[4097],4097));
    }

    @Test public void startupOrderingAndOnceOnlyPinRelease() throws Exception {
        Fake exchange = new Fake();
        OpenPortMut2Startup startup = new OpenPortMut2Startup();
        startup.start(exchange, () -> false);
        assertEquals(List.of("ats3 33 3 1\r\n", "atv 1 -2 2\r\n",
                "atw3 0 3\r\n", "ats3 1 15625 4\r\n"), exchange.commands);
        assertEquals(List.of(2000,2000,5000,2000), exchange.timeouts);
        startup.release(exchange);
        startup.release(exchange);
        assertEquals("atv 1 -1 5\r\n", exchange.commands.get(4));
        assertEquals(5, exchange.commands.size());
        assertFalse(exchange.cancellable.get(4));
    }

    @Test public void everyFailedStageStopsAndReleasesAnyPossiblyGroundedPin() throws Exception {
        for (int failure = 0; failure < 4; failure++) {
            Fake exchange = new Fake(); exchange.failAt = failure;
            OpenPortMut2Startup startup = new OpenPortMut2Startup();
            assertThrows(IOException.class, () -> startup.start(exchange, () -> false));
            assertEquals(failure == 0 ? 1 : failure + 2, exchange.commands.size());
            if (failure > 0) assertTrue(exchange.commands.get(exchange.commands.size()-1).startsWith("atv 1 -1 "));
        }
    }

    @Test public void cancellationReleasesPinWithoutSendingMoreStartupCommands() throws Exception {
        for (int stopAfter = 0; stopAfter <= 4; stopAfter++) {
            final int count = stopAfter;
            Fake exchange = new Fake();
            OpenPortMut2Startup startup = new OpenPortMut2Startup();
            assertThrows(InterruptedIOException.class,
                    () -> startup.start(exchange, () -> exchange.commands.size() >= count));
            assertEquals(count + (count >= 2 ? 1 : 0), exchange.commands.size());
        }
    }

    @Test public void failedReleaseIsRetriedAndReportedAlongsideStartupFailure() throws Exception {
        Fake exchange = new Fake(); exchange.failAt = 2; exchange.failRelease = true;
        OpenPortMut2Startup startup = new OpenPortMut2Startup();
        IOException failure = assertThrows(IOException.class, () -> startup.start(exchange, () -> false));
        assertEquals(1, failure.getSuppressed().length);
        exchange.failRelease = false;
        startup.release(exchange);
        assertTrue(exchange.commands.get(4).startsWith("atv 1 -1 "));
    }

    private static class Fake implements OpenPortMut2Startup.Exchange {
        final List<String> commands = new ArrayList<>();
        final List<Integer> timeouts = new ArrayList<>();
        final List<Boolean> cancellable = new ArrayList<>();
        int failAt = -1; boolean failRelease;
        @Override public void exchange(byte[] command, OpenPortStartupResponse reply,
                int timeout, boolean canCancel) throws IOException {
            String value = new String(command, StandardCharsets.US_ASCII);
            commands.add(value); timeouts.add(timeout); cancellable.add(canCancel);
            if (commands.size()-1 == failAt || failRelease && value.startsWith("atv 1 -1 "))
                throw new IOException("Synthetic failure");
            String id = value.trim().substring(value.trim().lastIndexOf(' ') + 1);
            String ack = value.startsWith("atw") ? "arw3 239 133 " + id + "\r\n" : "aro " + id + "\r\n";
            if (!reply.accept(bytes(ack), ack.length())) throw new IOException("Synthetic acknowledgement rejected");
        }
    }
}
