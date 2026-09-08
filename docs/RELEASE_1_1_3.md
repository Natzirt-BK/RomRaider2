# RomRaider2 1.1.3 RC1

Release candidate for Android, Windows, Linux, SteamOS Desktop Mode and macOS.
This is not a stable release.

## Highlights

- 25 gauge styles with visual selectors and a separate style for each channel.
- Seamless fullscreen layouts for 1–6 gauges, independent of the logger's channel
  selection, with an option to copy that selection into the gauge setup.
- Android keeps the screen awake in fullscreen gauge mode. Tap to show the
  temporary controls and exit fullscreen without stopping logging.
- Improved handheld gauge controls, wrapping labels and desktop window restoration.
- Android background recording with a Stop notification and reviewed recovery of
  interrupted recordings. The original recording is preserved during export.
- Definition-backed calculated logger channels, portable logger setups and
  expanded desktop saved-log analysis.

## Downloads and installation

Download from [GitHub Releases](https://github.com/Natzirt-BK/RomRaider2/releases/tag/romraider2-1.1.3).

For Android 8.0 or newer, install **RomRaider2_1.1.3_Android-debug.apk**.
It uses the existing application ID and signing identity. Back up saved work
before updating; uninstalling or clearing app data removes private recordings.
The optional **Android-side-by-side-test.apk** is the separate OpenPort Test app.
Both APKs are sideloaded, debuggable builds; Android may show an installation warning.

Desktop ZIPs include Java 21. Extract the complete package before launching.
macOS packages are unsigned. SHA-256 checksums accompany all seven packages.

## Compatibility and limits

Android connected logging uses **Tactrix OpenPort 2.0 through USB host/OTG**.
OBDLink and VAG-COM/KKL adapters are not supported in this release.
Basic Forester SSM logging has been tested in car. Sustained/background phone
sessions, Evo MUT-II and broader desktop adapter compatibility still need
hardware qualification. Automated tests do not establish vehicle compatibility.

There is no production flashing or live tuning. Android does not repair ROM
checksums. Definitions are separate and must match the ROM ID.

Configure gauges and logging while parked. The app does not replace vehicle
instruments or warning systems. STI branding is unofficial.

## Build verification

All packages were built from source revision
`b7c81765778546595320c3585d5c68ebc3267259`.

- [Desktop builds and checks](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34079178981)
- [Android and platform packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34080440667)
- [Android emulator regression checks](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34080442427)

Android reports version **1.1.3**, versionCode **110407**. Release documentation
on the main branch supersedes development-status text bundled at the build revision.
