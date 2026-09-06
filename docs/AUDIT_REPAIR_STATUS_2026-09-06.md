# Automated audit repair status — September 6, 2026 UTC

The repair candidate is on GitHub `master` at
[`c0ece4eb5f1750848ae2f78f909504cfc9acb902`](https://github.com/Natzirt-BK/RomRaider2/commit/c0ece4eb5f1750848ae2f78f909504cfc9acb902).
Its application version is **1.1.2**, Android versionCode **110406**. The later
status-documentation commit does not change application code or packaging.
Public downloads remain **1.1.1** with the original seven packages and seven
checksum sidecars. No releases, tags, installer pins, installed apps, user data
or signing keys were replaced during this repair pass.

## Repairs integrated

| Finding | Source outcome |
| --- | --- |
| A1: installer migration data loss | Companion installer preserves user configuration/custom data and retains predecessors/backups; application download pin unchanged. |
| A2: Android save completion | Failed open/write/flush/close preserves dirty state and recovery; success is published only after close. |
| A3/A4: gauge warnings and conversions | Shared retained hysteresis, explicit unavailable state, conversion-bound limits, schema-2 persistence, JavaFX limits editor and detached-gauge consistency. |
| A5: XML boundary | Strict encoding-aware entity rejection with valid non-entity DTD compatibility; no external entity/DTD loading. |
| A6/A7: source version and wording | Shared numeric 1.1.2 metadata, increasing Android code, stale/mismatched package rejection, signed-version override rejection, current Android wording. Publication/migration gates remain below. |
| A8: advisory evidence | Reproducible scoped OSV check and CI reports: 228 verified Maven pairs, no advisories returned at scan time, seven bundled JARs explicitly unmapped. Not a full security verdict. |
| Subsequent invalid-reading findings | Both logger engines retain gaps instead of fabricating zero; CSV blanks, finite-only peaks/statistics, unavailable gauges, graph gaps, analysis gates and safe legacy table overlays. |
| Subsequent dataflow/cache findings | Invalid simulation results replace stale downstream values; invalid table inputs never reach lookups. Expression cache includes variable bindings and cannot mix scalar/map contexts or reuse removed variables. |
| Android test lifecycle | Instrumentation follows recreated Activities and tests replacement-worker behavior, instead of holding a destroyed Activity's executor. |

Detailed evidence: [data preservation](DATA_PRESERVATION_FIXES.md),
[XML hardening](XML_IMPORT_HARDENING.md), [gauge repairs](GAUGE_WARNING_REPAIRS.md),
[version safeguards](RELEASE_VERSIONING.md), [advisory scope](DEPENDENCY_ADVISORY_SCOPE.md),
and [invalid readings, dataflow/cache and overlays](INVALID_LOGGER_READINGS.md).
The [original audit](PROJECT_AUDIT_2026-09-06.md) retains its historical findings;
this status report does not rewrite what was broken at that earlier snapshot.

## Final automated qualification

| Check | Result and scope |
| --- | --- |
| Linux and Windows | [PASS at c0ece4eb](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34014470476): core/portable/Compose/JavaFX checks, application builds, Windows helper architectures and package verification. |
| Android, macOS ARM64/x64, SteamOS | [PASS at c0ece4eb](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34014675375): signed Android and native-host desktop package jobs, shared version guard and package checks. Macs remain unsigned. |
| Android lifecycle and CSV | [PASS at 41138aad](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34014259076): explicit Activity recreation, actual gauge drawing/accessibility with invalid/recovered readings, restart, same-key upgrade, recording preservation, clear/corrupt setup and desktop parser compatibility. Subsequent candidate changes are desktop-only; [master regression at 5c269fb5 also passes](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34014592130). |
| Downloaded Android artifacts | Both final-run APKs independently rehashed against sidecars, cryptographically verified against the pinned permanent certificate, and checked for the correct standard/separate-test application IDs and 1.1.2 / 110406. No installation performed. |
| Local regression | Full core suite and Linux build pass; three existing opt-in native/corpus skips remain explicit. 60 display-enabled JavaFX tests, 35 Compose tests and 44 Android unit tests pass. Portable checks include 142 OpenPort, 69 MUT-II, 32 CSV, 190 XML and 18 invalid-reading assertions. |
| Advisory and packaging guards | Six scanner tests, version rejection fixtures and eight audited dependency hash checks pass. [Hosted expanded advisory inventory](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34013371953) passes within its documented limits. |

The earlier Android lifecycle failure at run `34013543594` is retained as failure
evidence, not counted as a pass. A redundant feature-branch package dispatch was
canceled because distribution signing is intentionally restricted to `master`;
the final master run above is the qualification evidence.

## Remaining gates and limits

- Subsequent maintainer work created and restore-tested an encrypted local
  signing backup. The owner accepted same-disk storage; no off-device copy is
  confirmed, so disk failure remains a risk. No key material was exposed or
  regenerated. See the [migration/backup checklist](ANDROID_1_1_2_MIGRATION.md).
- The permanent Android certificate differs from public 1.1.1. These APKs cannot
  perform a normal in-place upgrade over that old installation. Export/recovery
  and one-time manual migration must preserve recordings and unsaved work;
  uninstalling is not a lossless update. Same-key emulator tests do not validate
  migration from the old public certificate.
- Physical phone/provider, intended Forester profile and sustained logging,
  exact EVO/MUT-II/USB-C/OpenPort, Windows adapter, physical Mac and Steam Deck
  acceptance remain unperformed. No vehicle was connected or polled in this pass.
- Advisory gaps remain for seven unmapped JARs, native components, JDK/OS and
  unlocked build transitives; this is not a complete vulnerability/license audit.
- Android does not repair ROM checksums. Production ECU writing, flashing and
  live tuning remain unqualified; no new vehicle-write backend was introduced.

The safe automated repairs and their build qualification are complete for this
candidate. Release publication and hardware acceptance are separate work, not
silently authorized or completed by green CI.
