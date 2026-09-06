# Bounded Swing log loading

Implemented in **1.1.3 development source** for the legacy Swing Logger's
Analysis workspace. Public 1.1.2 packages are unchanged.

CSV loading now uses the same [row, column, cell and text limits](BOUNDED_CSV_IMPORT.md#limits)
as the replacement JavaFX viewer. The complete CSV is validated, initial
whole-log statistics are calculated, and the marker sidecar is read/validated on
one owned worker. No part of that preparation runs on the Swing event thread.
Initial statistics retain the shared service's exact finite/missing semantics;
there is no sampling or approximate percentile substitution.

The current dataset remains displayed and usable while preparing a replacement.
Load Log and Replay Last can select another file; the newer selection cancels
the preceding job and removes cancelled queued jobs. No partial dataset is
installed on malformed or oversized input. A CSV failure reports an error while
retaining the current dataset and does not stop its offline playback timer.

A malformed marker file is a separate failure: the valid CSV and its statistics
can still open, but marker editing stays read-only with the existing recovery
message. The exact sidecar snapshot prepared with the result becomes the basis
for [conflict-checked marker saves](MARKER_FILE_SAFETY.md). The CSV itself is not
fingerprinted or locked against external changes; use completed recordings.

## Lifecycle and cancellation

Load requests from outside the event thread are marshalled to it and checked
against the panel's lifecycle. Detaching the panel cancels work, shuts down its
worker and invalidates queued delivery. Late successes and errors cannot update
a detached panel or its later attachment. Reattaching allows a fresh worker on
the next explicit load; it does not automatically parse, play back or connect.
Cancelling the file chooser does not cancel an already accepted load.

The parser, dataset copy and statistics service check interruption. A blocked
filesystem read or a bounded primitive percentile sort may not stop immediately;
replacement work waits on the same worker instead of starting additional parser
threads. UI delivery still rejects the old result even if work completes late.

## Remaining work and checks

This completes background preparation of a newly opened Swing log. Subsequent
manual range-statistics calculations and marker writes still run synchronously
and need their own lifecycle work. It does not establish full JavaFX feature
parity, change live CSV recording, start an ECU connection or modify a ROM.

Synthetic checks cover bounded parsing, exact initial statistics and markers,
corrupt sidecars, queued/stale successes and errors, actual interruption, bursts
of 100 superseded requests and chooser cancellation. A real Swing-panel test
exercises public loading from another thread, retained source identity, queued
delivery after detachment, and explicit reload after reattachment.

Local qualification passed eight focused Swing tests (five worker and three
panel cases), the full Ant unit suite and Linux core build, 197 JavaFX tests,
35 Compose tests, portable-core checks and Linux JavaFX staging. Android code
and public downloads did not change.
