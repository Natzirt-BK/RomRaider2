# Large-log table responsiveness

Implemented in **1.1.3 development source** for the replacement JavaFX Logger's
Log Analysis table. Public 1.1.2 downloads are unchanged.

The table now represents original capture order as an immutable sample range,
without allocating a boxed row entry for every sample. Visible cells obtain
their original sample identity on demand. This is not pagination, truncation or
downsampling: all samples in the applied range remain available.

## Sorting and selection

Column sorting runs on one owned worker per table. Sort requests capture the
ordered column IDs and ascending/descending directions on the UI thread; workers
read only the immutable numeric dataset, never JavaFX cell factories. The worker
uses a stable merge sort over primitive sample IDs. Equal values retain original
capture order, and multiple sort columns are evaluated in their selected order.
The Sample column sorts original sample numbers, not positions in another sort.

Sorting uses the existing numeric `Double.compare` ordering, including signed
zero, infinities and NaN; it does not convert missing values to zero or change
the source data. Nonfinite readings still display as unavailable. Clearing all
sort columns immediately restores original capture order within the applied
range.

The current order stays visible while sorting, with an explicit pending status.
A new applied range initially shows its source order until its requested sort
finishes. The selected original sample is preserved when installing an order;
an inverse index gives direct sample lookup for selection and playback. Changing
display order never changes captured-time playback order, the cursor's source
sample identity, statistics, MAF/Injector inputs or the CSV.

JavaFX clears sort columns when its items object is replaced. The integration
explicitly restores the requested sort columns while suppressing recursive sort
requests, so range changes and finished sorts retain header directions.

## Bounds and cancellation

Each sort accepts at most 1,000,000 selected samples and 257 distinct sort keys
(256 numeric columns plus Sample). A dynamic limit of 100,000,000 key comparisons
rejects excessive work without applying a partial order. A limit/error leaves
the current order visible and reports the problem. Use fewer sort columns or a
smaller range if needed.

A sorted order owns two primitive integer arrays: sample order and its inverse,
about 8 MB total for one million samples, excluding ordinary object overhead.
The merge workspace is reused for the inverse. Source order has neither array.
The previous displayed order may remain allocated while its replacement runs;
these bounds do not describe the complete app's memory use.

New sort/range requests interrupt preceding work and remove cancelled queued
requests. Merge/copy loops check interruption. Shared-range drafts clear the
table and invalidate pending sorts until Apply; closure cancels and shuts down
the worker. Results and errors are generation-checked on UI delivery, including
already completed work queued before a newer request.

## Qualification and remaining work

Synthetic tests cover a million-row source-order view without index allocation,
direct lookup, stable random-reference comparisons across merge boundaries,
multiple keys, signed zero/nonfinite readings, read-only lists, invalid columns,
comparison limits, interruption, queued stale output/errors and bursts of 100
superseded requests. Native tests cover sort-header retention, range changes,
source-order restoration, cursor/selection continuity, shared drafts, closure
and a 20,000-row table at an 800×600 content size.

Local qualification passed 190 JavaFX tests (six new row-index/sort tests, three
worker tests and two native table tests), 35 Compose tests, portable-core checks
and Linux JavaFX staging. Actual 800×600 table and statistics renders were
inspected. Shared numerical code and Android were not changed by this update.

[CSV parsing](BOUNDED_CSV_IMPORT.md) and [range statistics](LOG_ANALYSIS_ARCHITECTURE.md#background-range-statistics--113-development-source)
have separate bounded workers. [Marker-sidecar loading/saving](MARKER_FILE_SAFETY.md)
now also runs off the JavaFX thread with conflict guards; legacy Swing asynchronous
handling remains a follow-up. This does not promise that every layout or plotting operation
has constant cost, nor qualify Android or vehicle hardware. No source CSV, ROM,
definition, active recording or ECU state is changed.
