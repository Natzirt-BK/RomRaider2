/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.ui;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One private session-bus connection owns one screen-saver inhibition cookie. */
final class LinuxDisplayAwake {
    private static final String SERVICE = "org.freedesktop.ScreenSaver";
    private static final String PATH = "/org/freedesktop/ScreenSaver";
    private static final int TIMEOUT_MS = 1500;
    private static final ScheduledThreadPoolExecutor DEADLINES = deadlines();

    private LinuxDisplayAwake() { }

    static DesktopDisplayAwake.Lease acquire() throws DesktopDisplayAwake.AcquisitionFailure {
        String address = System.getenv("DBUS_SESSION_BUS_ADDRESS");
        if (address == null || address.isBlank())
            throw new UnsupportedOperationException("No desktop session bus is available");
        return new Connection(address).inhibit();
    }

    private static ScheduledThreadPoolExecutor deadlines() {
        ScheduledThreadPoolExecutor result = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "Gauge display bus deadlines");
            thread.setDaemon(true); return thread;
        });
        result.setRemoveOnCancelPolicy(true);
        return result;
    }

    public interface Gio extends Library {
        Pointer g_cancellable_new();
        void g_cancellable_cancel(Pointer cancellable);
        Pointer g_dbus_connection_new_for_address_sync(String address, int flags, Pointer observer,
                Pointer cancellable, PointerByReference error);
        void g_dbus_connection_set_exit_on_close(Pointer connection, int exitOnClose);
        int g_dbus_connection_is_closed(Pointer connection);
        int g_dbus_connection_close_sync(Pointer connection, Pointer cancellable, PointerByReference error);
        Pointer g_dbus_connection_call_sync(Pointer connection, String bus, String path, String api, String method,
                Pointer parameters, Pointer replyType, int flags, int timeout, Pointer cancellable, PointerByReference error);
    }
    public interface GLib extends Library {
        Pointer g_variant_new_string(String value);
        Pointer g_variant_new_uint32(int value);
        Pointer g_variant_new_tuple(Pointer[] values, NativeLong count);
        Pointer g_variant_type_new(String signature);
        void g_variant_type_free(Pointer type);
        Pointer g_variant_get_child_value(Pointer value, NativeLong index);
        Pointer g_variant_get_string(Pointer value, Pointer length);
        int g_variant_get_uint32(Pointer value);
        void g_variant_unref(Pointer value);
        void g_error_free(Pointer error);
    }
    public interface GObject extends Library { void g_object_unref(Pointer object); }

    private static final class Connection implements DesktopDisplayAwake.Lease {
        private final Gio gio = Native.load("gio-2.0", Gio.class);
        private final GLib glib = Native.load("glib-2.0", GLib.class);
        private final GObject objects = Native.load("gobject-2.0", GObject.class);
        private Pointer connection;
        private String owner;
        private Integer cookie;

        Connection(String address) {
            PointerByReference error = new PointerByReference();
            try (Deadline deadline = new Deadline()) {
                // AUTHENTICATION_CLIENT | MESSAGE_BUS_CONNECTION; never borrow GTK's
                // shared connection, launch a bus, or exit the JVM when a bus closes.
                connection = gio.g_dbus_connection_new_for_address_sync(address, 9, null, deadline.token, error);
            }
            if (connection == null) throw failure(error, "Cannot connect to the desktop session bus");
            gio.g_dbus_connection_set_exit_on_close(connection, 0);
        }

        Connection inhibit() throws DesktopDisplayAwake.AcquisitionFailure {
            try {
                owner = serviceOwner();
                Pointer result = call(owner, PATH, SERVICE, "Inhibit",
                        tuple(glib.g_variant_new_string("com.romraider.RomRaider2"),
                                glib.g_variant_new_string("Full-screen gauges")), "(u)");
                try {
                    Pointer child = glib.g_variant_get_child_value(result, new NativeLong(0));
                    try { cookie = glib.g_variant_get_uint32(child); }
                    finally { glib.g_variant_unref(child); }
                } finally { glib.g_variant_unref(result); }
                return this;
            } catch (RuntimeException | LinkageError error) {
                try { close(); } catch (RuntimeException | LinkageError cleanup) {
                    error.addSuppressed(cleanup);
                    throw new DesktopDisplayAwake.AcquisitionFailure(error, this);
                }
                throw error;
            }
        }

        private String serviceOwner() {
            Pointer result = call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                    "GetNameOwner", tuple(glib.g_variant_new_string(SERVICE)), "(s)");
            try {
                Pointer child = glib.g_variant_get_child_value(result, new NativeLong(0));
                try { return glib.g_variant_get_string(child, null).getString(0, "UTF-8"); }
                finally { glib.g_variant_unref(child); }
            } finally { glib.g_variant_unref(result); }
        }

        @Override public boolean isValid() {
            return connection != null && gio.g_dbus_connection_is_closed(connection) == 0 && owner.equals(serviceOwner());
        }

        private Pointer tuple(Pointer... values) {
            // GVariant sinks the floating children; call_sync consumes this floating tuple.
            return glib.g_variant_new_tuple(values, new NativeLong(values.length));
        }

        private Pointer call(String bus, String path, String api, String method, Pointer parameters, String signature) {
            Pointer type = glib.g_variant_type_new(signature);
            PointerByReference error = new PointerByReference();
            try (Deadline deadline = new Deadline()) {
                Pointer result = gio.g_dbus_connection_call_sync(connection, bus, path, api, method, parameters, type,
                        1, TIMEOUT_MS, deadline.token, error); // NO_AUTO_START
                if (result == null) throw failure(error, "Desktop screen-awake request failed (" + method + ")");
                return result;
            } finally { glib.g_variant_type_free(type); }
        }

        @Override public void close() {
            if (connection == null) return;
            if (gio.g_dbus_connection_is_closed(connection) == 0) {
                if (cookie != null) {
                    try {
                        Pointer result = call(owner, PATH, SERVICE, "UnInhibit", tuple(glib.g_variant_new_uint32(cookie)), "()");
                        glib.g_variant_unref(result);
                        cookie = null;
                    } catch (RuntimeException ignored) {
                        // Disconnecting the private bus sender also releases its cookie,
                        // including a late Inhibit reply or a restarted desktop service.
                    }
                }
                PointerByReference error = new PointerByReference();
                try (Deadline deadline = new Deadline()) {
                    if (gio.g_dbus_connection_close_sync(connection, deadline.token, error) == 0) {
                        IllegalStateException problem = failure(error, "Cannot close the display-awake bus connection");
                        if (gio.g_dbus_connection_is_closed(connection) == 0) throw problem;
                    }
                }
            }
            objects.g_object_unref(connection);
            connection = null;
        }

        private IllegalStateException failure(PointerByReference error, String message) {
            // Error strings come from the desktop service; no unbounded/untrusted
            // native message is copied into application UI or logs.
            if (error.getValue() != null) { glib.g_error_free(error.getValue()); error.setValue(null); }
            return new IllegalStateException(message);
        }

        /** Protects the handshake and all native calls, not just D-Bus method replies. */
        private final class Deadline implements AutoCloseable {
            private final Pointer token = gio.g_cancellable_new();
            private final ScheduledFuture<?> timer;
            private boolean done;
            Deadline() {
                timer = DEADLINES.schedule(() -> {
                    synchronized (this) { if (!done) gio.g_cancellable_cancel(token); }
                }, TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            @Override public synchronized void close() {
                done = true;
                timer.cancel(false);
                objects.g_object_unref(token);
            }
        }
    }
}
