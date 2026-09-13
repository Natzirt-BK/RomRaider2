# RomRaider2 1.1.10 RC1

## Changes

- Android Gauges: green START and red STOP controls, including fullscreen, and
  tighter mounted layouts with larger gauge faces.
- Windows/Linux logger: setup preserves fullscreen/maximized geometry;
  recording-switch dropdown, visible default and optional main-window log name.
- Desktop and Android CSV values use two decimal places. Live readings and
  calculations retain their original precision.
- Desktop serial-port discovery with Refresh/manual entry and diagnostic-log
  controls under Troubleshooting.
- Desktop DimeMod reconnects verify fresh ECU identification/discovery metadata.

## Downloads and compatibility

Desktop packages include Java 21. Android requires Android 8.0 or later; the main
APK uses the existing distribution certificate and internal build code 110415.
The optional OpenPort Test APK installs separately. macOS packages are unsigned.

Android adapter support remains OpenPort 2.0 USB. The desktop ELM/OBDLink test is
standard read-only OBD-II, not enhanced SSM/MUT-II support. Production flashing
and live tuning are not available.

This is a release candidate, not a stable release. Physical-phone, Steam Deck,
Windows/macOS interaction and in-car acceptance remain outstanding. Configure
logging and gauges while parked. See the [qualification record](RELEASE_1_1_10_QUALIFICATION.md).
