# RomRaider2 for Android

The Android client provides offline ROM editing, log review and foreground-only,
read-only OpenPort logging. It contains no ECU writing or flashing path.

This page describes current source (1.1.2). Check the
[main download page](../../README.md#downloads) for the version actually published;
a source checkpoint is not a new APK release.

## Features

- Open a ROM with Android's document picker and match a RomRaider ECU definition
  against the exact ROM size and internal identifier.
- Search numeric calibration tables, inspect scaled values, edit a selected cell,
  and save a separate review copy. Bounded hexadecimal editing is also available.
- Import traditional RomRaider wide-column or RR2 long-form CSV logs.
- Import logger definitions, profiles and the read-only `type=mut2` subset of
  OpenPort `logcfg.txt`, including supported arithmetic RPN scaling.
- Preserve imported definitions and selected channels across Activity/process
  restarts. Select channels on the device without requiring a separate profile.
- Run explicitly labeled simulated data for offline setup and visual review.
- Log Subaru SSM K-Line or Mitsubishi MUT-II through an OpenPort 2.0 and USB host
  adapter. Only supported definition-backed read requests are sent.
- Record to separate app-private files, flush completed cycles, recover recordings
  after restart and export the standard RomRaider CSV format.
- Choose from 16 gauge themes, including nine new instrument faces and two
  premium night-cluster styles. Switch between
  LOGGER and GAUGES without replacing the active session or recording.
  See [gauge designs and data states](../../docs/GAUGE_DESIGN.md).

## Boundaries and qualification

Basic Forester logging was reported working during the owner's in-car test.
Sustained sessions, larger channel sets, exact EVO/MUT-II qualification and broader
physical-device testing remain separate acceptance checks. Synthetic tests and
successful builds do not establish vehicle reliability.

Logging stops when the app leaves the foreground. Gauges-only view switching is
not background recording. Configure channels, units and layout while parked.
Calculated logger-parameter dependencies are not yet evaluated. MUT-II standalone
priority values are not scheduled: selected PIDs are polled once per full cycle.

Android does not correct ROM checksums. Definition-backed and hexadecimal edits
are offline review features; Android-edited files must not be flashed without
independent target-specific validation and checksum handling.

## Build and test

Use a Java 21 JDK, Gradle and Android SDK 36. The Android project is separate from
the desktop build:

```sh
cd platform/android
gradle :shared-core:check :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
gradle :app:assembleOpenportDiagnostic :app:lintOpenportDiagnostic
```

The side-by-side variant is labeled **RomRaider2 OpenPort Test**. Standard and test
application IDs retain their historical suffixes for installation continuity;
these identifiers are not version labels. Numeric versions and version codes come
from the shared `version.properties`.

For disposable emulator tests:

```sh
gradle -Prr2AndroidTestBuildType=automation \
  :app:assembleAutomation :app:assembleAutomationAndroidTest
```

The lifecycle runner refuses physical-device targets and non-automation APKs.
It covers setup restoration, same-key updates, retained CSV export and gauge
switching with both simulated and injected-transport read-only sessions.
See [Android update tests](../../docs/ANDROID_UPDATE_RELIABILITY.md).

## Installing and updating

Export recordings and preserve setup/unsaved ROM work before any migration.
Do not uninstall an existing app merely to work around a signature mismatch.
The standard and OpenPort Test apps have separate storage; an existing test app
can also have an incompatible signing key.

Read the [1.1.2 signing migration guide](../../docs/ANDROID_1_1_2_MIGRATION.md)
before updating from 1.1.1, and use the
[parked read-only test procedure](../../docs/ANDROID_PREVIEW_TESTING.md)
when qualifying a device. The [MUT-II implementation audit](../../docs/ANDROID_MUT2_AUDIT_2026-09-05.md)
documents protocol coverage and remaining hardware gates.
