# Bounded Android CSV review — 1.1.3 development source

Use **OPEN CSV LOG** from the idle LOGGER workspace. File metadata and UTF-8 CSV
parsing run on a separate worker. The app reports loading status and offers
cancellation. A successful import shows an **imported, not live** summary:
total values, channel count, latest file-order sample, finite/missing counts and
finite-only minimum/maximum. Twelve channels appear per page. An unavailable last
sample remains unavailable; it is not replaced with an older finite value.
This summary is not a timeline, chart or full sample table and never feeds live gauges.

Switching workspace, beginning logging or cancelling invalidates pending results.
A failed/cancelled import preserves the previous summary. Returning from Editor or
Gauges retains that summary in memory; Activity/process recreation does not restore
it. Provider names are bounded display labels, not trusted local paths. Android's
cancellation signal is passed to metadata/file opening, but a provider that ignores
cancellation can still hold the single worker. The UI does not wait for that provider;
at most one newer import is queued, and superseded queued work is removed.

## Parsing limits

The portable reader streams records rather than retaining a tokenized copy of the
file. Summary mode accepts at most five million channel values, 256 channels,
64 Mi UTF-16 input characters, and 1,000,001 records including headers/blanks.
Individual fields are limited to 65,536 characters and records to 1 Mi characters;
retained per-channel metadata is also bounded. Limits are enforced while reading,
not after unbounded row allocation. The separate full-session API retains its
250,000-value ceiling. Limits are independent: reaching any one rejects the import.

Supported CSV includes standard RomRaider wide layout and the existing portable
long layout, leading BOM, quoted/escaped fields, quoted newlines, CR/LF/CRLF and
repeated wide headers. Malformed quoting, invalid timestamps, excessive input,
inconsistent long-form channel metadata and malformed UTF-8 are rejected. Original
numeric values and explicit unavailable samples are preserved in aggregate meaning;
this reader does not rewrite source files or change recording/export format.

## Verification scope

`portableCsvStreamingCheck` runs with a 64 MiB Java heap and generated five-million-
value input, without constructing the entire source string. Fifty checks cover
summary arithmetic, exact timestamp limits, CSV grammar, bounded early rejection,
read-ahead, cancellation, provider failure and ownership of borrowed Readers.
Android's `csv-import` phase exercises a 260,002-value file, summary pagination,
malformed UTF-8 retention, cancellation, bounded superseding work, workspace
switches, recreation and refusal during a synthetic live recording. Physical phone
document-provider acceptance remains pending. This is development source, not a
change to the published 1.1.2 downloads.
