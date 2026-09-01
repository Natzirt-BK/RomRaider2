# RomRaider2 1.1.0 RC3 release readiness

Status: RC3 prerelease. Stable promotion remains blocked on the connected
checks below.

The RC3 Windows and Linux ZIPs must come from the same source commit. Manual
results are recorded against those exact files so a later stable promotion does
not substitute a different build.

## Automated checks

The Linux and Windows workflows must:

- compile and run the shared Java 21 test suite;
- verify locked source and runtime dependencies;
- build the native application image for that operating system;
- include the launchers, runtime, settings, customization files, licenses, and
  required native libraries;
- reject ROMs, definitions, profiles, logs, private vehicle data, and retired
  Java3D/Graph3d files;
- record the source revision and verify every packaged file by SHA-256;
- publish the candidate ZIP and its checksum as workflow artifacts.

## Manual checks still open

- Windows 10/11 x64 clean-machine and visual pass, including the rebuilt
  Logger at normal, narrow, Dark, Light, and 150/200% scale.
- Windows OpenPort discovery, direct/bridged J2534 cases, disconnect, reconnect,
  and clean shutdown with real hardware.
- Linux OpenPort USB removal, log close, reconnect, and remaining in-car checks.
- Evo VIII/IX MUT-II vehicle test.
- Windows external serial-sensor test if it is claimed in RC3.

Use `RC3_QUALIFICATION_RECORD.md` for each completed pass. Keep ROMs,
definitions, full ECU identifiers, captures, and owner information out of the
record.

## Stable promotion rule

A failed hardware or package check means the problem is fixed and a new
candidate is built. Do not relabel an older artifact. Flashing and ECU memory
writing remain outside the 1.1.0 release.
