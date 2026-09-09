# Desktop logger profile integrity

This record covers XML profile serialization and persistence repairs. See the
[active plan](ACTIVE_WORK_PLAN.md) for current release status and the
[portable setup guide](PORTABLE_LOGGER_SETUP.md) for desktop exchange controls.

## Findings and repairs

The desktop serializer previously encoded profiles as ISO-8859-1. Units outside
that character set could become replacement characters. IDs, unit strings and
protocol were inserted into XML attributes without escaping, so text containing
`&`, `<` or quotes could produce a corrupt profile. The protocol was taken from
global settings at serialization time instead of the captured profile. Mutable
input maps/items could also change a supposedly captured profile afterward.

Profiles now serialize as UTF-8 using their own captured protocol. XML attribute
characters are escaped, attribute tabs/newlines/carriage returns are preserved
with numeric references, and text XML 1.0 cannot represent is rejected. Maps and
items are copied into immutable snapshots. Existing ISO-8859-1 profiles remain
readable; no source file is migrated or rewritten merely by loading it.

Switch conversion units are retained alongside parameter and external units.
The parser preserves encounter order within each section. The subsequent
[desktop setup-transfer integration](PORTABLE_LOGGER_SETUP.md) also preserves
cross-category selected order with an optional `rr2-order` attribute and restores
that order in the replacement desktop Logger's CSV registrations.

Missing protocol stays explicitly unspecified rather than being borrowed from
global settings. Non-profile roots and nested profiles reject rather than
masquerading as empty selections. The obsolete missing-DTD source-rewriting
fallback has been removed; external DTD retrieval was already disabled by the
shared parser.

## Save guarantees and limits

Both the legacy Save/Save As helper and the replacement desktop runtime's backup
path use the same writer. It serializes completely before opening any output,
writes and syncs a temporary file in the destination directory, then requires
atomic replacement. No non-atomic fallback truncates the previous profile.
Failed serialization or an unsupported atomic move preserves the original;
temporary output is cleaned up. Uppercase `.XML` filenames are recognized
without appending another suffix. Non-XML, directory and symbolic-link targets
are rejected. Parent directories must already exist; the runtime still creates
its package-owned profile directory before requesting a backup.

The local post-1.1.8 follow-up also moves the retained Swing logger's automatic
backup into the active settings directory's `profiles/profile_backup.xml`, using
the same storage helper as the modern desktop logger. Both create that parent
directory before saving. Swing recovery prefers the managed backup and falls back
to an existing legacy `~/.RomRaider/profile_backup.xml` only when no managed
backup exists. Reading does not migrate, overwrite or delete the old file.
Packaged Linux tests reproduced and then eliminated a shutdown backup failure
when a custom settings directory was used with a fresh home directory.

This is per-file replacement, not a transaction across profile and global
settings. It does not promise a directory-fsync durability guarantee, external
backup, recovery from filesystem failure, or atomic writes through Android
document providers. The repair does not connect an adapter, start logging, alter
ROMs or rewrite existing user profiles until the normal save/backup workflow runs.

## Qualification

Nine focused core tests cover Unicode/XML escaping and whitespace, the captured
protocol, immutable inputs, switch/external flags and units, legacy encoding,
invalid-text preservation, injected atomic-move failure, temporary-file cleanup,
source immutability with legacy DTD references, target safeguards and invalid
profile roots. Three compatibility tests feed actual desktop serialized bytes
to the Android/shared profile reader, verifying Unicode units, selection order
within sections, empty selections and explicit unsupported external entries.

Local qualification passed: the full Ant unit suite and Linux build, nine focused
profile tests, 155 JavaFX tests (including the three cross-reader cases), 35
Compose tests, shared-core checks and Linux JavaFX staging. Optional private
corpus tests remain conditional; these checks do not access a vehicle.

Those repairs were prerequisites for the subsequent
[desktop exchange controls](PORTABLE_LOGGER_SETUP.md), which add loaded-byte
identity, reviewed replacements, stale-session guards and selection-order
persistence. See that guide for current workflow and qualification limits.
