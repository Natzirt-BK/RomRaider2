# Android ROM checksums

Implemented for the next update; **not included in the packaged 1.1.11 RC1**.

Open a ROM and load its matching RomRaider ECU definition. The editor reports
checksum status without changing the ROM. For supported Subaru checksum tables,
**Save Copy** corrects the checksums on a separate byte snapshot and verifies the
result before opening the output stream. Successful provider close publishes
the corrected saved state only if no newer edits have occurred.

## Supported scope

- Exact ROM size/internal-ID matching and complete, same-file definition inheritance.
- Subaru `Checksum Fix` switch tables, including multiple addressed blocks.
- Big-endian 32-bit sums with the desktop `0x5AA5A55A` target and wrapping
  arithmetic. Range starts are word-aligned; unaligned end markers follow the
  desktop loop's full-word read semantics, with bounds checked on the full read.
- Full-size images whose file addresses match definition addresses. Offset and
  converted-image layouts are not inferred.

Disabled checksum protection is preserved, not silently re-enabled. A disabled
block leaves the copy unchanged and requires desktop validation. Unsupported
schemes, external checksum managers and missing checksum metadata offer a
clearly identified review-copy export, not a claim of successful correction.
Malformed, overlapping or out-of-file checksum layouts block export before
opening the destination. Changing checksum range headers with the hex editor
also blocks correction until the ROM and definition are reloaded.

Checksum validation does **not** establish that a calibration is correct for a
vehicle or that a ROM is safe to flash. Android flashing is not added by this work.

## Automated verification

- Portable checks cover inherited metadata, multiple blocks, disabled and
  unsupported schemes, unsigned/wrapping arithmetic, out-of-range and overlapping
  layouts, changed range headers, idempotence and source preservation.
- A desktop compatibility test compares every byte against the existing
  `RomChecksum.calculateRomChecksum` result for 100 deterministic randomized ROMs.
  This compares the checksum algorithm, not desktop save-date stamping.
- Android save tests cover corrected output, close failures, concurrent edits
  and refusal to open a destination for invalid checksum metadata.
- The `rom-checksums` emulator phase checks editor status on open, save and edit,
  plus the missing-definition warning.

The portable suite, Android unit tests/lint/APK builds, desktop parity test and
the isolated emulator phase passed locally on September 13, 2026.
A locally retained stock Forester ROM also validated successfully; correction
matched the desktop algorithm byte for byte both unchanged and after an in-memory
test edit. The source file was unchanged and no ROM output was written.

Physical-phone/file-provider acceptance and review against the user's exact
current Forester ROM remain outstanding. No ROM is flashed by these tests.
