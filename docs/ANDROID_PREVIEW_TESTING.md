# Android testing

Version 1.1.3 includes [calculated logger channels](CALCULATED_LOGGER_CHANNELS.md)
and [reviewed interrupted-recording recovery](ANDROID_RECORDING_RECOVERY.md).
It preserves the original spool and validates completed records before exporting;
omitting an unfinished tail requires explicit review.

This guide covers Android **1.1.3 RC1**. Check the
[release candidate](https://github.com/Natzirt-BK/RomRaider2/releases/tag/romraider2-1.1.3)
for published downloads. Packages are sideloaded and
remain debuggable, so Android or Play Protect may warn about an unknown app.
Back up recordings and saved work before replacing an installation.
The optional **RomRaider2 OpenPort Test** APK installs separately from the main
app. Import its logger setup separately and give USB access to only one app.
For build-signing details, see [update reliability](ANDROID_UPDATE_RELIABILITY.md).

The app includes the OpenPort filter-response repair, retains imported
profile selections when definitions are reloaded, and exports normal wide-column
RomRaider CSV files. Loading a definition alone selects no channels. Basic
Forester logging was reported working; larger profiles, sustained sessions, and
MUT-II vehicle qualification remain open.

## What is ready to try

- Open a ROM and a matching RomRaider ECU definition, search named numeric
  tables, inspect scaled values, edit one cell, reset it, and save a separate
  copy.
- Inspect bytes and make a bounded hexadecimal edit when validating a
  definition.
- Open traditional RomRaider wide-column CSV logs and RomRaider2 portable CSV
  logs.
- Import a RomRaider v370 Logger definition and an existing Logger profile.
- Run the clearly marked simulated Logger and save its CSV.
- Choose from 25 gauge styles, mix styles per channel, reset measured peaks and
  show or hide the simulated gauge demo without an ECU. Set up 1–6 fullscreen
  gauges independently, or copy the logger selection. Fullscreen mode keeps the
  screen awake; tap for temporary exit controls. Switching between LOGGER and
  GAUGES preserves the active session and recording.
- Open About / licenses to review the bundled software license and brand notice.
- Check whether Android detects an attached OpenPort 2.0 and grants USB
  permission.
- Attach an OpenPort 2.0 while RomRaider2 is closed and confirm Android offers
  to open RomRaider2. This prepares the adapter only; it does not query the ECU
  or start logging.

The application contains no ECU flash or memory-write command. ROM editing only
changes an in-memory document and saves a new file through Android's document
picker. Android does not correct ROM checksums, so saved files are for review
and desktop validation and must not be flashed.

## Connected Logger warning

The read-only Subaru SSM and Mitsubishi MUT-II K-line loggers are implemented.
Basic Forester logging was reported working, but larger-profile/sustained logging
and exact EVO/MUT-II qualification remain open. Use careful, parked testing only:

- keep the vehicle stationary and do not operate the phone while driving;
- use a compatible Subaru SSM K-line or Evo VIII/IX MUT-II vehicle and an
  OpenPort 2.0, with the correct protocol explicitly selected;
- start with ignition on and engine off;
- stop if the adapter, phone, or vehicle behaves unexpectedly;
- do not rely on the app for safety-critical monitoring.

CAN, transmission sessions, serial external sensors, ECU writing, and flashing are not
available in this Android version.
Definition-backed calculated parameters are supported. OBDLink and VAG-COM/KKL
adapter support is not included; use OpenPort 2.0 for connected logging.

## EVO VIII/IX MUT-II with OpenPort 2.0

On September 7 the owner reported successful Evo logging with the startup fix.
This confirms that reported connection/session, not all channel conversions or
sustained/background operation. The offered CSV was reviewed: 494 rows over
279.525 seconds with no obvious recording gaps. The separate definition audit
found a misleading rear-O2 label and unresolved scaling; do not treat successful
transport or explicit unit labels as validation of every channel.

Android 1.1.5 adds optional `paramunits` to each MUT-II TXT parameter, for example
`paramunits=rpm`, `paramunits=V`, `paramunits=°C` or `paramunits=%`. Unit labels
flow into selection, gauges, recordings and normal wide RomRaider CSV headers;
they never modify the scaling expression. Labels are bounded to 24 characters
and reject control characters/CSV delimiters. Omitted units retain raw/scaled.
Reimport the updated definition and reselect units/channels; old recordings are
unchanged. This is an RR2 TXT extension, not qualified for OpenPort standalone
firmware. Older APKs reject the new field. Unresolved units must not be guessed.

The **1.1.4 test build** adds slow five-baud initialization before the first
engine request. It grounds diagnostic pin 1 during the MUT-II session and
releases it on Stop or failed startup; no flash voltage or ECU memory write is
used. This change is not in the published 1.1.3 APK. Its automated tests pass,
but successful communication with the owner's Evo is still unconfirmed.
Allow several seconds for initialization. If it fails, preserve the complete
error text: it now distinguishes startup operations from the subsequent PID
request. If pin release cannot be confirmed, stop and disconnect the adapter
when safe. See [the investigation](EVO_MUT2_CONNECTION_DIAGNOSIS.md).

1. Connect the OpenPort through a **USB host/OTG data** adapter. A charging-only
   adapter is insufficient. Grant Android USB permission and use **Prepare
   OpenPort** to check adapter access; this does not start ECU logging.
2. Select **Protocol: MUT2**, then **Open logger definition**. Import the
   `type=mut2` OpenPort text configuration, or a logger XML containing
   a `MUT2` protocol. A ROM editor definition is not a logger definition.
   Starting with the next 1.1.4 development build, text definitions must have
   `XXRR2-MUT-IIXX` on their first line. The commented forms
   `; XXRR2-MUT-IIXX` and `# XXRR2-MUT-IIXX` are also accepted; use the semicolon
   form to retain OpenPort configuration syntax. Case must match exactly. A
   leading UTF-8 BOM and surrounding spaces are allowed, but preceding blank
   lines or other comments are not. Missing markers also block saved-definition
   restoration: add the header to the source file and import it again. XML and
   SSM definitions are unchanged. This copyable marker is simple validation,
   not authentication or proof of ECU compatibility. The previously supplied
   local 1.1.4 startup-test APK does not contain this later validation change.
3. Use **Choose channels**. Start with RPM and battery voltage for the parked
   test; add channels after checking those values. All selected channels are
   recorded; fullscreen mode shows 1–6 independently configured gauges.
4. Try **Start offline preview** first. Those values are simulated, not vehicle
   measurements. Stop it before the connected test.
5. With the vehicle parked, ignition on and engine off, start the read-only
   logger. `MUT2_GENERIC` means a plausible battery-PID response, **not** a match
   to a particular ECU calibration. Verify the definition against your vehicle.
6. Stop and wait for completion, then **Save live CSV**. Switching between
   LOGGER and GAUGES preserves the session. Recording can continue in the
   background through the service described below. USB
   disconnection stops capture, and the Editor cannot be opened during recording.
   The app does not automatically reconnect or resume ECU requests.

### Background recording

Start remains explicit. After the first notification-permission prompt, press
Start again; granting permission alone does not query the ECU. An accepted
recording uses a foreground service and can continue through Home, screen-off
and Activity replacement. The recording notification opens the app or stops that
specific session. If notifications are disabled, return to the app and use Stop;
Android's Active apps Stop terminates the whole app without an orderly flush.
Closing/swiping away the screen is not an orderly recording Stop.

Wait for stopping/adapter cleanup before saving or changing setup. The service
releases the adapter after capture; Prepare OpenPort again for another run. Gauge
peaks and receipt age belong to the capture, not a recreated screen. Stale readings
remain unavailable, and peak reset does not make them fresh. Use setup/stop/export
controls while parked. See [the service contract and test limits](ANDROID_BACKGROUND_RECORDING.md).
Real OpenPort/background behavior still needs supervised phone/vehicle acceptance;
the automated service tests use an isolated emulator and synthetic transport.

No arbitrary pin-voltage, fault-clear, reset, flashing, or ECU memory-write
operation is exposed. The 1.1.4 MUT-II startup uses only the fixed diagnostic
pin 1 ground/release described above. Unsupported logcfg options are rejected,
not executed. The importer
supports `paramname`, optional `paramunits` (1.1.5+), one-byte `paramid`, arithmetic `scalingrpn` (`x`, numeric
constants, `+ - * /`) and validated `priority`. Priority is informational here:
all selected PIDs are polled each cycle. Unspecified units are labeled `raw` or
`scaled`; the source channel name is preserved, and temperature curves or
ambiguous units are not guessed.

Recordings are kept in app-private storage rather than cache. **Recover /
export recordings** exports earlier sessions after an app restart; exporting
does not delete the recovery copy. Back these up before uninstalling or clearing
app data. Storage use grows with retained recordings. The current cycle may be
lost or incomplete after abrupt process/power loss; this is not a crash-proof
data recorder. The app restores saved definitions/channel selections
after restart or Activity recreation; this does not migrate data across an
uninstall or automatically restore a USB/logging session. Keep source setup
files as a backup. Phone-specific USB,
sleep, rotation, and permission behavior remain part of connected qualification.

Android's [USB host guide](https://developer.android.com/develop/connectivity/usb/host)
describes host-mode enumeration and permission requirements.

## Reporting useful results

Open an Android issue and include:

- APK version or Git commit;
- phone or tablet model and Android version;
- interface model and whether Android granted USB permission;
- the exact screen and steps that led to the result;
- definition and profile versions, when relevant;
- whether the test was offline, adapter-only, or parked ignition-on;
- a screenshot or the smallest useful error text.

Remove usernames, full ECU identifiers, ROM files, definitions, Logger
profiles, captured vehicle logs, and private calibration data before posting.
