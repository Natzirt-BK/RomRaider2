# Android preview testing

The Android build is an experimental RomRaider2 preview for early feedback. It
is not a replacement for the desktop release yet. The APK is debug-signed and
is installed by sideloading, so Android or Play Protect may warn that it is an
unknown application. Its preview-only application ID keeps it separate from a
future signed release. Because debug signing keys can change between preview
builds, an update may require uninstalling an older preview first.

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
- Review the two-column mobile gauge dashboard, switch between its five styles,
  reset measured peaks, and use the simulated gauge demo without an ECU.
- Check whether Android detects an attached OpenPort 2.0 and grants USB
  permission.
- Attach an OpenPort 2.0 while RomRaider2 is closed and confirm Android offers
  to open the preview. This prepares the adapter only; it does not query the ECU
  or start logging.

The application contains no ECU flash or memory-write command. ROM editing only
changes an in-memory document and saves a new file through Android's document
picker. Android does not correct ROM checksums, so saved files are for review
and desktop validation and must not be flashed.

## Connected Logger warning

The read-only Subaru SSM and Mitsubishi MUT-II K-line loggers are wired into
preview3 but have not
completed RC5 vehicle qualification. It is for careful, parked testing only:

- keep the vehicle stationary and do not operate the phone while driving;
- use a compatible Subaru SSM K-line or Evo VIII/IX MUT-II vehicle and an
  OpenPort 2.0, with the correct protocol explicitly selected;
- start with ignition on and engine off;
- stop if the adapter, phone, or vehicle behaves unexpectedly;
- do not rely on the preview for safety-critical monitoring.

CAN, transmission sessions, calculated
parameters, serial external sensors, ECU writing, and flashing are not
available in this Android preview.

## EVO VIII/IX MUT-II with OpenPort 2.0

1. Connect the OpenPort through a **USB host/OTG data** adapter. A charging-only
   adapter is insufficient. Grant Android USB permission and use **Prepare
   OpenPort** to check adapter access; this does not start ECU logging.
2. Select **Protocol: MUT2**, then **Open logger definition**. Import the
   existing `type=mut2` OpenPort text configuration, or a logger XML containing
   a `MUT2` protocol. A ROM editor definition is not a logger definition.
3. Use **Choose channels**. Start with RPM and battery voltage for the parked
   test; add channels after checking those values. All selected channels are
   recorded; the dashboard shows at most eight gauges.
4. Try **Start offline preview** first. Those values are simulated, not vehicle
   measurements. Stop it before the connected test.
5. With the vehicle parked, ignition on and engine off, start the read-only
   logger. `MUT2_GENERIC` means a plausible battery-PID response, **not** a match
   to a particular ECU calibration. Verify the definition against your vehicle.
6. Stop and wait for completion, then **Save live CSV**. Backgrounding the app,
   switching workspace, or disconnecting USB stops the session. It does not
   automatically reconnect or resume ECU requests.

No pin-voltage, fault-clear, reset, flashing, or ECU memory-write operation is
exposed. Unsupported logcfg options are rejected, not executed. The importer
supports `paramname`, one-byte `paramid`, arithmetic `scalingrpn` (`x`, numeric
constants, `+ - * /`) and validated `priority`. Priority is informational here:
all selected PIDs are polled each cycle. Unspecified units are labeled `raw` or
`scaled`; the source channel name is preserved, and temperature curves or
ambiguous units are not guessed.

Recordings are kept in app-private storage rather than cache. **Recover /
export recordings** exports earlier sessions after an app restart; exporting
does not delete the recovery copy. Back these up before uninstalling or clearing
app data. Storage use grows with retained recordings. The current cycle may be
lost or incomplete after abrupt process/power loss; this is not a crash-proof
data recorder. Definitions/channel selections need reloading after Activity
recreation. Phone-specific USB, sleep, rotation, and permission behavior remain
part of connected qualification.

Android's [USB host guide](https://developer.android.com/develop/connectivity/usb/host)
describes host-mode enumeration and permission requirements.

## Reporting useful results

Open an Android preview issue and include:

- APK version or Git commit;
- phone or tablet model and Android version;
- interface model and whether Android granted USB permission;
- the exact screen and steps that led to the result;
- definition and profile versions, when relevant;
- whether the test was offline, adapter-only, or parked ignition-on;
- a screenshot or the smallest useful error text.

Remove usernames, full ECU identifiers, ROM files, definitions, Logger
profiles, captured vehicle logs, and private calibration data before posting.
