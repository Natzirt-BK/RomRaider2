# Saved-run comparisons — 1.1.3 development source

JavaFX Log Analysis's **Run comparison** tab compares two independently selected
saved runs in explicit, common X bins. It offers counts/means/differences, a
shared-scale overlay and a separate B-minus-A plot. It does not infer time
alignment, engine operating conditions or safe calibration changes. Public
1.1.2 downloads do not contain this addition.

## Set up the two runs

Run **A** is the currently loaded Log Analysis CSV and its applied sample range.
Choose **Open run B CSV…** to import another completed capture, or **Use this CSV
for B** to compare a different range of A's CSV. B imports are local to this tab:
they do not replace Log Analysis, its cursor, or the MAF/Injector datasets.

Map X and value channels independently for A and B. Column numbers distinguish
duplicate labels; matching names are not required and no channel is selected
automatically. Set B's inclusive, 1-based first/last sample numbers, the common
X origin and positive width, and the minimum count required **per run**. New B
imports reset B's mappings/range for explicit review while retaining A's mapping.

Confirm channel correspondence, units and comparable run conditions before
selecting **Compare runs**. X units must match between the runs, as must value
units. Matching trims surrounding whitespace but is case-sensitive, so `mV`
and `MV` are not treated as interchangeable. No conversions are guessed. Two
blank unit labels still need the user's unit confirmation; the software cannot
validate their physical meaning or the comparability of driving conditions.

The comparison uses original numeric CSV values, not MAF/Injector filters,
derived fuel projections or automatically chosen pulls. A time channel may be
mapped explicitly, but its recorded coordinates are used unchanged: timestamps
are not shifted to a common start and sample numbers are never paired.

## Read the comparison

Each run is aggregated independently using the [binning convention](BINNED_LOG_ANALYSIS.md#bin-boundaries-and-statistics):
common origin/width, sample-weighted means, explicit floating-point boundary
tolerance and missing-reading rejection. The comparison covers the union of
observed X-bin slots, including gaps.

The table shows X edges, each run's count and qualified mean, **B − A** in the
value channel's units, and coverage. Differences exist only where both runs
meet the minimum count. An A-only or B-only bin keeps its available mean but has
no difference. Empty and low-count bins are explicit, never zero-filled. If two
finite means have an unrepresentable double difference, the row says
**Difference overflow** and leaves the difference unavailable.

The overlay uses a common vertical scale, teal filled dots for A and amber rings
for B, so coincident points remain distinguishable. Lines connect only adjacent
qualified bins within each run. **Plot B − A** shows shared qualified differences
with zero included in the scale and a dashed zero line. Missing coverage breaks
the line; there is no extrapolation across gaps. Plot positions are bin centers,
not resampled raw traces. Positive and negative differences are descriptive,
not automatically “better” or “worse.”

**Hide setup** reclaims plotting space without changing inputs/results. Expanded
setup controls scroll within a smaller Log Analysis window, keeping the results
area available. Screen
numbers use nine significant digits. **Copy comparison** produces a canonical
TSV with underlying double values, captured file/range/channel/bin metadata,
counts and coverage. Reordering table columns does not reorder the copied data.
A failed later import cannot replace that completed comparison's metadata with
an error message. Copying uses the clipboard, not the source files.

## Lifetimes and import limits

Changing mappings, bin settings, count thresholds or B's range clears stale
results. Applying A's range clears results and unit confirmation; a pending
shared-range draft blocks comparison until Apply. B's range remains independent.
Playback seeks do not recalculate the aggregate.

B imports use a separate worker and opt-in parser limits:

| Limit | Maximum |
| --- | ---: |
| Data rows | 1,000,000 |
| Channels | 256 |
| Numeric cells | 8,000,000 |
| Characters per physical line | 1,048,576 |
| Total decoded characters | 33,554,432 |
| Channel-label length | 512 characters |
| Numeric-field length | 1,024 characters |

Character counts use Java UTF-16 code units, not file bytes. Line/total limits
are enforced while reading, and row/cell limits before allocating the next
numeric row. Invalid numeric previews in bounded-import errors are shortened.
Existing unbounded parser callers retain their behavior; this does not complete
the separate general large-log review project. Use completed captures: imports
do not tail a recording that is still being written.

Each comparison range permits up to 5,000,000 already-loaded rows; its combined
coverage is limited to 256 X-bin slots including gaps. Geometry/import limits
reject with an error rather than returning a truncated run or preview subset.
The established strict CSV rules, UTF-8 decoding, quoted fields, CR/LF line
endings and missing-value semantics remain in effect. Multiline quoted records
are not newly supported.

Cancelled/failed B imports preserve the previous B dataset and completed result.
If inputs change during import, the late file is not installed over them; the
user is asked to select it again. Load tickets and calculation generations also
discard superseded or completed-but-not-yet-published work. Closing/replacing
the Log Analysis workspace cancels/disposes both workers. Reading/aggregation
check cancellation; immutable dataset copying is not an instant interruptible
operation, but stale copies cannot publish after disposal.

No CSV, ROM, setup file, live session or ECU is modified. This adds common-bin
run comparison, not automatic pull detection, time-warp alignment, statistical
significance testing or a tuning recommendation.

Synthetic tests cover unequal sample counts, shared/gapped/low-count coverage,
independent ranges, strict unit matching, missing values, overflow, bounds,
immutable outputs and cancellation. Parser checks cover limit boundaries,
line endings/BOM/quoted headers, bounded labels/error text and no partial results.
Native checks cover real file import, preserved failed-import state/metadata,
stale calculations/imports, same-CSV ranges, canonical copied columns, disposal
and rendered overlay/difference plots. These are offline software checks only.

Local qualification passed 13 focused core/parser tests, the full Ant unit suite
(optional private-corpus cases remain skipped), Linux core packaging, 152 JavaFX
tests (eight comparison cases), 35 Compose tests, portable-core checks and Linux
JavaFX staging. Final 1,000 × 640 plots and compact setup behavior were checked.
