# Bounded desktop CSV imports

Implemented in **1.1.3 development source** for the replacement JavaFX Logger's
normal **Open CSV log** action, including logs shared with MAF and Injector.
Public 1.1.2 downloads are unchanged.

The entire file must pass validation before replacing the current dataset.
Malformed or oversized inputs are rejected, not truncated or downsampled.
The previous dataset and analysis workspace remain available after a parse
failure. Use completed recordings; this is not a live-growing-file viewer or a
filesystem snapshot guarantee.

## Limits

Normal imports now use the same bounds as the second file in run comparisons:

| Resource | Maximum |
| --- | ---: |
| Samples | 1,000,000 |
| Numeric columns, including time | 256 |
| Numeric cells | 8,000,000 |
| Decoded characters in the complete input | 33,554,432 |
| Characters in one physical line | 1,048,576 |
| Decoded characters in a header field | 512 |
| Decoded characters in a numeric field, before trimming | 1,024 |

All limits apply together. Decoded-character budgets count UTF-16 code units,
including line endings, not file bytes. UTF-8 decoding is strict. BOM, quoted
single-line headers, CR/LF/CRLF, original numeric values and missing samples keep
their existing semantics; multiline quoted CSV records remain unsupported.
Field-count and field-length limits are enforced during tokenization, before an
excessively wide record can allocate a large field list.

## Cancellation and ownership

Each Logger window owns one daemon import worker. Choosing another file cancels
the preceding task, interrupts its worker and removes cancelled queued requests.
Closing the window shuts down the worker. Cancelling the file chooser leaves an
already accepted load running. The read loop, tokenizer and immutable dataset
copy check interruption. A blocked filesystem read may not stop immediately;
new work waits on the same worker instead of spawning additional parser threads.

Request identity is checked again on the JavaFX thread, so even a late result
from an uncooperative operation cannot publish a dataset or error over a newer
request or a closed window. Import does not open an ECU connection or change an
active recording, source CSV, definition or ROM.

## Scope and next work

This bounds parsing; it does not make the complete analysis workspace constant
memory. Subsequent [JavaFX range-statistics scheduling](LOG_ANALYSIS_ARCHITECTURE.md#background-range-statistics--113-development-source)
now moves that calculation off the UI thread. Large-log table setup/sorting and
marker-sidecar loading still need their own responsiveness work. Existing legacy parser overloads and the Swing analysis
entry point remain unbounded; Android's separate parser is unchanged.

Automated checks cover compatible files, rejected oversized fields and records,
real worker interruption, a burst of 100 superseded requests, close/caller
cancellation, recovery after a parse error, uncooperative late results and
cancelled choosers. Tests use synthetic data and no vehicle access.

Local qualification passed eight bounded-parser tests, the full Ant unit suite
(optional private corpora remain skipped), Linux core packaging, 172 JavaFX
tests, 35 Compose tests, portable-core checks and Linux JavaFX staging.
