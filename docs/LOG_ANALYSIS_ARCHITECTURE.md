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
This is separate from the still-pending asynchronous large-log statistics work.

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
samples per channel; this is not the pending bounded/asynchronous large-log
statistics implementation. Public 1.1.2 packages are unchanged.

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
