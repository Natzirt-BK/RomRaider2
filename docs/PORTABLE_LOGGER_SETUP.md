# Portable channel setups

Implemented in **1.1.3 development source**. Public downloads remain 1.1.2.
The shared Java 11 codec is platform-neutral; import/export controls are currently
available in Android only. Desktop UI integration remains a follow-up, not a
completed desktop-to-phone workflow.

The [desktop profile-integrity repair](DESKTOP_PROFILE_INTEGRITY.md) prepares
that integration by preserving UTF-8 units, escaped XML attributes, captured
protocols and immutable snapshots, and protecting existing profiles during saves.

## Android workflow

1. Stop live or simulated logging and wait for the recording to finish.
2. Load your logger definition and choose channels or import a traditional XML
   profile. Select **Export channel setup**, review what is included, then choose
   a new `.rr2logger` file in the document picker.
3. On another compatible Android installation, load the **exact same definition
   file** and choose the same protocol. Select **Import channel setup**.
4. Review the ordered channel IDs and units, then select **Use setup**. Cancel
   leaves the current profile unchanged. An explicitly empty setup clears all
   channel selections; it does not select the entire catalog.

The accepted selection is saved in the existing app-private recovery snapshot
and restored on restart. Import never starts logging or prepares an adapter.
The gauge theme is unchanged. ECU-specific addresses, module compatibility and
calculated dependencies still go through the normal logger-start validation.
Import acceptance is not proof that the setup is appropriate for a vehicle.

## Included and excluded

The file contains SSM or MUT2, the SHA-256 fingerprint of the original definition
bytes, and ordered channel IDs with explicit conversion-unit labels. Legacy
empty-unit defaults are resolved against the current catalog during export.
Missing IDs, unsupported external-profile entries, duplicate selections, missing
units and ambiguous unit labels reject the transfer rather than silently dropping
channels or choosing another conversion. Unit labels must match exactly; catalogs
with multiple case-insensitive matches are rejected because the current portable
runtime cannot distinguish those conversions reliably.

No definition contents, ROM bytes, CSV recordings, private paths, ECU identity,
USB permissions, port names, automatic connection flags or gauge settings are
included. The fingerprint requires byte-for-byte equality—even whitespace changes
to a definition require a new export. It is an identity check, **not a signature,
encryption or vehicle-compatibility certificate**. Channel IDs and unit labels
remain readable data despite their Base64 encoding.

This is a separate format, not a replacement for RomRaider XML profiles or
`.rr2analysis` fuel-analysis setups. Existing profile import and app-private
definition/profile restoration remain available.

## Limits and failure handling

- Version 1, strict UTF-8 envelope; LF and CRLF accepted. Required terminal newline.
- At most 128 KiB per file, 256 unique channel IDs, and 128 UTF-16 code units per
  ID/unit field. Control/format characters and malformed Unicode are rejected.
- Unknown, duplicate, missing, noncanonical or unsupported-version fields reject
  the entire file. Definition hashing is bounded to the existing 32 MiB limit.
- Parsing/hashing runs off the UI thread. Configuration revisions and import
  generations prevent late work or stale review dialogs from replacing newer
  selections. Failed/cancelled imports leave the current profile intact.
- Transfer entry points refuse to interrupt an active live or simulated session.
  A review is checked again before applying or launching the export picker.
- Export serializes a frozen snapshot before opening the destination. Activity
  recreation discards a pending export rather than writing a different setup.
  Provider writes are not assumed atomic: choose a new file. A write/close failure
  reports that the destination may be incomplete; retry to a new file. No source
  definition, profile or recording is rewritten or deleted.

## Automated checks

Shared-core checks cover canonical round trips, ordered/empty selections,
Unicode units, explicit default units, SSM/MUT2, exact fingerprint/protocol/unit
matching, ambiguous units, immutable output, malformed and oversized input,
unknown/duplicate fields, and interruption. Android instrumentation exercises
the actual Activity's review/cancel/apply flow, failed/mismatched import, stale
worker/dialog rejection, active simulated-logger guard, stream export, empty
selection and persistence across restart. Tests use synthetic definitions and an
isolated automation package, never a vehicle or a private ROM.

Real phone document-provider behavior, desktop exchange controls and hardware
acceptance remain open qualification items.

Local qualification: 54 shared transfer assertions, 47 Android unit tests, debug
and diagnostic APK/lint checks, automation APK/lint checks and the full isolated
emulator lifecycle suite passed. Desktop regression checks passed (152 JavaFX,
35 Compose), including an enlarged-font compact-window comparison test. Local
same-key installation checks used the same version twice; hosted regression
also exercises increasing version codes. No public release was replaced.
