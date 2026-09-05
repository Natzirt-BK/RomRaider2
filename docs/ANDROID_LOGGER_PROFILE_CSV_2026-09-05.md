# Android logger profile and CSV follow-up

Included in the 1.1.1 release, following the earlier in-car test.
No new vehicle requests were made for this work.

## Channel selection

Previously, importing a logger definition replaced the profile with every
defined channel. Definition and profile imports also shared one generation
counter, allowing a profile import to invalidate a pending definition import.
These mechanisms explain how an intended small profile could be replaced or
fail to load correctly; the exact phone import sequence was not captured.

- A definition is now only a catalog. Without a profile, no channels are selected.
- Reloading a definition retains the current profile, units, and unsupported entries.
- Independent import guards allow either import order and reject stale results.
- Channel editing and new logger starts wait for both imports to finish.
- Changing protocol invalidates both imports and clears the previous setup.
- A failed profile import does not silently fall back to selecting all channels.

## CSV exports

Live, simulated-preview, and retained-recording exports now use the desktop
RomRaider wide-column layout:

```csv
Time (msec),Engine Speed (rpm),Battery Voltage (V)
0,750,13.24
100,800,13.25
```

Time starts at zero; samples from each timestamp occupy one row. Repeated
channels within the same millisecond start a new row instead of overwriting
data. Missing or non-finite values become empty cells, not invented readings.
Numbers use locale-independent decimal notation and retain the stored numeric
precision; desktop display-format padding/rounding is not reconstructed because
older recovery recordings do not store conversion formats.

Private recovery files keep their existing long-form representation. Export
reads the entire spool in two streaming passes, independently of the recent
sample ring or the bounded CSV import reader. It neither migrates nor deletes
the recovery copy. Malformed records, backward timestamps, changed channel
metadata, and multiline column labels fail explicitly. The desktop parser is
line-oriented and cannot safely consume multiline headers.

## Offline verification

- Shared portable checks passed, including 32 new CSV assertions, 142 OpenPort
  control assertions, and 69 MUT-II assertions.
- Android JVM tests passed, including six new import-state tests and the
  existing 14 read-only session tests.
- Standard debug and side-by-side diagnostic APK builds and both lint checks
  passed. Existing SDK/deprecation warnings remain.
- The production desktop `RomRaiderCsvLogParser` opened synthetic output from a
  disk-backed session and verified cycle count, values, units, and relative time.
- A 250,001-value recording exported completely with only one recent sample
  retained in memory. Destination failure and malformed-spool tests verified
  that source recordings remained unchanged.

Phone installation/UI interaction, a phone-generated CSV, sustained in-car
logging, and larger channel sets have not been verified for these changes.
