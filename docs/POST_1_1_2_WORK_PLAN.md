# Work after 1.1.2

The gauge/editor release does not complete the remaining analysis and mobile
backlog. These are software tasks that can progress without a vehicle. New
application changes belong to the next shared numeric patch version, not silent
replacement of an already published 1.1.2 binary.

## Reusable saved-log analysis setups

Implemented in 1.1.3 development source; see [the user guide](FUEL_LOG_ANALYSIS.md#reusable-setups-113-development-source).
Fresh qualification is required before publishing replacement packages. Sample
indices are deliberately excluded rather than reused across different logs.

The implementation uses the existing JavaFX MAF/Injector panes, keeping the calculations
read-only. Save the analysis kind, channel labels and units, bin width, filter
definitions and explicit injector assumptions in a bounded, versioned document.
Do not store a source CSV, private path, ROM image or a claim that the setup is
safe for another vehicle.

On import, show a review state. Resolve columns by exact identity; ambiguous or
missing headers need explicit remapping. Clear unit confirmation and old results.
Do not silently carry sample indices into a different-length log or let a late
worker publish results from the preceding setup. Failed imports leave the current
setup unchanged; cancelled/failed exports must not overwrite an existing file.

Tests should cover round trips, duplicate headers, changed units, malformed and
oversized files, unknown schema versions, out-of-range samples, stale background
results, dirty-state preservation and the small-window controls. Successful setup
loading does not authorize transferring calculated corrections into a ROM.

## Mobile calculated-channel parity

The definition reader already retains dependency IDs, but selected parameters
currently require concrete addresses and the expression evaluator accepts only
the raw variable `x`. Supporting calculated channels therefore needs a compiled
measurement plan, not invented addresses or an Android-only formula shortcut.

- Resolve a bounded dependency graph and reject cycles, unknown IDs, unsupported
  targets and conversions before starting requests. Add only required leaf
  channels to the existing deduplicated read-only query plan.
- Preserve user-selected output order. Hidden dependencies must not unexpectedly
  appear in the dashboard or exported CSV.
- Honor explicit dependency-unit syntax such as `[P21:ms]`. Bare dependency IDs
  require a documented conversion-selection rule and comparisons with desktop
  behavior; changing display units must not silently change the intended formula.
- Evaluate only a complete current cycle. Missing/nonfinite inputs and invalid
  division produce unavailable readings, never zero or a previous-cycle value.
- Check the existing P200 engine-load and P201 injector-duty-cycle definitions
  against the real desktop converter using synthetic inputs, multiple units,
  zero RPM, missing data and profile import/restore. Preserve CSV compatibility.

Dynamic DimeMod discovery is separate: compare version/feature responses and
address structures with the retained desktop implementation before enabling any
runtime-derived channel. Do not hardcode the owner's RAM addresses. External
serial AEM input remains a distinct transport/lifecycle project.

## Then

Continue synchronized analysis filters, reviewed fitting/interpolation, log-to-map
tracing and binned/run-comparison views. Portable logger-setup export/import and
bounded large-log review follow. Background recording needs its own Android
service/USB/notification and failure-recovery design; switching to GAUGES is not
background recording.

Forester/EVO/OpenPort in-car acceptance, phone document-provider behavior and
physical Windows/Mac/Deck tests remain supervised tasks. No production ECU
writing or live tuning is enabled by completion of this plan.
