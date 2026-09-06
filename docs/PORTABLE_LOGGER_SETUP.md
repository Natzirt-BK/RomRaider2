# Portable channel setups

Implemented in **1.1.3 development source**. Public downloads remain 1.1.2.
The same shared Java 11 format is available through Android and the replacement
JavaFX desktop Logger. Legacy Swing and standalone Compose surfaces do not gain
separate exchange controls in this change.

The [desktop profile-integrity repair](DESKTOP_PROFILE_INTEGRITY.md) prepares
that integration by preserving UTF-8 units, escaped XML attributes, captured
protocols and immutable snapshots, and protecting existing profiles during saves.

## Desktop workflow

1. Load a logger definition and disconnect the Logger. Wait for pending connection
   or recording commands to finish.
2. Use **File → Export channel setup…** in the Logger window, review the ordered
   IDs/units, and choose a `.rr2logger` destination.
3. On Android or another compatible desktop installation, load the exact same
   definition and protocol, then use **Import channel setup** (on desktop, under
   File). Review the replacement before continuing.

Desktop import replaces all current selected channels, including any external
selections, with the reviewed list. It does not append to the existing list.
New external selections are not supported by this portable format. An empty
selection list explicitly clears all channels.

The desktop captures at most 32 MiB of definition bytes at load time and parses
those exact bytes. Export fingerprints that loaded snapshot, even if the original
file has since changed or been removed. Reload the definition to adopt new file
contents; reloading invalidates pending transfers. Snapshot revisions also reject
intervening channel/unit edits, protocol/path changes, closed runtimes and active
or pending connection commands.

Changing protocol clears the preceding protocol's selected IDs and ignores a
saved XML profile explicitly labeled for another protocol. A definition must
also be supported by the shared portable catalog reader; runtime-only DimeMod
entries and unresolved desktop ECU-specific entries cannot be silently remapped
or dropped to make a transfer succeed.

The complete ordered replacement is validated before registration changes. A
failed conversion/registration operation attempts to restore prior units and
selection order. If restoration itself fails, connection and further transfers
are inhibited until the Logger is closed and reopened; the last usable recovery
profile is not overwritten by that failed state. No transport is started by an
import. Existing startup auto-connect preferences are not changed or transferred.

Successful imports save an app-owned XML recovery profile and select it for the
next desktop startup, without overwriting the previously configured user profile.
Profile/settings persistence is separate from in-memory application. A save
failure is reported as an applied session selection with failed persistence,
not as a completely successful save or a rolled-back import. Export a copy before
closing in that case. Parsing, export serialization/writing and accepted import
application run on a dedicated worker; the runtime serializes configuration and
connection operations while applying a setup.

Desktop exports serialize before opening output and use a synced temporary file
plus required atomic replacement. CSV/XML/ROM suffixes, directories and symbolic
link targets are rejected; extensionless names receive `.rr2logger`. There is no
non-atomic fallback. Cancellation prevents pending work but cannot undo a
replacement that has already committed.

## XML profile ordering

New desktop XML profiles include an optional `rr2-order` attribute on selected
items. The desktop and shared Android reader use it to preserve a single order
across parameter, switch and external sections. Duplicate, incomplete, gapped or
unselected ranks reject the profile. Profiles without the extension keep document
encounter order. Older applications may ignore the extension and retain their
own ordering behavior; the `.rr2logger` exchange format remains version 1.

The replacement desktop runtime registers CSV channels in that selected order
when restoring a profile. Polling uses stable channel IDs, not display names;
pending adds/removes now obey the most recent selection intent. Deselecting a
channel before connection cannot leave its earlier addition queued for polling.

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

Real phone document-provider behavior and hardware acceptance remain open
qualification items. Transfer acceptance does not qualify a vehicle/protocol.

Local qualification: 54 shared transfer assertions, 10 shared profile-order
cases, 11 focused core source/order/query tests, 47 Android unit tests, debug
and diagnostic APK/lint checks, automation APK/lint checks and the full isolated
emulator lifecycle suite passed. Full Ant/Linux checks and desktop regression
checks passed (166 JavaFX, including 11 native transfer/runtime cases, and 35
Compose), along with shared-core checks and Linux JavaFX staging. Local
same-key installation checks used the same version twice; hosted regression
also exercises increasing version codes. No public release was replaced.

Desktop integration adds exact loaded-byte capture, cross-category order and
query-queue tests plus native runtime/transfer tests covering round trips,
runtime recreation, changed/deleted definitions, stale/failed/cancelled work,
empty selections, rollback and rollback failure, protected destinations and
persistence failure reporting. The Android lifecycle suite also exercises XML
cross-category ordering and duplicate-order rejection with Android's parser.
