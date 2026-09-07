# Active work plan — September 6, 2026

Owner priority: finish the gauge work first and show actual application renders,
then continue the remaining automated work. Physical in-car tests are deferred
until the owner is available; never initiate hardware polling to fill that gap.

Current gauge brief supersedes the earlier three/nine/eleven-face counts: **25
selectable styles** across Android, JavaFX and Compose. The four latest native
faces are Apex 24, Ion OLED, Loop Drive and Chrono Roll; references and actual
renders are in [the design guide](GAUGE_DESIGN.md). Legacy desktop Handheld
preferences remain readable but do not add a 26th picker choice.

Android now consolidates layout, independently assigned display channels,
explicit Logger-channel copy, per-channel visual style picking and demo Show/Hide
in Gauges. Only Full Screen keeps the screen awake; a tap reveals an exit menu
that times out. Desktop/handheld searchable style galleries and per-channel
persistence are now implemented; see [their contract](DESKTOP_GAUGE_STYLES.md).
[Independent slots and fitted 1–6 layouts](DESKTOP_GAUGE_DISPLAY.md) are now implemented
on desktop/handheld, with native-window full-screen menus. The Compose-owned window
now has direct native lifecycle checks. The [current source/package checkpoint](GAUGE_PACKAGE_QUALIFICATION.md)
passed both desktop builds and all platform packages; physical platform acceptance
remains open. [Screen-awake requests](DESKTOP_DISPLAY_AWAKE.md) now have window-scoped
ownership, OS backends and an unavailable indicator; service/physical platform
acceptance remains explicit. The legacy Swing bridge now has a retained,
borderless full-screen host; its native input/lifecycle checks cover updates,
tap/reset/timeout/exit and owner cleanup without logger commands. Public downloads
remain 1.1.2 RC1. The parked DimeMod published-channel span audit is now implemented:
crossing reads reject before runtime activation, while valid endpoints and aliases
remain compatible. RAM-tune/uninterpreted spans, cache identity and negotiation
cleanup remain separate work; neither these checks nor synthetic logging qualify
vehicle writes.

The Swing profile-review follow-up now rejects obsolete initialization snapshots
after a protocol-confirmation dialog, including owner closure while that dialog
is open. See the [lifecycle audit](DIMEMOD_CACHE_LIFECYCLE.md#profile-review-after-initialization-changes).
Catalog reloads now also carry a paired initialization snapshot and a superseding
reload token through their remaining stages. Installer-worker ownership,
failed-definition catalog availability and firmware/session cache identity remain
open; these guards do not initiate discovery or enable vehicle writes.

The screen-awake checkpoint `e4de5e42` passed both desktop builds and
[all platform packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34069217817).
Native API acquisition/release passed on Windows and both macOS architectures;
physical screen-idle acceptance remains untested. The following Compose-owned
window fix preserves geometry/mode, removes retained menu chrome and repairs root
Escape handling. Its native fixture uses an isolated Xvfb/Openbox desktop and
synthetic recording, not a logger runtime or vehicle.

The 25-style checkpoint `a5676f2f` passed both hosted Android regression runs,
both desktop build runs, and [all platform packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34064071712).
This includes the Android version-increment automation upgrade; no public release
was replaced by those CI artifacts.

The gallery checkpoint `8e5decfa` passed both desktop build runs and
[all platform packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34065073018).

1. Nine additional, original gauge designs (owner expanded the original three
   by six): Rally Precision, Circuit Stack, Retro VFD, Club Sport, Sweep Ribbon,
   Twin Arc, Amber Matrix, Vector HUD and Turbo Pod.
   Research Subaru/Mitsubishi and aftermarket instruments; preserve
   clear numeric readings, units, scale labels and unavailable-data states.
   Owner approved these nine and requested a further premium Subaru/Mitsubishi
   pass: STI logo/red night-cluster styling, Evolution-inspired styling, richer
   materials/illumination and carefully bounded visual motion. Keep all nine.
2. Gauges-only view on Android phones/tablets and SteamOS handheld mode, also
   accessible on desktop. Switching views must preserve the same logger session
   and recording. Setup controls stay outside the mounted display. Distinguish
   simulated, live, stale and stopped states. Verify portrait/landscape, small
   windows, view switching and invalid readings without a real ECU.
3. Show actual rendered designs; then checkpoint/push tested work and run fresh
   platform builds. Prepare numeric 1.1.2 publication with Android migration
   guidance, preserving the user's recordings and unsaved ROM work.
4. Desktop editor commands: multiply selection, custom fine/coarse steps,
   reload/revert and ROM properties, with undo/dirty-state safeguards.
5. MAF/injector follow-ups: synchronized filters, interpolation and fitting,
   saved analysis setups, reviewed transfer into ROM tables (not vehicle writes).
6. Read-only log-to-map tracing, binned 2D/3D analysis and run comparisons.
7. Android background-recording design, large-log review and portable setup
   export/import. Gauges-only view switching is not background recording.
   Audit follow-up: portable calculated Logger channels and verified DimeMod
   runtime discovery are needed for full desktop Shinji-profile parity;
   external serial AEM input is not currently supported on Android.
8. Remaining software platform/fork-adoption and scoped dependency audit work.
   Owner follow-up: include ECUFlash and the exact Forester/EVO definition sets,
   then clean up GitHub language, formatting and release/documentation consistency.
9. Later supervised hardware acceptance: intended Forester profile and sustained
   logging, exact EVO/MUT-II/OpenPort/USB-C, Windows adapters, Deck and Macs.
10. Production live tuning/flashing remain target-specific bench-qualified
    milestones, not something to enable merely because automated tests pass.

Checkpoints before gauge development: `79ad42f9` (approved compact editor)
and `a4aca11f` (initial read-only fuel-log analysis), pushed to GitHub `master`
and the development branch. These are source checkpoints, not public releases.

Gauge checkpoint `294791f6` adds the nine approved faces, native mounted views,
unit-aware scales, foreground/session protections and Android CSV continuity
tests. It is on GitHub `master`; hosted desktop builds and Android regressions
passed. The premium STI/Evolution refinement follows this checkpoint.

Premium checkpoint `f75ebba2` adds STI Night and Evolution Night; `e64b3234`
fixes the Compose runtime staging dependency found by clean hosted packaging.
[Platform package run 34022049448](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34022049448)
passed Android, SteamOS and both macOS architectures. These are source/build
checkpoints, not a new public release.

The JavaFX editor command pass completed item 4's selection multiplier,
session-local fine/coarse steps, protected saved-ROM reload and read-only ROM
properties. See [command semantics and test scope](DESKTOP_EDITOR_COMMANDS.md).
The remaining analysis/mobile work in items 5–7 is still pending; neither these
editor commands nor the gauges imply production live-tune qualification.

Items 1, 2 and 4 are implemented and verified: eleven added faces including the
premium STI/Evolution pair, retained-session gauges-only views and the protected
editor commands. Item 3 is complete: [1.1.2 is published](RELEASE_1_1_2.md) with all
seven platform packages and their checksum sidecars. The offline ECUFlash/Forester/EVO and companion-installer audit
is recorded in [the follow-up audit](GAUGE_EDITOR_AUDIT_2026-09-06.md).
Use [the post-1.1.2 plan](POST_1_1_2_WORK_PLAN.md) for concrete next steps and test
gates: reusable analysis setups first, then calculated mobile channels and the
remaining analysis/mobile backlog. Do not treat release publication as completion
of items 5–7 or as hardware qualification.

The next source patch is 1.1.3 (Android code 110407). Reusable JavaFX analysis
setups are implemented with strict file validation, explicit import review,
exact/ambiguity-aware channel matching, retained unresolved filters and guarded
asynchronous I/O. They do not yet ship in the public 1.1.2 packages. The rest of
item 5 is tracked in the progress entries below; item 6 and the remaining mobile
items remain open.

Portable calculated channels are now implemented in 1.1.3 development source,
including P200/P201, bounded hidden dependencies and explicit input-unit binding.
See [the calculation contract and verification scope](CALCULATED_LOGGER_CHANNELS.md).
This does not complete DimeMod runtime discovery, external serial sensors,
background recording, setup transfer or hardware acceptance.

The 1.1.3 source also provides reviewed, two-way linking of MAF/Injector sample
ranges and numeric filters. Link activation and condition edits clear affected
results and confirmations; setup/log replacement disconnects. Operating-condition,
range/statistics and curve/transfer progress is recorded below; remaining legacy
parity and hardware qualification are separate work.

Read-only [curve review](FUEL_CURVE_REVIEW.md) is now implemented in 1.1.3 source:
observed-bin interpolation with gap handling, bounded raw-sample polynomial fits
and descriptive injector line estimates. This advances fitting/interpolation;
remaining legacy parity and vehicle qualification remain separate work. No
calculated correction is applied automatically.

[Reviewed MAF-table transfer](REVIEWED_MAF_TRANSFER.md) is implemented in 1.1.3
source: explicit open-target selection, native stored-value/coverage review,
stale document/analysis/byte guards and one undoable offline edit. No automatic
ROM save or ECU write occurs. Injector transfer remains separate; a fitted
intercept is not a voltage-dependent latency curve. Public packages remain 1.1.2.

The optional [recorded-time rate filter](FUEL_RATE_FILTER.md) is implemented in
1.1.3 source, including explicit time scaling/gap limits, adjacent-row semantics,
linked condition drafts, strict version-2 setups and curve invalidation. This
advances transient filtering; named-condition progress is recorded below. The
remaining analysis/mobile backlog and supervised acceptance remain open.

[Named operating conditions](FUEL_OPERATING_CONDITIONS.md) are now implemented
for saved logs: all eight scalar gate categories, independent custom/rate filters,
explicit mappings and limits, strict version-3 setups and linked review. This
advances the filter migration without claiming automatic presets or live-capture
parity. Range/statistics progress is recorded below; injector transfer,
binned/comparison views, remaining mobile work and supervised qualification remain open.

[Three-workspace range sharing](SHARED_ANALYSIS_RANGE.md) is implemented in
1.1.3 source. Reviewed activation and an explicit shared Apply step keep Log
Analysis's cursor/views/range statistics aligned with the visible MAF/Injector
range drafts, while preserving independent fuel filters. Accepted-row progress
is recorded below; general bounded large-log review, injector transfer and the
rest of the analysis/mobile backlog remain open.

The shared [range-statistics arithmetic](LOG_ANALYSIS_ARCHITECTURE.md#finite-value-arithmetic--113-development-source)
now handles extreme finite values without the previous intermediate overflow
and squared-deviation underflow. This is a numerical audit fix, not accepted-row
inspection or bounded/asynchronous large-log processing.

[Accepted fuel samples](ACCEPTED_FUEL_SAMPLES.md) now provide exact original-row
inspection and per-channel statistics in both JavaFX fuel workspaces. Paged
readings, full-accepted-set statistics, explicit work/memory bounds, background
execution and stale-result disposal are implemented. This does not complete
general large-log handling, injector transfer, comparisons or the remaining
mobile work. Map-trace progress is recorded below.

[Read-only saved-log map tracing](LOG_MAP_TRACING.md) is now implemented in 1.1.3
source: reviewed capture of the selected editor table, frozen numeric geometry,
explicit axis-channel mappings, shared playback/range linkage and geometric
neighbor highlighting. It does not claim actual ECU lookup behavior, mutate the
editor table/selection or complete binned analysis/run comparisons.

[Binned saved-log analysis](BINNED_LOG_ANALYSIS.md) now provides one-axis and
two-axis aggregation, explicit counts/gaps, threshold-aware heatmaps, 2D curves
and isometric 3D mean plots. Applied-range linkage, bounded background work and
stale/cancelled result guards are implemented. Run comparisons, injector
transfer, general large-log handling and remaining mobile work are still open.

[Saved-run comparison](RUN_COMPARISONS.md) is implemented in 1.1.3 source:
independent CSV/range/channel selection, explicit common bins, count-aware
means/differences, shared-scale overlays, bounded second-log imports and stale
work guards. This advances item 6 without claiming automatic pull/time alignment
or statistical/vehicle qualification. Injector transfer, general large-log
review, portable setup transfer and remaining mobile work are still open.

[Portable channel setups](PORTABLE_LOGGER_SETUP.md) now have a shared bounded
codec and Android review/import/export controls. Ordered IDs/explicit units and
an exact definition fingerprint transfer without definition contents or live
state. Failed/stale imports preserve selection; intentional empty setups remain
empty and imports persist across restart without starting logging. Desktop
exchange controls, background recording, broad large-log handling and supervised
provider/hardware acceptance remain open.

[Desktop XML profile integrity](DESKTOP_PROFILE_INTEGRITY.md) is repaired as a
setup-transfer prerequisite: UTF-8/escaped attributes, captured protocol and
immutable items, preserved switch units, read-only loading, and synced atomic
save/backup replacement. This does not complete desktop `.rr2logger` controls,
global selection-order persistence or transactional live-runtime application.

The subsequent [desktop setup-transfer integration](PORTABLE_LOGGER_SETUP.md)
adds JavaFX File-menu import/export using the Android-compatible format, exact
loaded-definition bytes, reviewed replacement and cross-category order restored
into CSV registration. Stale/active-session rejection, rollback fault inhibition,
atomic export and separate persistence-failure reporting are implemented. Query
queues now use channel IDs and the last selection intent. Continue large-log
review and remaining mobile/service work; no hardware gate is discharged.

[Normal JavaFX CSV imports](BOUNDED_CSV_IMPORT.md) now use the same explicit
limits as second-log comparisons and a single owned, cancellable parser worker.
Superseded requests are interrupted and cancelled queued work is removed; stale
callbacks cannot replace a newer dataset. Continue large-log table/statistics
responsiveness and legacy Swing import handling before claiming broad parity.

The subsequent [JavaFX range-statistics worker](LOG_ANALYSIS_ARCHITECTURE.md#background-range-statistics--113-development-source)
moves initial and applied-range calculations off the UI thread with bounded
work, actual cancellation and late result/error rejection. Range drafts clear
old results, and completed output identifies its sample range and finite/missing
counts. Table setup/sorting, marker loading and legacy Swing work remain open.

[Log-table responsiveness](LOG_TABLE_RESPONSIVENESS.md) now avoids a boxed entry
for every source row and performs bounded stable sorting on a cancellable worker.
Requested sort headers and original sample/cursor identities survive row-list
replacement, range changes and source-order restoration. Marker loading and
legacy Swing behavior remain open; no hardware qualification is implied.

[Marker-file safety](MARKER_FILE_SAFETY.md) now adds asynchronous JavaFX loading
and saving, bounded strict version-1 validation, snapshot-based conflict checks
and required atomic replacement. Failed loads/saves cannot turn a partial list
into a replacement sidecar. Swing uses the same save guards; its asynchronous
handling remains open. Empty marker lists persist without deleting a sidecar.

[Swing log loading](SWING_LOG_LOADING.md) now prepares bounded CSVs, initial
whole-log statistics and marker snapshots off the event thread. New selections
cancel old work; detached/reopened panels reject late results and errors. Current
datasets remain usable during loading. Manual range statistics and marker writes
still need asynchronous Swing handling before moving on to broader mobile work.

Swing's subsequent range statistics and marker writes now also run off the event
thread. Range results are bounded and cancellable, preserve graph choices, and
cannot republish after dataset replacement or detachment. Marker proposals remain
unapplied until saved; accepted saves may finish after closure without updating
closed views. Reattachment after a pending save requires verification by reload.
Continue mobile/service work and compatibility audits; hardware gates stay deferred.

The [Android recording-ownership foundation](ANDROID_BACKGROUND_RECORDING.md)
now separates one-shot worker/resource lifetime from screen callbacks, with
immutable latest-cycle snapshots, cancellation/cleanup guards and retained CSVs.
It is not yet connected to the Activity: background recording remains disabled.
Next integrate the non-exported connected-device foreground service, explicit
notification Stop and same-session screen reattachment, then qualify those paths
on the isolated emulator before any supervised hardware acceptance.

The foreground service is now connected to the Activity in 1.1.3 source. Explicit
start transfers adapter ownership; Home/screen-off and screen replacement retain
the same recording, latest values, peaks and original receipt age. Notification
Stop, one-use requests, non-sticky restart behavior and cleanup guards are in
place. Continue service qualification and retained-recording recovery audits;
public packages and physical acceptance remain separate gates.

The retained-recording audit found that an unfinished final spool record could
block the entire export. [Reviewed recovery](ANDROID_RECORDING_RECOVERY.md) now
freezes and validates the completed prefix before destination writes, preserves
the original, reports omitted tail bytes and invalidates stale Activity reviews.
Continue larger-log mobile review and remaining platform/fork audits; provider
and physical USB acceptance remain separate from synthetic qualification.

The larger-log mobile pass now provides [bounded worker-based CSV summaries](ANDROID_CSV_REVIEW.md),
with cancellation, twelve-channel pagination and retention of the previous review
on failure. The owner's additional visual request adds Phosphor 84, Electric Bloom,
Sunset GT, Laser LED and Prism Cassette across Android/JavaFX/Compose. Android also
adds [full-screen mounted gauges](ANDROID_MOUNTED_DISPLAY.md): hidden app/system
chrome, foreground keep-awake even when stopped, accessible Stop/Exit and retained
recording identity. Saved 1–6 gauge layouts fill the mounted viewport without
scrolling, preserve face proportions and leave recording selections unchanged.
These changes are 1.1.3 development source, not a republished
1.1.2 release. Continue native qualification and remaining fork/platform audits;
physical acceptance stays deferred.
