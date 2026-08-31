# ROM recovery architecture

RomRaider2 keeps crash-recovery data separate from the ROM being edited. The
recovery service is UI independent so future editor, flashing, or headless tools
can use the same safety mechanism without putting storage logic in Swing.

## Current behavior

- An edit schedules a debounced snapshot from a cloned copy of the working ROM
  bytes. Disk I/O runs on a single daemon worker, never on Swing's event thread.
- Snapshots use atomic replacement where the filesystem supports it and retain a
  bounded history of five versions per open ROM.
- Each binary has a properties sidecar containing its source name and path, ROM
  ID, size, SHA-256 integrity digest, changed-cell count, creation time, and
  format version. Corrupt or incomplete snapshot pairs are not offered.
- Save, intentional close, reset-to-clean state, and normal application exit
  remove only recovery files owned by RomRaider2 for that ROM.
- Save As keeps the original recovery identity until the successful save clears
  it, preventing an old-path snapshot from being stranded.
- The editor status bar reports queued, saved, and failed recovery states. A
  recovery snapshot never overwrites the opened ROM.
- At startup, the newest valid snapshot for each recovered ROM is offered in a
  restore/discard/keep review. Restore opens the snapshot as a new unsaved
  workspace, clears its source filename, and therefore requires Save As before
  it can be written anywhere.
- A recovery set is removed only after its restored ROM has loaded successfully
  or the user explicitly discards it. Failed restores leave the files intact.

The default location is `~/.RomRaider/romraider2-recovery/<rom-key>/`. The ROM
key is a truncated SHA-256 digest of the source identity; it is not user input
and cannot escape the recovery root.

## Next checkpoint

Add an in-application recovery browser for older retained versions and optional
user-named checkpoints. Restoring any historical version must retain the same
unsaved-workspace and explicit Save As guarantees as startup recovery.
