# AUG2 desktop audit baseline

Status: active release-blocking audit
Source: user review of the Linux installer build and Windows RC4 build
Recorded: 2026-09-03

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

## Editor findings

| ID | Finding | Current evidence | Required acceptance test | Status |
| ---: | --- | --- | --- | --- |
| 1 | 3D rotation has insufficient Y-axis movement. | RC4 has a Compose surface, but source presence does not establish usable orbit controls. | On representative 3D maps, rotate through a useful pitch and yaw range with mouse and touchpad; selection and labels remain usable. | OPEN |
| 6 | Cancelling the ECU-definition file chooser freezes or locks the Editor. | The local repair replaces the Compose shell's direct AWT dialog path with the established KDE/native/Swing chooser boundary. | Open the chooser, cancel from several directories, then open a ROM and reopen the manager without delay or deadlock. | LINUX PASS; WINDOWS RETEST |
| 7 | Existing Editor definition XML files do not appear in the chooser. | The local repair routes Compose through the established cross-platform chooser and no longer relies on AWT's Linux filename filter. | Existing, already-added, and new XML files remain visible on Linux and Windows; selecting, cancelling, and re-adding are safe. | LINUX PASS; WINDOWS RETEST |
| 8 | Calibration tabs are missing. | The repaired Editor workspace hosts open calibration tables in scrollable, closable tabs and has automated open/select/close coverage. Packaged visual and state-retention approval remain required. | Multiple ROMs/tables can remain open as obvious tabs; switching preserves selection, edits, undo history, and layout. | IMPLEMENTED; RETEST |
| 9 | Save wording and behavior are wrong. | The packaged Editor app bar presents `Save As` immediately beside `Open ROM`, with explicit `Save Now` and `Save As…` choices. The user accepted the corrected Linux toolbar. | Present a clear Save action and a Save As path without ambiguous wording; dirty-state and overwrite confirmation behave predictably. | LINUX PASS; WINDOWS RETEST |
| 10 | ECU Definitions menu wastes space; Definition Manager should be beside Open ROM and own Add Definition. | The packaged Editor app bar has `Definitions Manager`; the redundant top menu is removed, and Add/Remove/Reorder actions live in the manager. The user accepted the corrected Linux toolbar. | A visible Definition Manager command sits beside Open ROM; adding/reordering/removing occurs inside it; redundant menu is absent. | LINUX PASS; WINDOWS RETEST |
| 11 | User Level menu is missing. | The local packaged shell now has a five-level User Level menu, marks the active level, persists changes, and refreshes the calibration list using the compatibility visibility rule. | User level is visible and persistent, and changes expose/hide the same calibration scope as the compatibility application. | IMPLEMENTED; RETEST |
| 12 | Settings menu is missing. | The local packaged shell now has a top-level Settings menu that opens the retained comprehensive Editor settings window; Logger setup remains separate. | All retained desktop settings are reachable, understandable, persistent, and separated from Logger connection setup. | IMPLEMENTED; RETEST |
| 13 | The Editor looks stripped down, lacks branding, and lost its advanced desktop character. | RC4 added window branding and richer surfaces, but aesthetic acceptance was not obtained. | First launch and loaded-ROM workspaces visibly read as a capable desktop tuning application, with approved logo, hierarchy, density, and tools. | IMPLEMENTED; RETEST |
| 14 | View contains only themes; High Contrast is unwanted and did not work. | RC4 still documents High Contrast, so the reported product mismatch remains. | Agree the retained theme set; remove High Contrast if that decision stands; every retained option must visibly work. | DECISION |
| 15 | Checksum Failed dialog does not match the application. | RC4 moved ROM interaction behind a neutral service, but visual parity is unqualified. | Trigger the warning with a safe fixture; typography, buttons, spacing, ownership, keyboard behavior, and recovery match the active shell. | IMPLEMENTED; RETEST |
| 16 | Windows top-menu visuals are not modernized. | Normal RC4 startup now uses Compose on Windows. | Inspect the exact Windows archive at normal and high DPI; menus must match the approved application visual system. | IMPLEMENTED; RETEST |
| 17 | Compare ROMs needs a visible button and must remain under Tools. | RC4 includes ROM comparison in the replacement shell. | Both entry points are present, invoke one workflow, and remain correctly enabled as documents open and close. | IMPLEMENTED; RETEST |

## Logger findings

| ID | Finding | Current evidence | Required acceptance test | Status |
| ---: | --- | --- | --- | --- |
| 2 | A collapsed Channels pane can be reopened but not put away again. | RC4 changed the Logger shell and channel browser; reversible collapse was not accepted. | The same discoverable control expands and collapses the pane repeatedly at wide and narrow sizes without losing selections. | IMPLEMENTED; RETEST |
| 3 | Loading a Logger definition needs a visible button, not only a menu command. | The local repair adds a visible Logger Definition control to the live workspace while retaining the menu route. | A visible Logger-definition action is available in normal flow and remains available through an appropriate menu/settings path. | LINUX PASS; WINDOWS RETEST |
| 4 | Logger definition files disappear in the Linux chooser, including definitions already loaded. | The local repair routes Compose through the established cross-platform chooser, applies an XML filter supported by KDE, and starts in the current definition's directory. | All appropriate XML files display in the selected folder before and after loading; cancellation and replacement are safe. | LINUX PASS; WINDOWS RETEST |
| 5 | Log output directory needs a directory browser. | The local Logger Setup repair adds a Browse control backed by the cross-platform directory chooser while retaining the path field. | A folder-browser control works on Linux and Windows, shows the current directory, permits text editing only as a secondary path, and persists. | LINUX PASS; WINDOWS RETEST |
| 18 | Logger-definition selection works on Windows but not Linux. | The installed Linux upgrade could omit `config/user/definitions`; a local installer patch now preserves it and its regression test passes. Chooser behavior still needs separate retest. | Test clean install and upgrade install with a preserved definition, then select another XML through the Linux chooser and start Logger. | INSTALLER PATCH |
| 19 | More than six Overview parameters overwrite tiles instead of reflowing or scrolling. | RC4 added responsive overview/dashboard layouts. | Add at least 12 parameters at wide, medium, and narrow widths; tiles never overlap or overwrite and scrolling appears when required. | IMPLEMENTED; RETEST |
| 20 | Modernized Dyno is missing. | The specialized Dyno remains in the compatibility shell; migration scope is explicitly undecided. | Decide and implement the desktop Dyno workflow before removing compatibility mode; qualify it with recorded/simulated data before connected claims. | OPEN |
| 21 | Dashboard layout/style controls are unclear or target the wrong gauge; sizing, detaching, and color customization are incomplete. | RC4 added per-channel gauge configuration, several gauge roles, saved layouts, resizing, scales, warnings, and themes. Detaching is not documented as complete. | Each tile edits itself; style buttons are individually visible; Standard/Large/Custom behavior is approved; tiles reflow; color changes persist; decide and test detaching. | IMPLEMENTED; RETEST |
| 22 | Analysis needs an in-context Open Log button and playback. | RC4 documents offline analysis and playback, but the exact interaction has not been accepted. | Open a log from inside Log Analysis, play/pause/seek it, select ranges and markers, and return without a redundant window. | IMPLEMENTED; RETEST |
| 23 | File > Open CSV Log produces a window without useful information. | RC4 replaced the earlier statistics-only path with expanded offline analysis. | Opening from File routes to the same useful Log Analysis workspace and immediately exposes graph, statistics, channels, ranges, markers, and playback. | IMPLEMENTED; RETEST |
| 24 | Analysis should be Log Analysis and contain the full offline-analysis workflow. | Expanded analysis exists in source; naming and packaged behavior need approval. | The visible name is Log Analysis and one coherent workspace owns the complete offline workflow. | IMPLEMENTED; RETEST |
| 25 | Overview/Data/Graph/Dashboard/Analysis navigation belongs at the top. | Navigation placement is a product decision not established by automated checks. | Move it to the agreed top-level position and verify it remains obvious at wide, narrow, keyboard, and touch layouts. | DECISION |
| 26 | Dashboard value/style/size buttons overlap and are too tall for the default desktop layout; Touch mode is missing. | RC4 added responsive layout work, but the reported desktop density problem remains unqualified. | No overlap at supported widths/DPI; desktop controls use approved density; a deliberate touch profile is available without degrading desktop defaults. | IMPLEMENTED; RETEST |

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
  definition-specific editing behavior. Packaged Linux review remains open.

This evidence qualifies only the non-visual baseline. Matrix rows remain
`NOT RUN` until their complete acceptance tests are performed.

## Release rule

Another desktop release must not be described as feature-complete, parity
qualified, or visually approved while any required row remains `OPEN`,
`DECISION`, `IMPLEMENTED; RETEST`, `INSTALLER PATCH`, or `NOT RUN`.
