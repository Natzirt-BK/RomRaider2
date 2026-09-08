#!/usr/bin/env python3
"""Exercise the actual serial library against an owned PTY; no device discovery.

Usage: python3 packaging/test-elm-serial-pty.py JAVA CLASSPATH
CLASSPATH includes compiled test/main/shared classes and jSerialComm 2.11.4.
"""
import os
import pty
import select
import subprocess
import sys
import time
import tty


def main():
    if len(sys.argv) != 3 or not sys.platform.startswith("linux"):
        raise SystemExit("Usage (Linux): test-elm-serial-pty.py JAVA CLASSPATH")
    master, slave = pty.openpty()
    tty.setraw(slave)
    child = None
    commands = []
    attempts = 0
    pending = bytearray()
    try:
        child = subprocess.Popen([sys.argv[1], "-cp", sys.argv[2],
                                  "com.romraider2.javafx.ElmSerialNativeProbe", os.ttyname(slave)])
        deadline = time.monotonic() + 30
        while child.poll() is None:
            if time.monotonic() >= deadline:
                raise RuntimeError("Native PTY test exceeded 30 seconds")
            readable, _, _ = select.select([master], [], [], .05)
            if not readable:
                continue
            pending.extend(os.read(master, 4096))
            if len(pending) > 4096:
                raise AssertionError("Unbounded command input")
            while b"\r" in pending:
                command, _, rest = pending.partition(b"\r")
                pending = bytearray(rest)
                text = command.decode("ascii")
                commands.append(text)
                if text == "AT WS":
                    attempts += 1
                    reply = b"AT WS\rELM327 v2.3\r>\r\n"
                elif text in {"AT E0", "AT L0", "AT H0", "AT CAF1", "AT TP 0", "AT TP 3"}:
                    reply = command + b"\rOK\r>"
                elif text == "0100":
                    progress = b"BUS INIT: ...OK\r" if attempts % 2 == 0 else b"SEARCHING...\r"
                    reply = progress + b"41 00 08 18 00 00\r>"
                elif text == "010C":
                    reply = (b"41 0D 01\r>" if attempts == 3 else
                             b"41 0C 1A" if attempts == 4 else
                             b"" if attempts == 5 else b"41 0C 1A F8\r>\r\n")
                else:
                    raise AssertionError("Unexpected command: " + repr(text))
                for offset in range(0, len(reply), 3):
                    os.write(master, reply[offset:offset + 3])
                    time.sleep(.001)
        if child.wait() != 0:
            raise RuntimeError("Native PTY probe failed")
        if attempts != 6 or len(commands) != 48 or pending:
            raise AssertionError("Incomplete native test sequence")
        print("Owned PTY command allowlist passed: 6 sessions, 48 commands, no hardware ports")
    finally:
        if child is not None and child.poll() is None:
            child.terminate()
            try:
                child.wait(timeout=3)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait(timeout=3)
        os.close(master)
        os.close(slave)


if __name__ == "__main__":
    main()
