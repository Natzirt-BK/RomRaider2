# 1.1.10 RC1 qualification

September 12, 2026. Automated release checks passed; physical-device and in-car
acceptance remains incomplete. This is a release candidate, not stable-release sign-off.

## Hosted builds and downloaded packages

All seven packages use source `ed3a414dbc22893880daf9472242b1c605a64d33`.

- [Windows/Linux tests and packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34743436337): passed.
- [Android, SteamOS and macOS packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34743436216): passed.
- [Android regression checks](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34743092392): passed.
  Android production code, test harness and version metadata are identical to
  the packaged source; subsequent changes affect only desktop layout/build diagnostics and README.
- All seven downloaded archives passed SHA-256, archive-integrity and
  archive-path/private-file checks. Windows/Linux source stamps and macOS bundle
  versions match the candidate; macOS packages remain unsigned.
- Both APKs passed distribution-signature, application-ID, 1.1.10 / 110415 version,
  service-manifest and bundled-notice checks. The final APKs are byte-for-byte
  identical to those used for the local signed-upgrade check.
- The main signed APK upgraded over published 1.1.9 / 110414 in an isolated
  emulator without uninstalling. Private test data and saved protocol/gauge style
  survived; the app cold-launched as 1.1.10 and exposed Gauges START/STOP controls.
- Downloaded Linux and SteamOS native launchers opened and closed normally on
  private displays with matching startup/window versions and bundled Java 21.
  Linux internal checksums and packaged defaults passed verification.
- The companion Linux installer is pinned to the verified archive checksum.
  Its full regression suite and an isolated installation of the actual archive passed.
- Windows initially exposed a connection-panel height issue. Tighter spacing
  fixed it; the final native-host tests, helper builds and package verifier passed.

## Package SHA-256

| Package | SHA-256 |
| --- | --- |
| Android main | `70d70fa1e8882912d10e2280e9b3938f1656c9969cfbafbcc75a6023ba708eca` |
| Android OpenPort Test | `aca44850ba567c961c8f96339fd1dcf0dfc5f70f9e5a4989ec764d85f360caed` |
| Linux x64 | `253167764d1fa44ab85c8abb740444fdb3fb44ea265f70ae7e9778a26bff98b4` |
| Windows x64 | `2533b9be0775102d0a67842b166c9136c1d7555dd1c20934c5bd8baa870b0da6` |
| SteamOS x64 | `ef7ebc68ea12022777c3fa96c1334dcbe462be50d4f706bd580ca795b422a58a` |
| macOS arm64 | `bc049bddedba746da61d6565d7f072cdf07d30c56d28709134fe17013500c6ec` |
| macOS x64 | `5941d9df4df8b0909be830e5d30425e52502574ec8bfa3b876e8021689ba1093` |

## Completed development checks

- Core: 759 tests, 756 passed, three optional skips; Linux and Windows core builds.
- Desktop: 337 JavaFX tests, 336 passed, one optional skip; 48 Compose tests passed.
- Android: 137 JVM tests, lint and isolated automation builds passed.
- Shared checks include CSV rounding/source precision and 132 gauge layouts.
- Isolated emulator checks passed Android CSV export/recovery, gauge recording
  controls, compact mounted rendering and recording continuity across views.
- Native-window checks passed fullscreen/maximized setup, recording-switch
  validation/rollback and log-name validation/rollback. Screenshots were reviewed.

## Remaining acceptance

Physical phone button feel and scaling, vendor document providers, Steam Deck,
interactive Windows/macOS behavior and the consolidated Forester adapter,
DimeMod reconnect, vehicle-switch and recording tests remain outstanding.
No emulator or synthetic test qualifies an adapter or vehicle channel.
