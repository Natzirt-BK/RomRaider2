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

The latest public build is **RomRaider2 1.1.0 RC3**. RC3 is a prerelease while
the remaining connected Windows and vehicle checks are completed.

| Platform | Package |
| --- | --- |
| Windows 10/11 x64 | [Download the portable Windows ZIP](https://github.com/Natzirt-BK/RomRaider2/releases/download/romraider2-1.1.0-rc3/RomRaider2_ECU_Studio_1.1.0_Windows_x64.zip) |
| Linux x64 | [Download the portable Linux ZIP](https://github.com/Natzirt-BK/RomRaider2/releases/download/romraider2-1.1.0-rc3/RomRaider2_ECU_Studio_1.1.0_Linux_x64.zip) |

Java 21 is included in both packages. Extract the ZIP before running it. On
Windows, open `RomRaider2.exe`. On Linux, open `bin/RomRaider2`.

[Release notes, checksums, and every RC3 download](https://github.com/Natzirt-BK/RomRaider2/releases/tag/romraider2-1.1.0-rc3)

### Experimental Android preview

Android 8.0 and newer users can also
[download RC4 Android Preview 1](https://github.com/Natzirt-BK/RomRaider2/releases/download/romraider2-1.1.0-rc4-android-preview1/RomRaider2_1.1.0_RC4_Android_preview1-debug.apk).
This is an early, debug-signed test build rather than the desktop RC4 release.
It supports offline ROM byte editing, RomRaider CSV review, Logger definition
and profile import, simulated logging, and early OpenPort 2.0 preparation. The
read-only Subaru SSM K-Line Logger still awaits RC5 vehicle qualification. ECU
writing and flashing are not present.

Read the [Android preview test guide](docs/ANDROID_PREVIEW_TESTING.md) before
sideloading it or connecting an adapter.

## Where it stands

| Area | Current status |
| --- | --- |
| Subaru Editor | Available; exact matching definitions are required |
| Subaru SSM Logger | Linux OpenPort 2.0 identification and sustained logging tested in car |
| Windows J2534 | Portable build and automatic 32/64-bit routing implemented; connected qualification is still open |
| Mitsubishi Lancer Evolution MUT-II | Read-only logger foundation implemented; vehicle qualification is still open |
| Android | Experimental editing and Logger preview available; connected SSM qualification is still open |
| DimeMod | Discovery, diagnostics, and Logger parameters retained; RAM writing stays hidden and disabled |
| ECU flashing | Not available in RomRaider2 1.1.0 |

Do not disable Windows driver-signing protection. Install the normal signed
driver for the interface. RomRaider2 chooses the direct or bundled J2534 bridge
path without changing the vendor driver.

## Highlights

- Tabbed calibration workspace with favorites, recent and changed maps, ROM
  comparison, grouped undo/redo, notes, and crash recovery.
- Search across maps, Logger channels, DTCs, settings, and commands.
- Light, dark, system, and high-contrast themes with 75%–300% scaling and
  desktop, touch, garage, dyno, and in-car layouts.
- A rebuilt Logger workspace with searchable channels, live data, graphs,
  dashboard gauges, MAF, injector, dyno, and offline analysis views.
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
