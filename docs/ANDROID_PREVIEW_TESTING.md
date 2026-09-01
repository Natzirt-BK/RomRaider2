# Android preview testing

The Android build is an experimental RomRaider2 preview for early feedback. It
is not a replacement for the desktop release yet. The APK is debug-signed and
is installed by sideloading, so Android or Play Protect may warn that it is an
unknown application. Its preview-only application ID keeps it separate from a
future signed release. Because debug signing keys can change between preview
builds, an update may require uninstalling an older preview first.

## What is ready to try

- Open a ROM, inspect bytes, make a bounded hexadecimal edit, reset it, and save
  a separate copy.
- Open traditional RomRaider wide-column CSV logs and RomRaider2 portable CSV
  logs.
- Import a RomRaider v370 Logger definition and an existing Logger profile.
- Run the clearly marked simulated Logger and save its CSV.
- Check whether Android detects an attached OpenPort 2.0 and grants USB
  permission.

The application contains no ECU flash or memory-write command. ROM editing only
changes an in-memory document and saves a new file through Android's document
picker.

## Connected Logger warning

The read-only Subaru SSM K-Line Logger is wired into the preview but has not
completed RC5 vehicle qualification. It is for careful, parked testing only:

- keep the vehicle stationary and do not operate the phone while driving;
- use a well-supported Subaru SSM K-Line vehicle and an OpenPort 2.0;
- start with ignition on and engine off;
- stop if the adapter, phone, or vehicle behaves unexpectedly;
- do not rely on the preview for safety-critical monitoring.

CAN, Mitsubishi MUT-II, transmission sessions, calculated parameters, serial
external sensors, ECU writing, and flashing are not available in this Android
preview.

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
