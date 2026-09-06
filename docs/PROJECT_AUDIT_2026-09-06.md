# Full-project audit — September 6, 2026 UTC

Subsequent work: [data-preservation repairs](DATA_PRESERVATION_FIXES.md) addresses
A1 and A2; [portable XML hardening](XML_IMPORT_HARDENING.md) addresses A5.
[Gauge warning/conversion repairs](GAUGE_WARNING_REPAIRS.md) address A3/A4.
The findings and evidence below describe the audited `7c2f0ce4`
snapshot and remain historical evidence, not a claim that those source fixes
are already in the public Android APK. Other findings remain open.

## Outcome

GitHub `master` now includes the tested Android update/setup work and JavaFX
Logger controls at `7c2f0ce464a5c2d1bc6e7d48ab48a61b7b939d47`. All six platform
build/package jobs and the separate Android lifecycle regression job passed.
**That is not a clean release audit: two data-loss risks need repair before the
next release, alongside import hardening and gauge-warning correctness.**

The public release remains **1.1.1**, built from `bec54a34`. No public assets,
tags, installer pins, installed apps, user settings or vehicle files were
replaced. The findings below are not silently fixed by this report. Only source
integration and audit/status documentation were updated during this audit.

## Scope and evidence

Reviewed the desktop core, JavaFX and retained Compose shells, Android app and
portable core, ROM save/recovery, XML imports, logging/export, gauge controls,
live-tune/flash boundaries, packaging/signing, release metadata, dependencies,
privacy checks, installer upgrade behavior and the recovered feature backlog.
The companion installer was audited at `1804e18` in
`Natzirt-BK/subaru-ecu-tools-linux`; it was not modified.

This is a cross-project source, automated-test and synthetic-workflow audit,
not an exhaustive proof about every line, a penetration test, a complete
dependency-vulnerability/license opinion, or hardware certification. No ECU,
OpenPort, phone, real ROM, private definition or profile was used for new tests.
Vehicle success recorded below is historical user-reported evidence, not a fresh
connected test. Public examples and fixtures are synthetic.

Fresh checks at the exact audited source commit:

| Area | Result | Evidence / limitation |
| --- | --- | --- |
| Linux Java 21 + JavaFX | PASS | [Core tests, display-enabled JavaFX tests, build and package verification](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34008591564) |
| Windows x64 + JavaFX | PASS | Same run: core/non-display tests, compilation, both J2534 helper architectures, application image and package verification; not physical UI/adapter acceptance |
| Android distribution packages | PASS | [Platform run](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34008605364): unit/shared checks, lint, standard/test APKs, pinned signing certificate |
| macOS ARM64 and x64 | PASS | Same platform run: native-host tests and unsigned Compose packages; not physical Mac acceptance |
| SteamOS x64 | PASS | Same platform run: tests, JavaFX bundle and package checks; not a real Deck/Game Mode/controller test |
| Android lifecycle / upgrade / CSV | PASS | [Regression run](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34008591560): emulator process restart, same-key upgrade, retained recordings, pending-save Activity recreation, clear selection, corrupt setup, and actual desktop parser reading Android-exported CSV |
| Local JavaFX / Compose | PASS | 56 JavaFX tests with Xvfb display checks; 34 Compose model tests |
| Portable core | PASS | `portableCoreCheck`, including 142 OpenPort control, 69 MUT-II and 32 CSV assertions; Gradle's disabled shared-core `test` task is not the test entry point |
| Companion installer | PASS, coverage gap found | All 11 top-level shell test scripts, including 14 music lifecycle cases; the separate custom-content migration probe below nevertheless reproduces data loss |
| Dependency integrity | PASS, not a vulnerability verdict | Eight audited binary/source hashes verified; Gradle verification/lock configuration used by builds |
| Published release | Consistent | Exactly one public release, 1.1.1, with seven packages and seven checksum sidecars; cached anonymous downloads rehashed successfully, current API digests match those packages, Linux installer pin matches |

The fresh Linux core run reports 345 tests: 342 passed, zero failures/errors,
and three opt-in skips (native J2534 linking, local CSV corpus, BMW XDF corpus).
Skipped hardware/corpus checks are not counted as successful qualification.

## Findings, ordered by priority

### A1 — P1: legacy installer migration can delete user definitions and profiles

The installer copies only `config/user/settings.xml` plus `logs`, `roms` and
`repositories`, then recursively removes an installer-marked legacy directory.
It does not preserve the rest of `config/user`, including managed definitions,
profiles or other custom content. See
[migration and cleanup](https://github.com/Natzirt-BK/subaru-ecu-tools-linux/blob/1804e1838788ac40a992dc819efa64b69dcd1d02/linux/install-romraider2#L99).

Reproduction used an isolated fake application image and installer-marked legacy
directory containing a synthetic custom definition, profile, settings and log.
Installation reported success. The log and settings survived; the definition
and profile did not exist in the new installation, and the legacy directory was
deleted. No backup of that legacy directory was made.

Current-to-current updates have the same incomplete active-data migration, but
retain the old installation as a backup, so that path is recoverable manually.
This distinction corrects the earlier blanket assumption that omitted user
content was always backed up. Existing installer tests miss these files.

Required repair: inventory and migrate all user-owned storage without copying
old executable files over a new release; retain legacy installations as backups
until migration is verified. Add current/legacy/repeated-upgrade tests for
managed definitions, profiles, custom files and failures. Do not automatically
delete the predecessor after only the current narrow whitelist succeeds.

### A2 — P1: Android ROM save marks the document clean before close succeeds

The `SAVE_ROM` callback calls `markSavedIfCurrent` and queues its success/recovery
update **inside** the output stream's try-with-resources block, before `close()`.
See [MainActivity SAVE_ROM](../platform/android/app/src/main/java/com/romraider/mobile/MainActivity.java).
A document provider can fail during close after accepting the writes. The catch
reports a failure, but the in-memory document has already been marked clean.
[Recovery storage](../platform/android/app/src/main/java/com/romraider/mobile/MobileRomRecoveryStore.java)
deletes its snapshot for a clean document, making this more than a false toast.

A synthetic `OutputStream` that accepts writes and throws on close reproduced
the callback ordering with the production `PortableRomDocument`: after the
exception, `hasChanges()` was false. This is an ordering reproduction, not a
claim that a physical phone/provider failure was induced.

Required repair: close successfully before publishing saved state/success;
preserve dirty state and recovery on any output failure. Add null-stream,
write/flush/close-failure and edits-during-save tests against the actual Android
save boundary. Desktop `RomFileService` already separates writing from saved-state
publication; this finding does not reopen the repaired desktop save defect.

### A3 — P2: JavaFX alarm gauges discard warning hysteresis state

[FxLoggerWindow.alarmGauge](../ui/javafx-desktop/src/main/java/com/romraider2/javafx/FxLoggerWindow.java)
passes `null` as the previous state on every render. With a high warning of 100
and hysteresis of 5, a reading of 101 enters HIGH; a subsequent 98 should remain
HIGH, but the current UI invocation returns NORMAL. This was reproduced with
the production `LoggerGaugeConfiguration`.

Required repair: retain per-channel warning state, update it from readings,
share it with detached gauges, and reset it deliberately on a new session,
configuration/conversion change or channel removal. Invalid/non-finite readings
also need an explicit display policy. Custom warning/scale editing is still
missing from JavaFX; stored/legacy preferences are not proof of UI parity.

### A4 — P2: changing units leaves custom gauge limits in the old units

Gauge configuration stores numeric limits keyed by parameter ID, without a
conversion identity. `LoggerDesktopRuntime.setUnitOption` changes the converter
and clears samples, but does not transform, invalidate or scope that saved
configuration. The new JavaFX unit selector exposes this existing runtime path.

Example using the production configuration: a 100-degree Celsius threshold is
NORMAL for 80 C, but becomes HIGH for the equivalent 176 F if its unchanged
numeric threshold is interpreted in Fahrenheit. Custom gauge scale endpoints
have the same problem. This affects channels with preexisting custom limits;
default channels without custom configuration are not implicated.

Required repair: bind configuration to a conversion identity or explicitly
clear/reconfirm it when units change. Do not silently reinterpret limits or
infer conversions merely from text labels. Include profile/restart round trips.

### A5 — P2: UTF-16 bypasses portable XML entity-declaration rejection

[PortableXmlReaderSupport](../platform/shared-core/src/main/java/com/romraider/portable/logger/definition/PortableXmlReaderSupport.java)
scans raw bytes for ASCII `<!ENTITY`. The portable ECU-definition reader has a
similar byte-to-single-byte-text check. UTF-16 interleaves zero bytes, so these
checks do not detect its entity declarations.

A small synthetic profile with one internal entity was rejected as UTF-8 but
accepted and expanded as UTF-16 by the production profile reader on JDK 21.
External-entity features are disabled and an empty resolver remains in place:
**this is not a demonstrated external-file/network disclosure or an OOM test**.
It is a confirmed bypass of the explicit no-entity policy; expansion protection
then depends on parser features that the portable helpers may ignore when
unsupported. Android parser behavior was not separately induced in this probe.

Required repair: reject entity declarations independently of document encoding
and parser optional features, while preserving the legitimate supported DTD
syntax. Add UTF-8, UTF-16LE/BE, harmless internal-entity and external-resolution
negative fixtures. Do not classify the VDF/BMW DOM factories as XXE merely
because they are unconfigured: the inspected calls create new output documents.

### A6 — next-release gate: version bump and Android signing migration

All current source/package defaults still say 1.1.1; Android defaults to
versionCode 110405. The audit's newly built artifacts retain those defaults and
are **CI evidence, not replacements for the existing public 1.1.1 downloads**.
Before publication, bump all platform versions together, increase Android's
versionCode, rebuild and verify the exact intended release commit.

The new standard and separate-test APKs were downloaded from the fresh CI run
and both passed cryptographic verification with the pinned permanent certificate:
`e47ef588575ddc47e5e69bb02cdcb0e82a60a54bfa060a15ea584d8b40304f34`.
Public 1.1.1 uses a different certificate:
`d450a406ddb707ead0e5ebac0b109465405e2b8d6249eb9991003255cf3d8fcb`.
The same-key emulator upgrade pass does not establish an in-place upgrade from
that old public APK. Export/recovery and one-time migration instructions are
required; uninstalling is not a lossless update. No phone was uninstalled or
cleared. A secure offline signing-key/password backup remains an operational
requirement, not something this audit claims to have completed.

### A7 — P2: status/naming and platform feature claims have drifted

The old implementation status still describes RC milestones and Android Preview
2. Active Android UI strings still include `RC5 QUALIFICATION PENDING` and
“this preview,” despite numeric release naming. These are not the intentionally
retained `.preview` application IDs or the legitimate offline simulation preview.
README highlights also mixed legacy/Compose capabilities with JavaFX availability
(high contrast, MAF/injector entries, warning configuration).

This audit marks the old status historical, adds this current matrix, and narrows
README feature wording. Active Android release-era wording remains a code
follow-up. Keep old dated release evidence rather than rewriting its history.

### A8 — audit limitation: no current vulnerability verdict

Targeted tracked-file checks found no vehicle ROM extensions, keystore files,
private-key blocks or common GitHub token patterns. License/source notices and
packaging copy/check paths are present, including JavaFX. This is not a full
history/secret scan or complete transitive-license audit.

The Dependabot alerts API returned HTTP 403 stating alerts are disabled; no
permissions or repository security settings were changed. Dependency hash and
lock verification proves expected bytes, not that dependencies have no known
vulnerabilities. Enable an authorized, maintained dependency/advisory audit as
separate follow-up; do not report this project as vulnerability-free.

## Capability and qualification matrix

| Platform / workflow | Implemented now | Still open |
| --- | --- | --- |
| Linux/Windows JavaFX | Editor, Logger, analysis/Dyno, channel categories/units, clear controls, rolling statistics; automated save/close regressions | Findings above; custom warning/scale UI; MAF/injector workflow parity; representative real-file, DPI/accessibility and Windows adapter acceptance |
| macOS Compose | Native ARM64/x64 packages and retained Compose workflows | Shell parity decision, physical Mac workflows; packages are unsigned |
| SteamOS | JavaFX Desktop Mode bundle | Deck/Game Mode/controller/keyboard/USB and physical display qualification |
| Android | Saved logger setup, stable signing preparation, read-only SSM/MUT-II, retained recording/export, offline ROM editing | Save/import findings, foreground-only lifecycle, system-inset acceptance, real phone/provider and larger-channel/sustained logging tests; Android does not repair ROM checksums |
| Forester / SSM | Basic Android logging reported working by user; earlier desktop logging evidence exists | Intended full-profile rate/timeouts, sustained sessions and real recorded CSV acceptance on the next release |
| EVO / MUT-II | Protocol, selection and synthetic checks; OpenPort transport implemented | Exact EVO + phone + USB-C adapter + OpenPort parked-car qualification |
| External sensors | Retained plugin interfaces and selection/setup | Actual requested sensor workflow; optional Windows Innovate LM-2/COM4J issue remains unqualified, not evidence that AEM is broken |
| Live tuning / flashing | Offline draft, preflight, recognition and mock simulation | No qualified production ECU-write backend; exact target evidence, transaction/recovery safeguards and bench qualification required |

The live-tune executor inspected accepts `MockEcuTransport`, not a production
transport. Flash registry scaffolding is not a qualified backend. No new vehicle
write path was found or exercised. Do not confuse transport writes that send
read-only logging requests with writing ECU calibration memory.

## Recommended next work

1. Repair A1 and A2 with failure-path regression tests before another release.
2. Close A5's encoding-independent XML boundary, then A3/A4 together with the
   custom gauge editor so warning behavior and unit persistence are coherent.
3. Finish current naming/status cleanup and release/signing migration checks;
   rebuild the chosen numeric patch version and repeat the automated matrix.
4. Run supervised phone/Forester/EVO and Windows hardware acceptance when the
   owner makes the equipment available. Do not silently restart polling.
5. Resume useful feature work: remaining editor commands, read-only log-to-map
   tracing, binned analysis and fork-adoption parity. Background logging and
   additional adapters/protocols need separate scoped designs. Production live
   tuning remains a later, explicitly qualified milestone.
