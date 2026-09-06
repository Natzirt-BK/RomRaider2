# Desktop selection and document commands

The JavaFX editor in the 1.1.2 source candidate adds these controls. These changes
are not in the public 1.1.1 package; macOS's Compose editor is unchanged by this
JavaFX UI pass.

## Selection math

Select cells in the table, then use **Multiply selection**, **± Fine** or
**± Coarse** in the inspector. With no explicit selection, the current cell is
used. Multiplication operates on scaled values: a factor of 1.05 means +5%,
0.95 means −5%, and 1 leaves values unchanged. Inputs use the system locale's
decimal separator. Negative and zero factors are allowed; this is calibration
math, not a vehicle-specific safe-range check.

Fine and coarse fields are optional positive step overrides for the open table.
Blank fields use the definition's increments; **Use definition steps** clears
both overrides. Definitions on disk are never changed by these controls.
The existing definition direction, integer rounding, storage clamping and
minimum-representable increment behavior still apply. Inspect the resulting
values; requested arithmetic is not always exactly representable in ROM storage.

Each operation validates the whole selection before starting, retains that
selection after refresh, and creates one Undo entry for changed cells. Invalid
or nonfinite inputs, out-of-bounds selections and nonfinite inverse conversions
are rejected before applying the operation. Normal locked-table and user-level
protections remain in place. The single-cell **Apply value** control remains
single-cell and is distinct from the selection commands.

## Reload and properties

**File → Reload saved ROM…** explicitly asks before discarding unsaved work.
It reads the saved file using the configured definitions, then replaces the
document only when loading succeeds and its in-memory bytes have not changed
since the request. Failed/cancelled loads retain the existing document. Edits
made while loading abort replacement and remain dirty. Save, another reload,
close and exit cannot race an unfinished reload. A successful reload clears
the replaced document's Undo history and open table views. It never writes the
source file. Recovered documents without a saved path must use Save As first.

**File → ROM Properties…** shows copyable, read-only metadata and the SHA-256
of the current in-memory image. Definition metadata is not proof of vehicle
identity, and SHA-256 is not an ECU-checksum validity result. Viewing properties
does not recalculate checksums or change any file.

## Verification

Synthetic core tests cover selection math, Undo/Redo, definition-step retention,
invalid inverses, reload success/failure, newer edits, conflicting operations and
observer cancellation. Native JavaFX tests cover selection retention, repeated
adjustments, default-step restoration, invalid input, locale parsing and
cancelling a dirty-document reload. No production ROM or ECU is modified by
these tests.
