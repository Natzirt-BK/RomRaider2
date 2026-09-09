/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger.dimemod;

import com.romraider.portable.logger.ReadOnlySsmProtocol;
import com.romraider.portable.logger.definition.*;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Explicit DimeMod negotiation, not a write-free operation. No general write API. */
public final class DimeModDiscovery {
    public enum Mode { OFF, DISCOVER_ONLY, DISCOVER_AND_LOG }

    /** Must remain visible even when the user pressed Stop during negotiation. */
    public static final class UncertainStateException extends IOException {
        public UncertainStateException(String message, Throwable cause) { super(message, cause); }
    }

    public interface Transport {
        PortableDimeModMetadata discoverDimeMod(BooleanSupplier cancelled) throws IOException;
    }

    /** Exchange must be bounded; cleanup ignores user cancellation, not I/O deadlines. */
    public interface Link {
        byte[] exchange(byte[] request, boolean cleanup) throws IOException;
        default void pause() throws IOException {
            try { Thread.sleep(100); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("DimeMod discovery interrupted");
            }
        }
        default long millis() { return System.nanoTime() / 1_000_000; }
    }

    private DimeModDiscovery() { }

    public static PortableDimeModMetadata discover(Link link, BooleanSupplier cancelled) throws IOException {
        long deadline = link.millis() + 30_000;
        check(link, cancelled, deadline);
        int saved = read(link, new int[]{0x60})[0] & 255;
        link.pause();
        check(link, cancelled, deadline);
        final int ack;
        try { ack = writeReply(link.exchange(write(0x60, 0xDE), false)); }
        catch (IOException | IllegalArgumentException ex) {
            throw new UncertainStateException("DimeMod entry was not confirmed. When parked, cycle ignition before reconnecting; no retry was sent.", ex);
        }
        if (ack != 0xAD) {
            // Match the author's non-DimeMod fallback, restoring exactly the saved byte.
            try {
                if (writeReply(cleanupExchange(link, write(0x60, saved))) != saved) {
                    throw new IOException("Unexpected saved-state acknowledgement");
                }
            } catch (IOException | IllegalArgumentException ex) {
                throw new UncertainStateException("DimeMod was not detected and saved-state restoration was not confirmed. When parked, cycle ignition before reconnecting.", ex);
            }
            return null; // Stock ECU: restored state, continue ordinary SSM logging.
        }
        byte[] header = new byte[5];
        IOException failure = null;
        try {
            for (int i = 0; i < header.length; i++) {
                link.pause();
                check(link, cancelled, deadline);
                header[i] = read(link, new int[]{0x60})[0];
            }
        } catch (IOException | IllegalArgumentException ex) {
            failure = new IOException("DimeMod discovery header was not completed.", ex);
        } finally {
            // A confirmed entry always gets one bounded exit, even on Stop/header failure.
            try {
                writeReply(cleanupExchange(link, write(0, 0)));
            } catch (IOException | IllegalArgumentException ex) {
                IOException cleanup = new UncertainStateException("DimeMod exit was not confirmed. When parked, cycle ignition before reconnecting.", ex);
                if (failure != null) cleanup.addSuppressed(failure);
                failure = cleanup;
            }
        }
        if (failure != null) throw failure;
        check(link, cancelled, deadline);
        int length = ((header[0] & 255) << 8) | (header[1] & 255);
        int high = header[2] & 255;
        int address = (high << 16) | ((header[3] & 255) << 8) | (header[4] & 255);
        if (length < 4 || (high > 0x0F && high != 0xFF)
                || (long) address + length > 0x1000000L) {
            throw new IOException("Invalid DimeMod metadata range; no metadata reads were sent.");
        }
        byte[] bytes = new byte[length];
        for (int offset = 0; offset < length; offset += 96) {
            link.pause();
            check(link, cancelled, deadline);
            int count = Math.min(96, length - offset);
            System.arraycopy(readMemory(link, address + offset, count), 0, bytes, offset, count);
        }
        try {
            PortableDimeModMetadata metadata = new PortableDimeModMetadata(bytes);
            if (!metadata.supported()) throw new IOException("Unsupported DimeMod version: " + metadata.version());
            link.pause();
            check(link, cancelled, deadline);
            int af = metadata.activeFeaturesAddress(), ai = metadata.activeInputsAddress();
            ByteBuffer runtime = ByteBuffer.wrap(read(link, new int[]{af, af + 1, af + 2, af + 3, ai, ai + 1}));
            metadata.updateRuntimeData(runtime.getInt(), runtime.getShort() & 0xFFFF, null, null);
            check(link, cancelled, deadline);
            return metadata;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new IOException("DimeMod metadata was rejected: " + ex.getMessage(), ex);
        }
    }

    /** Runtime addresses are bound to this ECU and never serialized into the source XML. */
    public static PortableLoggerDefinition merge(PortableLoggerDefinition base, String ecuId,
            PortableDimeModMetadata metadata) {
        if (!"SSM".equalsIgnoreCase(base.getProtocol()) || ecuId == null || ecuId.isEmpty() || !metadata.supported()) {
            throw new IllegalArgumentException("DimeMod channels require an identified SSM ECU");
        }
        List<PortableLoggerParameter> parameters = new ArrayList<>(base.parameters());
        for (PortableLoggerParameter channel : metadata.parameters()) {
            if (base.parameter(channel.getId()) != null) {
                throw new IllegalArgumentException("Definition conflicts with discovered channel " + channel.getId());
            }
            parameters.add(new PortableLoggerParameter(channel.getId(), channel.getName(), channel.getDescription(), 1,
                    Collections.singletonMap(ecuId, channel.addressesFor(null)),
                    channel.getDependencies(), channel.getConversions()));
        }
        return new PortableLoggerDefinition(base.getVersion(), base.getProtocol(), parameters);
    }

    private static byte[] read(Link link, int[] addresses) throws IOException {
        try { return ReadOnlySsmProtocol.readAddressValues(link.exchange(
                ReadOnlySsmProtocol.readAddressesRequest(addresses), false), addresses.length); }
        catch (IllegalArgumentException ex) { throw new IOException("Invalid DimeMod read response", ex); }
    }

    private static byte[] readMemory(Link link, int address, int count) throws IOException {
        byte[] request = {(byte) 0x80, 0x10, (byte) 0xF0, 6, (byte) 0xA0, 0,
                (byte) (address >>> 16), (byte) (address >>> 8), (byte) address, (byte) (count - 1), 0};
        for (int i = 0; i < request.length - 1; i++) request[10] += request[i];
        byte[] response = link.exchange(request, false);
        if (response == null || response.length != count + 6 || (response[0] & 255) != 0x80
                || (response[1] & 255) != 0xF0 || (response[2] & 255) != 0x10
                || (response[3] & 255) != count + 1 || (response[4] & 255) != 0xE0) {
            throw new IOException("Invalid DimeMod metadata response");
        }
        byte sum = 0;
        for (int i = 0; i < response.length - 1; i++) sum += response[i];
        if (sum != response[response.length - 1]) throw new IOException("Invalid DimeMod metadata checksum");
        return Arrays.copyOfRange(response, 5, response.length - 1);
    }

    private static void check(Link link, BooleanSupplier cancelled, long deadline) throws IOException {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("DimeMod discovery stopped");
        if (link.millis() >= deadline) throw new IOException("DimeMod discovery exceeded its 30-second limit");
    }

    private static byte[] cleanupExchange(Link link, byte[] request) throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            try { link.pause(); }
            catch (InterruptedIOException ex) { interrupted = true; Thread.interrupted(); }
            return link.exchange(request, true);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static byte[] write(int address, int value) {
        if (address != 0 && address != 0x60) throw new IllegalArgumentException("Not a discovery register");
        byte[] frame = {(byte) 0x80, 0x10, (byte) 0xF0, 5, (byte) 0xB8,
                0, 0, (byte) address, (byte) value, 0};
        for (int i = 0; i < frame.length - 1; i++) frame[9] += frame[i];
        return frame;
    }

    private static int writeReply(byte[] frame) throws IOException {
        if (frame == null || frame.length != 7 || (frame[0] & 255) != 0x80
                || (frame[1] & 255) != 0xF0 || (frame[2] & 255) != 0x10
                || frame[3] != 2 || (frame[4] & 255) != 0xF8) {
            throw new IOException("Invalid DimeMod negotiation response");
        }
        byte sum = 0;
        for (int i = 0; i < frame.length - 1; i++) sum += frame[i];
        if (sum != frame[6]) throw new IOException("Invalid DimeMod negotiation checksum");
        return frame[5] & 255;
    }
}
