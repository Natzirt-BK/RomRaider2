# Accepted fuel samples — 1.1.3 development source

JavaFX's MAF and Injector workspaces each have an **Accepted samples** tab.
Complete the saved-log analysis first, then choose **Inspect accepted samples**.
The viewer indexes the exact rows that contributed to that completed result:
its captured sample range, valid projection/bin coordinates, and all enabled
custom, named and recorded-time conditions. It does not rebuild the selection
from whichever controls happen to be visible later.

## Inspect original readings

The table shows original **1-based CSV sample numbers**, recorded time in the
CSV's own units, and the selected channel's original numeric reading. Sample
numbers identify data rows, not physical text-file line numbers; headers and
skipped blank lines are not samples. Gaps in sample numbers are expected
when intervening rows were rejected. A missing time column or nonfinite reading
is displayed as unavailable, never replaced with zero.

Choose any original CSV channel, including time. The channel selector includes
the 1-based column number so duplicate labels remain distinguishable. Values
are read from the same immutable dataset as the completed fuel result; there
is no additional copy of the raw CSV values and no unit conversion. On-screen
numbers use nine significant digits; this does not round or modify the dataset.

The table pages through 200 accepted rows at a time in recorded order. Its
page label counts accepted rows, while the first column identifies original
samples. Paging does not move Log Analysis's cursor, change a range or apply
new filters. This is an inspection surface, not a filter editor or rejection-
reason report.

## Statistics over the exact accepted set

Choose **Calculate channel statistics** to calculate the selected CSV channel's
finite/missing counts, minimum, maximum, mean, median, population standard
deviation and interpolated 5th/95th percentiles. These use **all accepted rows**,
not just the visible page. They are statistics of the original channel, not the
combined MAF correction or derived injector-fuel projection.

A row accepted by MAF can still lack a reading in an unrelated channel. That
reading contributes to the selected channel's missing count, not its mean.
An empty accepted set reports zero finite and missing samples and unavailable
numeric statistics; it never falls back to the whole CSV. Changing channels
clears old statistics and requires another explicit calculation.

These results are distinct from Log Analysis's range-only statistics. The
[shared-range link](SHARED_ANALYSIS_RANGE.md) still does not apply fuel filters
to Log Analysis. MAF and Injector can accept different rows even when their
ranges and conditions are linked, because their projections and assumptions
differ. The viewer does not interpret descriptive values as safe tuning changes.

## Bounds and stale-result guards

Indexing and requested channel statistics run on a dedicated, cancellable
worker, not the JavaFX thread. Review permits at most **1,000,000 accepted rows**
and a captured range of **5,000,000 scanned rows**. Larger reviews reject before
index allocation and ask for a narrower range and fresh analysis; they do not
silently truncate rows or calculate statistics from a preview subset. The
index retains four bytes per accepted row, plus ordinary object overhead;
statistics allocate/sort one channel's finite values at a time.

Loop stages check cancellation; the library array sort is checked immediately
after completion, not interrupted internally. Generation/source checks prevent
a cancelled, completed-but-queued or replaced job from publishing stale rows or
statistics. The existing input/setup/range invalidation clears the review as
well as fuel bins and curves. Closing the fuel pane shuts down the review worker.
This is bounded accepted-sample review, not a claim that the general CSV parser,
Log Analysis's range statistics or every analysis operation is now bounded and
asynchronous.

No source CSV, ROM, setup file, live session or ECU is modified. Public 1.1.2
packages remain unchanged; this feature requires the separately qualified
1.1.3 release.

## Verification scope

Synthetic core tests cover noncontiguous original indices, captured-filter
replay, adjacent-recorded-row rate semantics, injector projections, missing
and empty sets, explicit preflight bounds, channel validation and cancellation.
Native tests exercise paging across 205 accepted samples, full-set statistics,
duplicate column labels, queued and already-completed stale jobs, input
invalidation, worker disposal and a rendered 680 × 520 workspace. These are
offline software checks, not vehicle or calibration acceptance.

Local qualification passed seven new core tests, the full Ant unit suite
(optional private-corpus tests remain skipped), Linux core packaging, 132 JavaFX
tests (six new native sample-review cases), 35 Compose tests, portable-core
checks and Linux JavaFX staging.
