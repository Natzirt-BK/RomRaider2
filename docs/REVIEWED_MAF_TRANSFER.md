# Reviewed MAF-table transfer — 1.1.3 development source

JavaFX can transfer a completed saved-log MAF correction curve into an explicitly
selected, open ROM table. This is an **offline editor operation**, not live tuning,
an ECU write, or approval to flash a calibration. Public 1.1.2 downloads do not
include this feature. Android and other desktop shells are unchanged.

## Workflow

1. Open the matching ROM in the Editor and open/select its MAF mass-flow table.
2. In Logger → MAF, review the saved CSV's mappings, units, sample range and
   operating conditions, then run analysis and choose a completed curve in
   [Curve review](FUEL_CURVE_REVIEW.md).
3. Select **Review MAF table transfer…**. The dialog names the target document
   and table and shows every target voltage, original flow, correction percent,
   requested flow and actual stored/rounded flow. The target is never chosen
   automatically by a name match.
4. Review all rows and acknowledge the target, coverage and stored values before
   selecting **Apply to open ROM**. Cancel is the default action.
5. Check the result in the Editor. One Undo restores the transfer; Redo reapplies
   it. No ROM file is automatically saved, source CSV overwritten, or vehicle
   connection opened. Curve actions are cleared after a successful transfer;
   fresh analysis is required before creating another proposal.

The correction uses `original flow × (1 + correction percent / 100)` at the
actual target-axis voltages, not values parsed from rounded display labels or
the chart's evaluation grid. Interpolation leaves empty-bin gaps and outside-span
inputs uncovered. Polynomial fits may cross unsampled intervals inside their
accepted input span; a covered prediction does not establish measurement coverage
or a safe calibration. Uncovered target cells remain unchanged and are counted
explicitly. No extrapolation, implicit zero correction or clipping is used.

## Limits and safeguards

- First supported target: a populated, unlocked 2D table with one matching
  voltage axis, 1–2,048 cells and a ROM buffer no larger than 32 MiB. Recognized
  axis units are `V`, `volt`, `volts`; mass-flow units are `g/s`, `g/sec`, `g/sec.`,
  `lb/min`, `lbs/min` (case-insensitive). The dimensionless percentage does not
  convert flow units. Units alone cannot prove the ROM or table is appropriate;
  the owner must review compatibility.
- Ordinary 1/2/4-byte integer or float storage is supported. Static axes must
  contain finite numeric voltages. Masked/coupled-layout tables and overlapping
  flow/axis storage are rejected. Nonidentity flow scaling requires an explicit
  inverse expression. The preview verifies original values can be restored by
  the existing writer, including refusing unsigned 32-bit values that its signed
  integer conversion cannot round-trip.
- Requested values must be finite, representable and nonnegative, with a positive
  correction multiplier. Stored values are encoded/decoded using the existing
  writer and current scaling. Out-of-range values are rejected instead of clamped.
  Small corrections that round to the same bytes remain unchanged; a proposal
  with no changed cells is refused.
- Before applying, the editor must still have the same open target and session
  revision and must not be saving/reloading it. The same completed analysis and
  curve generation must remain current. The proposal also checks the exact ROM
  buffer, all captured bytes, table/axis/cell identities and scaling state.
  Even an unrelated ROM-byte edit invalidates the proposal.
- The command runs on the editor's serialized JavaFX thread. It groups changes
  in one history transaction and checks the resulting bytes against the review.
  A setter/presentation failure restores captured bytes and clears that attempted
  history batch. Overlapping cell caches refresh even if a presentation observer
  throws. A history notification failure is logged without disguising an already
  committed edit as a failed edit or blocking other history observers.

These checks are software guards, not a concurrent-mutation API or an assurance
that an arbitrary definition's scaling is physically valid. A corrupt or replaced
ROM during execution requires fresh review; a replacement buffer is never
silently overwritten by rollback.

## Still separate

Injector transfer is not implemented. An apparent flow estimate or one fitted
zero-fuel intercept is not a battery-voltage-dependent latency curve. Full legacy
operating-condition filters, vehicle qualification, and production ECU writing
remain separate work. Keep a known-good original and use the established
definition/checksum review before considering any later supervised flash.

Synthetic tests exercise read-only preview, rounded/scaled/float storage, missing
coverage, overflow, stale bytes/caches/scales/cell identities, target rejection,
rollback, alias refresh, grouped undo/redo and failed history observers. Native
JavaFX tests cover explicit acknowledgement, cancellation, accepted transfer,
analysis/editor changes during the modal review, target-session lifetime and a
small-window review dialog. No owner ROM or vehicle is used by these tests.

Local qualification on September 6, 2026: 17 transfer-engine tests and seven new
display-enabled JavaFX tests pass. The full Ant suite/Linux compilation passes
(existing optional corpus skips), as do all 104 JavaFX and 35 Compose tests with
no skips in those suites, portable checks and Linux staging. Public downloads
remain 1.1.2 pending separate release qualification.
