# AUG2 desktop audit baseline

Status: active release-blocking audit
Source: user review of the Linux installer build and Windows RC4 build
Recorded: 2026-09-03

Latest item-by-item review: [September 4 desktop audit](DESKTOP_AUDIT_2026-09-04.md).
Candidate `80ae9db2` passes native package construction, but close/save defects,
analysis selection errors, small-screen clipping, and parity gaps keep the
release blocked. Earlier source-implementation statuses below are not approvals.

## Why this audit exists

The earlier RC4 checks proved package construction, startup, selected visual
fixtures, and a limited set of Editor and Logger workflows. They did not prove
desktop feature parity with the retained Swing application. Passing those
checks therefore did not qualify the complete desktop experience described in
this review.

Every item below remains unqualified until it passes on an exact candidate
archive. Source code, automated tests, screenshots, and a successful launch are
supporting evidence; none substitutes for the end-to-end user workflow.

## Status vocabulary

- `OPEN`: no complete replacement has been accepted.
- `IMPLEMENTED; RETEST`: source contains a proposed correction, but the exact
  packaged workflow has not received user approval.
- `DECISION`: product behavior must be agreed before implementation is judged.
- `INSTALLER PATCH`: a distribution or upgrade defect has a tested local patch
  that is not yet a published installer revision.
- `LINUX PASS; WINDOWS RETEST`: the user accepted the workflow on the recorded
  Linux candidate; native Windows remains unqualified.
- `JAVAFX IMPLEMENTED; RETEST`: the workflow exists in the new default JavaFX
  desktop, but neither an older Compose approval nor an automated test counts
  as acceptance of the rebuilt JavaFX candidate.

## Editor findings

| ID | Finding | Current evidence | Required acceptance test | Status |
| ---: | --- | --- | --- | --- |
| 1 | 3D rotation has insufficient Y-axis movement. | JavaFX has separately clamped pitch and continuous yaw driven by vertical and horizontal pointer movement. | On representative 3D maps, rotate through a useful pitch and yaw range with mouse and touchpad; selection and labels remain usable. | JAVAFX IMPLEMENTED; RETEST |
| 6 | Cancelling the ECU-definition file chooser freezes or locks the Editor. | JavaFX Definition Manager uses an owned native FileChooser and treats cancellation as a no-op. | Open the chooser, cancel from several directories, then open a ROM and reopen the manager without delay or deadlock. | JAVAFX IMPLEMENTED; RETEST |
| 7 | Existing Editor definition XML files do not appear in the chooser. | JavaFX Definition Manager shows the configured definition list and uses a native XML chooser for additions. | Existing, already-added, and new XML files remain visible on Linux and Windows; selecting, cancelling, and re-adding are safe. | JAVAFX IMPLEMENTED; RETEST |
| 8 | Calibration tabs are missing. | JavaFX hosts open ROMs and calibration tables in separate obvious tab rows backed by the shared document session. | Multiple ROMs/tables can remain open as obvious tabs; switching preserves selection, edits, undo history, and layout. | JAVAFX IMPLEMENTED; RETEST |
| 9 | Save wording and behavior are wrong. | The JavaFX app bar presents `Save As` immediately beside `Open ROM`, with explicit `Save Now` and `Save As…` choices, overwrite confirmation, and keyboard shortcuts. | Present a clear Save action and a Save As path without ambiguous wording; dirty-state and overwrite confirmation behave predictably. | JAVAFX IMPLEMENTED; RETEST |
| 10 | ECU Definitions menu wastes space; Definition Manager should be beside Open ROM and own Add Definition. | The JavaFX app bar has `Definitions Manager`; the redundant top menu is absent, and Add/Remove/Reorder actions live in the manager. | A visible Definition Manager command sits beside Open ROM; adding/reordering/removing occurs inside it; redundant menu is absent. | JAVAFX IMPLEMENTED; RETEST |
| 11 | User Level menu is missing. | JavaFX has a five-level User Level menu, marks the active level, persists changes, and immediately filters the calibration catalog. | User level is visible and persistent, and changes expose/hide the same calibration scope as the compatibility application. | JAVAFX IMPLEMENTED; RETEST |
| 12 | Settings menu is missing. | JavaFX has a top-level Settings menu with Editor and Appearance settings; Logger connection setup remains separate. | All retained desktop settings are reachable, understandable, persistent, and separated from Logger connection setup. | JAVAFX IMPLEMENTED; RETEST |
| 13 | The Editor looks stripped down, lacks branding, and lost its advanced desktop character. | The user rejected the Compose presentation. JavaFX now has the approved logo, a branded desktop header, command deck, ROM metrics, map catalog, tabs, and dense work surfaces; aesthetic acceptance is still required. | First launch and loaded-ROM workspaces visibly read as a capable desktop tuning application, with approved logo, hierarchy, density, and tools. | JAVAFX IMPLEMENTED; RETEST |
| 14 | View contains only themes; High Contrast is unwanted and did not work. | The JavaFX desktop offers Light and Dark only; High Contrast is removed. View now owns workspace visibility rather than acting only as a theme menu. | Verify both retained themes work and High Contrast is absent. | JAVAFX IMPLEMENTED; RETEST |
| 15 | Checksum Failed dialog does not match the application. | JavaFX routes ROM interaction through owned and themed JavaFX dialogs, but visual parity is unqualified. | Trigger the warning with a safe fixture; typography, buttons, spacing, ownership, keyboard behavior, and recovery match the active shell. | JAVAFX IMPLEMENTED; RETEST |
| 16 | Windows top-menu visuals are not modernized. | The Windows package now starts the JavaFX shell; native Windows menu and DPI acceptance remains open. | Inspect the exact Windows archive at normal and high DPI; menus must match the approved application visual system. | JAVAFX IMPLEMENTED; RETEST |
| 17 | Compare ROMs needs a visible button and must remain under Tools. | JavaFX exposes Compare ROMs in the command deck and under Tools; both invoke its shared comparison workspace. | Both entry points are present, invoke one workflow, and remain correctly enabled as documents open and close. | JAVAFX IMPLEMENTED; RETEST |

## Logger findings

| ID | Finding | Current evidence | Required acceptance test | Status |
| ---: | --- | --- | --- | --- |
| 2 | A collapsed Channels pane can be reopened but not put away again. | JavaFX has one persistent Channels toggle that repeatedly inserts/removes the rail without clearing selection. | The same discoverable control expands and collapses the pane repeatedly at wide and narrow sizes without losing selections. | JAVAFX IMPLEMENTED; RETEST |
| 3 | Loading a Logger definition needs a visible button, not only a menu command. | JavaFX has visible Load Definition and Logger Setup buttons and retains the setup menu route. | A visible Logger-definition action is available in normal flow and remains available through an appropriate menu/settings path. | JAVAFX IMPLEMENTED; RETEST |
| 4 | Logger definition files disappear in the Linux chooser, including definitions already loaded. | JavaFX uses its native XML file chooser and starts beside the current definition when configured. | All appropriate XML files display in the selected folder before and after loading; cancellation and replacement are safe. | JAVAFX IMPLEMENTED; RETEST |
| 5 | Log output directory needs a directory browser. | JavaFX Logger Setup provides a native directory browser while retaining the path field as secondary input. | A folder-browser control works on Linux and Windows, shows the current directory, permits text editing only as a secondary path, and persists. | JAVAFX IMPLEMENTED; RETEST |
| 18 | Logger-definition selection works on Windows but not Linux. | The installed Linux upgrade could omit `config/user/definitions`; a local installer patch now preserves it and its regression test passes. Chooser behavior still needs separate retest. | Test clean install and upgrade install with a preserved definition, then select another XML through the Linux chooser and start Logger. | INSTALLER PATCH |
| 19 | More than six Overview parameters overwrite tiles instead of reflowing or scrolling. | JavaFX uses a wrapping FlowPane inside a vertically scrollable workspace for all selected channels. | Add at least 12 parameters at wide, medium, and narrow widths; tiles never overlap or overwrite and scrolling appears when required. | JAVAFX IMPLEMENTED; RETEST |
| 20 | Modernized Dyno is missing. | JavaFX now has a Road Dyno that projects estimated engine power and torque from live RPM/speed history with vehicle mass, rolling resistance, aerodynamic drag, and drivetrain-loss inputs. Calculation tests pass; real recorded data acceptance is open. | Qualify it with recorded/simulated data before connected claims. | JAVAFX IMPLEMENTED; RETEST |
| 21 | Dashboard layout/style controls are unclear or target the wrong gauge; sizing, detaching, and color customization are incomplete. | JavaFX exposes separate Analog/Digital/Trend/Alarm and Standard/Large/Custom controls for the explicitly selected tile, plus color selection and detachable resizable gauge windows. Role, size, color, and custom dimensions persist through the neutral settings model. | Each tile edits itself; style buttons are individually visible; Standard/Large/Custom behavior is approved; tiles reflow; color changes persist; decide and test detaching. | JAVAFX IMPLEMENTED; RETEST |
| 22 | Analysis needs an in-context Open Log button and playback. | JavaFX Log Analysis includes its own Open Log action, linked seek/table cursor, play/pause, and variable playback speed. | Open a log from inside Log Analysis, play/pause/seek it, select ranges and markers, and return without a redundant window. | JAVAFX IMPLEMENTED; RETEST |
| 23 | File > Open CSV Log produces a window without useful information. | JavaFX routes File > Open CSV Log into the same Log Analysis workspace with table, charts, statistics, markers, and playback. | Opening from File routes to the same useful Log Analysis workspace and immediately exposes graph, statistics, channels, ranges, markers, and playback. | JAVAFX IMPLEMENTED; RETEST |
| 24 | Analysis should be Log Analysis and contain the full offline-analysis workflow. | JavaFX names the tab Log Analysis and keeps the offline workflow in that workspace rather than opening a redundant summary window. | The visible name is Log Analysis and one coherent workspace owns the complete offline workflow. | JAVAFX IMPLEMENTED; RETEST |
| 25 | Overview/Data/Graph/Dashboard/Analysis navigation belongs at the top. | JavaFX places Overview, Data, Graph, Dashboard, Dyno, and Log Analysis tabs above the active workspace. | Verify it remains obvious at wide, narrow, and keyboard layouts. | JAVAFX IMPLEMENTED; RETEST |
| 26 | Dashboard value/style/size buttons overlap and are too tall for the default desktop layout; Touch mode is missing. | JavaFX uses desktop-density controls in one horizontal selected-gauge toolbar; supported narrow-width and DPI behavior still needs visual qualification. | No overlap at supported widths/DPI; desktop controls use approved density; a deliberate touch profile is available without degrading desktop defaults. | JAVAFX PARTIAL; TOUCH/RETEST |

## Cross-platform qualification matrix

Every row above must be evaluated against both a clean install and an upgrade
from the previously published installer package. A result applies only to the
archive checksum recorded in the qualification record.

| Gate | Linux x64 | Windows x64 | Evidence required |
| --- | --- | --- | --- |
| Clean package verification | NOT RUN | NOT RUN | Archive and internal checksums, source commit, build identity |
| Clean first launch | NOT RUN | NOT RUN | No stale settings; branding, menus, and definition prompt inspected |
| Upgrade/migration | NOT RUN | NOT RUN | Settings, ECU definitions, Logger definitions, profiles, logs, and layouts preserved |
| Editor items 1, 6-17 | NOT RUN | NOT RUN | Item-by-item findings with screenshots only where useful |
| Logger items 2-5, 18-26 | NOT RUN | NOT RUN | Item-by-item findings using the same definitions/profile/log fixture |
| High DPI and narrow layout | NOT RUN | NOT RUN | No clipped, overlapping, unreachable, or misleading controls |
| Keyboard/accessibility | NOT RUN | NOT RUN | Focus order, shortcuts, names, contrast, and screen-reader smoke check |
| User visual approval | NOT RUN | NOT RUN | Explicit approval tied to the exact archive checksum |

## Phase 0 evidence — 2026-09-03

- Exact refreshed public Linux RC4 ZIP tested:
  `2c70ada4bd4107612792deea93df292b8a0d79ef2ee9da1ff95d829182a17294`.
- Package source identity: `08360a89e0e1d3baa3ca41cdc212a7cbbf32730a`.
- Clean isolated install: installer pass; every internal package checksum pass.
- Forced isolated upgrade from the previous release marker: settings, Logger
  XML/DTD, ECU definition, logs, ROM content, and repository content retained
  identical SHA-256 values; previous installation retained as a backup.
- Exact public-package Editor and Logger launch smoke: both reached the Compose
  desktop shell under Xvfb. This is not a visual pass.
- Finding discovered during the smoke test: the production Compose Logger read
  `~/.RomRaider/profile_backup.xml` when no explicit profile was configured.
- Local correction: automatic Compose Logger profiles now live at
  `config/user/profiles/profile_backup.xml`; the installer preserves that
  directory during upgrade.
- Verification of the local correction: installer regression suite, complete
  Java 21 unit suite, production Java build, Compose tests/staging, application
  image build, and packaged Logger startup all pass. The corrected Logger no
  longer logs or reads the legacy profile.
- First desktop repair slice: Compose definition selection now uses the
  established KDE/native/Swing chooser boundary instead of AWT's unreliable
  Linux filename filtering. Logger Setup has a directory browser, and the live
  Logger workspace has a visible Logger Definition control. Compose tests pass;
  Linux and Windows packaged visual retests remain required.
- User visual result, Linux candidate commit `6330e210`, archive SHA-256
  `1caca616ab879ddf335b44bba34ae21f5e170b9fd810e6e52654c3d97033c3a4`:
  findings 3, 4, 5, 6, and 7 looked correct and functioned. Windows remains
  open.
- Second Editor repair slice: the existing closable calibration-tab workspace
  is retained; the app-bar save control now exposes explicit Save Now and Save
  As choices; Definitions Manager is beside Open ROM; definition addition and
  download are consolidated inside the manager; and the redundant Definitions
  menu is removed. The complete Java 21 suite, production Linux build, Compose
  tests/staging, and Linux application-image build pass. Packaged user approval
  for findings 8–10 remains open.
- User check of candidate commit `bec00724`, archive SHA-256
  `27393e397b05430191dd71e37fe621b4e61dd4130e8df0da296242e267675585`,
  correctly found that the packaged Compose startup surface still showed
  `Definitions`, retained the redundant ECU Definitions/Add Definitions menu,
  and had no `Save As` toolbar control. This candidate does not pass findings
  9 or 10. The correction is being applied to the Compose startup surface.
- User accepted the corrected Linux toolbar at commit `f635d420`: Save As is
  immediately beside Open ROM, Definitions Manager follows it, and the
  redundant definitions menu is absent. Findings 9 and 10 are Linux passes;
  Windows remains open.
- The first findings 11–12 candidate exposed a Settings launch defect: the
  retained comprehensive Settings window attempted to construct a second
  Swing Editor from the AWT event thread and therefore never appeared. The
  candidate does not qualify finding 12. The local repair makes Settings
  independent of a Swing Editor owner and retains optional live refresh hooks
  only when that shell already exists.
- User-led Editor follow-up: diagnostic trouble-code maps were incorrectly
  presented through the generic numeric calibration editor. The local repair
  recognizes standard P/B/C/U DTC switch definitions and presents only their
  Disabled/Enabled state control. Other switch tables retain their
  definition-specific editing behavior.
- User accepted Linux candidate commit `f50224f5`, archive SHA-256
  `610434d6b14b67d6a94899dd3b59fa909eae230966e9a27bc342dea1c1ba9fab`:
  Editor Settings opens correctly, qualifying finding 12 as a Linux pass, and
  a DTC entry correctly exposes only its Disabled/Enabled state switch. The
  corresponding Windows checks remain open.
- The user subsequently accepted finding 11 on the same Linux candidate after
  disabling the compatibility option that lists tables above the current user
  level: level changes correctly filtered real level 1, 4, and 5 tables.
- JavaFX migration checkpoint `412d8b4c` changes the default desktop provider
  and the Linux and Windows Java 21 packagers to JavaFX. Platform-specific
  OpenJFX 21.0.10 modules are checksum-locked; Compose and Skia are excluded
  from both staged desktop runtimes.
- A packaged Linux application image launched through its real entry point.
  A Windows application image built with the Windows Temurin 21 JDK and
  Windows OpenJFX modules launched under Wine and logged `Windows 10`, Java 21,
  and `Launching desktop shell: JavaFX Desktop ECU Studio`. This proves the
  provider/runtime boundary, not native Windows visual behavior.
- JavaFX Logger checkpoint `abfa9272` adds the complete in-window Log Analysis
  workspace and Road Dyno. Subsequent JavaFX work adds live Light/Dark switching
  and spreadsheet-format calibration block copy/paste through the neutral,
  one-transaction batch edit controller. The JavaFX test suite passes.

This evidence qualifies only the non-visual baseline. Matrix rows remain
`NOT RUN` until their complete acceptance tests are performed.

## Release rule

Another desktop release must not be described as feature-complete, parity
qualified, or visually approved while any required row remains `OPEN`,
`DECISION`, `IMPLEMENTED; RETEST`, `INSTALLER PATCH`, or `NOT RUN`.
