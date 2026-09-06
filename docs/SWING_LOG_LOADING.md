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

## Applied ranges and marker writes

Subsequent manual range-statistics calculations now use a separate bounded,
cancellable worker. Applying a range immediately clears the old statistics and
disables the empty statistics table while work runs. Existing graph-channel
choices, graphs, cursor and playback remain available. Completed output identifies
its exact inclusive sample range; changing spinner drafts without Apply does not
silently change that range. New range requests cancel preceding work, and replacing
the dataset or detaching rejects already queued results and errors. If a pending
calculation was interrupted by detachment, reattachment recomputes it from the
retained in-memory dataset without parsing files or starting playback.

Each calculation is limited to 1,000,000 selected samples, 256 channels and
8,000,000 cells. Statistics still use the shared exact finite-value service.
Graph selections are preserved while replacing the table model; clearing old
statistics must not inadvertently clear the graph's selected channels.

Marker writes now use their own worker and frozen proposal. Add/delete controls
remain disabled until the save completes; the preceding saved list and marker
navigation remain available. Successful persistence installs the saved snapshot
and updates the graphs. Failure leaves the preceding list and label text intact,
marks editing read-only and requires a reload. A label typed while a save is in
progress is not cleared by that earlier save's completion.

An accepted marker write may finish after the dataset is replaced or the panel
detaches, but cannot update the replacement/detached UI. Failures are reported in
application diagnostics. Reattaching after a pending marker save keeps editing
read-only until an explicit log reload verifies the saved list. As with JavaFX,
this does not guarantee completion across process termination or storage failure.

These changes do not establish full JavaFX feature parity, change live CSV
recording, start an ECU connection or modify a ROM.

## Checks

Synthetic checks cover bounded parsing, exact initial statistics and markers,
corrupt sidecars, queued/stale successes and errors, actual interruption, bursts
of 100 superseded requests and chooser cancellation. A real Swing-panel test
exercises public loading from another thread, retained source identity, queued
delivery after detachment, and explicit reload after reattachment.

Local qualification passed 17 focused Swing tests (five load-worker, four
range-worker, three save-worker and five panel cases), the full Ant unit suite
and Linux core build, 197 JavaFX tests,
35 Compose tests, portable-core checks and Linux JavaFX staging. Android code
and public downloads did not change.
