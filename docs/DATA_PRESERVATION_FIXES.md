# Data-preservation repairs after the project audit

This follow-up addresses findings A1 and A2 in the
[September 6 audit](PROJECT_AUDIT_2026-09-06.md). Source fixes do not replace the
public Android 1.1.1 APK. The installer continues to use the same checksum-pinned
desktop 1.1.1 package; its migration script is a separate repository update.

## A1: installer migration

The companion `subaru-ecu-tools-linux` installer now copies the whole user
configuration tree and the recognized top-level data directories. It retains
settings verbatim, preserves unknown custom files in the complete predecessor,
and never copies old executable/runtime/driver directories over new ones.

Existing current installations are authoritative. Otherwise the first available
legacy directory supplies user data. Multiple predecessors are not silently
merged. Legacy directories remain untouched rather than being deleted; this
also preserves absolute references into them. Current installations receive a
complete sibling backup. Failed activation attempts automatic restoration;
failed restoration reports where the complete backup remains.

Symlinked migration roots/settings fail safely for manual handling. Nested data
links are copied as links, not dereferenced. Packaged migration destinations may
not redirect copies through symlinks. Files lost in an older upgrade cannot be
recreated by this change without a surviving backup.

Regression cases include managed definitions/profiles/recovery/custom settings,
top-level definitions/profiles, preserved unknown files, unchanged settings and
application binaries, repeated install, current-over-legacy precedence, injected
copy failure, injected activation failure/rollback, and symlink-root refusal.
All 11 installer shell test scripts, including 14 music lifecycle cases, pass
locally. No real installed application or user-data directory was used.

## A2: Android ROM save completion

`MainActivity` routes ROM output through `MobileRomSave`, which owns an immutable
snapshot and only marks it saved after opening, writing, flushing and closing the
destination all succeed. A null destination or any I/O failure leaves the saved
baseline, dirty state and recoverable edits intact. Edits made after capture
remain dirty. A destroyed Activity does not queue a success UI/recovery callback.

Eight new unit tests cover success ordering, null/open/write/flush/close failure,
edits during close and snapshot ownership. They use the production recovery store
to verify that failed saves retain its bytes and can be restored. Android now has
40 unit tests (the existing 32 plus these eight), all passing locally, along with
the shared-core checks, standard APK build and lint.

The isolated emulator instrumentation also exercises close-failure preservation
and successful-save recovery cleanup using the real Android filesystem and the
same production helper, before its existing logger restart/upgrade/CSV checks.
This is not a physical phone/document-provider or vehicle qualification claim.

Android still does not repair ROM checksums. Saved copies remain for review and
desktop validation, not flashing. The audit's XML encoding, gauge warning/unit,
release/signing migration and hardware findings are separate follow-up work.
