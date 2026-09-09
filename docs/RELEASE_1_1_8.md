# RomRaider2 1.1.8 RC1

Release candidate for Android, Windows, Linux, SteamOS Desktop Mode and macOS.
This is not a stable release. Available packages are listed on
[GitHub Releases](https://github.com/Natzirt-BK/RomRaider2/releases).

## Highlights

- Android Logger puts connection status and Start/Stop first, with live readings
  below and compact Vehicle, Adapter, and Profile & channels setup sections.
  Landscape places Stop beside the session information.
- Android SSM discovers DimeMod channels and filters selectable channels by the
  loaded definition and identified ECU. Unavailable saved selections are retained
  in the profile but excluded from polling and CSV output.
- Save Logger Profile exports standard XML. RR2-specific transfers remain under
  More Profile Options.
- CSV summaries open in a separate scrollable window. Close Log File unloads
  the review without deleting the recording. Log Review stays at the bottom.
- The Android offline logger is removed. Gauge Demo remains a separate visual
  option; it does not record vehicle data.
- Desktop adds a separate read-only ELM327/OBDLink serial adapter test for
  supported standard OBD-II RPM, coolant and speed channels. It is not the full
  enhanced logger; hardware qualification is pending.
- Desktop fullscreen gauges restore window position more reliably when native
  window-manager updates arrive late.

The 25 gauge styles, independent 1–6-gauge layouts, Android background recording
and existing editor/log-analysis features remain available.

## Installation

Use **RomRaider2_1.1.8_Android-debug.apk** for the main Android app. The optional
**Android-side-by-side-test.apk** is the separate OpenPort Test app. Both are
sideloaded, debuggable builds. The main APK retains its application ID and
distribution signing key. Back up important recordings before updating;
uninstalling or clearing app data removes private recordings and saved setup.

Desktop ZIPs include Java 21. Extract the complete package before launching.
macOS packages are unsigned. Each package has a SHA-256 checksum sidecar.

## Compatibility and limits

Android vehicle logging requires **Tactrix OpenPort 2.0 through USB host/OTG**.
SSM setup performs the DimeMod negotiation handshake before read-only polling.
The new channel filtering and layout need further physical-device qualification.
Basic Subaru SSM and Evo MUT-II logging have owner-reported in-car results;
these do not validate every channel, controller or sustained/background session.

Android ELM support, enhanced SSM/MUT-II over ELM, bundled TCU editing and
expanded module diagnostics are not included. Production flashing and live
tuning remain unavailable. Android does not repair ROM checksums. Definitions
remain separate and must match the ROM ID exactly. Configure logging and gauges
while parked.

## Verification

Built from `81c62ce674c7ace57488d7e778df37a9adbb3999`. Desktop/platform builds and
Android regression checks passed. Downloaded packages were checked for integrity,
versions and signing; Linux and SteamOS launch/close checks passed.
See the [qualification record](https://github.com/Natzirt-BK/RomRaider2/blob/master/docs/RELEASE_1_1_8_QUALIFICATION.md)
for checksums, test scope and remaining hardware work.
