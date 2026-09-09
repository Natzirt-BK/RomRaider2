# Android session-first Logger layout

Version 1.1.8 reorganizes Logger around the current session:

- Connection status and Start/Stop come first. The button changes to a red Stop
  control while a session is active and disables while cleanup completes.
- Live readings appear directly below the session header during recording.
  Idle/stopped states do not present old readings as live measurements.
- Vehicle, Adapter, and Profile & channels are compact expandable sections.
  Only one is expanded at a time; starting a session collapses all three.
  Opening a section is display-only. Setup mutation controls remain disabled
  while the recording service owns the session.
- Adapter and import failures remain visible in the session header, even with
  setup collapsed. Unbound service state is labeled unknown, not connected or
  reconnecting. Preparing USB does not claim an ECU connection.
- Wide screens place the session information and Start/Stop side by side.
  Portrait screens stack them. Reflow reuses views without restarting capture.
- Log Review stays last, with recording exports, CSV import, the separate
  scrollable summary viewer and Close Log File. XML profile saving and the
  separate RR2 transfer format remain available under Profile & channels.

Gauges keep their separate tab and fullscreen mode. This UI update changes no
vehicle protocol, DimeMod discovery sequence, ECU write authority, CSV format or
channel-support rules. No session starts automatically from expanding a section
or rotating the device. The Logger page remains scrollable; its Start/Stop
control is near the top rather than pinned over other content.

The `logger-layout` emulator phase covers initial collapsed state, single-section
expansion, visible USB/import errors, busy-state control locking, auto-collapse,
portrait/landscape reflow, the actual Stop button and gauge navigation without
session replacement. Screenshots use an instrumentation-only fake transport,
not a production offline logger or a vehicle connection.
