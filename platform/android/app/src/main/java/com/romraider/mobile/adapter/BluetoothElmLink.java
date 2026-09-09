/* RomRaider2 - GPL 2.0 or later. */
package com.romraider.mobile.adapter;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/** Host link only: no device search, automatic pairing, ECU protocol selection or commands. */
public final class BluetoothElmLink {
    private BluetoothElmLink() { }

    /**
     * Takes ownership of an explicitly selected RFCOMM socket. The caller must obtain
     * the applicable Bluetooth permission before creating/opening it. No permission
     * is requested by this foundation, which is not yet exposed in Logger setup.
     */
    @SuppressLint("MissingPermission") // Future UI owner must gate creation; revocation becomes a failed open.
    public static BoundedElmStreamLink forSocket(BluetoothSocket socket) {
        Objects.requireNonNull(socket);
        if (socket.getConnectionType() != BluetoothSocket.TYPE_RFCOMM) {
            throw new IllegalArgumentException("ELM Bluetooth link requires an RFCOMM socket");
        }
        return new BoundedElmStreamLink(new BoundedElmStreamLink.Endpoint() {
            @Override public void connect() throws IOException { socket.connect(); }
            @Override public InputStream input() throws IOException { return socket.getInputStream(); }
            @Override public OutputStream output() throws IOException { return socket.getOutputStream(); }
            @Override public void close() throws IOException { socket.close(); }
        });
    }
}
