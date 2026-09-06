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

The current release is **RomRaider2 1.1.2** for desktop and Android, with
[release notes and verification](docs/RELEASE_1_1_2.md). Version
numbers use patch increments for routine updates, minor increments for milestones,
and rare major increments for substantial changes. Only the latest release is
kept on the Releases page; older source tags remain in Git history. See the
[versioning policy](docs/RELEASE_VERSIONING.md). Hardware qualification remains
platform-specific; a version number is not a claim of completed vehicle testing.

| Platform | Package |
| --- | --- |
| Windows 10/11 x64 | Portable Windows ZIP |
| Linux x64 | Portable Linux ZIP |
| SteamOS Desktop Mode x64 | SteamOS ZIP |
| macOS Apple silicon | Unsigned ARM64 package |
| macOS Intel | Unsigned x64 package |

Java 21 is included in every desktop package. Extract the ZIP before running
it. On Windows, open `RomRaider2.exe`. On Linux, open `bin/RomRaider2`. The Mac
packages are unsigned and have not completed physical Mac testing.

[Release notes, checksums, and all downloads](https://github.com/Natzirt-BK/RomRaider2/releases/latest)

### Android

Android 8.0 and newer users can download the standard APK or separate
**RomRaider2 OpenPort Test** APK from the
[latest release](https://github.com/Natzirt-BK/RomRaider2/releases/latest).

**The permanent signing key used by 1.1.2 cannot update the old public 1.1.1
installation in place.** Export recordings and preserve unsaved ROM work,
definitions and profiles before any manual replacement. Follow the
[migration checklist](docs/ANDROID_1_1_2_MIGRATION.md); do not uninstall or clear
storage to work around a signature mismatch. An unused separate-test package
can coexist with the standard app, but does not copy its private data. An old
test installation may also have the incompatible key. Distribution APKs remain
debuggable and are installed by sideloading.

The app preserves imported channel selections when reloading definitions,
exports recordings in the normal RomRaider CSV layout and includes the
OpenPort receive-filter repair.
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

The [gauge/editor and companion audit](docs/GAUGE_EDITOR_AUDIT_2026-09-06.md)
records automated checks, ECUFlash/Forester/EVO findings and remaining hardware
gates. The [earlier full-project audit](docs/PROJECT_AUDIT_2026-09-06.md) and
[repair qualification](docs/AUDIT_REPAIR_STATUS_2026-09-06.md) are dated records,
not a live release inventory. Repairs for
[installer migration and Android ROM-save failure handling](docs/DATA_PRESERVATION_FIXES.md)
and [encoding-safe XML imports](docs/XML_IMPORT_HARDENING.md) are documented
separately, along with [gauge warning/conversion repairs](docs/GAUGE_WARNING_REPAIRS.md).
See [shared-version and package checks](docs/RELEASE_VERSIONING.md) for versioning
and [Android update reliability](docs/ANDROID_UPDATE_RELIABILITY.md) for signing
and saved-setup protections.

| Area | Current status |
| --- | --- |
| Subaru Editor | Available; exact matching definitions are required |
| Subaru SSM Logger | Linux OpenPort 2.0 identification and sustained logging tested in car |
| Windows J2534 | Portable build and automatic 32/64-bit routing implemented; connected qualification is still open |
| Mitsubishi Lancer Evolution MUT-II | Read-only logger foundation implemented; vehicle qualification is still open |
| Android | Basic Forester logging reported working; profile retention and desktop-format CSV fixed; broader qualification remains open |
| DimeMod | Discovery, diagnostics, and Logger parameters retained; RAM writing stays hidden and disabled |
| ROM mod recognition | DimeMod, CarBerry, and MerpMod recognized from explicit loaded-definition evidence |
| ECU flashing | Not available in RomRaider2 1.1.2 |

Do not disable Windows driver-signing protection. Install the normal signed
driver for the interface. RomRaider2 chooses the direct or bundled J2534 bridge
path without changing the vendor driver.

## Highlights

Features below describe **1.1.2**. Consult the selected release's notes for its
package verification and platform-specific limits.

Development source is now **1.1.3**, adding
[desktop/Android channel-setup transfer](docs/PORTABLE_LOGGER_SETUP.md), reusable/linked
[MAF/Injector analysis setups](docs/FUEL_LOG_ANALYSIS.md#reusable-setups-113-development-source),
[curve review](docs/FUEL_CURVE_REVIEW.md), explicitly confirmed
[offline MAF-table transfer](docs/REVIEWED_MAF_TRANSFER.md),
[recorded-time transient filtering](docs/FUEL_RATE_FILTER.md), and Android
[calculated logger channels](docs/CALCULATED_LOGGER_CHANNELS.md). Saved-log
[named operating conditions](docs/FUEL_OPERATING_CONDITIONS.md) now cover all eight
legacy scalar gate categories with explicit mappings and limits.
[Reviewed range sharing](docs/SHARED_ANALYSIS_RANGE.md) connects the three analysis
workspaces while keeping range-only statistics distinct from filtered fuel results.
[Accepted-sample inspection](docs/ACCEPTED_FUEL_SAMPLES.md) adds original-row
paging and full-accepted-set channel statistics to both fuel workspaces.
[Read-only map tracing](docs/LOG_MAP_TRACING.md) follows the saved-log cursor over
an explicitly captured table snapshot, with manual axis-channel mapping.
[Binned log analysis](docs/BINNED_LOG_ANALYSIS.md) adds count-aware tables,
heatmaps and 2D/3D mean plots with explicit gaps and bounded background work.
[Run comparisons](docs/RUN_COMPARISONS.md) align independent CSV ranges in common
bins, with count-aware overlays/differences and bounded second-log imports.
[Normal desktop CSV imports](docs/BOUNDED_CSV_IMPORT.md) now also have explicit
size limits and cancellable background parsing.
[Range statistics](docs/LOG_ANALYSIS_ARCHITECTURE.md#background-range-statistics--113-development-source)
run on a bounded worker and reject results from superseded ranges.
[Log tables](docs/LOG_TABLE_RESPONSIVENESS.md) retain original sample identities
with lightweight source indexing and cancellable background sorting.
[Marker files](docs/MARKER_FILE_SAFETY.md) load/save off the JavaFX thread, with
strict bounds, conflict checks and atomic replacement.
[Legacy Swing loading](docs/SWING_LOG_LOADING.md) now prepares bounded CSVs,
initial statistics and marker snapshots off the event thread, with separate
workers for later range calculations and marker saves.
[Desktop profile persistence](docs/DESKTOP_PROFILE_INTEGRITY.md) preserves Unicode
units and captured protocol, with escaped XML and atomic save/backup replacement.
These follow-ups are not in the published 1.1.2 downloads.

Android 1.1.3 source also adds [service-owned background recording](docs/ANDROID_BACKGROUND_RECORDING.md),
with explicit start, notification Stop and same-recording screen reattachment.
Physical OpenPort/background acceptance is still pending; synthetic service tests
do not qualify phone power management or vehicle compatibility.
[Interrupted-recording recovery](docs/ANDROID_RECORDING_RECOVERY.md) validates a
private snapshot before export and requires review before omitting an unfinished
final record; the original recovery file remains unchanged.
[Large-log Android review](docs/ANDROID_CSV_REVIEW.md) now streams bounded summaries
off the UI thread, with cancellation and channel pagination. The source also adds
five vibrant/retro gauge styles and a [full-screen mounted display](docs/ANDROID_MOUNTED_DISPLAY.md)
that fits 1–6 gauges without scrolling and keeps the screen awake while visible,
including when logging is stopped. Changing the displayed count does not change
the channels being recorded.

- Tabbed calibration workspace with favorites, recent and changed maps, ROM
  comparison, grouped undo/redo, notes, and crash recovery.
- JavaFX [selection math and document controls](docs/DESKTOP_EDITOR_COMMANDS.md):
  multiply cells, custom adjustment steps, protected reload and ROM properties.
- Search across maps, Logger channels, DTCs, settings, and commands.
- Light/dark JavaFX themes, configurable scaling and touch controls. The
  retained Compose/macOS interface has a different feature set.
- A rebuilt JavaFX Logger workspace with searchable channels, live data,
  graphs, dashboard gauges, Dyno and offline analysis. Current source adds
  channel categories, unit selection and rolling statistics.
- Saved Gauge/Value/Trend/Alarm desktop layouts with conversion-bound limits.
  Nine original [new gauge designs](docs/GAUGE_DESIGN.md), STI Night and Evolution
  Night styles, and gauges-only views are available
  on Android, JavaFX and Compose. Switching views preserves the
  logger; desktop and Android configuration options are not identical.
- Initial read-only [MAF and injector log analysis](docs/FUEL_LOG_ANALYSIS.md)
  in JavaFX. Full legacy analysis parity remains in progress.
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
