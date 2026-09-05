# Android Preview 3.1 — OpenPort receive-filter acknowledgement repair

Preview 3.1 is a repair revision of Preview 3, not a new feature milestone or
a vehicle-qualified stable release. Android versionCode advances to 110404;
the standard application name and preview application ID remain unchanged.

## Cause and repair

A real OpenPort adapter (firmware 1.17.4955) returned `arf3 0 0\r\n` after
the channel-3 pass-filter request. Preview 3 expected `arf `, rejected the valid
channel-qualified reply and timed out before ECU identification. Firmware
identification, voltage measurement, channel opening and the preceding six
timing/format settings all acknowledged successfully in the diagnostic.

The shared control-response validator now requires a complete CRLF line for
channel 3, a bounded unsigned filter ID and the observed zero trailing field.
Wrong channels, unknown formats, extra fields and arbitrary embedded `arf`
substrings do not count as success. The supported response format is deliberately
narrow; this is not blanket qualification of every OpenPort firmware version.

The Android SSM and MUT2 paths both use the repaired validator. Fragmented
control responses remain bounded to 4096 bytes. Complete adapter errors fail
explicitly. Timeouts identify the setup operation and distinguish no USB reply
from an unrecognized reply without exposing raw payloads. Binary ECU response
decoding, polling protocols and ECU-write restrictions are unchanged.

## Local evidence

- 142 new deterministic control-response assertions passed, including the
  captured reply, every fragmentation boundary, bytewise input, wrong channels,
  malformed fields, complete adapter errors, input bounds and privacy-safe
  timeout messages. Both SSM and MUT2 validator selection paths are checked.
- Existing portable-core checks, 69 MUT2 assertions and all 14 Android logger
  session JVM tests passed.
- Standard and side-by-side Android builds and lint passed. Lint has zero
  errors and the two existing compile/target SDK-version warnings.
- A fresh response from the real adapter passed the **actual new production
  Java validator** through a bounded Linux/libusb diagnostic; the same bytes
  failed the old prefix check. No ECU query, initialization pulse, memory write,
  ECU reset or programming-voltage command was sent in that diagnostic.
- The initial local repair APK passed signature verification and installed and
  launched beside Preview 3 in an API36 emulator. The final release APKs are
  separately built and identified by their release-side SHA-256 files.

The current emulator image can receive USB passthrough at kernel level but
does not expose USB-host devices through the Android app API. Therefore the
Linux adapter test is not claimed as an Android USB/ECU end-to-end test.

## Two installation choices in the same current release

1. **Standard APK:** application ID `com.romraider.mobile.preview`, name
   **RomRaider2 Preview**, version `1.1.0-rc4-preview3.1`.
2. **Optional side-by-side test APK:** application ID
   `com.romraider.mobile.preview.openporttest`, name **RomRaider2 OpenPort Test**,
   version `1.1.0-rc4-preview3.1-side-by-side`. It leaves the standard preview
   and its recordings untouched. Import the setup and grant USB permission to
   this separate app; choose it in Android's USB app chooser and close the other.

These are debug-signed prerelease APKs. CI signing keys can differ between
builds. If an update is refused, do not uninstall or clear app data before
exporting recordings. The side-by-side choice avoids replacing the installed
standard preview. It is not an automatic migration of its data or permissions.

## Remaining acceptance gate

Retest the phone, USB-C host/data adapter and OpenPort with the correct vehicle
logger definition/profile, parked with ignition on and engine off. Confirm
**READ-ONLY LIVE DATA / ECU ...**, plausible values, recording/export and clean
stop/reconnect before claiming connected logging success. Offline preview and
gauge demo values are simulated and do not count as vehicle evidence.

Foreground-only logging and all other [Android qualification limits](ANDROID_MUT2_AUDIT_2026-09-05.md)
remain. Production ECU memory writing, resetting and flashing are unavailable.
