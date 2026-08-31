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

Markers, event detection, calculated channels, XY graphs, and map awareness
should consume this dataset and shared cursor through dedicated services. They
should not be added as direct state and unmanaged threads inside `EcuLogger`.
