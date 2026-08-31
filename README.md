# RomRaider2 ECU Studio

RomRaider2 is a much-needed modernization of RomRaider for Subaru and
Mitsubishi Lancer Evolution VIII/IX ECU editing, logging, diagnostics, and log
analysis. It preserves useful work from the DimeMod fork while adding a new
interface, current runtime, safer diagnostics, and improved logging.

RomRaider2 is part of **Ecu Tools by NatZirt**, a project that makes established
Subaru and Lancer Evolution tuning software practical to install and use on
Linux. Windows users can download a self-contained portable RomRaider2 package.

## What RomRaider2 adds

- A modern tabbed calibration workspace with favorites, recent and changed-map
  navigation, persistent tab order, and recently closed map recovery.
- Fast table filtering and unified search across maps, logger parameters, DTCs,
  settings, and commands.
- ROM comparison, grouped undo/redo, selected-cell revert, change summaries,
  notes, and integrity-checked crash recovery with safe startup restore review.
- An optional interactive 3D map surface integrated with the active table.
- Integrated live-data cards, traces, and a datalog workspace driven by actual
  logger samples.
- Offline RomRaider CSV analysis with linked tables, graphs, statistics, range
  selection, 0.25x–8x playback, persistent typed markers, latest-capture replay,
  and configurable linked X/Y plotting.
- Dark, light, system, and high-contrast themes; 75%–300% scaling; and Compact,
  Touch, Garage, Dyno, and In-Car display modes.
- Integrated application window controls and responsive resizing.
- Symmetric lower-left and lower-right resize grips for frameless windows.
- A shared Subaru and Lancer Evolution VIII/IX platform model, including a
  read-only Mitsubishi MUT-II logging foundation.
- A definition-aware DimeMod feature inventory that separates mapped support
  from verified runtime state and leaves RAM writing disabled.
- Versioned, isolated settings and privacy-safe local diagnostic reports.
- Self-contained 64-bit Java 21 application images for Linux and Windows.

## What is fixed from the inherited build

- Calibration tabs respond across the complete tab header.
- The 3D view stays closed until requested and follows the pointer correctly on
  both drag axes.
- Window resizing includes the edges and bottom corner, and status-bar text uses
  a consistent baseline without clipping.
- Windows menus, tabs, lists, tables, combo boxes, and file choosers keep
  readable contrast under the application themes.
- The narrow Favorites header keeps its action separate from the section label.
- OpenPort/J2534 receive handling waits for complete messages and resynchronizes
  when Subaru SSM queries change, eliminating the observed logging gaps.
- J2534 logging no longer presents an irrelevant serial COM-port selector.
- External serial sensors validate configuration and reconnect when their port
  changes; Windows-only plugins are hidden on unsupported systems.
- Logger definitions can be validated and installed into managed user data,
  activated, saved, and reloaded directly from the Logger; the old false
  updater no longer redirects to an obsolete forum page.
- Missing Logger definitions use one application-styled prompt with clear
  install-file and external-sensors-only choices.
- Raw exception details are no longer exposed in error dialogs or uploaded
  automatically.
- End-of-life Java and logging dependencies were replaced with audited Java 21,
  JNA, jSerialComm, and Log4j components.

## Current validation and limits

The Linux OpenPort 2.0 Subaru SSM/ISO9141 path has completed ECU identification
and sustained in-car logging. Mitsubishi MUT-II vehicle logging and Windows
connected-hardware testing are still qualification items. The RC2 unexpected
USB-disconnect fix has automated coverage; its connected retest is deferred to
RC3. ECU memory writing and flashing are not enabled in RomRaider2 RC2.

Release archives contain software only. Vehicle ROMs, definitions, logger
profiles, captured logs, and owner-specific tuning material are not included.
Always match definitions by exact ROM ID and begin with a supervised read-only
connection test.

## Downloads and Linux setup

Packages and supported Linux installation commands are on the
[Ecu Tools by NatZirt release page](https://github.com/Natzirt-BK/subaru-ecu-tools-linux/releases/tag/romraider2-1.1.0-rc2).

See `docs/Building_RomRaider_VSCode.md` for the current Java 21 development
setup. `docs/Building_RomRaider.txt` is retained for historical build context.

## License and attribution

RomRaider2 retains RomRaider's GPL notices and upstream attribution:

- https://github.com/RomRaider/RomRaider
- https://github.com/DimeSPb/RomRaider
- https://www.romraider.com/
