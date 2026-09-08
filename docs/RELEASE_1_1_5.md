# RomRaider2 1.1.5 RC1

Release candidate for Android, Windows, Linux, SteamOS Desktop Mode and macOS.
This is not a stable release.

## Highlights

- Desktop Logger channels adapt to the available width, with inline units and
  adjustable control sizing. Target Module uses definition-backed choices.
- Logger setup can stop pending connection attempts and change startup
  preferences without requiring an established vehicle connection.
- Dashboard opens the dedicated gauge display without a duplicate desktop tab.
  Compact analysis layouts leave more room for charts and results.
- Live graphs share a timestamp axis and show channel ranges, units and gaps.
  Dyno calculations check RPM/speed timing, units and continuous pull segments.
- More reliable CSV recording, including unique capture filenames, clean
  stop/restart behavior and visible handling of writer failures.
- Android MUT-II startup improvements and explicit unit labels for imported
  logger channels. Basic Evo logging has been reported working in car.

The existing 25 gauge styles, independent 1–6-gauge layouts and Android
background recording remain available.

## Installation

Use **RomRaider2_1.1.5_Android-debug.apk** for the main Android app. The optional
**Android-side-by-side-test.apk** is the separate OpenPort Test app. Both are
sideloaded, debuggable builds. The main APK retains its application ID and
distribution signing identity. Back up saved work before updating; uninstalling
or clearing app data removes private recordings.

Desktop ZIPs include Java 21. Extract the complete package before launching.
macOS packages are unsigned. Each package has a SHA-256 checksum sidecar.

## Compatibility and limits

Android connected logging still requires **Tactrix OpenPort 2.0 through USB
host/OTG**. Broader adapter support, built-in TCU editing and expanded diagnostics
are planned, not included in this release.

Basic Subaru SSM and Evo MUT-II logging have owner-reported in-car results.
Those results do not validate every channel, controller or adapter. Sustained
background sessions, reconnect behavior and physical platform acceptance need
further testing. Configure logging and gauges while parked.

Production flashing and live tuning are unavailable. Android does not repair
ROM checksums. Definitions remain separate and must match the ROM ID exactly.
This release does not bundle the unfinished Evo calibration definitions or
definition-installer changes.

## Verification status

Candidate preparation is in progress. Hosted build, signing and package results
must be recorded before these release notes are used for publication.
