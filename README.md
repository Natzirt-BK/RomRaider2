# RomRaider2 ECU Studio

[![Build](https://github.com/Natzirt-BK/RomRaider2/actions/workflows/build.yaml/badge.svg)](https://github.com/Natzirt-BK/RomRaider2/actions/workflows/build.yaml)
[![Release candidate](https://img.shields.io/github/v/release/Natzirt-BK/RomRaider2?include_prereleases)](https://github.com/Natzirt-BK/RomRaider2/releases)
[![License](https://img.shields.io/badge/license-GPL--2.0%2B-blue)](license.txt)

ECU editing, read-only logging, gauges and log analysis for Subaru and Mitsubishi
Lancer Evolution. Built on RomRaider and DimeMod, with desktop and Android interfaces.

RomRaider2 is in development. **The current download is 1.1.5 RC1; a stable
release is not available yet.**

Development source is **1.1.6**, with a separate desktop
[read-only ELM/OBDLink adapter test](docs/ELM_IN_CAR_TEST.md) awaiting vehicle
qualification. It is not included in the current download.

## Downloads

[Download 1.1.5 RC1](https://github.com/Natzirt-BK/RomRaider2/releases/tag/romraider2-1.1.5)
· [Release notes](docs/RELEASE_1_1_5.md)

| Platform | Package |
| --- | --- |
| Windows 10/11 x64 | Portable ZIP |
| Linux x64 | Portable ZIP |
| SteamOS Desktop Mode x64 | SteamOS ZIP |
| macOS Apple silicon / Intel | Unsigned ZIP; hardware testing pending |
| Android 8.0+ | APK; install manually |

Desktop packages include Java 21. Extract before launching: `RomRaider2.exe` on
Windows or `bin/RomRaider2` on Linux. SHA-256 checksums accompany each download.
The optional **OpenPort Test** Android APK installs separately from the main app.

Only the current candidate is offered on GitHub Releases. Application versions
use `major.minor.patch`; **RC1** is the release stage, not a separate version
sequence. See [versioning](docs/RELEASE_VERSIONING.md).

## Features

- Tabbed calibration editor with matching definitions, search, ROM comparison,
  grouped undo/redo and recovery.
- Read-only Subaru SSM and Mitsubishi MUT-II logging, channel profiles and
  RomRaider-format CSV recordings.
- Configurable gauges, live graphs and gauges-only views that preserve logging.
- Saved-log playback, statistics, markers, and desktop MAF/injector analysis.
- Light/dark desktop themes, touch controls and compact calibration tables.

Platform features differ. Read the [Android guide](docs/ANDROID_PREVIEW_TESTING.md)
before connecting an OpenPort 2.0 through a USB host/OTG adapter.

### New in 1.1.5

Desktop Logger gains responsive channel controls, inline units, a Target Module
dropdown and connection/setup fixes. Dashboard opens the full-screen gauge view
without a duplicate tab. Graphs, Dyno timing checks, CSV recording and desktop
shutdown are improved. Android includes MUT-II startup fixes and explicit
imported-channel unit labels.

The [25 gauge styles](docs/GAUGE_DESIGN.md), independent 1–6-gauge layouts,
[background recording](docs/ANDROID_BACKGROUND_RECORDING.md) and
[saved-log analysis](docs/FUEL_LOG_ANALYSIS.md) remain available.

See the [active work plan](docs/ACTIVE_WORK_PLAN.md) and
[technical documentation](docs/README.md) for details.

## Compatibility and limits

Basic Forester SSM and Evo MUT-II logging have owner-reported in-car results.
Channel accuracy, sustained/background sessions, Windows adapters, macOS and
Steam Deck still need further hardware testing.
**Production flashing and live tuning are not available.** Android does not
repair ROM checksums.

Definitions are separate downloads and must match the ROM ID exactly. Keep an
original ROM and back up saved work before changing an installation. Set up
logging and gauges while parked; the app does not replace vehicle instruments
or warning systems. Incorrect calibration can damage an ECU or engine.

## Building and contributing

Use a 64-bit Java 21 JDK and Apache Ant:

```sh
ant unittest
ant build
```

See the [build guide](docs/Building_RomRaider_VSCode.md) and
[contributing guide](CONTRIBUTING.md). Bug reports should include the app version,
platform, interface and steps to reproduce. Remove private vehicle data from
logs and screenshots before posting.

## License and credits

RomRaider2 is independent community software, licensed under GPL-2.0-or-later.
It retains upstream authorship and notices from
[RomRaider](https://github.com/RomRaider/RomRaider) and
[DimeMod](https://github.com/DimeSPb/RomRaider).
See [open-source credits](docs/OPEN_SOURCE_PROVENANCE.md) and [license.txt](license.txt).
STI branding is unofficial and does not imply manufacturer endorsement.
