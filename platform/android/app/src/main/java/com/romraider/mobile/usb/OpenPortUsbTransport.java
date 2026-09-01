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
import com.romraider.portable.logger.PortableLoggerDataSource;
import com.romraider.portable.logger.PortableLoggerQueryBatch;
import com.romraider.portable.logger.ReadOnlySsmProtocol;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/** Owns one Android USB host session with an OpenPort 2.0 adapter. */
public final class OpenPortUsbTransport implements Closeable,
        PortableLoggerDataSource {
    private static final int CONTROL_TIMEOUT_MS = 2000;
    private static final int VEHICLE_TIMEOUT_MS = 2500;
    private static final int READ_SLICE_MS = 250;
    private static final int DRAIN_SLICE_MS = 20;
    private static final int MAX_VEHICLE_RESPONSE_BYTES = 16 * 1024;

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final UsbInterface usbInterface;
    private final UsbEndpoint input;
    private final UsbEndpoint output;
    private final String firmwareVersion;
    private final Integer batteryMillivolts;
    private final OpenPortKLineFrameDecoder kLineDecoder =
            new OpenPortKLineFrameDecoder();
    private boolean kLineOpen;
    private boolean closed;

    private OpenPortUsbTransport(UsbDevice device,
            UsbDeviceConnection connection, UsbInterface usbInterface,
            UsbEndpoint input, UsbEndpoint output, String firmwareVersion,
            Integer batteryMillivolts) {
        this.device = device;
        this.connection = connection;
        this.usbInterface = usbInterface;
        this.input = input;
        this.output = output;
        this.firmwareVersion = firmwareVersion;
        this.batteryMillivolts = batteryMillivolts;
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
                    OpenPortWireProtocol.identifyRequest(), "ari ");
            String firmware = OpenPortWireProtocol.parseFirmwareVersion(
                    identify, identify.length);
            opening.exchange(OpenPortWireProtocol.openRequest(), "aro\r\n");
            Integer voltage = null;
            try {
                byte[] battery = opening.exchange(
                        OpenPortWireProtocol.batteryVoltageRequest(), "arr ");
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
                connection.releaseInterface(endpoints.usbInterface);
                connection.close();
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
        return candidate != null
                && device.getDeviceId() == candidate.getDeviceId();
    }

    /** Opens only the 4800-baud, read-only SSM K-line transport. */
    public synchronized void openReadOnlyKLine() throws IOException {
        ensureOpen();
        if (kLineOpen) return;
        exchange(OpenPortWireProtocol.openSsmKLineRequest(), "aro\r\n");
        kLineDecoder.reset();
        kLineOpen = true;
    }

    /** Identifies the connected Subaru ECU without changing ECU memory. */
    public synchronized String identifyEcu() throws IOException {
        openReadOnlyKLine();
        byte[] response = transceiveSsm(ReadOnlySsmProtocol.ecuInitRequest());
        try {
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
        openReadOnlyKLine();
        int expected = batch.getAddresses().length;
        byte[] response = transceiveSsm(batch.request());
        try {
            return ReadOnlySsmProtocol.readAddressValues(response, expected);
        } catch (IllegalArgumentException ex) {
            throw new IOException("The ECU logger response was invalid.", ex);
        }
    }

    public synchronized void closeReadOnlyKLine() {
        if (closed || !kLineOpen) return;
        try {
            exchange(OpenPortWireProtocol.closeSsmKLineRequest(), "aro\r\n");
        } catch (IOException ignored) {
            // A timeout or detach is a normal channel-close path.
        } finally {
            kLineOpen = false;
            kLineDecoder.reset();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        try {
            closeReadOnlyKLine();
            exchange(OpenPortWireProtocol.closeRequest(), "aro\r\n");
        } catch (IOException ignored) {
            // Detach and timeout are normal close paths.
        } finally {
            closed = true;
            connection.releaseInterface(usbInterface);
            connection.close();
        }
    }

    private synchronized byte[] exchange(byte[] request, String expected)
            throws IOException {
        ensureOpen();
        write(request, CONTROL_TIMEOUT_MS);

        long deadline = android.os.SystemClock.elapsedRealtime()
                + CONTROL_TIMEOUT_MS;
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] chunk = new byte[Math.max(64, input.getMaxPacketSize())];
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            int count = connection.bulkTransfer(input, chunk, chunk.length,
                    READ_SLICE_MS);
            if (count <= 0) continue;
            if (received.size() + count
                    > OpenPortWireProtocol.MAX_CONTROL_RESPONSE_BYTES) {
                throw new IOException("OpenPort control response is too large.");
            }
            received.write(chunk, 0, count);
            byte[] response = received.toByteArray();
            if (OpenPortWireProtocol.contains(response, response.length, "are ")) {
                throw new IOException("The OpenPort reported a command error.");
            }
            if (OpenPortWireProtocol.contains(response, response.length,
                    expected)) return response;
        }
        throw new IOException("OpenPort did not answer the adapter command.");
    }

    private byte[] transceiveSsm(byte[] frame) throws IOException {
        ensureOpen();
        if (!kLineOpen) {
            throw new IOException("The OpenPort SSM channel is not open.");
        }
        drainInput();
        kLineDecoder.reset();
        write(OpenPortWireProtocol.transmitSsmKLineRequest(frame),
                CONTROL_TIMEOUT_MS);

        long deadline = android.os.SystemClock.elapsedRealtime()
                + VEHICLE_TIMEOUT_MS;
        int receivedBytes = 0;
        ByteArrayOutputStream responseWindow = new ByteArrayOutputStream();
        byte[] chunk = new byte[Math.max(64, input.getMaxPacketSize())];
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            int count = connection.bulkTransfer(input, chunk, chunk.length,
                    READ_SLICE_MS);
            if (count <= 0) continue;
            receivedBytes += count;
            if (receivedBytes > MAX_VEHICLE_RESPONSE_BYTES) {
                throw new IOException("OpenPort vehicle response is too large.");
            }
            responseWindow.write(chunk, 0, count);
            byte[] raw = responseWindow.toByteArray();
            if (OpenPortWireProtocol.contains(raw, raw.length, "are ")) {
                throw new IOException("The OpenPort rejected the SSM request.");
            }
            final List<byte[]> frames;
            try {
                frames = kLineDecoder.accept(
                        java.util.Arrays.copyOf(chunk, count));
            } catch (IllegalArgumentException ex) {
                throw new IOException("The OpenPort vehicle response was invalid.", ex);
            }
            if (!frames.isEmpty()) return frames.get(0);
        }
        throw new IOException("The ECU did not answer the read-only SSM request.");
    }

    private void drainInput() {
        byte[] chunk = new byte[Math.max(64, input.getMaxPacketSize())];
        for (int attempt = 0; attempt < 16; attempt++) {
            int count = connection.bulkTransfer(input, chunk, chunk.length,
                    DRAIN_SLICE_MS);
            if (count <= 0) return;
        }
    }

    private void write(byte[] request, int timeoutMs) throws IOException {
        int written = connection.bulkTransfer(output, request, request.length,
                timeoutMs);
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
