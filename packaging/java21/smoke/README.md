# Packaged desktop diagnostics

These separate Java 21 probes use synthetic bytes only. They never load vehicle
definitions, connect a Logger session, or write ECU memory. The output directory
owns all settings, logs, screenshots and tiny synthetic files.

`PackagedDocumentSafety` exercises ten guarded-close/save-baseline cases.
`PackagedDesktopRepair` also exercises the interpolation/axis UI, offline preview,
small inspector and Dyno scroll recovery, settings cancellation, and analysis
numeric sorting/ranges. Both must terminate successfully with their `..._PASS`
sentinel; a launched process alone is not a pass.

The repair probe also checks Logger touch/full-screen presentation with startup
auto-connect disabled and Definition Manager's native work-area bounds.

Compile with a full JDK 21 against the candidate's `app/*` and `app/lib/common/*`
JARs on Windows (`lib/app/*` and `lib/app/lib/common/*` on Linux), then package
the resulting classes in a diagnostic JAR. The runtime images
intentionally omit the standalone `java` command. Make a **separate copy** of the
verified image, add the diagnostic JAR in its `app` directory, and change only
that copy's launcher `.cfg` main class to the probe's fully qualified class name.
Add `$APPDIR/diagnostic.jar` to its `app.classpath`. On Windows write this text
without a UTF-8 BOM (ASCII suffices). Run the copy's native launcher with an
absolute, disposable output directory as its sole argument; Linux needs a
display or Xvfb. Do not replace user settings, package archives, or release pins.

Record the original archive SHA-256, source commit, package verifier result,
runtime platform/scale and exact diagnostic changes. This checks the packaged
JVM and application JARs, but is **not an unmodified production-entry-point
acceptance run**. It also does not exercise the native Save As file chooser,
real-definition reopen/checksum paths, actual ECU hardware, physical mixed DPI,
accessibility, or user acceptance. Keep those gates separate in the audit.

## Logger installer probe

`PackagedLoggerInstaller` uses the packaged Swing logger, real dialogs and tiny
synthetic XML definitions. Compile it with `-proc:none` using the classpath above;
its main class is `com.romraider2.smoke.PackagedLoggerInstaller`. Pass a fresh
absolute output directory and optionally `native`, `close`, `native-close`,
`worker-close` or `native-worker-close`.
Default mode tests the Swing fallback picker. Native modes require KDE `kdialog`,
`xdotool` and the private-display marker set by `packaging/run-desktop-window-tests.sh`.
Do not run native automation on a user's display. The wrapper clears inherited
Wayland selection and forces Qt/GTK helpers onto its private X11 display.

The flow checks picker cancellation, confirmation cancellation, changed setup
during confirmation, invalid XML rejection, saved installation and shutdown
backup persistence. Close mode exits through the real shutdown path while an
installation confirmation is open. Worker-close mode closes after submission,
before completion can commit. Require exit code zero and the matching
`PACKAGED_LOGGER_INSTALLER_PASS`, `PACKAGED_LOGGER_INSTALLER_CLOSE_PASS` or
`PACKAGED_LOGGER_INSTALLER_WORKER_CLOSE_PASS` sentinel,
which is emitted only after shutdown persistence checks. Initial missing-settings
and absent synthetic Dyno-corpus warnings are expected; backup failures are not.
No controller is started and no vehicle file is opened. This remains a diagnostic
entry point in a separate app-image copy, not untouched-launcher acceptance.

## Linux launcher and compilation checks

`PackagedDiagnosticRead` exercises the real Swing logger's background-read
reservation and progress dialog using an injected synthetic request. Build a
separate diagnostic image with main class `com.romraider2.smoke.PackagedDiagnosticRead`
and run it through the private-display wrapper with one fresh absolute output
directory. Require `PACKAGED_DIAGNOSTIC_READ_PASS` after all four scenarios and
shutdown backup checks. It tests success, Cancel Read, title-bar close and failed
reads, delayed cleanup, duplicate-operation rejection and blocked logger restart.
It saves progress-window captures and verifies the real CSV/image Save controls
with standard and DimeMod results. No real adapter is opened, and this is not a
hardware acceptance test of the menu's connection factory.

Keep copied runtime images in disk-backed storage. On Linux, `/tmp` may be a
memory-backed filesystem; accumulating images there can cause swap pressure and
invalid timing results. Set `TMPDIR` (and `-Djava.io.tmpdir` for JVM scratch files)
to a private disk-backed test directory when needed. Do not loosen timeout checks
to count a resource-starved run as a pass.

Run `test-linux-launch.sh IMAGE FRESH_ABSOLUTE_OUTPUT_DIRECTORY` through
`packaging/run-desktop-window-tests.sh`, using a disposable copy of an unmodified
Linux app image. It compares startup-log and window-title versions, dismisses the
known first-run ECU Definitions Manager, closes the editor, and requires a clean
process exit with `PACKAGED_LINUX_LAUNCH_CLOSE_PASS`. This is an actual production
entry-point check, but still uses a virtual display and no vehicle files.

Run `ant -f packaging/java21/smoke/incremental-build.xml` with JDK 21 to check the
shared Ant compiler macro. It changes a constant without touching its consumer,
removes another source, and checks that rebuilt bytecode reflects both changes
while a non-class resource survives. Require `INCREMENTAL_BUILD_CONSISTENCY_PASS`.
Disposable fixture outputs remain under `build/compile-regression-*`.
