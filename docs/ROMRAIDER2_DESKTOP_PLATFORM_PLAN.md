# RomRaider2 Desktop and Mobile Platform Plan

Status: proposed execution plan
Origin: AUG2 audit review, 2026-09-02

The item-by-item release gate is recorded in
`AUG2_DESKTOP_AUDIT_BASELINE.md`. That baseline, rather than the earlier RC4
visual fixtures, defines desktop acceptance.

## Objective

Stabilize the desktop ECU editor/logger, prevent desktop and mobile UI concerns from bleeding together, and determine whether JavaFX should replace Compose Desktop for Windows, Linux, and macOS.

The goal is not to rewrite the domain layer. ECU protocols, ROM/table models, logging, analysis, persistence, recovery, and shared services should remain UI-neutral and reusable.

## Target boundaries

```text
shared-core and platform-neutral services
        |-- JavaFX desktop UI: Windows, Linux, macOS
        |-- Compose Android UI
        `-- SteamOS: an explicit desktop or handheld profile

Swing remains only as a temporary compatibility implementation.
```

Android must depend on shared-core, not desktop UI modules. SteamOS must be classified deliberately: Desktop Mode should follow the desktop qualification path; a touch/handheld profile may use Compose, but it must have its own entry point and audit.

## Phase 0: Establish a reliable baseline

1. Record the AUG2 audit as the desktop baseline and separate confirmed defects into toolkit, migration, product, installer, and documentation categories.
2. Fix the installer migration defect that leaves `config/user/definitions/logger/logger.xml` missing after upgrade.
3. Verify clean-install, upgrade-install, startup recovery, logger startup, definition loading, and ROM open/save on the current release.
4. Do not publish another release claiming desktop parity until these end-to-end checks pass. Isolated screenshots and package checks are not sufficient qualification.

Progress: the Linux installer has a local, regression-tested patch that
preserves `config/user/definitions` and the package-owned Logger profile backup
during an upgrade. A clean install of the exact refreshed public RC4 Linux ZIP
passed its archive and internal checksum checks. A forced upgrade preserved the
seeded settings, Logger XML/DTD, ECU definition, logs, ROM content, and
repository content byte-for-byte and created a recoverable backup. The Editor
and Logger reached the Compose shell under Xvfb. These changes remain
uncommitted and unpublished, and the visual workflow audit remains open.

The smoke test also found and fixed a production-runtime isolation defect: an
unconfigured Compose Logger previously read the legacy
`~/.RomRaider/profile_backup.xml`. Its automatic backup now belongs to
`config/user/profiles/profile_backup.xml`, under the package settings root.

Exit condition: a reproducible baseline exists and the current release can be tested without stale or missing user data.

## Phase 1: Stop the desktop bleeding and repair high-value defects

Keep Compose Desktop in place temporarily, but freeze new desktop UI expansion. Repair the defects that are clearly required regardless of toolkit:

- settings and user-level access;
- calibration document/table tabs;
- file chooser directory handling and definition filtering;
- logger definition and output-directory selection;
- channel visibility and more than six overview channels;
- offline analysis, playback, markers, time-series, and X/Y workflows;
- calibration 3D controls and parity with the retained Swing tools;
- keyboard navigation, accessibility, high-contrast behavior, and high-DPI layout;
- stale documentation and release notes that imply parity which is not present.

Keep legacy Swing workflows available while their replacements are built. Do not remove the compatibility path until each workflow has a tested replacement.

Exit condition: the desktop audit has explicit pass/fail criteria for every workflow, and no feature is considered migrated merely because a Compose screen exists.

## Phase 2: Time-boxed JavaFX desktop spike

Build a separate JavaFX desktop module over the existing neutral services. Implement only representative hard cases:

- native file selection and definition management;
- Logger setup and live workspace;
- calibration table editing and document tabs;
- 3D calibration viewing;
- settings, keyboard navigation, accessibility, and resize behavior.

Compare JavaFX with the repaired Compose shell using the same Windows, Linux, and macOS checks. Measure native dialog behavior, HiDPI rendering, accessibility, packaging, startup/recovery, testability, and development complexity.

Decision gate: adopt JavaFX for desktop only if it materially improves desktop reliability and maintainability. Otherwise, continue with Compose Desktop and apply the same module and audit boundaries.

## Phase 3: Platform-specific implementation and qualification

If JavaFX wins the spike:

1. Make JavaFX the desktop entry point while retaining Swing as a compatibility mode.
2. Migrate Editor and Logger by workflow, with parity gates rather than screen-by-screen migration.
3. Keep Android Compose in its own module, build, entry point, artifact, and audit.
4. Give SteamOS a separate qualification profile and explicit UI decision.
5. Use separate CI workflows, packaging jobs, release artifacts, and qualification records for desktop, Android, and SteamOS.

Each platform must pass its own audit before release. A successful Android build must not be used as evidence for desktop readiness, and vice versa.

## Release and maintenance rules

- Shared-core may not import Compose, JavaFX, Swing, AWT UI, or Android UI classes.
- Desktop, Android, and SteamOS release notes must state exactly which workflows were qualified.
- Keep a compatibility launch path until the replacement is proven in clean-install and upgrade scenarios.
- Re-run the full platform audit after changes to shared services, persistence, protocols, or packaging.
- Treat macOS as unqualified until both x64 and ARM64 bundles have been run through the same checks as Windows and Linux.

## Resume checkpoint

When continuing this work, start with Phase 0:

1. inspect and fix the installer definition migration;
2. reproduce the remaining AUG2 audit failures on a clean and upgraded
   installation;
3. record the results in the existing platform audit checklist;
4. then begin the JavaFX spike without changing the production desktop entry point.

This document is a plan, not an indication that the JavaFX migration has already been started.
