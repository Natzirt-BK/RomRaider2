# Android workflow checkpoint

September 9, 2026. Local changes on top of 1.1.8 RC1; not a published update.
The next distributed update needs a new numeric patch version, not replacement
1.1.8 binaries. No installed user application, vehicle definition or release was
replaced during this pass.

## Implemented

- Upper-left workspace menu: Logger, Gauges, Review, Editor.
- Separate bottom START and STOP controls with session-dependent availability.
- Ripple, pressed depth, disabled appearance and system-respecting haptic feedback.
- More compact landscape chrome, with status and recording controls visible.
- Dedicated Review workspace with CSV import, scrollable summary, Close Log File,
  last-recording export and retained-recording recovery.
- Save prompt after completed nonempty recordings and transport cleanup.
  Later/cancel retains the recording. The selected export survives Activity and
  stopped-Service recreation without substituting a newer recording.
- Reused gauge drawing objects and unchanged reading snapshots, with unavailable
  and recovered values still updating both visuals and accessibility state.
- Isolated [Bluetooth stream foundation](ANDROID_BLUETOOTH_LINK.md), not yet an
  adapter selection or a production logging path.

## Automated evidence

The automation APK, instrumentation APK, JVM tests and Android lint build pass.
All 137 Android JVM tests pass, including three completed-log ownership tests and
twelve new bounded-stream tests. Lint reports no errors; the two remaining
warnings concern the newer Android SDK/target, not ignored runtime failures.
SDK migration needs a separate compatibility pass.

Emulator coverage during this work includes:

- Setup seed/restore, cleared selection, corrupt saved setup and recovery.
- Logger portrait/landscape layout, real START/STOP control state, visible errors,
  section ordering, pressed/released appearance and disabled feedback.
- Save prompt acknowledgement, retained completed log and exact export identity
  across Activity and stopped-Service recreation.
- CSV import, retained previous results on failure, scrollable review, close,
  cancellation, recreation and isolation from an active recording.
- Gauge setup, demo show/hide, all 25 seamless styles, cached rendering, 1–6
  mounted layouts, fullscreen exit and live-session continuity.
- Calculated channels, channel transfer and ECU-specific channel selection.
- Recording recovery, background service, denied notification permission and
  deliberate process death. The service remained stopped after process death;
  recovery succeeded without restarting a vehicle connection.

Affected checks were repeated after subsequent changes. Tests ran only in the
isolated automation package on a disposable emulator with synthetic transports.
They did not open an adapter or send a vehicle request. Same-key distribution
upgrade testing was not repeated in this pass.

## Remaining acceptance

- Physical-phone touch/haptic feel and layout at the user's font/display scaling.
- Actual Android document-provider save/cancel and reopening the resulting CSV.
- Final signed, newly versioned package and cross-platform release qualification.
- Bluetooth device selection, permissions, service integration and actual adapter
  qualification. Generic OBD responses do not establish enhanced SSM/DimeMod or
  MUT-II support. KKL USB remains separate work.

The existing in-car recording evidence does not need to be repeated merely to
prove that basic recording starts. Further vehicle tests should target specific
remaining changes or adapter/protocol behavior.
