# Background desktop diagnostics

Local development after 1.1.8 RC1. No public release, adapter additions, ECU writes
or vehicle testing are included in this work.

## Read Codes workflow

The retained Swing Read Codes menu action captures its definition entries, ECU
identity, target module and connection settings before confirmation. After
approval, a dedicated worker stops the logger and waits for that controller to
finish before opening a diagnostic connection. A still-running controller causes
the read to fail without opening another connection.

An owned progress window keeps the event thread responsive. **Cancel Read** and
the progress window's close button both request cancellation. The window stays
open until the operation and its connection cleanup finish. Cancellation does
not force-close a connection from another thread or promise that native calls
can be interrupted immediately.

Logging cannot restart and another diagnostic read cannot begin while the
operation owns its reservation. Cancelled and failed reads do not restart
logging. If logging was active when the confirmation appeared, the user can
choose **Resume logging after a successful read**. That option is honored only
after the result window closes and the original logger/setup is still current.
Closing the logger, replacing its catalog, or changing its connection settings
invalidates the operation's results and automatic resume.

Results are displayed on the event thread, after the connection has closed.
Empty results are described as no codes among the available definition entries,
not a clean bill of health for every module in the vehicle. The existing
[standard-code validation](DESKTOP_DIAGNOSTIC_READS.md) remains in use.

## Ownership and cleanup

- The worker receives copied serial/KWP timing, adapter selection and module
  address bytes. Its connection factory does not look up current global settings.
- The request checks owner/setup validity and interruption before connection
  creation and between read stages. The UI checks again when delivering results.
- Cancellation after work finishes but before UI delivery still suppresses the
  queued result. A closed owner receives no result or reconnect action.
- Completion is queued only after cleanup exits. Cancellation does not use a
  future's early completion notification to release the adapter reservation.
- Cleanup runs with the worker's interrupt flag temporarily cleared, then
  restores it. A failed close prevents success; a close failure following an
  earlier error is retained as a suppressed exception.
- The controller no longer holds its monitor during its bounded worker join.
  Status checks remain available while it stops, and an old stop cannot clear
  the reference to a newer worker.

The old synchronous compatibility entry point remains; the normal menu uses
the new captured-request workflow. Other retained Swing diagnostic tools have
not all been migrated to workers by this change.

## Saved results

CSV reports now contain both standard and DimeMod codes, including reports with
only DimeMod results. Current/memorized flags are retained, shared DimeMod entries
appear once, and commas, quotes and line breaks in names are escaped. Files use
UTF-8 and retain the existing three-column diagnostic format. Live recording CSV
formatting is unchanged.

Image exports now include both tables instead of only the first one. Filenames
include milliseconds, and both export paths reject an existing destination rather
than overwrite it. The CSV writer removes a newly created incomplete file if its
write fails; it never removes a pre-existing destination after a collision.

The report content is captured when the result window opens. Export still runs
through the retained window's Save controls; this change does not migrate those
file writes to a background worker.

## Offline verification

Unit tests cover event-thread responsiveness, cancellation before work and after
queued completion, a synthetic non-interruptible operation, owner closure,
cleanup failures, copied module/timing values and controller stop responsiveness.

The packaged probe uses the real Swing logger's reservation and progress dialog
with an injected synthetic request. It checks successful, cancelled, title-bar
closed and failed reads, including a deliberately delayed close. It also tries
to start logging and a second read while the reservation is held. It does not
open a real adapter or exercise the menu's hardware connection factory.
It also drives the real CSV and image Save buttons, reads back the synthetic CSV,
and checks that the image includes the standard and DimeMod tables.

Hardware timing, physical controls, Windows/macOS native windows and real-vehicle
diagnostic coverage remain unqualified by these tests.

## Local qualification — September 9, 2026

- Core: 723 tests, 720 passed and three optional-environment skips; no failures.
- JavaFX: 291 tests passed with native-window, logger-stress and synthetic
  audit-capture checks enabled; no skips.
- Compose: 48 tests passed with native-window coverage; no skips.
- Shared portable checks passed. The empty shared-core unit-test task remained
  skipped; the separate portable checks ran.
- The fresh Linux image passed dependency/module checks and unmodified launcher
  startup, version consistency, first-run dialog handling and normal shutdown.
- The packaged diagnostic probe passed all four worker scenarios, both Save
  controls, and shutdown profile persistence. Its exported PNG was also inspected.
- The same packaged core passed KDE and Swing installer flows, confirmation-close
  and native worker-close, including shutdown profile persistence.

The final core JAR SHA-256 is
`5e1e2727ff10147ef021dc44416f490dc7f809e4aa76ef14e3eb327e3b691cc1`.
The image's JAR matched the tested build. This is a local artifact hash, not a
public release asset hash.

An earlier full UI retry timed out during shutdown, and two private window
managers failed to start while RAM/swap were nearly exhausted. Accumulated test
images were moved from memory-backed `/tmp` to disk-backed qualification storage;
test artifacts were preserved and no user application or VM was stopped. The
final reruns above passed with normal memory headroom and unchanged timeouts.
The resource-starved attempts were not counted as passes.

Published 1.1.8 packages, installed applications, Android code, vehicle files and
signing material were not changed. Further release work requires a new numeric
patch version and its own platform qualification.
