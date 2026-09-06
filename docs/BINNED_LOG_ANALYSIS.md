# Binned saved-log analysis — 1.1.3 development source

JavaFX Log Analysis's **Binned analysis** tab groups original CSV readings into
one-axis bins or a two-axis grid. It provides a bin table, mean/count heatmap,
2D mean plot and isometric 3D bin-mean plot. Public 1.1.2 packages are unchanged.

## Choose the analysis explicitly

Map X and the value channel. For a grid, enable **Two-axis grid (3D)** and map Y.
Enter each axis's origin and positive width in that CSV channel's units. Origins
start at zero; widths and channels are deliberately not guessed. A time column
can be chosen explicitly and retains its recorded units. In one-axis mode the
unused Y controls have no effect on validation or accepted samples.

The analysis uses Log Analysis's **applied sample range**, not a MAF/Injector
accepted-row subset. Fuel filters, projections and assumptions are not applied.
A pending shared-range draft blocks calculation until Apply. Applying a range,
changing a mapping, origin, width, mode or minimum count clears old results and
cancels stale work. Playback seeks do not repeatedly recalculate the aggregate.

Select **Calculate bins** to process every row in that range. Rows missing a
finite X, enabled Y or value reading count as missing/nonfinite; they are not
zeroes. Invalid bin geometry rejects the calculation with an actionable error,
rather than silently dropping finite readings. All-missing input produces an
empty result with its invalid count, not a synthetic zero-valued grid.

## Bin boundaries and statistics

Axis bin index `k` represents `[origin + k × width, origin + (k + 1) × width)`.
Negative indices are supported. The quotient receives one upward floating-point
ULP before flooring, consistent with the existing fuel-bin decimal-boundary
convention: a value one quotient ULP below a boundary may enter the upper bin.
This prevents common decimal division rounding such as `0.3 / 0.1` from creating
an unexpected lower-bin assignment. It is an explicit tolerance, not exact
decimal arithmetic. Displayed edges use fused multiply-add arithmetic.

Each occupied bin reports its contributing sample count and original-value
mean/minimum/maximum. Means weight **samples equally**, not other bin averages.
Compensated accumulation uses a wider decimal accumulator if an intermediate
sum would overflow. It avoids publishing infinite means for finite inputs; this
is not a claim of exact arbitrary-precision statistics for every operation.

The grid covers the complete observed bin-index rectangle, including gaps.
Empty cells retain count zero and unavailable statistics. **Minimum count**
starts at one. A populated bin below the chosen threshold keeps its true count
but hides mean/min/max in the views and copied table; it is labeled **Low count**.
This threshold is a user-selected display rule, not a confidence interval or a
claim that the remaining bins are safe for tuning.

## Views and copying

- **Bin table:** explicit X/Y lower and exclusive upper edges, count,
  mean/min/max and coverage state. Unused Y columns are unavailable in 2D mode.
- **Mean heatmap:** each cell shows `mean / count`. Empty or low-count cells have
  no mean color. A numeric legend identifies the blue-to-amber mean scale, which
  is relative to this result and must not be compared as an absolute color scale
  between runs.
- **2D plot:** qualified mean values at bin centers, with lines only between
  adjacent qualified bins. Empty or low-count bins break the line.
- **3D plot:** isometric stems/points at X/Y bin centers, with height representing
  mean value. The baseline is the minimum qualified mean, not necessarily zero;
  constant means use a centered height. No surface is filled or interpolated
  across gaps. Bin-center ranges and mean limits are labeled.

Use **Hide axes** to reclaim plot space without changing mappings or results.
The table and heatmap use compact, centered values. Screen values use nine
significant digits. **Copy bin table** produces a labeled TSV with underlying
double values, fixed canonical columns and the same count-threshold omissions;
visual column reordering does not scramble the copied headers. Header whitespace
is normalized in the metadata line. Copying uses the clipboard only and does not
write or alter the original RomRaider CSV capture.

## Bounds and lifetimes

The background calculation permits at most **5,000,000 selected rows**, **256
bin slots per axis** and **8,192 total cells including gaps**. Bin indices must
remain within ±1,000,000,000 and their edges must remain finite and distinct.
Oversized or unrepresentable geometry asks for a narrower range or revised
origin/width; no preview subset is published as a complete aggregate. Memory
depends on the bounded bins rather than retaining another raw-sample array.

The worker checks cancellation during aggregation and grid construction.
Generation checks discard both cancelled jobs and already-completed callbacks
whose inputs changed before publication. Closing/replacing the log workspace
disposes its worker and views. This bounds the new aggregation, not the existing
general CSV parser or every whole-log analysis operation.

No ROM table, source CSV, live session or ECU is modified. These are descriptive
saved-log views, not automatically generated calibration corrections. Run
comparisons, general large-log processing and portable setup transfer remain
separate follow-ups.

Synthetic tests cover sample weighting, gaps, negative/origin/decimal boundaries,
missing and unused channels, selected ranges, immutable output, grid limits,
extreme finite means and cancellation. Native checks cover both modes, heatmap
gaps, minimum counts, canonical clipboard ordering, stale jobs, range linkage,
worker shutdown and actual compact heatmap/3D renders. These are software tests,
not vehicle or calibration acceptance.

Local qualification passed eight core aggregation tests, the full Ant unit
suite (optional private-corpus cases remain skipped), Linux core packaging,
144 JavaFX tests (six new binned-view cases), 35 Compose tests, portable-core
checks and Linux JavaFX staging. The final 1,000 × 640 heatmap and 3D renders
were inspected, including the compact axes-hidden layout.
