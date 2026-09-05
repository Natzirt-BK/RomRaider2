# Desktop repair audit — September 4, 2026 (Pacific)

September 5 follow-up: [B1 repair and prerelease refresh](DESKTOP_CSV_RELEASE_REFRESH_2026-09-05.md)
supersedes this report's B1 and publication-HOLD status. The user authorized the
current RC4 prerelease refresh; B2 and the uncompleted acceptance/hardware gates
remain open. The evidence below records the September 4 candidate, not the new ZIPs.

## Outcome

The requested A1–A9 repair sequence is implemented and pushed. **Release HOLD
remains**: new audit findings affect overlapping CSV loads and an optional Windows
sensor plugin, and the external acceptance matrix is not complete. This report supersedes the current-status
claims in the [original audit](DESKTOP_AUDIT_2026-09-04.md); its reproductions
remain historical evidence, not claims that repaired defects still reproduce.

Code candidate: `4d4fae1cf190103fef84a2c9205a2a5e64f18c21`, comprising the prior
`b587c6c5` safety repair, `e2a3967f` desktop repair and `4d4fae1c` full-screen
correction. [Both native build jobs passed](https://github.com/Natzirt-BK/RomRaider2/actions/runs/33948980266).
Later documentation-only commits do not change this candidate's code or hashes.

## Verified evidence

| Check | Result and scope |
| --- | --- |
| Core Java | 344 tests in 90 suites: 341 passed, three optional skips, zero failures/errors. Linux and Windows CI passed. |
| JavaFX | 30 local tests passed with native Xvfb smoke enabled; Linux CI passed. Windows CI deliberately skips the 11 opt-in native-window tests, running the other 19. |
| Retained Compose | 34 tests passed locally; not included in the default desktop package. Its legacy worker-side checksum preparation remains distinct from the JavaFX owner-thread save path. |
| Portable core | Dedicated executable check passed; its disabled JUnit task is not counted as a pass. |
| Windows bridges | 45 tests passed; both architecture helpers built. No vendor driver or ECU hardware was exercised. |
| Distribution | Eight pinned dependency hashes and both CI package verifiers passed. Downloaded archive SHA manifests passed. This verifies bytes, not a comprehensive vulnerability certification. |
| Linux native package | Downloaded package verifier passed. All 21 synthetic repair assertions passed using a diagnostic copy's bundled runtime/classes at 853×533 Xvfb. A separate normal-launcher copy opened Editor and Definition Manager in an isolated network namespace. |
| Windows native package | Downloaded package verifier passed in Windows 11. All 21 synthetic assertions passed at both 100% and process-local forced 150% JavaFX scale; the unchanged normal launcher also opened Editor and its bounded Definition Manager. The optional LM-2 MTS plugin failed to load (B2). This is a VM at 1280×800, not physical mixed-DPI qualification. |

Exact ZIP SHA-256 values:

- Linux: `b2cc5454068209033882c8813da9e28f3a087dd212c91ac589595ca345d26cb3`.
- Windows: `67ab945c3eac1f68eb3795f187817fd71da31b27575e95281497b41b54d469c0`.

The [diagnostic probes](../packaging/java21/smoke/README.md) add one JAR and change
the main class only in disposable copies. Original archives and extracted
verification trees stay unchanged. They test actual packaged JVM/application
bytes but are not unmodified production-entry-point workflow acceptance.
Synthetic Save As bypasses the native file chooser, and synthetic reopen does
not qualify XML-definition matching or real checksum dialogs. Snapshots captured
immediately after sizing can precede a scene resize pulse; native window-bounds
assertions and normal-launch screenshots are separate evidence.

## Repair disposition

| Finding | Current disposition |
| --- | --- |
| A1–A3: close, target tab, save baseline | Implemented. Guarded close/cancel, explicit background tab, immutable save snapshot, owner-thread publication, pending-close rejection, later edits, failure, undo/redo, Save As and saved baseline have focused tests. Packaged synthetic checks support this result, not real-ROM qualification. |
| A4: analysis sorting | Implemented and tested. Numeric columns sort numerically; view selection seeks source IDs and playback selects the matching item after sorting. Statistics sort numerically too. |
| A5: small layouts | Inspector/Dyno scroll recovery, wrapped header/actions and bounded owned windows implemented. Native modal fitting waits until GTK geometry is available. Physical mixed-DPI and every dialog/profile combination remain unqualified. |
| A6: analysis ranges | Implemented and tested. One-based inclusive controls update table, charts, statistics and playback bounds; markers outside the range expand the view before seeking. Time Series still displays one selected channel, not a multi-channel comparison plot. |
| A7: settings and editor parity | Six settings tabs, axis editing, rectangular interpolation, full-table copy, offline Live Tune evidence, UI scale and touch profile implemented. Settings cancellation and legacy click semantics tested. Swing-only controls are explicitly labeled compatibility preferences, not JavaFX features. |
| A8: auto-connect | Consumed once after valid file configuration through the existing guarded session lifecycle. Disabled, already-running and deferred-setup behavior tested with a stub; no actual connection was attempted. |
| A9: spaced IPC paths | Versioned, bounded UTF-8 framing and an isolated socket round trip pass. Older senders are accepted; older running receivers cannot decode the new protocol, so an upgrade needs the old instance closed. |

## Full 26-item re-audit

`RETEST` means the implementation has supporting evidence, but the exact full
user workflow has not been accepted on both clean and upgraded installations.
No older Compose or user approval is transferred to this JavaFX candidate.

| ID | Workflow | Current audit result / remaining gate |
| ---: | --- | --- |
| 1 | 3D rotation | RETEST: separate pitch/yaw present; representative maps, mouse and touchpad acceptance remain. |
| 2 | Channels toggle | RETEST: repeated-toggle implementation retained; earlier native evidence exists, but current complete selection/persistence matrix remains. |
| 3 | Logger definition action | RETEST: visible button and setup/menu routes retained. |
| 4 | Linux XML visibility | RETEST: native chooser wiring present; exact-package replacement/cancel and upgrade matrix remain. |
| 5 | Output folder browser | RETEST: native directory browser retained; current clean/upgrade persistence workflow remains. |
| 6 | ECU chooser cancellation | RETEST: manager cancellation and modal ownership supported; repeated native chooser cancellation followed by real-definition ROM loading remains. |
| 7 | Existing ECU definitions | RETEST: configured list retained; full add/re-add/remove/reorder persistence remains. |
| 8 | ROM/calibration tabs | SAFETY REPAIRED: wrong-background-tab regression covered; complete multi-ROM/table selection/layout acceptance remains. |
| 9 | Save behavior | SAFETY REPAIRED: synthetic package save/close baseline checks pass; native Save As/overwrite and real-definition reopen remain. |
| 10 | Definition Manager placement | IMPLEMENTED/RETEST: adjacent action retained; wrapped buttons and native work-area fitting tested. |
| 11 | User levels | RETEST: five levels/filtering/persistence retained; exact retained-behavior comparison remains. |
| 12 | Comprehensive settings | IMPLEMENTED/RETEST: Editor, Appearance, Tables, Colors, Clipboard and labeled Compatibility tabs; cancel/apply and XML dimension migration covered. |
| 13 | Desktop character | RETEST: branding and dense workspaces retained; aesthetic approval is the user's decision. |
| 14 | Light/Dark themes | RETEST: two themes retained; touch changes preserve owned styles; complete current-candidate theme matrix remains. |
| 15 | Checksum warning | NOT QUALIFIED: shared interaction boundary tested, but safe-fixture native visual/keyboard acceptance remains. |
| 16 | Windows menus/DPI | RETEST: native package/forced-scale evidence is limited to a VM; physical mixed DPI, multiple monitors and focus/menus remain. |
| 17 | Compare routes | RETEST/GAP: toolbar and Tools invoke one workspace, but it still selects the first two ROMs rather than an explicit arbitrary pair. |
| 18 | Linux clean/upgrade definitions | NOT QUALIFIED: separate unpublished installer preservation work must be reviewed; installer music publication does not publish that patch. |
| 19 | More than six Overview tiles | RETEST: wrapping/scrolling retained; historical 12-tile synthetic no-overlap evidence remains supporting only. |
| 20 | Road Dyno | LAYOUT REPAIRED: calculation reachable through scrollable setup; formulas tested, calibrated recorded-data validation remains. |
| 21 | Gauge customization/detach | RETEST: selected-tile style/size/color/detach controls retained; complete per-tile persistence, resize and live-update acceptance remains. |
| 22 | In-context Open Log/playback | SORT/RANGE REPAIRED; B1 below blocks complete load/marker qualification. |
| 23 | File → Open CSV | SAME WORKSPACE; B1 remains. |
| 24 | Full Log Analysis | RANGES RESTORED; B1, populated recorded-log acceptance and any desired multi-channel plot remain. |
| 25 | Top navigation | RETEST: top tabs retained; narrow keyboard/focus/accessibility qualification remains. |
| 26 | Density/Touch | IMPLEMENTED/RETEST: reversible touch targets, runtime profile and touch launch option applied; actual full-screen flag corrected. Physical handheld/touch acceptance remains. |

## Next findings and priority

### B1 — P2: overlapping CSV loads can associate data with the wrong marker file

`FxLoggerWindow.openLog()` sets shared `activeLogFile` before launching each
background parse. Completion calls `showDataset(dataset)`, which reads the
*current* shared file rather than the file belonging to that parse. There is no
request-generation or disposed-window guard in the completion callback.

Controlled completion-state reproduction on this candidate, using the actual
window/pane and two synthetic names:

```text
REPRODUCED dataset=first-request.csv markerSource=second-request.csv
```

This reproduces the association after a later request has replaced the shared
file; it is not a timing measurement of two native chooser operations. No marker
was written or deleted. Source analysis shows that adding/removing markers then
targets the wrong `.rr2markers.properties` sidecar. CSV content is not written by
the marker service. An older load can also replace a newer analysis view; a
completion after closing Logger can create a new undisposed analysis pane.
This path predates the A4–A9 repairs; it was discovered during this re-audit.

Next code change: make each load own its immutable source and generation, ignore
stale or disposed completions, close superseded panes, and test reversed
completion order, failure, close-during-load and marker source identity. Do this
before treating Log Analysis as release-qualified.

### B2 — P2: optional Innovate LM-2 MTS plugin fails on native Windows x64

Both Windows diagnostic runs reported failure to load the external
`Lm2MtsDataSource` plugin. The first run's COM4J initialization failed while
loading its native resource; the second reported `com4j-amd64.dll` as an invalid
Win32 application. Other plugins loaded and the Logger UI/display assertions
continued successfully. This is a plugin-capability failure, not a failed
package checksum or evidence that every Logger transport is broken.

Read-only JAR/bytecode inspection confirms a naming mismatch: the bundled JAR
contains `com4j/com4j-x64.dll` and `com4j/com4j-x86.dll`, while its loader constructs
`com4j-` + `os.arch` + `.dll`; this x64 JVM reports `amd64`. Its fallback opens
the output file before copying the missing resource and can leave an empty DLL.
That explains the initial null-stream exception and is consistent with the
subsequent invalid-image error.

The two scale runs reuse a diagnostic copy; the second failure includes state
left by the first, not an independent clean-start reproduction. The probe never
connected this sensor or qualified the vendor COM server's architecture. Next:
qualify a clean x64-compatible loader/vendor path, or explicitly disable this
unsupported plugin with an actionable explanation. Do not spoof global JVM
architecture or replace signed vendor drivers to conceal this mismatch.

### Remaining qualification work

1. Resolve B1 and the optional Windows plugin's B2 compatibility boundary, then
   improve explicit ROM-pair selection and validate representative
   edit/undo/paste/Save As/reopen/checksum and populated-log/marker/Dyno/gauge flows.
2. Review the separate installer definitions-preservation patch and validate
   clean/upgrade migration in disposable installation roots. Preserve the
   unrelated local installer commit; do not reset it or silently publish it.
3. Run physical DPI/multi-monitor, keyboard/accessibility and real-device Logger
   tests, then obtain user acceptance tied to the two archive hashes.
4. Only after those gates and explicit promotion authority: publish release
   artifacts/update installer pins. ECU memory writes remain outside this work.

## Boundaries and handoff

No real ROM was edited, no ECU connection or production memory write was made,
and no raw vehicle files or private screenshots were committed. Probe runtime
warnings about deliberately absent definitions are not connection tests.
The separate installer music fix remains published at `c7e3f92` with
[successful CI](https://github.com/Natzirt-BK/subaru-ecu-tools-linux/actions/runs/33946567972).
Its unrelated unpublished local preservation commit remains untouched.

No release tag, public release asset or installer download pin was changed.
Evidence logs, ZIPs, diagnostic copies, synthetic files and screenshots remain
in private temporary folders and the dedicated Windows VM review folder.
