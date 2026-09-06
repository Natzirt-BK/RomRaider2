# MAF and injector saved-log analysis

The JavaFX desktop Logger now exposes **MAF** and **Injector** tabs alongside
Log Analysis. This is the first read-only migration of the legacy tools, not
full MAF/injector workflow parity. This feature is part of version 1.1.2.

## Using a saved log

1. Open a CSV through either tab or the existing Log Analysis command. All three
   workspaces share the latest successfully loaded dataset. Opening a log from
   MAF or Injector keeps that workspace selected.
2. Explicitly map the required channels. Header labels and units are shown;
   channels are not guessed and values are not converted between units.
3. Choose a sample range (one-based, both ends inclusive), a positive bin width,
   and up to three inclusive channel filters. Filters are combined with AND.
4. Confirm the units and assumptions, then select **Analyze saved log**.
5. Inspect bin counts, mean, minimum and maximum, or the bin-average chart.
   **Copy results** copies a labeled TSV summary; it does not write the CSV or
   change a calibration table.

Analysis runs on a background worker. Editing an input clears old results and
cancels outstanding work. Replacing the CSV clears mappings, filters and unit
confirmation. Closing the window stops its analysis workers. Late calculations
cannot publish into a replacement log or a closed window.

## Calculations and limits

- MAF uses the inherited `MafUpdateHandler` calculation: A/F Learning (%) plus
  A/F Correction (%), grouped by MAF voltage (V). These are observed trim
  statistics, not an automatically generated replacement MAF scale.
- Injector uses the inherited `InjectorUpdateHandler` projection:
  `load(g/rev) / 2 / stoichiometric AFR * 1000 / fuel density(g/L)`, grouped by
  pulse width (ms). The divisor of two assumes the legacy four-cylinder model.
  The result is estimated fuel volume per combustion event, not measured flow,
  a fitted injector scaling, or a latency correction. The displayed 14.7 and
  732 defaults are legacy examples; no fuel properties are detected.
- Required or enabled-filter values that are missing/nonfinite are rejected.
  Negative axes and nonpositive injector pulse widths/fuel estimates are also
  rejected. A legitimate zero MAF trim is retained. Accepted, filtered and
  invalid counts are shown separately.
- Bins are anchored at zero with exclusive upper bounds. One floating-point
  rounding ULP is snapped at decimal boundaries. At most 2,000 occupied bins
  are allowed; overly fine binning fails with a request to widen the bins or
  narrow the range. No empty bins are fabricated.
- There are **no automatic closed-loop, AFR, temperature, transient, or tip-in
  filters**. Users must select appropriate conditions and understand the logged
  channel units/state encoding. Three arbitrary numeric filters do not replace
  the full legacy filter pipeline. Sample ranges are independent of the
  existing Log Analysis cursor/range. In 1.1.3 development source, MAF and Injector
  conditions can optionally be linked as described below.

## Still deferred

Live capture, full legacy operating-condition filtering, MAF interpolation, injector
regression/scaling/latency fitting, and explicit
reviewed transfer into ROM tables remain follow-ups. Neither pane can connect
to a vehicle, alter ROM bytes, save over the source CSV, or execute ECU writes.
Android and the other desktop shells are unchanged by this JavaFX addition.

Synthetic regression tests check inherited arithmetic, finite/missing handling,
range/filter boundaries, bin limits, unit-confirmation gating, stale-result
invalidation, replacement/close behavior, and a scrollable small-window form.
They are not real-vehicle or real-log tuning qualification.

## Reusable setups (1.1.3 development source)

The published 1.1.2 packages do not include this follow-up. In JavaFX, open a CSV
and use **Save analysis setup…** or **Load analysis setup…** above the MAF/Injector
workspace. Choose an `.rr2analysis` file. Saving requires valid, distinct channel
mappings, finite/ordered filter limits and positive numeric settings. It does not
run analysis or certify that the selected operating conditions are appropriate.

The bounded UTF-8 properties document contains its schema version, analysis kind,
exact channel labels/units, bin width, up to three filters, stoichiometric AFR and
fuel density. It deliberately excludes CSV contents, source paths, ROM data,
sample indices and unit confirmation. Channel names themselves can contain
personal information; inspect a setup before sharing it.

Import replaces inputs only after the whole document validates and the MAF or
Injector kind matches the current tab. Exact label/unit matches are restored;
duplicate or missing headers remain unresolved, including filters. Filter limits
are retained so a missing filter cannot silently disappear. Select the intended
channel or explicitly clear its limits. All imported inputs need review, unit
confirmation is cleared and the sample range visibly resets to the entire
current log. Choose the appropriate range before analyzing.

File work is asynchronous. A late import cannot replace newer edits, a different
log or a closed pane. Failed imports retain current mappings and results. Exports
write a synchronized temporary sibling and require atomic replacement; unsupported
atomic replacement fails without a non-atomic fallback. CSV/ROM extensions,
linked files and directory destinations are rejected. Native chooser cancellation
does not request an export. The panes never save over the captured CSV or apply
results to a ROM.

Local qualification: full Ant tests/Linux compilation, 79 display-enabled JavaFX
tests and 35 Compose tests pass. The new coverage includes six setup-store tests
and six JavaFX setup tests: both file kinds, strict parsing, duplicate/changed
channel identities, failed-file preservation, unresolved filters, range reset,
wrong-kind imports and stale analysis/import callbacks. Android unit tests, lint,
both 1.1.3 APK versions/notices and portable checks pass after the shared version
bump. Native file-chooser/provider failure paths and physical platform behavior
still need separate acceptance; these results do not qualify vehicle tuning.

## Linked MAF / Injector conditions (1.1.3 development source)

Both tabs initially keep independent ranges and filters. After loading a CSV,
select **Link MAF / Injector range and filters…** in the tab whose conditions
you want to use. The native review dialog lists its inclusive sample range and
numeric filters, with one-based CSV column numbers to distinguish duplicate
headers. Approving replaces the other tab's range and filters; cancelling changes
neither setup. Invalid ranges, unresolved filters, nonfinite/reversed limits and
channels from another dataset are rejected before review. Changes made to either
setup while the dialog is open invalidate that approval.

While linked, range/filter edits in either tab update both immediately. Incomplete
edits are mirrored too, so both tabs visibly reject the same invalid draft instead
of using different last-valid conditions. Every condition change cancels pending
calculations, clears results/copy output and clears each tab's unit confirmation.
Confirm the units separately before running each analysis again. The same range
and numeric filters do **not** guarantee identical accepted counts: MAF and
Injector require different measurements, with their own invalid-value rules.

Channel mappings, bin widths, stoichiometric AFR and fuel density remain
independent. The Log Analysis cursor, playback, statistics and charts are not
linked by this feature. Changing a fuel assumption clears that pane's own
confirmation without changing the other pane.
No operating conditions are inferred, and no analysis
result is applied to a ROM or sent to a vehicle.

Unchecking either box disconnects without reverting the current conditions.
Successfully importing an analysis setup, replacing either dataset or closing a
pane also disconnects. A failed/wrong-kind import leaves the link and inputs
unchanged; a queued import cannot overwrite a newly reviewed link. Links are
window-local and are not saved in `.rr2analysis` files. They require the exact same
loaded dataset instance, not merely matching filenames, row counts or headers.

Native JavaFX tests cover confirmation/cancellation, both update directions,
duplicate-column identity, malformed conditions, dataset replacement, close,
setup import, cancellation of both analysis workers, stale setup callbacks and
matching synthetic filter counts including a nonfinite filter sample.

Local qualification for this follow-up: all 90 display-enabled JavaFX tests and
35 Compose tests pass, with no skips in those suites. Portable checks and Linux
staging pass; Ant tests/Linux compilation pass with the existing optional corpus
skips. The nine new link tests include the actual native confirmation dialog.
The source version remains 1.1.3; public 1.1.2 downloads are unchanged.
