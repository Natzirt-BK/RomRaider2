# Invalid logger readings

During the audit follow-up, both logger engines were found to replace non-finite
conversion results with `0.0`. That can make an invalid expression or raw float
look like a valid measurement, including to gauge warnings.

## Portable engine and Android repair

The portable converter now retains invalid results as `NaN` (missing data), and
rejects non-finite raw float samples even if a constant expression could mask
them. Valid zero readings and normal numeric conversion/formatting are unchanged.
The existing RomRaider-style CSV writer emits an empty cell for invalid samples.

Android shows an em dash / `NO VALID DATA`, hides the current needle and active
segments, and updates peaks only from finite samples. A later valid reading
recovers normally; resetting peaks during a gap waits for the next valid sample.
Unknown-channel scale calculation also handles missing/overflowing bounds.

Automated evidence: 18 portable assertions cover invalid arithmetic, raw floats
in both byte orders, constants masking invalid raw floats, valid/gap/recovery/zero
CSV output and import. Four Android model tests cover peaks, reset/recovery,
display wording and finite drawing coordinates. Shared-core checks, all 44
Android unit tests, standard/diagnostic APK assembly and lint passed locally.
No vehicle or physical phone was accessed; these are synthetic checks.

The desktop engine's zero fallback still requires its own consumer audit and
repair. This portable change does not claim to fix that separate path, detect
every semantically wrong definition, or validate vehicle-specific conversions.
