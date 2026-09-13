# 1.1.11 RC1 qualification

Packaged source: `8825fdd147206cf11b9b4d2e6a2d72bdd4d893c8`.
Version: **1.1.11 RC1**, Android build code **110416**.
This record does not claim physical-device or vehicle qualification.

## Completed checks

- Core regressions, the full desktop native-window suite and Compose tests.
- Shared-core checks, complete Android unit tests, lint and both APK builds.
- Hosted [Linux and Windows builds](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34746125677).
- Hosted [Android, SteamOS and macOS builds](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34746129079).
- All seven downloaded archives: SHA-256, archive integrity and private-file/path checks.
- Android distribution certificate, package versions, service manifests and notices.
- Actual signed 1.1.10 → 1.1.11 emulator upgrade retained a private test marker,
  saved SSM protocol and gauge theme; the updated app launched successfully.
- Linux package verification and visible Linux/SteamOS application windows on
  isolated displays. The original startup port remained occupied during launch.
- Linux installer regression suite and isolated installation of the actual archive.

## Publication checks

The [Android lifecycle run](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34746686143)
passed from `63eb8806`, which differs from the packaged source only in synthetic
instrumentation support flags. Production code is unchanged. The complete local
emulator lifecycle suite also passed. All 14 uploaded asset digests match the
qualified local packages. The public release tag resolves to the packaged source.
A fresh Linux installation using the installer's default public download and
pinned hash passed; the downloaded archive matches the qualified package byte
for byte. Installer source: `34b15f8` in `subaru-ecu-tools-linux`.
The installer's [hosted validation](https://github.com/Natzirt-BK/subaru-ecu-tools-linux/actions/runs/34747334304)
passed. Only 1.1.11 remains in GitHub Releases; the previous release assets and
metadata were retained locally before removal, and its source tag remains.

## Package SHA-256

| Package | SHA-256 |
| --- | --- |
| Android | `0ac0b11b607afd11322c7d7302b5bb71768c5bcad0a38492fa1015916d2b84f9` |
| Android OpenPort Test | `46ae9cafeee362bd91b3eff4ff4c696983f77da8915b48a144cfb16290dafe49` |
| Linux x64 | `1e06b6130e36b15bc115ed2080ea89562d08dbeed10956c1a67a549765810f25` |
| Windows x64 | `22fb5a12b0c2118541fd13828f2710784f95693754a82c4b972947dc47a2d454` |
| SteamOS x64 | `89990b390795628a8a2190e117a774d1d6c7802eaf0725a92430ad11bf9529a7` |
| macOS arm64 | `2df040bf35fa4192bfafc28d001f8de6ce6b2b4d2e41a0aa88851f513541f812` |
| macOS x64 | `5d2f09d8248d38a42719ddc2eb7b8ca6a332ca27f1c7d7596e0b2ba04a49b9e5` |

## Deferred acceptance

One consolidated Forester session covers confirmed channel filtering, DimeMod,
profile selection, reconnects, recording and gauges. Physical-phone, Steam Deck,
Windows/macOS interaction and additional adapter tests remain outstanding.
