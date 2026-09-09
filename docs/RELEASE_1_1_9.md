# RomRaider2 1.1.9 RC1

Release candidate for Android, Windows, Linux, SteamOS Desktop Mode and macOS.

## Android

- Workspace menu for Logger, Gauges, Review and Editor.
- Separate bottom START and STOP buttons, clearer pressed/disabled feedback,
  and a more compact landscape layout.
- Save prompt after stopping a recording. Later or cancelling keeps the internal
  recording; exports retain the selected session across screen recreation.
- Dedicated Review screen with CSV import, scrollable summaries, Close Log File
  and recording recovery.
- Gauge drawing reuses unchanged data and drawing objects without changing the
  25 styles or recorded values.

## Desktop

- Windows/Linux/SteamOS Logger setup has linked protocol, transport and target
  selectors, a Load Profile button and clearer channel controls.
- Recording options include a filename prefix, elapsed time, supported Fast
  Polling, vehicle-switch recording and CSV timestamp/number-format preferences.
- Fullscreen gauges add Start/Stop recording and elapsed time. Dashboard tile
  customization and gauge-channel assignment are reorganized.
- Fixed repeated definition prompts, profile backup handling and definition
  installation rollback. Diagnostic reads run in the background and reject
  stale requests before returning results.

## Installation

Use **RomRaider2_1.1.9_Android-debug.apk** for Android. The optional
**Android-side-by-side-test.apk** installs the separate OpenPort Test app.
Both are sideloaded, debuggable builds. Back up important recordings before
updating; uninstalling or clearing app data removes private recordings and setup.

Desktop ZIPs include Java 21. Extract the full package before launching.
macOS packages are unsigned. SHA-256 checksum files accompany the downloads.

## Compatibility

Android vehicle logging still requires **Tactrix OpenPort 2.0 through USB host/OTG**.
SSM setup includes DimeMod negotiation; channel polling is read-only.
This update does not add Android Bluetooth or KKL USB adapter support.
The desktop ELM/OBDLink adapter test remains a limited standard-OBD test.

Production flashing, live tuning and bundled TCU editing are unavailable.
Android does not repair ROM checksums. Definitions remain separate and must
match the ROM. Configure logging and gauges while parked.

## Verification

Package qualification is in progress. See the
[qualification record](https://github.com/Natzirt-BK/RomRaider2/blob/master/docs/RELEASE_1_1_9_QUALIFICATION.md)
for completed checks and remaining physical-device acceptance.
