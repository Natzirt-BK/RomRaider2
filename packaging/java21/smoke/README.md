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
