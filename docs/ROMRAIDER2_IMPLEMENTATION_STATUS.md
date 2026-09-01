# RomRaider2 implementation status

Last updated: 2026-08-31

## Working now

- Java 21 builds and self-contained Windows/Linux application images.
- Modern Editor workspace with tabs, search, favorites, recent and changed
  maps, comparison, undo/redo, notes, recovery, integrated 3D, and live data.
- Managed Editor and Logger definition installation.
- Rebuilt Logger shell with searchable channels and Data, Graph, Dashboard,
  MAF, Injector, Dyno, and Analysis workspaces.
- Offline CSV statistics, time and X/Y graphs, range selection, playback, and
  markers.
- Subaru SSM/ISO9141 through OpenPort 2.0 on Linux, including a sustained in-car
  logging pass.
- Windows J2534 discovery and automatic direct/cross-bitness routing.
- Read-only Evo VIII/IX MUT-II protocol and synthetic protocol tests.
- DimeMod discovery, diagnostic codes, runtime Logger parameters, and mapped
  feature display. RAM-tune research remains hidden.
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

RC3 is not ready to publish yet. It still needs final packages and connected
qualification against the exact candidate checksums.

## Safety boundary

No normal production path can flash an ECU or write ECU memory. The mock
transport is the only RomRaider2 transport that simulates writes. Read ECU and
Write ECU remain unavailable until their protocols, identity checks, preflight,
verification, recovery, and connected test plan are complete.

## Next work

1. Build and inspect the next Windows and Linux RC3 candidate packages.
2. Run the Windows visual/clean-machine pass when the VM is available again.
3. Repeat Linux OpenPort USB-removal, reconnect, and shutdown testing in car.
4. Test simultaneous Subaru logging and the external AEM wideband.
5. Qualify the Evo MUT-II path on a vehicle before describing it as supported.
6. Continue separating Logger state from Swing before replacing more of the UI.
7. Prototype the replacement calibration shell without splitting ROM or Logger
   state between two applications.

The detailed manual checks are in `WINDOWS_RELEASE_CHECKLIST.md` and
`LINUX_IN_CAR_QUALIFICATION.md`.
