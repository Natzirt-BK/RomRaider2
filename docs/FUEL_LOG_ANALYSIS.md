# MAF and injector saved-log analysis

The JavaFX desktop Logger now exposes **MAF** and **Injector** tabs alongside
Log Analysis. This is the first read-only migration of the legacy tools, not
full MAF/injector workflow parity. It is not yet in the public 1.1.1 release.

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
  existing Log Analysis cursor/range.

## Still deferred

Live capture, synchronized legacy filtering, MAF interpolation, injector
regression/scaling/latency fitting, portable saved analysis setups, and explicit
reviewed transfer into ROM tables remain follow-ups. Neither pane can connect
to a vehicle, alter ROM bytes, save over the source CSV, or execute ECU writes.
Android and the other desktop shells are unchanged by this JavaFX addition.

Synthetic regression tests check inherited arithmetic, finite/missing handling,
range/filter boundaries, bin limits, unit-confirmation gating, stale-result
invalidation, replacement/close behavior, and a scrollable small-window form.
They are not real-vehicle or real-log tuning qualification.
