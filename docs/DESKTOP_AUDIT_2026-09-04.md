# Desktop audit — September 4, 2026

Current results and next priorities: [completed repair re-audit](DESKTOP_REPAIR_AUDIT_2026-09-04.md).
The original findings below are historical; the linked report identifies the
exact repaired candidate, package hashes and remaining gates.

## Verdict

### Repair follow-up (current work)

The original findings below are retained as historical evidence. A1–A3 were
repaired in `b587c6c5`; both native CI jobs passed. Diagnostic copies of that
candidate's Linux and Windows packages passed ten synthetic save/close cases
using their packaged JVM/application classes. Original archives were unchanged;
the diagnostic copies added a probe JAR and changed their launcher entry point.
This is not native file-chooser, real-definition/checksum, or hardware acceptance.

The A4–A9 follow-up now implements:

- Numeric source-sample sorting and linked table/chart/statistics/playback ranges.
- Scrollable calibration and Dyno controls, wrapped Dyno header and definition
  actions, bounded owned windows, and post-native-show modal fitting.
- Axis editing and rectangular interpolation through shared undo/redo history,
  configurable full-table clipboard headers, and a read-only Live Tune draft
  with before/after bytes and explicit unqualified-runtime evidence.
- Editor behavior/warning, font, cell size, color, clipboard, UI scale and touch
  preferences. Swing-only floating-frame/icon/overlay preferences are explicitly
  labeled compatibility settings, not claimed as active JavaFX functionality.
  Legacy cell dimensions do not silently shrink the JavaFX table defaults.
- Once-only Logger startup auto-connect through the existing guarded session
  lifecycle; tests use a stub and never connect hardware.
- Length-framed UTF-8 launch arguments, bounded socket reads and compatibility
  with older senders. An isolated ephemeral-port test covers paths with spaces
  and Unicode without contacting the user's existing application instance.

Local verification: 344 core tests (341 passed, three optional skips), 30 JavaFX
tests with native Xvfb smoke enabled, 34 Compose tests, portable-core check, eight
pinned dependency hashes, and a synthetic repair probe. Settings cancel/apply,
legacy click semantics, numeric sorting/ranges, actual axis/interpolation button
actions, reversible touch styling, small scroll recovery and modal placement
have focused regression coverage. Matched Linux/Windows CI and packaged probes
passed; see the linked re-audit for their scope and the next CSV-load finding.

**Release remains HOLD.** A complete physical-display, hardware, clean/upgrade,
native file-dialog, recorded-data and user-acceptance matrix is not implied by
these automated repairs. The linked follow-up distinguishes implemented fixes
from those external qualification gates; no release assets or pins are promoted.

### Original audit verdict

**HOLD: not release-qualified.** The startup sizing correction is implemented,
tested, pushed, and verified on fresh native Windows launches. The wider audit
found reproducible document-safety and analysis defects plus incomplete parity
work. Successful compilation and package verification do not clear these gates.

This is a source, automated-test, synthetic-workflow, and desktop smoke audit of
the default JavaFX desktop against all 26 AUG2 findings. It is not a claim that
every hardware, upgrade, accessibility, or physical-display acceptance test was
performed. No ECU connection, recording, flashing, or production memory writing
was attempted. Real vehicle files were not edited or published.

## Identity and checks

- Startup fix: `64ce679de3e65eeef12329c3cbaf1fcbcff98d29`.
- Audited candidate: `80ae9db2eccb5c77ba1b1cbb862b11c96d1fb138` on
  `feature/romraider2-foundation`. The second commit only corrects a Swing test's
  event-thread use; production desktop code is unchanged from the startup fix.
- [Successful native Linux/Windows build](https://github.com/Natzirt-BK/RomRaider2/actions/runs/33943595141).
- Linux ZIP SHA-256:
  `ceda415581cc3a1d1aedcace4c4d7a93026e47bf6e9ca80b15c67b9a33270e0b`.
- Windows ZIP SHA-256:
  `330489f99bd1af6d7d06246acc01655211f15185cc79f64c6cf656762f3ba8e1`.
- Both downloaded ZIPs and internal checksum manifests passed; VERSION.txt
  identifies the candidate commit. Native CI verifiers passed, the downloaded
  Linux verifier passed locally, and the Windows verifier ran in the guest
  before its fresh launch. The extracted application contents were not patched.
- Shared Java suite: 330 tests across 86 suites, zero failures/errors, three
  optional skips (native J2534 linking fixture, external log corpus, BMW XDF
  corpus). Thus 327 tests executed successfully, not 330 hardware passes.
- JavaFX: 18 local tests passed, including five placement geometry cases and
  the opt-in native-window smoke test under Xvfb. Linux CI runs the native-window
  test under Xvfb; Windows CI skips that opt-in test and runs the remaining 17.
- Retained Compose compatibility: 34 tests passed. Portable shared-core `check`
  passed its dedicated executable check; its disabled JUnit task is intentional.
- Windows bridge: 45 tests passed; both x86 and x64 helpers built.
- Eight audited dependency hashes verified. This verifies the pinned bytes, not
  absence of vulnerabilities across every bundled legacy dependency.
- First CI attempt failed in the existing Swing chrome test with a concurrent
  JLayeredPane mutation. Running its assertions on the Swing event thread fixed
  that test; the full local suite and both native CI jobs then passed.

## Confirmed defects and source gaps

### Follow-up — A1–A3 safety repair

The subsequent safety repair changes the default JavaFX desktop as follows:

- File/menu/runtime closes now dispatch the same vetoable close request as the
  native window button. Cancel leaves the document and recovery state intact;
  Logger menu close uses the same request helper.
- Closing a ROM tab targets that ROM explicitly and reads current session state.
- JavaFX saves capture output bytes and cell/axis revert values on the document
  thread, write a private snapshot in the background, then publish the saved
  baseline on the JavaFX thread. Edits made after capture remain dirty, and
  failed writes do not publish a new filename or saved baseline.
- Pending saves prevent duplicate saves and document/application disposal until
  publication finishes. Cancelling an observer future cannot bypass cleanup.
- The retained Compose compatibility entry point keeps its existing worker-side
  checksum preparation: its question handler blocks that worker while the UI
  answers. This repair does not claim event-thread snapshot capture for Compose.

Local verification: 337 shared Java tests (334 passed, three optional skips),
21 JavaFX tests including native Xvfb close/cancel/tab smoke tests, 34 retained
Compose tests, portable shared-core check, Linux compilation and JavaFX staging.
New coverage includes delayed completion, edits during I/O, failed writes,
Save As, undo/redo against the saved baseline, axes, pending-close rejection,
cancelled save observers, and legacy checksum preparation threading.

These are source/regression-test results, not native Windows or hardware
qualification. The original candidate evidence below remains historical;
A4–A9 and the broader acceptance gates remain open. **Release verdict: HOLD.**

### A1 — P1: File → Exit bypasses unsaved-change confirmation

`FxEditorWindow.java:172` invokes `stage.close()` directly, whereas the warning
is attached to `WINDOW_CLOSE_REQUEST` at line 133. The native title-bar close
request and programmatic close are not equivalent paths. Disposal calls
`EditorDocumentController.close()`, which clears ROM data and marks recovery
resolved, so this is a potential loss of both unsaved work and its recovery.

Synthetic UI reproduction: create a dirty document, fire the File → Exit menu
item, and count close-request events. Result:

```text
DIRTY_FILE_EXIT dirtyBefore=true showingAfter=false closeRequests=0
```

The Logger File → Close action has the same direct-close pattern
(`FxLoggerWindow.java:187`), bypassing its active-session confirmation. The
runtime does perform cleanup; an active hardware-session reproduction was not
attempted. Route user-initiated closes through one guarded close operation.

### A2 — P1: closing a background ROM tab closes the active ROM

`FxEditorWindow.java:473` activates the clicked document, then immediately calls
`closeActiveRom()`. That method reads the window's cached `snapshot`. The session
listener at line 101 queues the replacement snapshot through `Platform.runLater`,
so it has not arrived during the close handler.

Reproduction: open synthetic documents A and B, leave A active, fire B's tab
close request. Observed remaining document: B; A was closed. This can prompt for
or discard the wrong workspace. Pass the clicked document explicitly to the
close operation, without relying on a deferred active-state render.

### A3 — P1: save completion can clear unsaved changes made during the save

`EditorDocumentController.java:157` saves on a worker while editing remains
available. `RomFileService.java:23–29` produces output, writes it, then calls
`RomChangeService.markSaved(rom)` against the *current* mutable document, not the
revision/bytes actually written. `Rom.saveFile()` normally returns its live byte
array, creating an additional concurrent-write concern.

A controlled save-boundary probe captures output, delays completion, changes
the in-memory document, then completes the save. Observed:

```text
SAVE_RACE disk=20 memory=30 dirty=false
```

This reproduction uses a synthetic ROM subclass to control timing; it is not a
claim that a real vehicle file was corrupted. Freeze/capture a save revision and
only mark that revision saved; serialize close/save and preserve later edits as
dirty. Atomic replacement already protects the destination from many partial
file-write failures, but does not solve this document-state race.

### A4 — P2: sorting Log Analysis disconnects selection from playback

The table stores source-row IDs, but `FxLogAnalysisPane.java:199` passes the
selected *view index* to `playback.seek`. Cursor updates likewise select a view
index at line 226. Columns remain sortable.

Reproduction: load three synthetic samples, sort Sample descending, select the
first visible row. Observed:

```text
SORTED_LOG selectedSourceRow=2 playbackSourceRow=0
```

Seek by the selected source-row ID and map the cursor back to its displayed
position, or deliberately disable sorting. Numeric columns currently expose
formatted strings as well, so numeric-sort semantics need explicit coverage.

### A5 — P2: small-screen contents remain clipped after window placement is fixed

At 853×533 logical pixels, the decorated window fits, but the calibration
inspector's fine/coarse controls extend below the visible workspace with no
vertical recovery path. Dyno setup fields and Calculate extend below the window,
and its header/chart extend beyond the usable width. Evidence was captured with
synthetic data under Xvfb; this is a small-logical-display test, not physical
monitor certification.

Relevant construction: `FxCalibrationPane.java:493` (fixed inspector VBox) and
`FxDynoPane.java:64–115` (header and unscrolled left form). Make content scrollable
or reflow it, then verify keyboard access and native DPI layouts. Owned dialogs
still have fixed initial scene sizes and need the same small-screen review.

The exact Windows candidate was also launched with a process-local
`-Dglass.win.uiScale=1.5` override. Both main windows fit, and Logger Setup stayed
visible, but Definition Manager's title bar and lower buttons were clipped.
Escape dismissed it safely. This is native Windows rendering at forced 150%
JavaFX scale, not an OS-wide or physical-monitor DPI test. Windows settings
offered only 100% on this virtual display; no persistent display setting changed.
The scaled process was closed after review.

### A6 — P2: the full offline analysis workflow is not implemented

The JavaFX view loads playback with `LogRange.all(dataset)` and constructs
statistics for the full dataset. It has no user range-selection control or
range-driven chart/statistics refresh, despite AUG2 items 22–24 requiring ranges.
The neutral playback/range services are tested; that does not make their missing
UI reachable. Time Series currently displays one selected channel at a time.

### A7 — P2: settings, touch, and advanced Editor parity are incomplete

- `FxSettingsWindow` exposes only user level, high-table visibility, value-limit
  warning, theme, and default scale. It does not expose all retained SettingsForm
  display/font/clipboard/warning preferences. AUG2 item 12 cannot be marked full
  parity merely because this window opens.
- `-logger.touch` is recognized only as a Logger launch route in
  `JavaFxDesktopRuntime.java:116`; no JavaFX touch-density/profile application was
  found. AUG2 item 26 remains incomplete.
- `FxEditorWindow.java:643` implements Live Tune as an informational message,
  not the staged-change/evidence workspace described elsewhere. This is safe
  with respect to writes, but not a completed preview workflow.
- Shared axis-edit/interpolation operations exist, but the JavaFX calibration
  view does not wire axis editing or interpolation controls. Do not transfer
  Compose/Swing capability claims to the default shell.

### A8 — P2: the Logger auto-connect option is not consumed by JavaFX startup

`FxLoggerSetup.java:56` displays and saves the preference. The JavaFX host/window
and neutral Logger runtime never read it to start a session. The read that
performs startup connection lives in the retained Swing EcuLogger. Either wire
the preference through the safe session lifecycle or remove/disable the promise
until qualified. No connection was attempted to investigate this finding.

### A9 — P2: second-instance ROM paths containing spaces are split apart

`EditorLoggerCommunication.java:109` joins arguments with spaces, and line 91
decodes with `split(" ")`. The JavaFX host then treats each token as a separate
File and filters for existing files. A normal path such as `review copy.bin`
therefore cannot round-trip. This is source-confirmed; the live user's existing
instance was not used as an IPC test target. Use framed/length-prefixed or
properly encoded arguments, and cover spaces, Unicode, and multiple files.

## AUG2 item-by-item disposition

`PARTIAL` below means some source or smoke evidence exists, not full acceptance.
Prior Windows visual checks on `2e67dfca` are identified as earlier-candidate
evidence; they are not silently relabeled as tests of the new archive.

| ID | Workflow | Audit disposition |
| ---: | --- | --- |
| 1 | 3D pitch/yaw | PARTIAL: independent drag math present; representative-map mouse/touchpad acceptance remains. |
| 2 | Channels collapse/reopen | PARTIAL: repeated toggle smoke passed on earlier Windows candidate; selected-channel preservation across all sizes remains. |
| 3 | Visible Logger definition action | PARTIAL: visible button and setup route present, inspected on Windows. |
| 4 | Linux Logger XML visibility | PARTIAL: native XML chooser wiring present; exact-package replacement/upgrade workflow unqualified. |
| 5 | Output directory browser | PARTIAL: earlier native Windows browse/cancel passed; persistence and Linux path workflow remain. |
| 6 | ECU chooser cancellation | PARTIAL: earlier Windows cancel/reopen and subsequent ROM load passed; repeated exact-package full sequence remains. |
| 7 | Existing ECU definitions visible | PARTIAL: earlier Windows file visible/loaded; full add/re-add/reorder matrix remains. |
| 8 | ROM/calibration tabs | FAIL: tabs exist, but wrong-document close is reproduced (A2); safety retest required. |
| 9 | Save wording and behavior | FAIL: visible Save As/Save Now and overwrite route exist, but A1/A3 block safe qualification. |
| 10 | Definition Manager placement | PARTIAL: adjacent toolbar control and Add/Remove/Move inside manager inspected; full persistence workflow remains. |
| 11 | User levels | PARTIAL: five levels, persistence, filtering wired; exact-package comparison with retained behavior remains. |
| 12 | Comprehensive settings | INCOMPLETE: only a subset is exposed (A7). |
| 13 | Branding/desktop character | PARTIAL: branded light/dark shell inspected; no new user aesthetic acceptance is inferred. |
| 14 | Light/Dark, no High Contrast menu | PARTIAL: two exposed themes; earlier Windows switching passed; full current-candidate theme matrix remains. |
| 15 | Checksum warning dialog | NOT QUALIFIED: themed interaction boundary exists; safe-fixture visual/keyboard warning path not exercised here. |
| 16 | Windows menus and DPI | PARTIAL: native 100% menus and fresh window placement passed; physical high-DPI/multi-monitor gate remains. |
| 17 | Compare toolbar/Tools routes | PARTIAL: both routes present; current implementation compares the first two documents, not an explicit arbitrary pair. Multi-document acceptance remains. |
| 18 | Linux clean/upgrade definition selection | NOT QUALIFIED: installer preservation was not rerun against this candidate. |
| 19 | More than six Overview channels | SYNTHETIC PASS: 12 tiles, zero pairwise overlap at 1280×800 and 853×533; narrower layout scrolls. No hardware or user pass inferred. |
| 20 | Road Dyno | PARTIAL/FAIL NARROW: formula tests pass and UI exists, but small-screen controls clip (A5); calibrated recorded-data acceptance remains. |
| 21 | Dashboard style/size/color/detach | PARTIAL: 12 synthetic cards reflow without overlap; controls exist. Per-tile persistence, detach/reattach, and live-update acceptance remain. |
| 22 | In-context Open Log/playback | FAIL/PARTIAL: routes and playback exist; sorted selection is wrong (A4), range selection absent (A6). |
| 23 | Useful File → Open CSV | FAIL/PARTIAL: routes into analysis; same A4/A6 limitations. |
| 24 | Complete Log Analysis workspace | INCOMPLETE: tab is correctly named; required range workflow absent. |
| 25 | Top Logger navigation | PARTIAL: top tabs inspected at wide/narrow sizes; complete keyboard/accessibility qualification remains. |
| 26 | Dense dashboard controls and Touch | INCOMPLETE: dashboard toolbar wraps; touch profile is not applied (A7). |

## Safety, distribution, and coverage limits

- The inspected Live Tune simulation accepts `MockEcuTransport` specifically;
  JavaFX's preview action does not invoke production writes. Mock safety,
  identity/preflight, privacy diagnostics, and XML entity protections are covered
  by the passing shared tests. This is not a security certification of every
  transport or plugin.
- Both archive verifiers enforce source identity, bundled runtime/platform
  modules, neutral settings, and exclusion of private ROM/definition payloads.
  Raw review screenshots, logs, and the private VM test ISO are kept outside the
  public repository. Public documentation contains sanitized findings only.
- The bare-classpath synthetic Logger probe logged a missing Phidget native
  library because it did not use the package's native-library path. The verified
  Linux package includes that library; this probe warning alone is not a
  confirmed package defect.
- Native Windows startup was exercised at 1280×800/100%, without maximizing;
  both windows and initial owned prompts remained visible. Xvfb geometry cases
  include negative monitor origins, panel offsets, and smaller logical bounds.
  These do not qualify physical monitor transitions, mixed DPI, or accessibility.
- Remaining external gates: real clean/upgrade preservation, exact-package
  edit/undo/paste/save/reopen after the safety repairs, populated real-log and
  gauge workflows, checksum warning fixtures, screen-reader/focus checks, real
  high-DPI/multi-monitor layouts, hardware qualification, and explicit user
  acceptance tied to archive hashes.
- Android device/USB behavior, macOS distribution, and SteamOS handheld/touch
  release qualification were not exercised. Portable-core checks are supporting
  evidence only; desktop results must not be transferred to those platforms.
- No GitHub release assets, prerelease tags, or installer download pins were
  replaced. Updating the source branch does not authorize publication of these
  unqualified candidates.

## Recommended repair order

1. A1/A2: unify guarded close actions and close the explicitly requested document;
   preserve recovery until a deliberate save/discard decision.
2. A3: make save completion revision-aware and serialize save/close lifecycle.
3. A4: maintain source-row identity through sort, selection, seek, and markers.
4. A5/A6: make small-window actions reachable and implement linked analysis ranges.
5. A7–A9: resolve promised parity, startup preference, and IPC path handling.
6. Rebuild matched candidates, rerun the full acceptance matrix, then seek user
   approval before any release/installer promotion.
