# Desktop reliability checkpoint

Local development after 1.1.8 RC1; no new public release or adapter support.
Base source: `44b39be433af4cb5df73416c034bd1b34341e200` plus the local changes
described here. Hardware was not connected or commanded.

## Installer and recovery

- Definition preparation parses frozen bytes without replacing the active file.
- The Swing request captures its initiating owner, catalog and setup. Obsolete
  completion is rejected before the serialized file/settings commit.
- Rollback belongs to one installation; it cannot use another installation's
  backup or overwrite a newer owned result.
- Successful installation is not undone by a subsequent stale catalog refresh.
- Both desktop workspaces save automatic profiles under the active settings
  directory. The retained Swing logger can still read its old backup location.

A Java 21 Linux app image was built using the normal packaging script. Separate
diagnostic copies received the probe JAR and a diagnostic main-class/classpath
change. Original app-image files and installed applications were preserved.
Tests used synthetic definitions,
isolated settings/home directories, disabled auto-connect, Xvfb and Openbox.

The KDE native picker and Swing fallback each passed picker cancellation,
confirmation cancellation, setup change during confirmation, invalid-definition
rejection and successful install/activation. The real shutdown path saved the
backup in the managed directory. Closing with installation confirmation open
preserved the original selection and installed no candidate file. The native
worker-close case also preserved the original selection after submission and
before completion could commit.

The initial probe could not drive KDE because Qt inherited the desktop Wayland
session. Test pickers were closed; the wrapper now forces native helpers onto
its private X11 display. The first completed fallback flow exposed the legacy
backup-path failure; subsequent runs require backup persistence before printing
their pass sentinel. Neither initial failure was treated as a clean pass.

## DimeMod diagnostic reads

The read-codes path now compares a fresh ECU ID and initialization payload before
using cached DimeMod runtime addresses. Captured bytes cannot be changed through
the SSM initialization object's input or getter. Owner/setup changes and failed
or cancelled responses cannot return stale diagnostic values. This adds no
discovery or write commands and does not modify normal logger reconnect behavior.

Matching identification bytes do not establish firmware identity. Full metadata
provenance, cache/session binding and negotiation cleanup remain open; see the
[lifecycle record](DIMEMOD_CACHE_LIFECYCLE.md#diagnostic-identity-follow-up).

## Compact analysis tables

MAF and injector analysis use shorter From/Below headings with units, and a
two-line Mean heading. The exclusive upper boundary remains explicit without
clipping in the narrow pane. Clipboard/export formatting is unchanged. Native
tests check rendered headings in both modes; 1024-pixel logger captures were
also reviewed.

## Build consistency

The native-launcher check exposed old version strings embedded in otherwise
unchanged classes. Ant had refreshed the generated Version class but skipped
consumers of its compile-time constants. The shared compilation macro now
rebuilds class files in its destination, retaining non-class resources and other
build outputs. A fixture verifies constant propagation, removal of orphaned
bytecode and resource preservation. This applies to desktop builds for all three
operating systems and the core test compilation.

The launcher probe also handles the known first-run ECU Definitions Manager
before closing the editor. Its original single-close timeout was a probe
limitation, not evidence of a production shutdown failure. Startup-log and
window-title versions must now agree.

## Final local verification

The final rebuilt core and staged desktop workspace passed:

- Core: 688 tests, 685 passed, three optional-environment skips, no failures.
- JavaFX: 291 tests, no failures or skips, including native windows, logger
  stress and synthetic audit captures.
- Compose: 48 tests, no failures or skips, with native-window coverage enabled.
- Shared portable checks, including streaming CSV, recording recovery, XML
  security and gauge layout checks. The empty shared-core unit-test task was
  skipped; portable checks ran separately.
- Ant compilation consistency fixture and shell syntax/patch whitespace checks.
- Fresh Java 21 Linux app-image build, including eight dependency hash checks
  and the runtime-module audit.
- Unmodified Linux entry point: matching version text, first-run dialog handling
  and normal process shutdown.
- Separate diagnostic copy of that image: KDE and Swing installer flows,
  confirmation-close and native worker-close, including persisted profile backup.

Final local JAR SHA-256 values (not public release asset hashes):

| Component | SHA-256 |
| --- | --- |
| Core | `0f4cca848a7d09d8a9e05e25505814e0730ef162c911361c714733548a0cd8a5` |
| JavaFX workspace | `03388473c6bb71e0a40b11e14a4f5f966f43c3ad152132ad033d2e4a3b6599e6` |

The packaged core and JavaFX JARs matched these inputs exactly. Initial and
intermediate images were not substituted for this final qualification.

## Acceptance limits

The automated checks do not qualify vehicle communication, actual ECU data,
physical display/input behavior, Windows/macOS native dialogs, power-loss
recovery or cross-process transactions. Diagnostic copies are not public builds.
Release 1.1.8 and the user's installed applications remain unchanged.
