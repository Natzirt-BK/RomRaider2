# Saved-log curve review — 1.1.3 development source

The JavaFX MAF and Injector workspaces now include **Curve review**. Run the
normal saved-log analysis first, after reviewing mappings, units, sample range,
filters and injector assumptions. This source feature is not in public 1.1.2.
Neither calculation action changes a ROM, captured CSV or vehicle. A completed
MAF curve can now enter a separate [reviewed offline table transfer](REVIEWED_MAF_TRANSFER.md);
applying that proposal requires explicit acknowledgement and confirmation.

## Two different operations

**Interpolate observed bins** draws piecewise straight lines between adjacent
occupied bins' observed mean input and mean output. It does not substitute the
bin midpoint for the measured mean input. Empty-bin gaps are unavailable, and
the chart splits at those gaps even when a gap is narrower than the plotting
grid. Isolated observed points remain visible. There is no extrapolation beyond
the first/last observed bin mean.

**Fit accepted raw samples** performs ordinary least squares on the exact raw
projections accepted by the preceding analysis, not on bin centers or averages.
Each accepted sample has equal weight. MAF supports polynomial degrees 1–20
(default 3); Injector always uses degree 1. A polynomial can cross unsampled
intervals and overfit, especially at high degree. A continuous fitted line is a
model, not evidence of measured coverage throughout that interval. Evaluations
outside the accepted raw input span are unavailable.

The legacy desktop action called “interpolate” uses `Polyfit`; the new workspace
distinguishes polynomial fitting from piecewise interpolation explicitly. It
retains the existing MAF trim projection and four-cylinder injector fuel-volume
projection described in [the analysis guide](FUEL_LOG_ANALYSIS.md). It does not
replace the legacy operating-condition filter pipeline or establish tuning parity.

## Evaluation and statistics

Optionally enter up to 512 finite evaluation inputs separated by commas or
whitespace. Their order and duplicates are preserved. Leave the field blank for
the 201-point review grid (one point for a single-input interpolation). Inputs
are limited to 8,192 characters. The table labels unsupported results
**Unavailable**; it never replaces them with zero. **Copy curve review** copies
the method, diagnostics, units and values as text/TSV, not a ROM-ready table.

Fits report raw sample count, RMSE, residual standard deviation and R². With
`p = degree + 1`, RMSE uses `sqrt(SSE/n)` and residual SD uses
`sqrt(SSE/(n-p))`. Residual SD is unavailable when there are no residual degrees
of freedom, and R² is unavailable for constant output. These are descriptive fit
statistics, not confidence limits or proof of a valid physical model. See the
[NIST least-squares definition and residual standard deviation](https://www.itl.nist.gov/div898/handbook/pmd/section4/pmd431.htm).

Injector additionally reports apparent flow `slope × 60000` in cc/min and the
fitted zero-fuel intercept `-intercept/slope` in ms, only for a finite positive
slope and finite derived values. These follow the legacy line-fit calculation.
The intercept can lie outside measured input coverage; reporting that line
parameter does not enable extrapolated curve evaluations. It is **not verified
injector latency**, a battery-voltage compensation curve, or measured injector
flow. Fuel assumptions and the load-derived projection can bias both estimates.
No result is transferred into a calibration automatically.

## Numerical and lifecycle limits

The engine scales input/output and incrementally builds a small triangular QR
factor using Givens rotations. Additional fitting storage is bounded by degree,
not the number of samples; the already-loaded immutable dataset is replayed with
the captured range/filters. The orthogonal-rotation method follows the approach
described by [LAPACK's plane-rotation documentation](https://www.netlib.org/lapack/explore-html/da/d81/group__rot__aux__grp.html),
but this is an independent Java implementation, not a claim of LAPACK certification.

Insufficient samples, zero input span, nonfinite results and poorly resolved
designs fail explicitly. The numerical-rank guard compares QR diagonal magnitudes
at a relative threshold of `1e-10`; it is not a condition-number estimate.
`accepted samples × (degree + 1)²` must not exceed 100 million work units. Reduce
degree or narrow the reviewed range if that bound is exceeded; samples are not
silently downsampled. Bin means also preserve constant extreme finite values and
avoid overflowing the difference between opposite-sign extreme outputs.

Curve work runs on its own cancellable worker. Changes to evaluation inputs or
degree clear the previous curve; changes to parent analysis inputs, linked
conditions, setup or dataset also clear its accepted-data source. Late callbacks
cannot repopulate a replaced or closed pane. Closing the parent shuts down the
curve worker. Curve degree/evaluation inputs are currently session-local and are
not included in `.rr2analysis` setup files.

Synthetic tests compare streamed QR against an independent Jama Householder
solver, plus known linear/polynomial results, single-bin raw fits, degree 20,
large numeric scales, repeated inputs, invalid rows, filter/range replay,
work limits, cancellation and synthetic injector projections. Native JavaFX
tests cover interpolation gaps, requested ordering, statistics, invalid review
inputs, stale jobs, parent invalidation and explicitly unverified injector labels.
These checks are software qualification, not vehicle or calibration acceptance.

Local qualification on September 6, 2026: all 12 new curve-engine tests pass,
along with the full Ant suite/Linux compilation (existing optional corpus skips).
All 97 display-enabled JavaFX tests and 35 Compose tests pass, with no skips in
those suites; portable checks and Linux staging pass. Seven new JavaFX curve
tests cover the native integration, including the 1,000 × 640 rendered workspace.
Public downloads remain 1.1.2 pending separate release qualification.
