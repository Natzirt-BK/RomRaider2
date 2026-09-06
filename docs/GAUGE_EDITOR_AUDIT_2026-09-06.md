# Gauge/editor and companion-project audit — September 6, 2026

This is a follow-up to the [earlier project audit](PROJECT_AUDIT_2026-09-06.md)
and [repair qualification](AUDIT_REPAIR_STATUS_2026-09-06.md). It covers the
subsequent gauge, mounted-dashboard and editor command changes, plus offline
checks of the owner's ECUFlash/Forester/EVO workspaces. It is not a blanket
security, hardware or calibration-safety certification.

## Findings and repairs

| Finding | Outcome |
| --- | --- |
| Compose staging omitted its new portable-runtime producer dependency | Fixed in `e64b3234`; clean hosted Android, SteamOS and both Mac package jobs passed in [run 34022049448](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34022049448). |
| JavaFX table refresh could lose the selected block | Selection is retained; multiply and custom adjustments apply to the whole selection with grouped Undo. |
| Reload could lose work if implemented as close-then-open | Added load-first replacement, explicit discard confirmation, newer-edit detection and pending-operation guards. |
| Definition size parser indexed before the start of short strings and could overflow unit multiplication | Short byte counts and case-insensitive units now parse; negative/overflowing counts fail explicitly. |
| Shared-cell cache used storage-type codes as byte widths and missed partial overlaps | Float and MOVI20 addresses now use actual byte widths; every overlapping byte region refreshes all affected cell models. Edit/Undo/Redo tests cover float aliases and partial integer overlaps. |
| Legacy checksum table opened an unowned Swing dialog from the model | Warnings now use the existing UI-neutral ROM interaction boundary. Invalid/disabled checksums are still reported; this does not suppress warnings or approve a ROM. |
| Android packaging omitted the software license and STI attribution | Both notices are now generated assets, accessible through About / licenses. Package verification compares their bytes with the repository originals; emulator checks exercise both dialogs. |

The selection/reload/parser work is at `551b44c4` with hosted
[Linux/Windows](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34022661714)
and [Android/Mac/SteamOS](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34022661871)
passes. The subsequent shared-cell/checksum-boundary fixes at `4824204a` also
passed fresh [Linux/Windows](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34023171449)
and [Android/Mac/SteamOS](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34023171471)
qualification. The final Android notice change still requires fresh packaged
artifacts before publication.

## Application checks

- Full Ant core tests and Linux compilation pass. Existing optional native and
  external-corpus skips remain outside this result.
- All 73 display-enabled JavaFX tests and 35 Compose tests pass locally.
  The new tests exercise real JavaFX controls with synthetic ROMs.
- The portable check renders eleven new gauge faces across 385 edge cases,
  including missing/invalid values and unit-aware reference scales. Android
  native tests cover all themes and session/CSV continuity with injected fake
  transport; they are not physical USB or ECU timing tests.
- Premium needle motion changes only the pointer, never numeric readings,
  warning calculations, peaks or logged values. No startup sweep fabricates
  live readings. Gauges-only switching is not background recording.
- Version rejection fixtures, eight bundled-dependency hash checks and six
  advisory-scanner tests pass.
- A fresh OSV scan at 08:48 UTC queried 228 verified Maven coordinate/version
  pairs and returned no known advisories. Seven bundled JARs remain unmapped;
  native binaries, JDK/OS and unlocked build transitives are not covered. See
  [advisory scope](DEPENDENCY_ADVISORY_SCOPE.md).

## ECUFlash and exact definition checks

No device was opened or polled. ROMs, definitions, profiles, keys and private
vehicle evidence were not uploaded. In-memory serialization below never wrote
a ROM file; the source-file bytes were rechecked afterward.

| Scope | Fresh result |
| --- | --- |
| ECU Tools companion installer at `ab50d34` | All 11 shell suites pass. Music lifecycle includes 14 silent regressions for terminal hangup, killed owner, EOF, completion and return-to-menu. USB tests use fake sysfs/Wine; VM harness checks do not start a connected test. |
| EVO `88780008`, SH7055, 512 KiB, IX GT-A/Wagon automatic family | Existing offline audit passes with zero review items: 156 data tables and 151 axes per editor, 156 shared data addresses, no coverage gaps, 319 unresolved structures still withheld. Pinned input and retained application-evidence hashes match. Historical filenames containing “EvoX” are not treated as target identity. |
| EVO final RomRaider v4 on current application core | Exact-match load: 156 tables, 307 regions, 9,864 numeric cells; no bounds/missing-cell/nonfinite findings. In-memory no-edit serialization is byte-identical. |
| Forester `Z2WC412I_DM23100`, ECU `3B12584306`, 1 MiB baseline definition | Base and retained test image load 609 tables, 884 regions and 19,200 numeric cells without structural/nonfinite findings. |
| Forester installed custom overrun definition | Matches its controlled workspace copy by SHA-256. The retained test image loads 618 tables, 896 regions and 19,286 numeric cells without structural/nonfinite findings. Its paired ECUFlash custom XML also matches the controlled workspace copy. |
| Forester serialization/checksum | Base no-edit serialization changes four save-stamp bytes. The retained v33 test ROM still has its previously documented stale checksum; serialization changes its four checksum bytes and three save-stamp bytes. These are **not byte-identical round trips** and do not make the retained input flash-approved. All original files remain unchanged. |

The independent Forester overrun and rev-limit/DBW audits also pass, as do the
three final overrun Logger variants, their exact source-hash checks, and six
RAM-variable code-use checks. A legacy Logger audit script referenced an obsolete
Downloads directory; its local path was repaired to use the pinned workspace
v370 sources without changing those sources or any definition. Shared axes and
intentional aliases remain visible in the XML inventory rather than being
misrepresented as independent calibration regions.

The fresh EVO audit also rechecks retained ECUFlash 1.44.4870 application-load
and controlled-file-edit evidence. It does not rerun ECUFlash against a cable.
The Forester results are structural and serialization checks, not proof that
every calibration's physical meaning or tuning value is correct. A private
legacy ECUFlash shortcut bypasses the maintained companion launcher's checks;
it was inspected but not launched or silently replaced.

## Logger-profile and release limits

The Shinji profile contains 18 ECU selections plus an external AEM input. With
the stock/custom-overrun v370 XML, the portable Android path cannot resolve
`DM019`/`DM911` (desktop DimeMod runtime-discovered channels) or `P200`/`P201`
(desktop calculated channels); external serial AEM input is also unavailable.
The portable resolver already reports unavailable selections instead of
substituting addresses or values. Working basic SSM logging does **not** establish
full desktop profile parity. Derived channels and version-checked DimeMod
discovery remain explicit mobile follow-ups; no guessed RAM addresses were added.

Public downloads remain 1.1.1 until new artifacts are independently verified and
published. The permanent Android signing identity cannot update the old public
certificate in place. Follow the [data-preserving migration checklist](ANDROID_1_1_2_MIGRATION.md).
Mac packages are unsigned. Physical Forester/EVO/OpenPort, phone/provider,
Windows adapter, Mac and Deck acceptance remain deferred. Production flashing
and live tuning remain unqualified.
