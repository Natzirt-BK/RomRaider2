# Android Bluetooth connection foundation

September 9, 2026. Local implementation; not available in Logger setup or a
published APK. Existing OpenPort logging is unchanged.

`BluetoothElmLink` adapts an explicitly supplied RFCOMM socket to the shared
`Elm327Session.Link` interface. `BoundedElmStreamLink` handles connection and
write deadlines, a bounded receive queue and cancellation through socket close.
It starts no connection until its owner calls `open` and sends no commands on
its own. A new connection needs a new link instance; there is no automatic retry.

A timed-out read returns no data without leaving a worker holding the caller's
buffer. Later bytes remain available to the session parser. Write/connection
timeouts terminate the link; input overflow fails rather than silently dropping
part of a reply. Close wakes pending reads and cancels pending connection/write
work. The endpoint contract requires close to abort platform I/O promptly.
EOF terminates the link and discards queued bytes rather than treating a reply
from a disconnected adapter as successful.

Twelve JVM tests cover read deadlines and late replies, connect/write deadlines,
concurrent-write rejection, cancellation, EOF, queue overflow, permission loss,
interruption, invalid state and integration with the shared Mode 01 session.
The integration fixture validates supported PIDs and a single RPM response;
it does not open Bluetooth or connect to a vehicle.

## Required before exposing Bluetooth

- Explicit paired-device selection and version-appropriate permission handling.
  No MAC-address guessing or automatic protocol probing.
- A separately labeled read-only generic OBD adapter check, followed by service
  ownership, background lifecycle and selected-device persistence integration.
- Permission denial/revocation, radio-off, cancellation and Activity/service
  recreation tests through the actual Android entry point.
- Exact adapter model/firmware and parked-vehicle qualification. Multiple ECU
  responders currently fail the shared check until explicit selection is added.

No Bluetooth permission or selectable adapter was added to the application by
this foundation. Bluetooth Classic is not BLE. A working host link or generic
Mode 01 response does not establish SSM, DimeMod, MUT-II, KKL USB or CAN support.
Enhanced protocols still need their own initialization and framing work.

## Platform references

Android documents [BluetoothSocket](https://developer.android.com/reference/android/bluetooth/BluetoothSocket)
as a blocking stream whose close operation aborts ongoing I/O. The bridge uses
that contract instead of reflection, guessed RFCOMM channels or polling
`InputStream.available()`.

The eventual device picker must follow Android's
[Bluetooth permission requirements](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).
Paired-device communication and discovery have distinct permissions; USB-only
use should not request Bluetooth access.
