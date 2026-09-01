# RomRaider2 implementation status

Last updated: 2026-09-01

## Working now

- Java 21 builds and self-contained Windows/Linux application images.
- Modern Editor workspace with tabs, search, favorites, recent and changed
  maps, comparison, undo/redo, notes, recovery, integrated 3D, and live data.
- Managed Editor and Logger definition installation.
- Rebuilt Logger shell with searchable channels and Data, Graph, Dashboard,
  MAF, Injector, Dyno, and Analysis workspaces.
- First Compose Desktop Logger workspace using the real session, recording,
  channel-selection, and received-sample services. It includes responsive
  Overview, Data, Graph, and Dashboard views with light and dark themes.
- Offline CSV statistics, time and X/Y graphs, range selection, playback, and
  markers.
- Subaru SSM/ISO9141 through OpenPort 2.0 on Linux, including a sustained in-car
  logging pass.
- Windows J2534 discovery and automatic direct/cross-bitness routing.
- Read-only Mitsubishi Lancer Evolution MUT-II protocol and synthetic protocol
  tests.
- DimeMod discovery, diagnostic codes, runtime Logger parameters, and mapped
  feature display. The Logger now records whether the connected runtime
  advertises RAM Tune; vehicle writes remain disabled.
- Offline live-tuning plans with identity and capability preflight, bounded
  staged changes, stale-value checks, mock readback verification, and a new
  read-only Editor preview of selected or all changed tables.
- Light, dark, system, and high-contrast themes plus scalable desktop and touch
  layouts.
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
is published as a development prerelease. A fresh Linux Java 21 application
image builds and starts with the replacement workspace packaged. The packaged
Windows visual pass remains open and is disclosed on the release.

The Editor can now project changed bytes from real selected tables into an
offline live-tuning plan. Preflight evaluates the ECU family, ECU role, DimeMod
state, mapped definition, runtime RAM Tune discovery, and exact ECU identity.
The new Tune inspector previews byte ranges, RAM addresses, before/after data,
and safety gates. It has no connect or write command, and the plan can only run
against the mock ECU transport.

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

1. Extend the replacement calibration shell on the neutral ROM/table boundary.
2. Inspect the native macOS ARM64 and Intel application bundles on real Macs.
3. Rerun ECU definition table editing, logger definition/profile import, and
   the offline logger preview on the Galaxy S25; keep the wired live logger for
   RC5 connected qualification.
4. Run the RC4 Windows package and visual pass when the VM is available again.
5. Resume OpenPort and external-sensor in-car qualification during RC5 work.
6. Qualify the Mitsubishi Lancer Evolution MUT-II path on a vehicle before
   describing it as supported.

The detailed manual checks are in `WINDOWS_RELEASE_CHECKLIST.md` and
`LINUX_IN_CAR_QUALIFICATION.md`.
