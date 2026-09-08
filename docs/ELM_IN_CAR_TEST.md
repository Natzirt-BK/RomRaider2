# Read-only adapter qualification

Development version **1.1.6**. The published 1.1.5 release does not have this
test window. Offline tests are not evidence of vehicle compatibility.

## Scope

Desktop Logger → **Read-only adapter test…** is a separate, short standard
OBD-II recorder for an explicitly selected ELM327-compatible serial adapter.
OBDLink devices exposing a compatible serial interface are candidates, not
yet qualified models. No logger definition is required by this test window.
If first-run Logger Setup appears, close it without starting the normal Logger.

This is **not** the connection mode for a Tactrix OpenPort 2.0 or a pass-through
KKL cable. Use the existing Logger for OpenPort testing. It also does not add
Android ELM, BLE, Wi-Fi, enhanced Subaru SSM, Mitsubishi MUT-II, flashing,
live tuning, actuator commands or code clearing.

Only adapter setup and Mode 01 supported-PID discovery/read requests are sent:

| Channel | Standard PID | Conversion | CSV units |
| --- | --- | --- | --- |
| Engine Speed | 0C | (256 × A + B) / 4 | rpm |
| Coolant Temperature | 05 | A − 40 | C |
| Vehicle Speed | 0D | A | km/h |

Unsupported channels are neither polled nor added to the CSV. A synchronized
NO DATA or negative response creates a blank value, never zero or a repeated
reading. Five consecutive all-missing cycles stop the test. Malformed,
ambiguous, timed-out or out-of-sync responses retire the session; it does not
automatically reconnect. A new test creates a new connection.

## Parked test

1. Park safely, apply the parking brake and close other software using the
   adapter. Do not operate the computer while driving. For engine-idle checks,
   use an outdoor, well-ventilated location.
2. Record the adapter's exact model, firmware, host connection type, configured
   serial baud rate and vehicle year/model. Turn ignition **ON**, not ACC.
   Do not assume an OpenPort port name identifies an ELM-compatible device.
3. Disconnect the normal RR2 Logger and stop connection attempts. Open
   **Logger → Read-only adapter test…**. Select the adapter's explicit OS
   serial port, its configured host baud, and Automatic or the known standard
   OBD-II protocol. The default 38,400 baud is a starting selection, not a
   detected value. There is no baud probing or port scanning.
4. Choose a **new** CSV filename, 30 or 60 seconds and the adapter confirmation
   checkbox. Start the read-only test. Existing files and symbolic-link paths
   are refused before the adapter is opened.
5. First test with ignition ON/engine OFF. Check that the vehicle's speed and
   engine speed read zero if those channels are supported. A zero is valid
   only when an actual matching ECU reply contains it.
6. Stop, then use a new filename for an idle test. Compare engine speed with
   the cluster and coolant temperature with a trusted independent reading,
   where available. Do not infer sensor accuracy from plausible numbers alone.
7. Test **Stop**, restarting with a new filename, closing the window during
   connection, and disconnecting the USB adapter while recording. Each test
   must stop without freezing the UI or leaving a second polling worker.
8. Open completed CSVs in RR2's **File → Open CSV log…**. Check headers, units,
   increasing timestamps and missing values. Retain the CSV plus the exact
   final status, adapter details and vehicle details for review. If it fails,
   capture the error rather than repeatedly changing protocol or baud blindly.

## Acceptance gate and limits

- A compatibility result requires real ECU replies, reasonable comparisons,
  saved-file review and stop/disconnect/restart checks on the exact hardware.
  A completed recording alone is not a declaration of model-wide support.
- The session requires successful acknowledgements for warm reset, echo off,
  linefeeds off, headers off, CAN auto-formatting and protocol selection before
  supported-PID discovery. Identity banners do not prove those capabilities.
- Headers are hidden in this first slice. Multiple ECU replies are rejected,
  even if identical. Explicit module selection is a follow-on task; do not
  discard one response arbitrarily to make a test pass.
- Initialization has a 30-second aggregate command deadline. Individual reads
  have up to 1.5 seconds, shortened near the recording limit. OS/USB-driver
  port opening is outside the command deadline and may take longer; it runs
  off the UI thread. A cancelled late-opened session is closed before any
  adapter command. A wedged native driver may require unplugging the device.
- Serial I/O uses nonblocking driver calls, bounded polling and short cancel
  checks. The default exclusive serial lock remains enabled. If cleanup reports
  an error, unplug the adapter before retrying; do not assume the port is free.
- CSV uses `Time (msec),Engine Speed (rpm),…`, decimal points independent of
  OS locale, and CRLF rows. Each complete row is flushed. Samples are sequential
  PID reads, not simultaneous measurements; the timestamp is the end of the
  completed sample. A failed initialization may leave an empty new file.
  Buffered-file flush is not a guarantee against power-loss data loss.
- Adapter settings are warmed/reset for this test and are not restored to a
  preceding application's state. Host baud is preserved. No EEPROM or vehicle
  calibration write command is issued; adapter protocol-selection behavior
  follows its own firmware.

Wire behavior follows the manufacturer's
[ELM327 datasheet](https://www.elmelectronics.com/wp-content/uploads/2017/01/ELM327DS.pdf).
Serial calls use the pinned
[jSerialComm 2.11.4 API](https://fazecast.github.io/jSerialComm/javadoc/com/fazecast/jSerialComm/SerialPort.html).
The legacy ELM logger is not used by this test mode.

## Repeatable offline serial check (Linux)

After compiling the desktop test classes, run:

```sh
python3 packaging/test-elm-serial-pty.py "$JAVA_HOME/bin/java" \
  'ui/javafx-desktop/build/classes/java/test:ui/javafx-desktop/build/classes/java/main:platform/shared-core/build/classes/java/main:lib/common/jSerialComm-2.11.4.jar'
```

The wrapper creates and owns a pseudo-terminal. The native probe only accepts
`/dev/pts/<number>`, not a hardware serial path. Six sessions exercise actual
jSerialComm reads/writes, fragmented replies, close/reopen, malformed data,
timeout, cancellation and a fresh connection after failure. A command allowlist
and a 30-second process limit bound the fixture. It does not emulate the vehicle
bus, voltage, firmware timing or adapter electrical behavior.
