# Android Logger layout

Local September 9 changes build on 1.1.8; they are not a published update.

- The upper-left workspace menu is ordered Logger, Gauges, Review, Editor.
  The current workspace name remains visible beside it.
- Connection status comes first. Separate START and STOP buttons stay at the
  bottom of Logger, START above STOP. START is disabled during a session;
  STOP is disabled when idle and while cleanup completes. The scrolling content
  has its own space above these controls rather than sitting underneath them.
- Live readings appear directly below the session header during recording.
  Idle/stopped states do not present old readings as live measurements.
- Vehicle, Adapter, and Profile & channels are compact expandable sections.
  Only one is expanded at a time; starting a session collapses all three.
  Opening a section is display-only. Setup mutation controls remain disabled
  while the recording service owns the session.
- Adapter and import failures remain visible in the session header, even with
  setup collapsed. Unbound service state is labeled unknown, not connected or
  reconnecting. Preparing USB does not claim an ECU connection.
- Review has its own workspace, with recording exports, CSV import, the separate
  scrollable summary viewer and Close Log File. XML profile saving and the
  separate RR2 transfer format remain available under Profile & channels.

Gauges keep their separate workspace and fullscreen mode. This UI update changes no
vehicle protocol, DimeMod discovery sequence, ECU write authority, CSV format or
channel-support rules. No session starts automatically from expanding a section
or rotating the device. Custom action buttons have ripple, pressed-depth,
disabled-state and system-respecting haptic feedback.

After a nonempty recording finishes and adapter cleanup completes, the foreground
Activity prompts to save a CSV copy. Save CSV opens Android's filename/location
picker; Later or cancelling the picker keeps the internal recording. A bounded,
process-owned completed-log store tracks acknowledgement and the selected export
across Activity and stopped-Service recreation. It owns no transport or UI object.
Discovery-only sessions and empty failed starts do not prompt. Review retains Recover / Export Recordings for
process-death recovery; clearing app data or uninstalling still removes private files.

The `logger-layout` emulator phase covers initial collapsed state, single-section
expansion, visible USB/import errors, busy-state control locking, auto-collapse,
portrait/landscape reflow, the actual Stop button and gauge navigation without
session replacement. Screenshots use an instrumentation-only fake transport,
not a production offline logger or a vehicle connection.

The same local pass reuses each gauge's native drawing surface, vector path and
unchanged reading snapshot. Animated indicators still receive fresh positions;
measurement values and gauge artwork are unchanged. Rendering tests cover all
25 styles, missing/recovered data, cached-state updates and Canvas release.

See the [September 9 qualification checkpoint](ANDROID_WORKFLOW_CHECKPOINT_2026-09-09.md)
for automated coverage and remaining physical checks.
