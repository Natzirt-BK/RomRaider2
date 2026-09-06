# Offline log analysis architecture

## Current boundary

The Logger's Analysis tab reads RomRaider2 and RomRaider CSV captures into an
immutable numeric dataset. Parsing and statistics do not depend on Swing, the
live logger controller, an ECU transport, or the unfinished legacy
`PlaybackManagerImpl`.

The parser preserves full channel labels, extracts the final parenthesized unit
when present, treats blank and non-finite samples as missing, and rejects rows
whose field count no longer matches the header. It does not silently shift
values between channels.

The workspace provides whole-log or inclusive sample-range statistics:

- finite and missing sample counts;
- minimum and maximum;
- arithmetic mean and median;
- population standard deviation;
- linearly interpolated 5th and 95th percentiles.

CSV loading runs off the Swing event thread. The result is a read-only analysis
surface and does not enable memory reads, writes, resets, or flash operations.

## Finite-value arithmetic — 1.1.3 development source

JavaFX normal CSV imports now have [bounded, cancellable parsing](BOUNDED_CSV_IMPORT.md).
The range-statistics worker described below is separate from CSV parsing.

The shared statistics service uses compensated summation for the mean. If an
intermediate sum overflows, it recomputes that channel's sum with exact decimal
representations of the binary-double inputs, then divides with 34-digit decimal
precision before returning a double. This preserves small cancellation residuals
without routing ordinary logs through arbitrary-precision arithmetic.

Population standard deviation is calculated in centered, scaled coordinates;
it does not square original-unit values or subtract a rounded original-unit
mean from adjacent large readings. Opposite-sign percentile endpoints use a
weighted interpolation when their difference overflows. The definitions above
are unchanged: standard deviation divides by the finite sample count, not count
minus one, and missing readings do not become zeroes. An all-missing channel
retains unavailable numeric statistics; a finite singleton has zero deviation.

Regression tests cover opposite maximum doubles, squared-deviation overflow
and underflow, adjacent doubles at a large offset, cancellation, subnormal
readings, missing/singleton rules and 21 scaled distributions spanning 500
decimal orders. These are numerical software checks, not calibration validation.
Local qualification passed all nine statistics tests, the full Ant unit suite
(optional private-corpus tests remain skipped), the Linux core build, 126 JavaFX
tests, 35 Compose tests, portable-core checks and Linux JavaFX staging.

This change applies to consumers of `LogStatisticsService`, including Swing and
JavaFX range statistics. It does not change fuel-bin means, curve-fit residuals,
recorded CSV values or gauge readings. Exact percentiles still sort finite
samples per channel. Public 1.1.2 packages are unchanged.

## Background range statistics — 1.1.3 development source

The replacement JavaFX Log Analysis pane now computes whole-log and applied-range
statistics on one owned worker, including the initial dataset load. It clears old
statistics immediately when starting a new calculation and shows the exact
inclusive sample range being calculated. Table, chart and playback controls do
not wait for that calculation. Completed results show finite/missing counts and
explicitly identify range-only statistics: MAF/Injector filters are not applied.

The existing exact finite-value service above is unchanged; this is scheduling,
not approximate percentiles, downsampling or a new statistical definition. Work
is bounded to 1,000,000 selected samples, 256 channels and 8,000,000 selected
numeric cells. The entire selected range must fit; no partial statistics appear.
The normal CSV import already applies these bounds to the complete dataset.

Selecting another range cancels the preceding calculation and removes cancelled
queued tasks. Shared-range drafts clear results and invalidate pending work until
Apply. Closing the pane cancels work and shuts down its worker. Request identity
is checked on UI delivery, including errors, so a completed-but-queued result
cannot reappear over a newer range, draft or closed workspace. An independent
unapplied range-field edit retains the last applied range's statistics, with the
completed sample range identified in the status label.

The numeric service checks interruption during scanning and arithmetic. Its
bounded primitive-array percentile sort is not interruptible mid-sort; the next
request waits on the same worker, and the preceding result is still rejected.
Subsequent [table responsiveness work](LOG_TABLE_RESPONSIVENESS.md) supplies
allocation-free source-row indexing and cancellable background sorting.
Marker-sidecar reads and legacy Swing range statistics remain separate
follow-ups. No CSV, ROM, live logger or gauge state is modified.

Regression checks exercise exact range/missing-value semantics, actual worker
interruption, a burst of 100 superseded requests, queued/stale result and error
rejection, draft/close handling, limit boundaries and recovery after failure.
Native JavaFX tests await normal asynchronous delivery while checking linked
ranges, clearing, playback and the original sample identities.
Local qualification passed 179 JavaFX tests (including six new worker cases and
one new native statistics case), 35 Compose tests, portable-core checks and Linux
JavaFX staging. The shared numerical service and Android code did not change.

## Linked cursor and playback

`LogCursorModel` owns the one range-clamped sample selection shared by the time
graph, timeline slider, step controls, and playback service. Views do not keep
independent cursor positions.

`LogPlaybackService` is a deterministic state machine with pause, replay, stop,
seek, step, range, and 0.25x through 8x speed behavior. It owns no executor and
creates no unmanaged playback thread. The Swing workspace supplies measured
elapsed time from a bounded UI timer, while unit tests can drive the same service
without sleeping.

The Java2D time graph displays up to five channels selected from the statistics
table. Each trace shows its current value and visible-range bounds. Clicking the
graph or moving the timeline updates the same cursor; captured timestamps drive
playback when present, with a deterministic sample interval fallback otherwise.

## Validation

Focused tests cover quoted headers, UTF-8 byte-order marks, missing values,
malformed row widths, nonnumeric values, selected ranges, typed table output,
cursor clamping, captured-time playback, speed/completion behavior, graph
painting and seeking, and analysis-control composition. An optional regression
can also parse a separately supplied CSV corpus; no captured vehicle logs are
stored in the software repository.

## Next layer

JavaFX's [read-only map tracing](LOG_MAP_TRACING.md) now consumes the dataset and
shared cursor through an immutable table-geometry service and a separate view.
Further analysis, event detection and map-aware aggregation should likewise use
dedicated services, not direct state and unmanaged threads inside `EcuLogger`.
