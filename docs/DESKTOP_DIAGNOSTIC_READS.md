# Desktop diagnostic-read reliability

Local development after 1.1.8 RC1. These changes do not add adapter support,
ABS scanning, transmission diagnostics or ECU writes. Vehicle acceptance is
still pending; all checks described here use synthetic definitions and replies.

## Standard trouble codes

The retained Swing diagnostic reader used a list entry as a stopping marker.
An empty list failed before error handling; a missing marker could walk past
the end; a full-length initialization always omitted the final definition.
The [upstream reader](https://github.com/RomRaider/RomRaider/blob/master/src/main/java/com/romraider/logger/ecu/comms/readcodes/ReadCodesManagerImpl.java)
also contains the marker-based loop. The local replacement builds a read plan
before opening the connection.

The existing local initialization-length limits remain:

| Initialization bytes | Selected definition IDs |
| --- | --- |
| Fewer than 56 | Numeric `D` indices below 256 |
| 56–103 | Numeric `D` indices below 488 |
| At least 104 | Every supplied entry, including the last |

Sparse lists no longer need a boundary marker, and reordering cannot move an
above-limit entry into a limited read. Empty selections, duplicate IDs and
unclassifiable IDs in a limited read fail before connection creation. Full-length
reads retain support for custom IDs. These inherited limits are not a new
capability-discovery mechanism or independent validation of definition addresses.

Each read owns its selected entries, converter selection and address bytes.
Protocol optimization cannot modify the live catalog through the diagnostic
queries. Address deduplication still applies to code bits sharing a byte pair.
Requests retain the existing maximum of 150 code entries per batch.

Every selected query must receive one two-byte reply. A missing, malformed or
duplicate reply fails the operation; default numeric zero cannot stand in for
a reply. Current, memorized and combined flags retain their existing meaning.
The converter's `-1` unsupported-entry marker is excluded from active codes,
but a read with no available standard entries fails instead of reporting a
successful clean result. A successful empty result covers only the available
entries in the supplied definition, not every possible fault or vehicle module.

Cancellation and owner/setup checks run before and after each batch. Partial
results are not displayed after a failed batch, and a completed or failed plan
cannot be reused. The existing manager still closes its connection in `finally`.
Diagnostic failure wording now includes the logger definition and adapter setup,
instead of assuming every problem is a serial-port or ignition issue.

## Results tables

Standard and DimeMod tables have stable text/Boolean column types even when
empty, so sorting does not depend on a first row. Replacing standard results
notifies the table; DimeMod flags are copied from the supplied sets so later
changes cannot alter an already displayed result.

## Verification scope

Offline regression tests cover selection boundaries, shortened/reordered lists,
the final entry, invalid definitions, missing/short/duplicate replies, unsupported
markers, bit conversion, batching, cancellation, stale owners and captured
addresses. Synthetic replies also pass through the actual SSM K-line and CAN
request/response classes to check address deduplication and per-bit delivery.
These protocol checks do not open a connection or qualify electrical timing.

The earlier [DimeMod identity checks](DIMEMOD_CACHE_LIFECYCLE.md#diagnostic-identity-follow-up)
remain in place. Full firmware/session cache binding is still separate work.

The subsequent [background diagnostic workflow](DESKTOP_ASYNC_DIAGNOSTICS.md)
moves the retained Swing menu action off the UI thread and adds cancellation.
Native adapter calls may still take time to return after cancellation.

## Initial local qualification — September 9, 2026

- Core: 702 tests, 699 passed and three optional-environment skips; no failures.
  This includes 11 new read-plan tests and three new result-table tests.
- JavaFX: 291 tests passed with native-window, logger-stress and synthetic
  audit-capture checks enabled; no skips.
- Compose: 48 tests passed with native-window checks enabled; no skips.
- Shared portable checks passed, including the bounded streaming CSV check.
- The fresh Java 21 Linux image passed dependency/module checks and unmodified
  launcher startup, version consistency, first-run dialog and normal shutdown.
- Patch whitespace and test-wrapper shell syntax checks passed.

The image's core JAR matched the tested local build:
`bd7718d7258bb9dae5cc385bb2d77a8b2c73c335073085dbfbaa5b8084ab0f69`
(SHA-256). This is not a public release asset hash. GitHub releases, installed
applications, Android code and vehicle files were not changed.
