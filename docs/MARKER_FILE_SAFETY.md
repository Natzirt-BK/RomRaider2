# Marker-file loading and save safety

Implemented in **1.1.3 development source**. Public 1.1.2 packages are unchanged.
Markers remain a separate `<capture>.csv.rr2markers.properties` sidecar; neither
loading nor saving markers modifies the captured CSV, a ROM or live logging.

## JavaFX workflow

Marker loading and saving use one owned worker. The table, charts, statistics
and playback remain available while a sidecar is being read. Add/remove controls
are disabled until loading succeeds, and while an accepted edit is being saved.
The previously saved list remains visible until persistence succeeds. Marker
label text remains available after a failed save rather than being cleared.

An invalid/unreadable sidecar does not prevent CSV analysis, but marker editing
stays disabled. **Reload markers** retries after the file is corrected or restores
the latest sidecar after an external-edit conflict. There is no automatic reset,
partial import or overwrite of an unsupported document. Reload also clears the
read-only state after a failed edit. A view without an associated CSV can use
explicitly labeled session-only markers; it writes no file.

Closing the view cancels pending reads and rejects late UI callbacks. An already
accepted edit is allowed to finish its save after closure rather than being
silently cancelled. A post-close failure is recorded in the application's
diagnostics log; reopen the recording to verify the saved list if the view was
closed while **Saving markers** was displayed. This is not a guarantee that a
save survives application/process termination or a storage failure.

## Validation and compatibility

The existing version-1 Java properties format is retained, including escaped
Unicode labels. Reads and writes are bounded to 1 MiB, 4,096 markers and 512
UTF-16 code units per label. Invalid surrogate sequences are rejected. Version,
count, sample, type and label fields are required; duplicate or unknown
properties, invalid types and incomplete entries reject the entire document.
All sample indices must belong to the loaded dataset.

Earlier code silently skipped malformed or out-of-range entries. The strict
reader now reports those files instead of displaying a partial list that could
overwrite the omitted entries on the next save. Correct a separate copy of the
sidecar or restore the matching full recording before reloading. Sample-range
validation is not proof of CSV identity: version 1 does not fingerprint the CSV,
and a different recording with the same number of rows can still pass it.

Removing the last marker now writes a valid version-1 document with zero markers
through the same atomic path. It does not delete the sidecar. Older readers can
read that empty list. Unknown documents, symbolic links and directories are not
treated as empty marker files or overwritten as recovery actions.

## Saves and conflicts

The reader captures the exact sidecar bytes (or explicit absence) alongside an
immutable marker list. Saving first validates and serializes a frozen proposal,
then uses a unique same-directory temporary file, synced output and required
atomic replacement. There is no non-atomic fallback. Existing fixed-name `.tmp`
files are not reused or removed. Filesystems without atomic replacement report
a failure and retain the prior sidecar.

The destination is checked against the captured bytes before preparing output
and again immediately before replacement. Changed, newly created or removed
sidecars require a reload. Bounded lock stripes serialize cooperating writers
to the same normalized path within one JVM, so two workspace saves cannot both
replace the same snapshot. This is optimistic conflict detection, not a universal
filesystem compare-and-swap: a noncooperating process or another path alias can
still race between the final check and replacement. Avoid editing the same
sidecar from separate applications simultaneously.

## Swing and qualification

The legacy Swing analysis pane also uses captured snapshots and conflict-checked
saves. Failed loads disable marker editing; failed saves restore the preceding
displayed list, retain label text, and require reloading the log. Subsequent
[Swing loading work](SWING_LOG_LOADING.md) now prepares the CSV, initial statistics
and sidecar snapshot off the event thread. Later range calculations and marker
writes now also use separate workers. Pending saves retain the prior list;
detaching permits an accepted write to finish while suppressing late UI delivery.
Reattachment after a pending save requires an explicit reload to verify the saved
list before editing. The shared store never enables ECU access.

Automated checks cover Unicode/empty round trips, bounds, invalid and unknown
documents, protected paths, changed/created/deleted sidecars, simultaneous
cooperating writers, interruption and source-CSV preservation. Worker/native
tests cover disabled controls, reload after failure/conflict, immutable proposals,
off-UI loading, closure and accepted-save completion. Tests use synthetic files.
The settings-window regression test also now constructs/disposes Swing UI on the
event thread after an intermittent off-thread failure during qualification.

Initial marker-file qualification passed 14 focused core/Swing tests (including nine bounded
store cases), the full Ant unit suite and Linux core build, 197 JavaFX tests
(five marker-worker and two native marker cases), 35 Compose tests, portable-core
checks and Linux JavaFX staging. The actual marker recovery screen was inspected
at 800×600. Android code and public downloads were not changed.
