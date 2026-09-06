# Android interrupted-recording recovery

Implemented in 1.1.3 development source. Public 1.1.2 downloads are unchanged.
This is an offline export feature: it never reconnects an adapter or starts a
recording, and does not change the app-private original.

## Export behavior

Stop logging and allow adapter cleanup to finish, then choose a retained recording
and a CSV destination. The app validates and converts a private snapshot before
opening the destination stream. Healthy recordings export in the normal RomRaider
wide-column format: `Time (msec)`, channel labels/units and relative timestamps.
The system document picker may itself create an empty destination document;
RomRaider2 does not write it before validation and any required review.

If the final sample record lacks its terminating newline, the app offers a
**Review interrupted recording** dialog showing the completed sample-record count
and omitted byte count. Choose **Export recovered data** or **Cancel**. Recovery
never fills missing readings with zero or invents a completed final cycle. Even
a syntactically plausible last record without its newline is treated as unfinished.
Earlier completed records from the last cycle may appear with blank columns.

Completed records with invalid values, metadata changes, backwards timestamps,
malformed UTF-8 or broken CSV syntax fail validation instead of being skipped.
An unfinished quote or UTF-8 code point in the final uncommitted record cannot
prevent recovery of the valid prefix. Newlines inside quoted fields are not
mistaken for record boundaries. Labels containing line breaks remain unsupported
by the desktop-compatible output format.

Validation failure or review cancellation leaves existing destination contents
untouched by the app. A destination/provider failure after copying begins can
leave an incomplete destination, which the app reports. The original remains
available for retry. No atomic replacement guarantee is made for Android document
providers.

## Ownership and bounds

- A recovery job copies at most the captured source length into a private
  temporary file. Non-regular files/symlinks and observed source size, modification
  time or file-identity changes are rejected. This is intended for idle app-private
  spools, not a synchronization protocol for untrusted concurrent external writers.
- CSV framing and conversion stream through fixed-size buffers. A record is
  limited to 1,311,744 bytes; retained fields and channel counts also use the
  existing converter limits (65,536 characters per field and 4,096 channels).
  Completed records are converted from the frozen snapshot, never reread from the
  original during destination copying. Cancellation checks bound ongoing work.
- Preparation needs temporary disk space for the source snapshot and converted
  CSV. Failure to obtain that space stops export and preserves the original.
  Temporary copies are deleted after completion, cancellation or ordinary failure.
  A process kill can leave disposable files in Android's private cache; these are
  not the original recovery spools and are not automatically exported.
- Activity destruction cancels outstanding preparation and invalidates an open
  recovery review. An explicitly accepted destination copy may finish after the
  Activity closes, but cannot publish stale UI results into its replacement.
- Process death cannot recover bytes never written to storage. Recovery verifies
  complete sample records, not whole-cycle commit or sustained physical USB
  reliability. See [background recording qualification](ANDROID_BACKGROUND_RECORDING.md).

## Verification

Local qualification passed 190 portable recovery assertions, the existing shared
core checks and all 65 Android unit tests in debug and automation variants.
Debug, diagnostic and automation APK builds and lint passed (five pre-existing
lint warnings, no errors). The complete API 36 emulator lifecycle script passed;
both normal and recovered Android CSV fixtures also passed the production desktop
parser's exact time/value/unit checks. Local reinstall uses the same APK; hosted
CI separately checks an increasing-version, same-key upgrade.

The portable recovery suite covers clean LF/CRLF exports, every byte split of a
quoted/multiline/UTF-8 trailing record, malformed completed records, strict UTF-8,
field/record bounds, source preservation, immutable prepared output after source
replacement, destination failure, cancellation, closed handles, cleanup and
symlink rejection where the host permits creating the fixture.

Native Android automation exercises the actual Activity export path with isolated
app-private file destinations: clean export, document-picker cancellation,
tail-review cancellation/acceptance, validation before destination writing,
Activity recreation, dialog-window disposal, cancellation of queued preparation
and failed destination opening. The process-death fixture
also converts its surviving completed records. These are not third-party document
provider or physical-device acceptance tests.
