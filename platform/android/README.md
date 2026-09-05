# RomRaider2 Android foundation

This standalone Android project is an early portable client. It deliberately
ships no ECU writing path.

Implemented:

- open a ROM through Android's document picker;
- match a standard or standalone RomRaider ECU definition against the exact
  ROM size and internal ID;
- search named numeric calibration tables, inspect scaled values, edit a
  selected cell, and save a separate review copy;
- inspect the first 256 bytes, apply bounded hexadecimal edits, reset edits,
  and save a separate copy;
- open and summarize traditional RomRaider wide-column and RomRaider2
  portable long-form CSV logs;
- securely import an existing RomRaider logger definition and profile;
- resolve direct SSM parameters and switches against exact units, module
  targets, and ECU mappings;
- show selected serial external inputs as unavailable instead of silently
  dropping them;
- plan deduplicated, 64-address-or-smaller read batches and apply the same
  signed, unsigned, endian, float, arithmetic, conditional, and bitwise
  conversions used by the desktop logger;
- run a clearly labeled simulated logger session with updating values and save
  that session as portable CSV;
- enumerate attached USB devices;
- request Android USB permission for an OpenPort 2.0, claim its bulk interface,
  identify its firmware, and read vehicle battery voltage without querying the
  ECU;
- share a bounded OpenPort K-line stream decoder and read-only Subaru SSM
  init/address codec with no ECU write commands;
- open a foreground-only 4800-baud SSM K-Line session, identify the engine ECU,
  exclude transmission-only parameters, execute the selected read batches,
  display converted values, and record a live CSV.
- select Mitsubishi MUT-II explicitly and use the OpenPort 2.0 ISO9141 channel
  at 15625 baud, 8N1, with read-only single-PID requests;
- import the read-only `type=mut2` subset of an OpenPort `logcfg.txt` directly,
  including arithmetic RPN scaling, or load a MUT2 RomRaider logger XML;
- select channels on the phone without requiring a separate logger profile;
- flush each completed cycle and retain separate app-private recording files,
  available from **Recover / export recordings** after restart. Normal stop
  closes the writer; new sessions do not discard older recordings.

Current preview limits:

- calculated logger-parameter dependency evaluation;
- MUT2 standalone `priority` is documented but not scheduled: every selected
  PID is polled once per full cycle; choose fewer channels for faster updates;
- setup definitions/channel selections must be reloaded after Activity/process
  recreation; protocol and gauge theme persist;
- qualify the wired read-only OpenPort logger on a supported car during RC5;
- responsive large-screen and landscape layouts;
- release signing, Play Store packaging, and broader physical-device checks.

The first debug APK launched successfully on a Galaxy S25, and the traditional
logger CSV that exposed the original header gap now opens correctly. The newer
definition/profile import and offline logger preview still need a phone rerun.
The OpenPort preparation and SSM/MUT2 live logger paths still need connected-device
checks. The live button is labeled as awaiting RC5 qualification, stops when
the app leaves the foreground, and keeps ECU writing absent.

Android does not correct ROM checksums. Definition-backed and hexadecimal
edits are offline review features; Android-edited files must not be flashed.

See [Android preview testing](../../docs/ANDROID_PREVIEW_TESTING.md) before
sideloading the APK or reporting a result.

The Android SDK is intentionally separate from the desktop build. With a Java
21 JDK and Android SDK 36 installed:

```bash
cd platform/android
gradle :app:assembleDebug
```

Regression/build gate for the standard preview:

```bash
gradle :shared-core:check :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The portable checks and Android session tests use synthetic responses, not an
ECU. See [MUT-II implementation audit](../../docs/ANDROID_MUT2_AUDIT_2026-09-05.md)
for evidence and the remaining hardware gate.

## OpenPort acknowledgement repair test build

The receive-filter reply is channel-qualified (`arf3 0 0` on the tested
adapter firmware), not `arf ` followed by a space. The control-response checks
cover the captured reply, fragmentation, wrong channels, malformed fields,
bounded input and complete adapter errors. Timeout messages identify the setup
operation and distinguish no reply from an unrecognized reply without exposing
raw payloads. This repairs adapter setup, not proof of connected ECU logging.

To build the separately installed phone test application:

```bash
gradle :shared-core:check :app:testDebugUnitTest :app:assembleOpenportDiagnostic :app:lintOpenportDiagnostic
```

It appears as **RomRaider2 OpenPort Test**, with application ID
`com.romraider.mobile.preview.openporttest` and version suffix `-side-by-side`.
It uses a local debug signature and installs beside Preview 3, leaving that
app's settings and retained recordings untouched. Import the logger definition
and profile into the test app separately and grant it USB access. If Android
offers both apps for the OpenPort, choose the test app and close the other one.
Do not uninstall Preview 3 to install this test build.

Preview 3.1 uses versionCode 110404. The normal debug/release application IDs
are unchanged; the side-by-side variant has its own application ID.
No ECU writing, reset, programming-voltage or automatic reconnect operation is
added by this repair. Physical phone/adapter/vehicle logging still requires the
parked, ignition-on/engine-off acceptance procedure.
