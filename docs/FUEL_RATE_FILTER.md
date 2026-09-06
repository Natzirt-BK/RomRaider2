# Recorded-time transient filtering — 1.1.3 development source

The JavaFX MAF and Injector saved-log workspaces include an optional **Transient
rate filter**. It is off by default, supplies no tuning limits, and does not
infer channel mappings or time units. Public 1.1.2 downloads are unchanged.

Expand the control and explicitly choose a numeric signal and the CSV's
recognized time column. Enter seconds per recorded time unit (`1` for seconds,
`0.001` for milliseconds), a maximum absolute signal change per second, and a
maximum allowed adjacent-sample gap in seconds. Review these together with the
other mappings and conditions before confirming units and running analysis.
For MAF voltage, the rate unit is V/s; other signals retain their own logged
units per second. No conversion of the signal itself is inferred.

For each selected row after the first:

```
elapsed seconds = (current recorded time − previous recorded time) × time scale
absolute rate   = |current signal − previous signal| / elapsed seconds
```

The maximum rate and gap are inclusive. The rate limit may be zero; the time
scale and maximum gap must be finite and positive. An enabled but incomplete
condition blocks analysis, setup export and link activation—it never silently
turns itself off.

## Adjacency, gaps and counts

- The first row inside the selected range has no predecessor and is counted as
  invalid/unavailable for this condition. The filter never reads a row outside
  that range to initialize the derivative.
- The predecessor is always the immediately preceding recorded row, including
  a row excluded by another filter or an invalid fuel projection. It is not the
  last accepted row. This avoids bridging over rejected samples.
- Missing/nonfinite signal or time in either row, duplicate or reversed
  timestamps, gaps larger than the chosen maximum, and nonfinite arithmetic
  produce an unavailable rate. They are never replaced by zero or interpolated.
  A missing signal also invalidates the following pair; a long gap rejects the
  current pair but does not prevent the next valid adjacent pair being used.
- An otherwise valid rate above the limit contributes to the filtered count.
  Unavailable required measurements take precedence over numeric exclusions and
  contribute to the invalid count, as with the existing analysis filters.
- Recorded timestamps are used as stored, not wall-clock time, sample indices,
  assumed sampling frequency, or an automatically selected time scale.

The immutable rate condition is captured with the analysis request. Curve fits
replay precisely the same accepted raw projections, including the same range
boundary and rate exclusions. Editing any rate field or replacing the dataset
invalidates analysis, curve results and any pending MAF transfer review.

## Linked conditions and reusable setups

An explicitly enabled MAF/Injector condition link also shares the rate filter.
The confirmation shows its signal/time column identities, time scale, maximum
rate and maximum gap. Later edits mirror incomplete drafts as well as valid
values, and clear both tabs' unit confirmations and results. Injector and MAF
projections may still accept different samples. Setup import, dataset replacement
and closing a pane retain their existing disconnection behavior.

`.rr2analysis` exports with an enabled rate condition use strict schema version 2.
They store signal/time label-and-unit identities and the three numeric limits,
not timestamps, captured data, file paths, sample ranges or approval. Exports
without it remain version 1. Version 1 imports explicitly clear the rate filter;
old readers reject version 2 instead of silently dropping its condition.

Missing or ambiguous imported signal/time identities remain unresolved, with the
filter enabled and its limits retained for review. They must be remapped or the
filter explicitly disabled. Duplicate/unknown properties, malformed values,
oversize files and non-atomic replacement failures retain the existing strict
setup-file behavior. Disabled, incomplete session-local rate drafts are not saved.

## Relationship to the legacy workflow

The legacy MAF/Injector handlers inspect optional CL/OL state, AFR, RPM, mass
flow, intake/coolant temperature, MAF-voltage change and tip-in data. Their
MAF-voltage derivative uses handler wall-clock timing and updates its predecessor
only after earlier checks pass. Missing optional inputs can bypass checks.

This saved-log filter deliberately uses explicit recorded-time adjacency and
rejects missing required rate inputs. It is not a claim of byte-for-byte legacy
filter parity, an automatic closed-loop/transient classifier or verified tuning
conditions. Named operating-condition controls and the broader legacy filter
workflow remain separate backlog items. No vehicle connection, ECU write or
automatic ROM change is introduced.

Synthetic checks cover both analysis projections, inclusive bounds, range starts,
missing values, timestamp discontinuities, filtered predecessors, deterministic
fit replay, setup versions and identity matching. Native JavaFX tests cover
explicit inputs, import/reset, unresolved time channels, linked invalid drafts,
actual analysis counts, curve invalidation and dataset replacement. These are
software checks, not vehicle or calibration acceptance.

Local qualification on September 6, 2026: all nine rate-engine tests and eight
setup-store tests pass, including two new version/time-identity checks. All 110
display-enabled JavaFX tests and 35 Compose tests pass with no skips in those
suites; six new JavaFX tests include an actual 1,000 × 640 expanded-controls
render. The full Ant suite/Linux compilation (existing optional corpus skips),
portable checks and Linux staging also pass. Release downloads remain 1.1.2.
