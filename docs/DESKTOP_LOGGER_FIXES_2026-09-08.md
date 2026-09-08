# Desktop Logger fixes — September 8, 2026

Follow-up to the [all-tab audit](DESKTOP_LOGGER_AUDIT_2026-09-07.md). Changes are local; no release was published and no vehicle communication was performed.

## Completed

| Area | Change |
| --- | --- |
| Setup and Load Definition | Share a validate-before-apply transaction. Unsupported protocol/transport/module combinations are rejected. The module dropdown previews a newly chosen definition without applying it. Failed application/save restores settings and prior selected channels/conversions; failed restoration inhibits connection until restart. Desktop definition parsing no longer invokes the legacy loader's settings-changing fallback. |
| Settings files | Serialize to a sibling temporary file and atomically replace the destination only after serialization succeeds. Failed writes retain the previous file. Filesystems without atomic replacement report a failure instead of performing an unsafe overwrite. |
| Connection controls | Stop is retained behind a pending start, including the interval before CONNECTING is published. Queued commands check disposal. Startup preference remains editable during attempts without reloading transport settings. |
| Dyno | Match RPM to speed by timestamp, accept only supported speed units, restrict channel mappings, select a continuous pull, and invalidate results on changed inputs or failed calculation. Resetting live history clears the old result. |
| Graph | Shared timestamp axis, numeric elapsed-time labels, explicit per-channel ranges/units, color-and-dash legend, conversion-compatible samples, and visible discontinuities. Hidden Graph does not redraw on every sample. |
| Overview and Data | Reuse Overview cards; retain Data's selected channel across updates; size all six Data columns to fit compact windows. |
| Dashboard | Stable card/menu/resize targets through live updates; update native instrument readings in place. Detached windows reuse their cards and clear their caches when closed. One Dashboard button opens the dedicated gauge display; no duplicate Gauges-only tab. |
| Dyno, MAF and Injector layout | Compact workspaces use expandable setup drawers instead of permanently squeezing the chart/results. MAF/Injector field labels wrap and result columns resize. Wide workspaces keep side-by-side setup. |
| Log Analysis | Existing CSV, range, cursor, markers, sorting, bins, comparison and linked-analysis functionality retained and included in the full desktop regression run. No unsupported calculation changes were made merely to change this tab. |

## Calculation and display semantics

- Dyno supports mph, km/h, kph, kmh, m/s and mps. RPM must be labeled rpm. Each speed reading requires an RPM reading within 250 ms. These are synchronization limits, not a claim of measurement precision.
- Pull segments split at invalid/nonaccelerating samples, speed intervals over two seconds, non-increasing RPM, or a greater-than-15% change in RPM/speed ratio. The latest segment with at least three calculated points is used. These conservative heuristics help avoid joining shifts/separate pulls; they are not automatic gear detection or certified dyno accuracy.
- Graph displays each channel against its own explicitly labeled numeric range. The vertical percentage refers to that range, not a shared physical unit. All traces use the same time axis; gaps over two seconds and nonfinite readings are not connected.
- Compact setup drawers are deliberate controls, not missing functionality: expand **Run setup and calculate** or **Analysis setup · channels, filters and units** to configure the task.

## Verification

Automated regression coverage was added for mismatched timestamps, unit conversion/rejection, separate pulls, Dyno result invalidation, graph timing/gaps/conversion identity, invalid destinations, save rollback, atomic-file failure preservation, pending-start cancellation, responsive setup drawers, and Dashboard control identity during 100 synthetic updates.

Final validation passed:

- Core `ant unittest build-linux` (optional corpus-dependent skips remain).
- JavaFX desktop: **272 passed**, zero failures/skips, with native-window smoke tests and all-tab capture enabled under Xvfb/Openbox.
- Compose: **38 passed**, zero failures; **8 conditional native-gauge tests skipped**.
- `stageJavaFxLinux` and `git diff --check` passed. These are local build outputs, not an installed application or public release.

Screenshots use synthetic channels and an in-memory CSV, never a connected adapter. Existing parked definition-installer changes were preserved, not edited by this work.

Still requires real-environment qualification: adapter disconnect/recovery, in-car sampling and Dyno comparison, actual Steam Deck touch/full-screen behavior, Windows/macOS native checks, and a multi-hour recording soak. Those are verification gates, not remaining implementations from this audit. Android UI and Evo calibration definitions are unchanged.

## Follow-up recording and stress pass

Further offline review found and fixed additional recording-lifecycle defects:

- Rapid captures used second-resolution filenames with overwrite semantics. Files now use atomic create-new and numbered collision suffixes, retaining the normal RomRaider CSV header/timestamp format.
- Partially collected rows could survive stop/start. Recording start, stop, cleanup and reset now clear pending row state; zero-channel responses do not create empty timestamp-only rows.
- Names/units containing delimiters, quotes or newlines now receive CSV field escaping.
- The relative-timestamp origin uses an explicit initialization flag, so a first timestamp of zero is handled correctly.
- File-close failure clears internal recording/stream ownership, and write errors retain their original exception if cleanup also fails. Failed header writes do not announce recording success.
- File capture state now overlays the connection state: stopping capture restores external-only mode correctly and never changes STOPPED into ECU LIVE. Connection-stop cleanup executes its control-reset callback even if file closing fails.
- The asynchronous response worker now has a bounded 2,048-response queue, preserves response order, honors stop-before-start, and exposes handler failures/overflow to the polling owner. Saturation stops the session visibly rather than silently dropping samples. Worker shutdown is awaited before replacement; overlapping update generations are rejected. Transport cleanup still runs when worker shutdown reports an error.

Verification passed on this follow-up:

- Twenty captures forced to the same filename timestamp, with **20,000 total rows**, retained all distinct values and correct row timestamps without overwrites.
- Partial-row restart, quoted headers, failed writes, external-only/disconnected state preservation, queue overflow, worker failure, stop-before-start and **1,000-response ordered dispatch** regressions passed.
- UI stress delivered **32,000 synthetic channel updates**, made **320 tab selections** and **40 gauge-display round trips**, retained recording state, and bounded history at **2,000 readings per channel**. This was an accelerated test, not a multi-hour wall-clock soak.
- `ant unittest build-linux` passed. Desktop UI run: **272 passed**, no failures, one opt-in screenshot-capture test skipped; stress was enabled. Compose: **38 passed**, eight conditional native tests skipped. JavaFX Linux staging and `git diff --check` passed.

No installed application, GitHub release, ECU or vehicle was changed by this pass.
