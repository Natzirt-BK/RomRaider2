# RomRaider2 implementation status

Last updated: 2026-09-04

## Working now

- Java 21 builds and self-contained Windows/Linux application images.
- Modern Editor workspace with tabs, search, favorites, recent and changed
  maps, comparison, undo/redo, notes, recovery, integrated 3D, and live data.
- Responsive calibration documents with a framed heatmap, distinct selection
  and changed-cell states, labelled wide-layout actions, and a wrapping cell
  editor that keeps every command reachable on narrow displays.
- Managed Editor and Logger definition installation.
- Rebuilt Logger shell with searchable channels and Data, Graph, Dashboard,
  MAF, Injector, Dyno, and Analysis workspaces.
- Saved desktop dashboard arrangements with Standard, Wide, and Large tiles
  that can present a Gauge, large Value, live Trend, or configured Alarm.
- JavaFX is the default Windows and Linux desktop shell. Compose and Swing are
  retained only as explicit compatibility selections during qualification.
- The JavaFX Editor owns native definition/ROM selection, ROM and calibration
  tabs, direct scaled edits, dedicated DTC switches, undo/redo, an independently
  rotatable 3D surface, recovery, user levels, settings, comparison, and save/
  save-as workflows.
- The JavaFX Logger owns definition and output-directory browsing, reversible
  channel navigation, responsive Overview/Data/Graph/Dashboard views, per-gauge
  Analog/Digital/Trend/Alarm roles, Standard/Large/Custom sizing, colors, and
  detachable gauges. Role, size, color, and exact custom dimensions persist.
- JavaFX Log Analysis includes an in-context Open Log action, linked table and
  playback cursor, variable-speed playback, time-series and X/Y charts,
  descriptive statistics, and persistent marker sidecars.
- The JavaFX Road Dyno calculates estimated engine power and torque from live
  RPM/speed history plus vehicle, rolling-resistance, and aerodynamic inputs.
- First editable Compose calibration checkpoint using the active ROM table.
  The opt-in grid renders real values, axes, heatmaps, change state, selection
  details, and narrow scrolling. One-cell edits use the same scaling, range
  checks, ROM bytes, and undo/redo history as the classic grid. Active-cell
  copy/paste and Ctrl+Z/Ctrl+Y also stay on that boundary. Shift+arrow or
  Shift+click selects a rectangular value range; copied ranges use tab/newline
  spreadsheet format, and pasted value blocks are validated before being
  grouped into one undo step. Ctrl+A selects the full calibration surface.
- The Logger continues to use its UI-neutral ECU runtime rather than a hidden
  Swing `EcuLogger`; the JavaFX UI does not own protocol or session logic.
- The Compose Logger can configure its definition, protocol, transport,
  module, serial port, output directory, auto-connect preference, and external
  sensor ports. Captured CSV files can be opened into a Compose-owned offline
  statistics window.
- The Compose Editor includes ROM comparison and a read-only Live Tune preview
  for DimeMod, CarBerry, and MerpMod detection and safety-gate review.
- Compose calibration ranges can now be interpolated across, down, or in both
  directions using definition axis breakpoints. Each operation is validated,
  reversible, and recorded as one undo step.
- ECU definition priority management is now Compose-owned, including search,
  missing-file status, add/remove, reorder, and persistent save.
- Offline CSV statistics, time and X/Y graphs, range selection, playback, and
  markers.
- Subaru SSM/ISO9141 through OpenPort 2.0 on Linux, including a sustained in-car
  logging pass.
- Windows J2534 discovery and automatic direct/cross-bitness routing.
- Read-only Mitsubishi Lancer Evolution MUT-II protocol and synthetic protocol
  tests.
- DimeMod discovery, diagnostic codes, runtime Logger parameters, and mapped
  feature display. The Logger now records whether the connected runtime
  advertises RAM Tune plus its signature address and lookup-table size;
  vehicle writes remain disabled.
- Loaded-ROM recognition for DimeMod, CarBerry, and MerpMod based on explicit
  ROM identity or branded definition tables. The Editor now lists mapped
  CarBerry and MerpMod features as well as DimeMod features, while keeping all
  definition evidence separate from live ECU-session verification. Summary
  counts and status colours make that distinction visible at a glance.
- Offline live-tuning plans with identity and capability preflight, bounded
  staged changes, stale-value checks, mock readback verification, and a new
  read-only Editor preview of selected or all changed tables.
- Light and dark JavaFX desktop themes. High Contrast is intentionally removed
  from the new desktop UI in response to the active audit.
- The JavaFX polish pass now keeps branding and active-session context readable
  in both themes, collapses decorative branding at narrow desktop widths,
  preserves usable Editor and Logger navigation rails, wraps Dashboard controls
  as groups, and gives empty workspaces and definition lists clear guidance.
- Calibration values and axes now honor the active definition format instead
  of exposing binary floating-point noise. The inspector shows engineering
  units and actual X/Y coordinates, while the heatmap and 3D renderer use
  scaled engineering values.
- JavaFX theme changes propagate to every open Editor and Logger window.
  Successful checksum updates stay in the status bar, custom dialogs close with
  Escape, CSV browsing starts in the configured output folder, and Linux native
  Save As results are protected from duplicate ROM extensions.
- The Road Dyno now presents readable channel names and units and independently
  identifies engine-speed and vehicle-speed inputs as channels are selected.
- Long ROM, calibration, channel, and definition names retain compact layouts
  with full-name tooltips. The clean-install definition prompt is deferred until
  the Editor has painted, avoiding an unrendered first-run window on Linux.
- Shared modern table spacing and alternating row treatment across Logger
  channels, live values, offline analysis, Editor live data, change history,
  ROM comparison, live-tune preview, and ROM-modification details. Specialized
  channel checkboxes and unit selectors retain their original behavior. Live
  parameter and analysis channel names use one restrained accent colour.
- Local privacy-safe diagnostics and versioned settings separate from older
  RomRaider installs.

## RC3 milestone reached

The shared Swing Logger has had its first complete visual pass instead of only
receiving isolated fixes. The main and specialized workspaces now share the
same structure, theme, empty-state behavior, and navigation on Windows and
Linux. Automated coverage protects the new layout, workspace shortcuts, charts,
and the DM20 parser.

RC3 is published as a prerelease. Connected qualification against the exact
release checksums remains open before stable promotion.

## RC4 public development milestone

RC4 now has its first user-visible step away from Swing. The replacement Logger
workspace is loaded into the existing application through a small provider
boundary, so there is still one ECU session and one set of selected channels.
The existing workspaces stay available while the replacement grows.

The OpenPort reconnect path also has an explicit Reconnecting state, quieter
retry logging, and a capped 1/2/4/5-second retry schedule. The Java and Compose
tests pass, and the Logger workspace has passed clean Linux visual checks for
Overview, Data, Graph, Dashboard, dark mode, and a 600-pixel-wide layout. RC4
is published as a development prerelease. Fresh Linux and Windows Java 21
application images build and start with the replacement workspace packaged.
The matching Windows source passed its Java and Compose tests in the VM, and
the packaged first-run interface passed a native Windows visual check.

The Editor can now project changed bytes from real selected tables into an
offline live-tuning plan. Preflight evaluates the ECU family, ECU role, DimeMod
state, mapped definition, runtime RAM Tune discovery, and exact ECU identity.
It also requires structurally valid runtime signature and lookup-table metadata
as a separate check from the advertised feature bit.
The new Tune inspector previews byte ranges, RAM addresses, before/after data,
and safety gates. Its state badge and byte table now use the same visual system
as the Logger and Editor data surfaces. It has no connect or write command, and
the plan can only run against the mock ECU transport.

A portable, UI-free ROM and logger core now supports bounded ROM byte edits,
change ranges, save-copy output, bounded logger sessions, and round-trip CSV.
The SteamOS Desktop Mode bundle builds and passes a Linux launch smoke test.
Native macOS package definitions now cover Apple silicon and Intel separately;
the actual application bundles still need to run on both Mac architectures.
An Android debug APK now builds and passes lint with no errors. It can
open ROM files, match exact RomRaider ECU definitions, search and edit named
scaled numeric tables, make advanced byte edits, review traditional or
portable CSV logs, and
prepare an OpenPort 2.0 through Android USB by reading adapter firmware and
vehicle voltage. It can securely import the actual v370 logger definition and
an existing profile, resolve direct parameters, run their real conversions in
a simulated live session, and save that session as CSV. The shared core has
bounded OpenPort K-line decoding, read-only Subaru SSM framing, and a
deduplicating 64-address query planner. The Android application now wires those
pieces into a foreground-only, engine-targeted OpenPort K-Line logger that can
identify an ECU, display converted values, and record CSV. The path is clearly
marked as awaiting RC5 in-car qualification and exposes no ECU write command.
Android Preview 2 is published separately for early device and workflow
feedback. Android does not correct ROM checksums, so its saved ROM copies are
for review and desktop validation and must not be flashed.

## Safety boundary

No normal production path can flash an ECU or write ECU memory. The live-tune
executor accepts only the mock transport. Read ECU, Write ECU, and vehicle RAM
writes remain unavailable until their protocols, identity checks, preflight,
verification, recovery, and connected test plans are complete.

## Next work

1. Run the Windows JavaFX package natively at normal and high DPI, including
   definitions, ROM editing, Logger hardware setup, dashboard, Dyno, and Log
   Analysis.
2. Continue keyboard and screen-reader accessibility work across the JavaFX
   calibration and Logger workspaces.
3. Rerun ECU definition table editing, Logger definition/profile import, and
   the offline logger preview on the Galaxy S25; keep the wired live logger for
   RC5 connected qualification.
4. Resume OpenPort and external-sensor in-car qualification during RC5 work.
5. Qualify the Mitsubishi Lancer Evolution MUT-II path on a vehicle before
   describing it as supported.

macOS ARM64 and Intel work remains paused while the desktop and Android
interfaces settle.

The detailed manual checks are in `WINDOWS_RELEASE_CHECKLIST.md` and
`LINUX_IN_CAR_QUALIFICATION.md`.
