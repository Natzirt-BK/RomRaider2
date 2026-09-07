# Development roadmap

Current source: **1.1.3**. Current download: **1.1.2 RC1**.
This roadmap tracks software work toward a stable release. Hardware testing is
listed separately and can wait until a vehicle is available.

## Reusable saved-log analysis setups

Implemented in 1.1.3 development source; see [the user guide](FUEL_LOG_ANALYSIS.md#reusable-setups-113-development-source).
Fresh qualification is required before publishing replacement packages. Sample
indices are deliberately excluded rather than reused across different logs.

The implementation uses the existing JavaFX MAF/Injector panes, keeping the calculations
read-only. Save the analysis kind, channel labels and units, bin width, filter
definitions and explicit injector assumptions in a bounded, versioned document.
Do not store a source CSV, private path, ROM image or a claim that the setup is
safe for another vehicle.

On import, show a review state. Resolve columns by exact identity; ambiguous or
missing headers need explicit remapping. Clear unit confirmation and old results.
Do not silently carry sample indices into a different-length log or let a late
worker publish results from the preceding setup. Failed imports leave the current
setup unchanged; cancelled/failed exports must not overwrite an existing file.

Tests should cover round trips, duplicate headers, changed units, malformed and
oversized files, unknown schema versions, out-of-range samples, stale background
results, dirty-state preservation and the small-window controls. Successful setup
loading does not authorize transferring calculated corrections into a ROM.

## Mobile calculated-channel parity

Implemented in 1.1.3 development source using a compiled measurement plan.
See [selection semantics, compatibility limits and test scope](CALCULATED_LOGGER_CHANNELS.md).
Public 1.1.2 remains unchanged. The following requirements govern this work:

- Resolve a bounded dependency graph and reject cycles, unknown IDs, unsupported
  targets and conversions before starting requests. Add only required leaf
  channels to the existing deduplicated read-only query plan.
- Preserve user-selected output order. Hidden dependencies must not unexpectedly
  appear in the dashboard or exported CSV.
- Honor explicit dependency-unit syntax such as `[P21:ms]`. Bare dependency IDs
  require a documented conversion-selection rule and comparisons with desktop
  behavior; changing display units must not silently change the intended formula.
- Evaluate only a complete current cycle. Missing/nonfinite inputs and invalid
  division produce unavailable readings, never zero or a previous-cycle value.
- Check the existing P200 engine-load and P201 injector-duty-cycle definitions
  against the real desktop converter using synthetic inputs, multiple units,
  zero RPM, missing data and profile import/restore. Preserve CSV compatibility.

Dynamic DimeMod discovery is separate: compare version/feature responses and
address structures with the retained desktop implementation before enabling any
runtime-derived channel. Do not hardcode the owner's RAM addresses. External
serial AEM input remains a distinct transport/lifecycle project.

The [DimeMod channel audit](DIMEMOD_CHANNEL_AUDIT.md) repairs desktop channels
that could be exposed without their discovered address block, old-layout oil
inputs and the FFS/failsafe bit mix-up. It also confirms that the legacy discovery
handshake uses writes; it must not be copied into Android's read-only logger.
Native frame/payload checks, bounded chunk assembly and unknown-version runtime
guards are now implemented and synthetically tested. The
[portable typed-channel audit](PORTABLE_TYPED_CHANNELS.md) confirms existing
integer/float decoding with definition-to-query tests and native desktop
comparisons; no new decoder is needed. Continue metadata/cache/negotiation-error
validation and verified mobile runtime/version/address mapping.

The metadata follow-up now bounds each field read, isolates retained buffers and
channel collections, rejects runtime-pointer spans that wrap on the wire, and
aligns DM02C's version gate with its parsed address. Published-channel spans now
also reject boundary crossings before runtime activation, with endpoint/alias
compatibility preserved across nine fixtures. RAM-tune/uninterpreted spans,
ECU-bound cache identity and negotiation cleanup remain; none of these checks
qualifies the legacy write handshake for Android's read-only logger.

The [cache/lifecycle follow-up](DIMEMOD_CACHE_LIFECYCLE.md) rejects late ECU and
DimeMod callbacks from closed modern desktop workspaces, including after a new
workspace opens. Read-codes now captures metadata once and uses a separate
runtime-only API returning an independent snapshot; cache changes cannot select
discovery writes or replace its decoded result. The retained Swing owner now
stores accepted state synchronously, discards superseded UI notifications and
rejects closed-owner callbacks/cache requests. Full ECU/session binding and
in-flight UI reload cancellation remain separate work; invalidating a cache
must not silently increase discovery writes.

Initialization callbacks now carry an attempt-lifetime token through the shared
query manager. Both desktop owners recheck it under their state lock; expired
cache lookups fail instead of requesting discovery. Tokens expire before
connection cleanup and on Stop. This closes the late-attempt callback boundary,
not the remaining firmware/module/transport binding of cached dynamic addresses.

## Then

Implemented in 1.1.3 development source: [linked, reviewed conditions](FUEL_LOG_ANALYSIS.md#linked-maf--injector-conditions-113-development-source) between MAF and Injector:
the same loaded dataset, inclusive sample range and up to three numeric filters.
Keep the input-channel mappings, bin widths and injector fuel assumptions
independent. Sharing conditions must be explicit, reject invalid ranges or
unresolved filter channels, and clear affected results and unit confirmation.
It must not silently overwrite an edited setup or carry sample indices across
different datasets. Cover source/target replacement, stale worker results,
duplicate column identities, missing/nonfinite filter values and closed panes.
This is not automatic closed-loop/transient filtering or full legacy parity.

Read-only [curve review](FUEL_CURVE_REVIEW.md) is implemented in 1.1.3 source,
including raw-sample polynomial/linear fits and observed-bin interpolation.
[Reviewed offline MAF-table transfer](REVIEWED_MAF_TRANSFER.md) is also implemented,
with explicit target/stored-value review, stale guards and grouped undo/rollback.
Injector transfer still needs its own evidence-backed mapping; line-fit intercepts
must not be treated as a voltage-dependent latency curve.
An optional [recorded-time rate filter](FUEL_RATE_FILTER.md) now supplies explicit
adjacent-row transient filtering, shared conditions and versioned setup transfer.
The [eight named scalar condition controls](FUEL_OPERATING_CONDITIONS.md) are also
implemented, with explicit mappings/limits, linked review and strict version-3
setups. Neither feature infers suitable operating conditions or claims live-capture
parity. [Reviewed range sharing](SHARED_ANALYSIS_RANGE.md) now connects all three
workspaces to Log Analysis's cursor and range-only views/statistics; it does not
apply fuel filters to those statistics. Separate [accepted-sample inspection and
statistics](ACCEPTED_FUEL_SAMPLES.md) now cover each fuel result's exact original
rows with bounded background work and stale-output guards.
[Read-only log-to-map tracing](LOG_MAP_TRACING.md) now follows the saved-log cursor
over a reviewed, frozen 2D/3D table snapshot with explicit axis mappings and
geometric-neighbor semantics. [Binned 2D/3D views](BINNED_LOG_ANALYSIS.md) now add
explicit axis origins/widths, counts, gap-preserving tables/plots and bounded
background aggregation. [Common-bin run comparisons](RUN_COMPARISONS.md) now
support independent CSV/range selection, count-aware means/differences, bounded
second-log imports and gap-aware overlays. Continue injector transfer.
Android [portable channel-setup export/import](PORTABLE_LOGGER_SETUP.md) is now
implemented with a platform-neutral bounded codec, exact-definition matching,
explicit review and stale-work guards. JavaFX desktop exchange controls now use
the same format, exact loaded-definition snapshots, reviewed channel/unit
replacement and cross-category order persistence. Normal JavaFX CSV imports now
use [bounded, cancellable parsing](BOUNDED_CSV_IMPORT.md), including actual worker
interruption and stale-delivery guards. JavaFX [range statistics](LOG_ANALYSIS_ARCHITECTURE.md#background-range-statistics--113-development-source)
also run on a bounded, cancellable worker with explicit range status and stale
result/error rejection. [Table responsiveness](LOG_TABLE_RESPONSIVENESS.md) adds
allocation-free source indexing and cancellable stable sorting with direct
sample lookup. [Marker-file I/O](MARKER_FILE_SAFETY.md) now runs on a JavaFX worker
with bounded strict validation, immutable snapshots and conflict-checked atomic
saves. Swing gains save guards and now [prepares new CSVs, initial statistics and
marker snapshots on a bounded worker](SWING_LOG_LOADING.md). Subsequent Swing
range statistics and marker writes now use their own workers, with graph/sample
identity preservation, frozen save proposals and detached/replacement guards.
Continue the remaining mobile/service work and broader compatibility audits.

The [desktop profile persistence prerequisite](DESKTOP_PROFILE_INTEGRITY.md) now
preserves Unicode, escaped attributes, captured protocol and immutable snapshots,
and saves through atomic replacement. The desktop transfer integration builds
on those repairs, with explicit rollback/persistence failure handling; neither
feature establishes hardware qualification or production ECU writes.

The [Android background-recording contract](ANDROID_BACKGROUND_RECORDING.md)
now has a screen-independent recording owner and connected-device foreground
service integrated with the Activity. Explicit starts, notification Stop,
same-owner reattachment, finite-only peaks, original receipt age and bounded
wake-lock handling are implemented. Continue qualification and retained-recording
recovery audits. Gauges-only switching and background capture remain distinct
features; real USB/background behavior is not established by synthetic tests.

[Reviewed interrupted-recording recovery](ANDROID_RECORDING_RECOVERY.md) is now
implemented in 1.1.3 source. Completed records are validated from a private
snapshot before destination writes; omission of an unfinished tail requires
review. [Larger-log mobile summary review](ANDROID_CSV_REVIEW.md) is now implemented
with bounded streaming, worker cancellation, pagination and retained previous
results. Physical document-provider acceptance remains pending.

The owner's additional gauge request adds five vibrant/retro native faces and
[full-screen mounted mode](ANDROID_MOUNTED_DISPLAY.md), including display keep-awake
while visible even without active logging, plus saved 1–6 gauge layouts that fit
the viewport without changing the recording selection. Continue emulator/native visual and
lifecycle qualification, then the remaining fork-adoption audit; do not substitute
synthetic test data for supervised hardware acceptance.

Forester/EVO/OpenPort in-car acceptance, phone document-provider behavior and
physical Windows/Mac/Deck tests remain supervised tasks. No production ECU
writing or live tuning is enabled by completion of this plan.
