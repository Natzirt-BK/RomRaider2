# RomRaider2 1.1.2 RC1

Release candidate for desktop and Android. Not a stable release.

## Highlights

- Eleven added gauge styles, including STI Night and Evolution Night.
- Gauges-only views on Android, JavaFX and Compose; switching views preserves
  logging. In this build, Android logging stops when the app leaves the foreground.
- A compact JavaFX editor with centered cells, **Save Options**, reduced header
  space and collapsed calibration categories.
- Selection multiplication, configurable adjustment steps and protected ROM reload.
- Initial desktop MAF/injector saved-log analysis.
- Fixes for Android saves, gauge readings, XML imports and calibration-table refresh.

## Installation

Desktop ZIPs include Java 21. Extract the package before launching.
Windows x64, Linux x64, SteamOS Desktop Mode, macOS Intel and Apple silicon
packages are available; macOS packages are unsigned.

Android requires version 8.0 or newer. Install the standard APK manually; the
optional **OpenPort Test** APK installs separately. Back up recordings and saved
work before replacing an installation.

Each package includes a SHA-256 checksum. Build details and verification are in
the [technical audit](https://github.com/Natzirt-BK/RomRaider2/blob/master/docs/GAUGE_EDITOR_AUDIT_2026-09-06.md).

## Known limits

- Basic Forester SSM logging has in-car confirmation. Sustained Android logging,
  EVO MUT-II and other platform/adapter combinations still need hardware testing.
- Android calculated channels, dynamic DimeMod channels and external serial
  sensors are not included in this build.
- Production flashing and live tuning are unavailable. Android does not repair
  ROM checksums. Use exact matching definitions and preserve original ROMs.
- Newer 1.1.3 source features, including seamless fullscreen layouts and Android
  background recording, are not included in these downloads.

Configure gauges while parked. They do not replace vehicle instruments or warnings.
STI branding is unofficial and does not imply manufacturer endorsement.
