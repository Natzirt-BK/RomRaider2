# RomRaider2 ECU Studio

[![Build](https://github.com/Natzirt-BK/RomRaider2/actions/workflows/build.yaml/badge.svg)](https://github.com/Natzirt-BK/RomRaider2/actions/workflows/build.yaml)
[![Latest release](https://img.shields.io/github/v/release/Natzirt-BK/RomRaider2?include_prereleases)](https://github.com/Natzirt-BK/RomRaider2/releases)
[![License](https://img.shields.io/badge/license-GPL--2.0%2B-blue)](license.txt)

RomRaider2 is a desktop ECU editor, logger, diagnostics, and log-analysis
application for Subaru and Mitsubishi Lancer Evolution. It carries the
useful RomRaider and DimeMod work forward on Java 21 with a cleaner interface,
portable Windows and Linux packages, and stricter separation between normal
logging and unfinished ECU-write research.

## Downloads

The current release is **RomRaider2 1.1.1** for desktop and Android. Version
numbers use patch increments for routine updates, minor increments for milestones,
and rare major increments for substantial changes. Only the latest release is
kept on the Releases page; older source tags remain in Git history. See the
[versioning policy](docs/RELEASE_VERSIONING.md). Hardware qualification remains
platform-specific; a version number is not a claim of completed vehicle testing.

| Platform | Package |
| --- | --- |
| Windows 10/11 x64 | [Download the portable Windows ZIP](https://github.com/Natzirt-BK/RomRaider2/releases/download/romraider2-1.1.1/RomRaider2_ECU_Studio_1.1.1_Windows_x64.zip) |
| Linux x64 | [Download the portable Linux ZIP](https://github.com/Natzirt-BK/RomRaider2/releases/download/romraider2-1.1.1/RomRaider2_ECU_Studio_1.1.1_Linux_x64.zip) |
| SteamOS Desktop Mode x64 | [Download the SteamOS ZIP](https://github.com/Natzirt-BK/RomRaider2/releases/download/romraider2-1.1.1/RomRaider2_SteamOS_1.1.1_x64.zip) |
| macOS Apple silicon | [Download the unsigned ARM64 package](https://github.com/Natzirt-BK/RomRaider2/releases/download/romraider2-1.1.1/RomRaider2_1.1.1_macOS_arm64.zip) |
| macOS Intel | [Download the unsigned x64 package](https://github.com/Natzirt-BK/RomRaider2/releases/download/romraider2-1.1.1/RomRaider2_1.1.1_macOS_x64.zip) |

Java 21 is included in every desktop package. Extract the ZIP before running
it. On Windows, open `RomRaider2.exe`. On Linux, open `bin/RomRaider2`. The Mac
packages are unsigned and have not completed physical Mac testing.

[Release notes, checksums, and all downloads](https://github.com/Natzirt-BK/RomRaider2/releases/tag/romraider2-1.1.1)

### Android

Android 8.0 and newer users can also
[download Android 1.1.1](https://github.com/Natzirt-BK/RomRaider2/releases/download/romraider2-1.1.1/RomRaider2_1.1.1_Android-debug.apk).

Version 1.1.1 preserves imported channel selections when reloading definitions
and exports recordings in the normal RomRaider CSV layout. It includes the
OpenPort receive-filter repair. The [same release](https://github.com/Natzirt-BK/RomRaider2/releases/tag/romraider2-1.1.1)
also provides a **side-by-side test APK**, labeled **RomRaider2 OpenPort Test**,
which leaves the standard app and its recordings untouched. Debug signing
keys can differ between builds: export recordings before any uninstall, and
use the side-by-side option if it is not already installed. Either package may
refuse an update if its existing installation has a different signing key.
The Android package remains debug-signed and is installed by sideloading.
It supports exact definition-backed calibration table editing, advanced ROM
byte editing, RomRaider CSV review, Logger definition and profile import,
simulated logging, and read-only OpenPort 2.0 SSM K-Line and Mitsubishi MUT-II
logging through a USB host/OTG adapter, with recording, recovery and CSV export.
Basic Forester Android logging was reported working in the in-car test; larger
channel sets, sustained sessions, and MUT-II vehicle qualification remain open.
Software and emulator tests do not establish on-car reliability. ECU writing
and flashing are not present.

Read the [Android test guide](docs/ANDROID_PREVIEW_TESTING.md) before
sideloading it or connecting an adapter.

## Where it stands

The [full-project audit](docs/PROJECT_AUDIT_2026-09-06.md) separates current
`master` from the published 1.1.1 packages. Source repairs for
[installer migration and Android ROM-save failure handling](docs/DATA_PRESERVATION_FIXES.md)
are documented separately; XML and gauge-warning findings remain open. New
Android source work is not yet in the public downloads.

| Area | Current status |
| --- | --- |
| Subaru Editor | Available; exact matching definitions are required |
| Subaru SSM Logger | Linux OpenPort 2.0 identification and sustained logging tested in car |
| Windows J2534 | Portable build and automatic 32/64-bit routing implemented; connected qualification is still open |
| Mitsubishi Lancer Evolution MUT-II | Read-only logger foundation implemented; vehicle qualification is still open |
| Android | Basic Forester logging reported working; profile retention and desktop-format CSV fixed; broader qualification remains open |
| DimeMod | Discovery, diagnostics, and Logger parameters retained; RAM writing stays hidden and disabled |
| ROM mod recognition | DimeMod, CarBerry, and MerpMod recognized from explicit loaded-definition evidence |
| ECU flashing | Not available in RomRaider2 1.1.1 |

Do not disable Windows driver-signing protection. Install the normal signed
driver for the interface. RomRaider2 chooses the direct or bundled J2534 bridge
path without changing the vendor driver.

## Highlights

- Tabbed calibration workspace with favorites, recent and changed maps, ROM
  comparison, grouped undo/redo, notes, and crash recovery.
- Search across maps, Logger channels, DTCs, settings, and commands.
- Light/dark JavaFX themes, configurable scaling and touch controls. The
  retained Compose/macOS interface has a different feature set.
- A rebuilt JavaFX Logger workspace with searchable channels, live data,
  graphs, dashboard gauges, Dyno and offline analysis. Current source adds
  channel categories, unit selection and rolling statistics.
- Saved Gauge/Value/Trend/Alarm dashboard layouts. Custom warning/scale editing,
  warning correctness, and legacy MAF/injector workspace parity remain work
  items; desktop and Android dashboards are not feature-identical.
- RomRaider CSV analysis with linked tables and graphs, statistics, sample
  ranges, playback, markers, and configurable X/Y plotting.
- Managed Logger-definition install and reload instead of the old forum
  redirect.
- Portable Java 21 application images with local rolling diagnostics and no
  automatic upload.

## Definitions and vehicle files

Definitions are distributed separately from the application. Match the ROM ID
exactly and keep a known-good original ROM before editing anything. Application
releases and this repository do not include ROMs, owner logs, Logger profiles,
or private tuning files.

Start with a supervised, read-only connection test. A definition mismatch or a
bad calibration can damage an ECU or engine. If you are not sure what a table
or operation does, stop and verify it first.

## Building

RomRaider2 uses a 64-bit Java 21 JDK and Apache Ant:

```sh
ant unittest
ant build
```

See [the Java 21 build guide](docs/Building_RomRaider_VSCode.md) for the full
setup and platform packaging notes.

## Contributing

Bug reports and focused fixes are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md)
before opening an issue or pull request. Connected hardware reports are most
useful when they include the RomRaider2 version, operating system, interface,
protocol, exact steps, and a diagnostic log with personal and vehicle data
removed.

## History and license

RomRaider2 remains GPL-licensed and retains the original project notices and
authorship. Its main upstream sources are:

- [RomRaider/RomRaider](https://github.com/RomRaider/RomRaider)
- [DimeSPb/RomRaider](https://github.com/DimeSPb/RomRaider)
- [RomRaider.com](https://www.romraider.com/)

See [OPEN_SOURCE_PROVENANCE.md](docs/OPEN_SOURCE_PROVENANCE.md) and
[license.txt](license.txt) for details.
