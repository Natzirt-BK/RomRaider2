#!/usr/bin/env python3
"""Linux display-awake native ABI/lifetime checks on isolated D-Bus sessions.

Usage: python3 packaging/test-display-awake.py /path/to/java test-classpath
Requires dbus-run-session and the distribution's python3-gi. No real desktop
inhibition, logger runtime, adapter, preferences or vehicle is used.
"""
import os
import socket
import subprocess
import sys
import tempfile
import time


def command(java, classpath, mode):
    return [java, "-Djava.awt.headless=true", "-cp", classpath,
            "com.romraider.ui.LinuxDisplayAwakeProbe", mode]


def private_service(mode, java, classpath):
    import gi
    gi.require_version("Gio", "2.0")
    from gi.repository import Gio, GLib

    assert os.environ.get("RR2_PRIVATE_AWAKE_TEST") == "1"
    bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
    service = "org.freedesktop.ScreenSaver"
    signature = "s" if mode == "wrong-type" else "u"
    info = Gio.DBusNodeInfo.new_for_xml(f"""<node><interface name='{service}'>
        <method name='Inhibit'><arg type='s' direction='in'/><arg type='s' direction='in'/>
          <arg type='{signature}' direction='out'/></method>
        <method name='UnInhibit'><arg type='u' direction='in'/></method>
        </interface></node>""")
    held = set()
    events = []
    failures = []
    owner_id = None

    def method(connection, sender, path, interface, name, parameters, invocation):
        try:
            if name == "Inhibit":
                assert parameters.unpack() == ("com.romraider.RomRaider2", "Full-screen gauges")
                held.add(sender)
                events.append("inhibit")
                if mode == "timeout":
                    return  # Simulate a service that takes the lease but never replies.
                invocation.return_value(GLib.Variant(f"({signature})", ("wrong",) if signature == "s" else (0xFFFFFFFF,)))
                if mode == "service-loss":
                    def lose_name():
                        Gio.bus_unown_name(owner_id)
                        return GLib.SOURCE_REMOVE
                    GLib.timeout_add(300, lose_name)
            else:
                assert name == "UnInhibit"
                assert parameters.unpack() == (0xFFFFFFFF,)
                assert sender in held
                held.remove(sender)
                events.append("uninhibit")
                invocation.return_value(GLib.Variant("()", ()))
        except BaseException as error:
            failures.append(repr(error))
            invocation.return_dbus_error("com.romraider.TestFailure", str(error))

    def changed(connection, sender, path, interface, signal, parameters):
        name, old_owner, new_owner = parameters.unpack()
        if name.startswith(":") and old_owner and not new_owner:
            if name in held:
                held.remove(name)
                events.append("disconnect-release")

    bus.register_object("/org/freedesktop/ScreenSaver", info.interfaces[0], method, None, None)
    bus.signal_subscribe("org.freedesktop.DBus", "org.freedesktop.DBus", "NameOwnerChanged",
                         "/org/freedesktop/DBus", None, Gio.DBusSignalFlags.NONE, changed)
    child = None
    loop = GLib.MainLoop()
    finished_at = None
    deadline = time.monotonic() + 12

    def start(*unused):
        nonlocal child
        child = subprocess.Popen(command(java, classpath, mode))

    if mode == "missing":
        start()
    else:
        owner_id = Gio.bus_own_name_on_connection(bus, service, Gio.BusNameOwnerFlags.NONE, start, None)

    def poll():
        nonlocal finished_at
        if time.monotonic() > deadline:
            failures.append("Private bus fixture timed out")
            if child is not None:
                child.kill()
            loop.quit()
            return GLib.SOURCE_REMOVE
        if child is not None and child.poll() is not None:
            if finished_at is None:
                finished_at = time.monotonic()
            if time.monotonic() - finished_at > 0.3:
                loop.quit()
                return GLib.SOURCE_REMOVE
        return GLib.SOURCE_CONTINUE

    GLib.timeout_add(30, poll)
    loop.run()
    assert child is not None and child.wait(timeout=2) == 0, (mode, failures)
    assert not failures, failures
    assert not held, (mode, "Leaked inhibition", events)
    expected = [] if mode == "missing" else ["inhibit", "disconnect-release"] if mode in (
        "process-exit", "timeout", "wrong-type") else ["inhibit", "uninhibit"]
    assert events == expected, (mode, events)
    print(f"PASS: private service observed {mode}: {events}", flush=True)


def main():
    if sys.argv[1:2] == ["--private"]:
        private_service(*sys.argv[2:])
        return
    java, classpath = sys.argv[1:]
    test_env = dict(os.environ, RR2_PRIVATE_AWAKE_TEST="1", G_DEBUG="fatal-warnings")
    for mode in ("normal", "missing", "timeout", "wrong-type", "service-loss", "process-exit"):
        subprocess.run(["dbus-run-session", "--", sys.executable, __file__, "--private", mode, java, classpath],
                       env=test_env, check=True, timeout=15)
    # A valid Unix socket that never completes D-Bus authentication must also time out.
    with tempfile.TemporaryDirectory(prefix="rr2-awake-handshake-") as directory:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as server:
            endpoint = os.path.join(directory, "silent.sock")
            server.bind(endpoint)
            server.listen(1)
            test_env["DBUS_SESSION_BUS_ADDRESS"] = "unix:path=" + endpoint
            subprocess.run(command(java, classpath, "handshake"), env=test_env, check=True, timeout=8)
    print("PASS: all seven isolated Linux display-awake scenarios", flush=True)


if __name__ == "__main__":
    main()
