# Desktop Logger audit — September 7, 2026

Follow-up: the implementation findings below have been addressed locally. See [September 8 fixes and validation](DESKTOP_LOGGER_FIXES_2026-09-08.md). This document preserves the original audit evidence; its findings are not a current unresolved-bug list.

Scope: all eight tabs in the primary desktop Logger, shared setup/connection/recording controls, and the dedicated gauge display. Reviewed local source at `b9327ad7` plus the pending startup-preference and Dashboard changes. Android and Evo calibration work are outside this audit. This is an offline code, automated-test, and synthetic UI review, not vehicle qualification or a claim that every interaction is verified.

## Findings to address before another release

1. **High — Dyno does not synchronize RPM and speed by timestamp.** `FxDynoPane.project` (line 245) tail-aligns lists by index and uses only speed timestamps. RPM readings from an entirely different time interval can therefore produce a curve. Missing samples or different polling rates can associate power with the wrong RPM and distort torque. Match samples by time with a bounded tolerance, reject gaps, and select one continuous pull instead of sorting all accelerating points in the retained history into one curve.

2. **High — rejected Logger Setup changes remain in memory.** `FxLoggerSetup` (line 116) changes global settings before `reloadConfiguration()` validates them. Its exception handler shows an error but does not restore the previous configuration. A nonexistent XML path, for example, leaves that invalid path in settings even though the dialog reports failure and the old channel catalog may remain. Validate a candidate configuration before applying it, and roll back failed application/persistence. The preference-only save improvement does not solve this separate transaction defect.

3. **High — unknown transport/module selections silently change destination.** `LoggerDesktopRuntime.configureDestination` (line 644) falls back to the first transport/module and updates settings. `validateLoggerConfiguration` checks required fields and XML loading, not membership of the requested transport/module. Reject unsupported explicit selections or require a visible selection; do not silently substitute a different ECU target.

4. **Medium — Graph is not an interpretable time-series display.** `FxLoggerWindow.LiveGraph` (line 1422) has no channel legend, labeled time axis, or numeric/unit scales. Each series independently spans its own minimum/maximum and its own list length, with four recycled colors. Unequally timed samples appear equally spaced, and different channels can overlay perfectly despite different values. Use a common timestamp axis, identifiable series, explicit scales/units, and gaps for missing data. The synthetic two-channel capture visibly demonstrates indistinguishable overlaid trends.

5. **Medium — Dyno accepts unsupported speed units and retains obsolete results.** `metersPerSecond` (line 274) treats everything other than a km/kph label as mph, including `m/s`, blank units, and non-speed channels. Both selectors permit any selected logger channel. Changing inputs does not invalidate the displayed curve/peaks; the calculation error handler (line 237) also leaves the previous result visible. Require supported dimensional units, distinct valid channel mappings, and clear or visibly mark stale results when inputs change or calculation fails.

6. **Medium — compact layouts hide important information.** At 1024×768 with the channel rail visible, the Dyno setup consumes most of the available width and truncates the chart title. MAF/Injector truncate sample-boundary labels and constrain result tabs/tables, including in the 1280×800 capture. Data's initial column widths total 1,065 pixels, putting all three statistics columns off-screen in the compact capture. Horizontal scrolling exists; this is a usability issue, not lost data. Collapse/rescale the setup areas and choose responsive table widths. An earlier window-manager-free 640×480 capture was below the declared 900×620 minimum and is not evidence of a supported-size defect. A 1280×800 capture is a Steam Deck-sized desktop proxy, not verification on an actual Deck.

7. **Medium — live refresh rebuilds interactive Dashboard controls.** `FxLoggerWindow.refreshViews` (line 558) clears and recreates Overview and Dashboard cards on sample refresh, including hidden tabs. Card menu and resize controls therefore have short-lived node identities during logging. Code confirms the recreation; menu dismissal/drag interruption and CPU impact still need a dedicated sustained-stream reproduction. Keep card/control instances stable and update values in place. Do not treat the current Dashboard visual reorganization as resolving this lifecycle risk.

## Tab-by-tab coverage

| Tab | Review and automated coverage | Outcome / remaining gate |
| --- | --- | --- |
| Overview | Synthetic selected channels, empty-state code, live refresh path, small/laptop captures | Basic value cards render; shared card recreation needs load testing. |
| Data | Live table, units, rolling statistics, reset semantics; statistics/display tests | Statistics work is covered; responsive column sizing needs improvement. Reset intentionally clears view history, not the CSV. |
| Graph | Canvas drawing, invalid-value filtering, time and scale semantics; synthetic capture | Confirmed interpretability/timing deficiencies above. Requires dedicated timestamp and series-identification tests. |
| Dashboard | Tile roles/sizes/style/limits/detach code, synthetic captures, mounted-gauge transition tests | Separate Gauges-only tab removed locally; launch button and collapsed customization remain in Dashboard. Stable live controls still need work. |
| Dyno | Projection/validation source and existing acceleration/deceleration/channel-guess tests; captures | Timestamp synchronization, unit rejection, stale-result invalidation, continuous-run selection, and compact layout need work. Existing happy-path tests do not establish accuracy. |
| Log Analysis | CSV parsing/loading, playback/ranges, sorting/source identity, markers, statistics, X/Y, bins, comparison and map-trace tests; capture harness loads synthetic CSV | Stronger existing offline coverage; retain large-log/performance and manual interaction qualification. No claim of vehicle-data validity. |
| MAF | Mapping confirmation, filters, sample ranges, invalidation/cancellation, linked conditions/setup, curve/sample views and transfer tests | Existing guards are present; compact setup/result presentation needs improvement. Outputs explicitly require review, not automatic application to a ROM. |
| Injector | Shared analysis lifecycle plus injector mappings/fuel assumptions and tests | Same compact-layout concerns; four-cylinder/fuel-property assumptions require explicit confirmation. No changes to vehicle definitions. |

Shared review includes initial connecting versus reconnecting wording, startup preference editing during attempts, destination validation, file-capture stop handling, and session command guards. A very early Stop command can be ignored while a command is pending or before state leaves STOPPED (`LoggerSessionService.submit`); classify this as a race-risk requiring a deterministic cancellation test, not a reproduced vehicle failure. `QueryManagerImpl.runLogger` does notify STOPPED on a communication exception, which reaches the file-capture stop monitor; do not incorrectly report that recording necessarily continues through reconnect.

## Local changes already requested separately

- Startup auto-connect preference can be saved without reconfiguring an active connection worker; connection-field changes remain guarded. Setup has an explicit Disconnect / stop attempts action.
- Desktop Dashboard has one entry point to the dedicated gauge display, a consolidated tile customization panel, and compact per-tile menus. Android is unchanged.
- Those changes are local, not installed or published by this audit. Newly identified audit findings have not been silently implemented.

## Validation and limits

- Core `ant unittest`: passed (optional corpus-dependent tests may skip).
- Full JavaFX suite with native-window smoke tests and the audit capture enabled: **263 tests passed**, zero failures/skips, under Xvfb with a functioning Openbox window manager. This includes the pending startup-preference and Dashboard changes. A passing suite does not negate the uncovered logic/UX gaps above.
- Compose suite: **38 passed, 8 conditional native-gauge tests skipped**, zero failures; compilation includes the pending desktop navigation/setup changes.
- `git diff --check`: passed.
- Opt-in `FxLoggerAuditCaptureTest` captures all eight tabs at 1024×768 and 1280×800 using in-memory synthetic channels and a synthetic CSV; it never starts a connection or opens a log writer. Captures are local under `/home/tristan/.cache/rr2-logger-audit.G3iB4E` and are not release assets.
- Initial UI runs without a functioning window manager failed focus/placement checks. The first capture harness also left synthetic LIVE state active when closing, triggering the application's legitimate disconnect confirmation; the harness now publishes STOPPED before cleanup. These are not established product defects.
- No real adapter/ECU commands, road pulls, Windows/macOS validation, actual Steam Deck touch checks, or sustained multi-hour recording tests were performed.

## Repair order

1. Make setup transactional and reject unsupported destinations; prove early cancellation behavior.
2. Correct Dyno synchronization/units/result invalidation and establish numerical regression tests.
3. Replace the live Graph's sample-index plot with labeled timestamp-aware series.
4. Stabilize Dashboard controls during live updates; then address responsive layouts across every tab.
5. Repeat the full offline suite and prolonged synthetic recording, then qualify connection loss/recovery and CSV output with an adapter. Vehicle tests come last, not as a substitute for deterministic tests.

Forester DimeMod note: the Validated folder's logger XML is standard v370 plus exact-ECU overrun additions, not a static list of every DimeMod parameter. RR2 appends dynamic DimeMod channels after successful metadata discovery from the ECU. Loading that XML offline alone does not establish a missing-channel defect; absent parameters after successful live discovery need a separate trace.
