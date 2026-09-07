/*
 * RomRaider2 ECU Studio - GPL 2.0 or later.
 * OpenPort command framing is based on NikolaKozina/j2534, BSD-3-Clause.
 * See licenses/NikolaKozina-j2534-BSD-3-Clause.txt.
 */
package com.romraider.mobile.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import com.romraider.portable.openport.OpenPortWireProtocol;
import com.romraider.portable.openport.OpenPortKLineFrameDecoder;
import com.romraider.portable.openport.OpenPortControlResponse;
import com.romraider.portable.openport.OpenPortMut2Startup;
import com.romraider.portable.openport.OpenPortStartupResponse;
import com.romraider.portable.logger.ReadOnlyLoggerTransport;
import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.ReadOnlyMut2Protocol;
import com.romraider.portable.logger.PortableLoggerQueryBatch;
import com.romraider.portable.logger.ReadOnlySsmProtocol;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/** Owns one Android USB host session with an OpenPort 2.0 adapter. */
public final class OpenPortUsbTransport implements Closeable,
        ReadOnlyLoggerTransport {
    private static final int CONTROL_TIMEOUT_MS = 2000;
    private static final int VEHICLE_TIMEOUT_MS = 2500;
    private static final int READ_SLICE_MS = 250;
    private static final int DRAIN_SLICE_MS = 20;
    private static final int MAX_VEHICLE_RESPONSE_BYTES = 16 * 1024;

    private final UsbDevice device;
    private final UsbIo io;
    private final String firmwareVersion;
    private final Integer batteryMillivolts;
    private final OpenPortKLineFrameDecoder kLineDecoder =
            new OpenPortKLineFrameDecoder();
    private boolean kLineOpen;
    private PortableLoggerProtocol activeProtocol;
    private java.util.function.BooleanSupplier cancelled = () -> false;
    private boolean closed;
    private final OpenPortMut2Startup mut2Startup = new OpenPortMut2Startup();

    /** Small I/O boundary so the actual transport can be tested without Android USB. */
    interface UsbIo {
        int read(byte[] buffer, int timeoutMs);
        int write(byte[] buffer, int timeoutMs);
        int packetSize();
        long elapsedRealtime();
        void close();
    }

    OpenPortUsbTransport(UsbIo io) {
        this(null, io, "offline-test", null);
    }

    private OpenPortUsbTransport(UsbDevice device, UsbIo io,
            String firmwareVersion, Integer batteryMillivolts) {
        this.device = device;
        this.io = io;
        this.firmwareVersion = firmwareVersion;
        this.batteryMillivolts = batteryMillivolts;
    }

    private OpenPortUsbTransport(UsbDevice device,
            UsbDeviceConnection connection, UsbInterface usbInterface,
            UsbEndpoint input, UsbEndpoint output, String firmwareVersion,
            Integer batteryMillivolts) {
        this(device, new UsbIo() {
            public int read(byte[] buffer, int timeout) {
                return connection.bulkTransfer(input, buffer, buffer.length, timeout);
            }
            public int write(byte[] buffer, int timeout) {
                return connection.bulkTransfer(output, buffer, buffer.length, timeout);
            }
            public int packetSize() { return input.getMaxPacketSize(); }
            public long elapsedRealtime() { return android.os.SystemClock.elapsedRealtime(); }
            public void close() {
                try { connection.releaseInterface(usbInterface); }
                finally { connection.close(); }
            }
        }, firmwareVersion, batteryMillivolts);
    }

    public static boolean isOpenPort(UsbDevice device) {
        return device != null
                && device.getVendorId() == OpenPortWireProtocol.VENDOR_ID
                && device.getProductId() == OpenPortWireProtocol.PRODUCT_ID;
    }

    public static OpenPortUsbTransport open(UsbManager manager,
            UsbDevice device) throws IOException {
        if (manager == null || !isOpenPort(device)) {
            throw new IOException("No OpenPort 2.0 is selected.");
        }
        if (!manager.hasPermission(device)) {
            throw new IOException("USB permission is required.");
        }
        EndpointSet endpoints = endpoints(device);
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) {
            throw new IOException("Android could not open the OpenPort 2.0.");
        }
        if (!connection.claimInterface(endpoints.usbInterface, true)) {
            connection.close();
            throw new IOException("Android could not claim the OpenPort USB interface.");
        }

        OpenPortUsbTransport transport = null;
        try {
            OpenPortUsbTransport opening = new OpenPortUsbTransport(device,
                    connection, endpoints.usbInterface, endpoints.input,
                    endpoints.output, "", null);
            byte[] identify = opening.exchange(
                    OpenPortWireProtocol.identifyRequest(), "ari ", "firmware identification");
            String firmware = OpenPortWireProtocol.parseFirmwareVersion(
                    identify, identify.length);
            opening.exchange(OpenPortWireProtocol.openRequest(), "aro\r\n", "adapter preparation");
            Integer voltage = null;
            try {
                byte[] battery = opening.exchange(
                        OpenPortWireProtocol.batteryVoltageRequest(), "arr ", "battery measurement");
                voltage = OpenPortWireProtocol.parseBatteryMillivolts(
                        battery, battery.length);
            } catch (IOException | IllegalArgumentException ignored) {
                // Firmware identification is sufficient to prepare the adapter.
            }
            transport = new OpenPortUsbTransport(device, connection,
                    endpoints.usbInterface, endpoints.input, endpoints.output,
                    firmware, voltage);
            return transport;
        } catch (RuntimeException ex) {
            throw new IOException("The OpenPort response was not understood.", ex);
        } finally {
            if (transport == null) {
                try { connection.releaseInterface(endpoints.usbInterface); }
                finally { connection.close(); }
            }
        }
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public Integer getBatteryMillivolts() {
        return batteryMillivolts;
    }

    public boolean matches(UsbDevice candidate) {
        return device != null && candidate != null
                && device.getDeviceId() == candidate.getDeviceId();
    }

    /** Opens only an explicitly selected read-only K-line transport. */
    public synchronized void openReadOnlyKLine(PortableLoggerProtocol protocol) throws IOException {
        ensureOpen();
        checkCancelled();
        if (kLineOpen && activeProtocol == protocol) return;
        closeReadOnlyKLine();
        exchange(OpenPortWireProtocol.openKLineRequest(protocol), "aro\r\n",
                protocol.name() + " channel open");
        kLineDecoder.reset();
        kLineOpen = true;
        try {
            int setting = 0;
            for (byte[] config : OpenPortWireProtocol.kLineConfigurationRequests()) {
                checkCancelled();
                exchange(config, "aro\r\n", protocol.name()
                        + " timing/format setting " + (++setting));
            }
            checkCancelled();
            exchange(OpenPortWireProtocol.kLinePassFilterRequest(),
                    OpenPortControlResponse.forKLineFilter(protocol));
            if (protocol == PortableLoggerProtocol.MUT2) {
                mut2Startup.start(this::exchangeStartup, cancelled);
            }
            activeProtocol = protocol;
        } catch (IOException | RuntimeException ex) {
            try { closeReadOnlyKLine(); }
            catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    /** Identifies SSM or confirms a generic MUT2 responder; never changes ECU memory. */
    public synchronized String identifyEcu(PortableLoggerProtocol protocol) throws IOException {
        return identifyEcu(protocol, () -> false);
    }

    @Override
    public synchronized String identifyEcu(PortableLoggerProtocol protocol,
            java.util.function.BooleanSupplier cancelled) throws IOException {
        this.cancelled = cancelled;
        openReadOnlyKLine(protocol);
        try {
            if (protocol == PortableLoggerProtocol.MUT2) {
                return ReadOnlyMut2Protocol.probeIdentity(transceiveSsm(
                        ReadOnlyMut2Protocol.request(ReadOnlyMut2Protocol.PROBE_PID)));
            }
            byte[] response = transceiveSsm(ReadOnlySsmProtocol.ecuInitRequest());
            return ReadOnlySsmProtocol.ecuId(response);
        } catch (IllegalArgumentException ex) {
            throw new IOException("The ECU identification response was invalid.", ex);
        }
    }

    /** Executes one address-read batch; no write request is exposed. */
    @Override
    public synchronized byte[] read(PortableLoggerQueryBatch batch)
            throws IOException {
        if (batch == null) throw new IOException("Logger query batch is missing.");
        openReadOnlyKLine(batch.getProtocol());
        int expected = batch.getAddresses().length;
        byte[] response = transceiveSsm(batch.request());
        try {
            if (batch.getProtocol() == PortableLoggerProtocol.MUT2) {
                return new byte[] {(byte) ReadOnlyMut2Protocol.value(
                        batch.getAddresses()[0], response)};
            }
            return ReadOnlySsmProtocol.readAddressValues(response, expected);
        } catch (IllegalArgumentException ex) {
            throw new IOException("The ECU logger response was invalid.", ex);
        }
    }

    public synchronized void closeReadOnlyKLine() {
        if (closed) return;
        IOException pinFailure = null;
        try { mut2Startup.release(this::exchangeStartup); }
        catch (IOException ex) { pinFailure = ex; }
        try {
            if (kLineOpen) exchange(OpenPortWireProtocol.closeSsmKLineRequest(), "aro\r\n", "channel close");
        } catch (IOException ignored) {
            // A timeout or detach is a normal channel-close path.
        } finally {
            kLineOpen = false;
            activeProtocol = null;
            kLineDecoder.reset();
        }
        if (pinFailure != null) throw new IllegalStateException(
                "MUT-II diagnostic pin release was not confirmed. Disconnect the OpenPort when safe.", pinFailure);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        RuntimeException cleanupFailure = null;
        try {
            try { closeReadOnlyKLine(); }
            catch (RuntimeException ex) { cleanupFailure = ex; }
            exchange(OpenPortWireProtocol.closeRequest(), "aro\r\n", "adapter close");
        } catch (IOException ignored) {
            // Detach and timeout are normal close paths.
        } finally {
            closed = true;
            io.close();
        }
        if (cleanupFailure != null) throw cleanupFailure;
    }

    private byte[] exchange(byte[] request, String expected, String operation)
            throws IOException {
        return exchange(request, OpenPortControlResponse.forPrefix(operation, expected));
    }

    private synchronized byte[] exchange(byte[] request, OpenPortControlResponse reply)
            throws IOException {
        ensureOpen();
        write(request, CONTROL_TIMEOUT_MS);

        long deadline = io.elapsedRealtime()
                + CONTROL_TIMEOUT_MS;
        byte[] chunk = new byte[Math.max(64, io.packetSize())];
        while (io.elapsedRealtime() < deadline) {
            int count = io.read(chunk, READ_SLICE_MS);
            if (count <= 0) continue;
            if (reply.accept(chunk, count)) return reply.bytes();
        }
        throw new IOException(reply.timeoutMessage());
    }

    private void exchangeStartup(byte[] request, OpenPortStartupResponse reply,
            int timeoutMs, boolean cancellable) throws IOException {
        ensureOpen();
        if (cancellable) checkCancelled();
        write(request, CONTROL_TIMEOUT_MS);
        long deadline = io.elapsedRealtime() + timeoutMs;
        byte[] chunk = new byte[Math.max(64, io.packetSize())];
        while (io.elapsedRealtime() < deadline) {
            if (cancellable) checkCancelled();
            int count = io.read(chunk, READ_SLICE_MS);
            if (count > 0 && reply.accept(chunk, count)) return;
        }
        throw new IOException(reply.timeoutMessage());
    }

    private byte[] transceiveSsm(byte[] frame) throws IOException {
        ensureOpen();
        checkCancelled();
        if (!kLineOpen) {
            throw new IOException("The OpenPort K-line channel is not open.");
        }
        drainInput();
        checkCancelled();
        kLineDecoder.reset();
        write(OpenPortWireProtocol.transmitSsmKLineRequest(frame),
                CONTROL_TIMEOUT_MS);

        long deadline = io.elapsedRealtime()
                + VEHICLE_TIMEOUT_MS;
        int receivedBytes = 0;
        byte[] chunk = new byte[Math.max(64, io.packetSize())];
        while (io.elapsedRealtime() < deadline) {
            checkCancelled();
            int count = io.read(chunk, READ_SLICE_MS);
            if (count <= 0) continue;
            receivedBytes += count;
            if (receivedBytes > MAX_VEHICLE_RESPONSE_BYTES) {
                throw new IOException("OpenPort vehicle response is too large.");
            }
            // Binary ECU bytes can spell ASCII command markers. Decode packet
            // framing here; do not scan vehicle payloads as control responses.
            final List<byte[]> frames;
            try {
                frames = kLineDecoder.accept(
                        java.util.Arrays.copyOf(chunk, count));
            } catch (IllegalArgumentException ex) {
                throw new IOException("The OpenPort vehicle response was invalid.", ex);
            }
            if (!frames.isEmpty()) return frames.get(0);
        }
        throw new IOException("The ECU did not answer the read-only " + activeProtocol + " request.");
    }

    private void drainInput() {
        byte[] chunk = new byte[Math.max(64, io.packetSize())];
        for (int attempt = 0; attempt < 16; attempt++) {
            int count = io.read(chunk, DRAIN_SLICE_MS);
            if (count <= 0) return;
        }
    }

    private void checkCancelled() throws java.io.InterruptedIOException {
        if (cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("Logger stopped");
    }

    private void write(byte[] request, int timeoutMs) throws IOException {
        int written = io.write(request, timeoutMs);
        if (written != request.length) {
            throw new IOException("OpenPort USB write failed.");
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("The OpenPort USB session is closed.");
    }

    private static EndpointSet endpoints(UsbDevice device) throws IOException {
        for (int interfaceIndex = 0;
                interfaceIndex < device.getInterfaceCount(); interfaceIndex++) {
            UsbInterface usbInterface = device.getInterface(interfaceIndex);
            UsbEndpoint input = null;
            UsbEndpoint output = null;
            for (int endpointIndex = 0;
                    endpointIndex < usbInterface.getEndpointCount(); endpointIndex++) {
                UsbEndpoint endpoint = usbInterface.getEndpoint(endpointIndex);
                if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) input = endpoint;
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) output = endpoint;
            }
            if (input != null && output != null) {
                return new EndpointSet(usbInterface, input, output);
            }
        }
        throw new IOException("OpenPort bulk USB endpoints were not found.");
    }

    private static final class EndpointSet {
        private final UsbInterface usbInterface;
        private final UsbEndpoint input;
        private final UsbEndpoint output;

        private EndpointSet(UsbInterface usbInterface, UsbEndpoint input,
                UsbEndpoint output) {
            this.usbInterface = usbInterface;
            this.input = input;
            this.output = output;
        }
    }
}
