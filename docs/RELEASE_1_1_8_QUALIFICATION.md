# 1.1.8 RC1 qualification

September 8, 2026. Automated release checks passed. Physical-device and vehicle
qualification remains incomplete; this is not a stable-release sign-off.

## Build and regression checks

All seven packages were built from clean source commit
`81c62ce674c7ace57488d7e778df37a9adbb3999`.

- [Windows/Linux tests and packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34311662038): passed.
- [Android, SteamOS and both macOS packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34311710600): passed.
- [Android regression checks](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34311661996): passed.

The Android checks cover restart and version-increment upgrade, profile
persistence, ECU-specific channel selection, portrait/landscape Logger layout,
gauge setup/fullscreen continuity, CSV review, background recording, notification
Stop and process-death recovery. Synthetic CSV exports also pass the production
desktop parser. Emulator transport is synthetic; no vehicle commands were sent.

Local Android unit tests (122), lint, shared-core checks and the complete
recording-lifecycle script also passed. The local lifecycle run reinstalled the
same automation APK; upgrade-version coverage comes from the hosted run.

## Downloaded package checks

- All package checksums and ZIP integrity checks passed. Archive paths were
  checked for traversal and private signing/vehicle files.
- Both APKs retain the distribution certificate, application IDs and version
  1.1.8/110412. Recording-service isolation and bundled notices were verified.
- The Linux package verifier, internal checksums and source stamp passed.
  Unmodified Linux and SteamOS launchers opened the editor/definition dialog and
  closed normally under isolated Xvfb/Openbox, using bundled Java 21 and packaged
  settings. Automatic connection was disabled. No startup/shutdown errors were found.
- Windows source/version stamps matched. Hosted native tests, J2534 helper
  builds and package verification passed.
- Both macOS packages passed native-host tests and archive checks, with matching
  1.1.8 application metadata. They are unsigned.

| Package | SHA-256 |
| --- | --- |
| Android main | `eab3eeab6709d7822706ea2ad62d0aac7022161170f2b8ea38e50c1f55e31965` |
| Android OpenPort Test | `41b2a2d9dcdd9428f05db92be321a89785e5f43b22601ec433678b2b1734639d` |
| Linux x64 | `ec3f7b1968f1abcd28d3c39bee31d691b4c65f2cea586ab1a38702ab85fea86c` |
| Windows x64 | `303b78a652b0f88626d393810725482bbef5e2002cfe508276b6856757d4daac` |
| SteamOS x64 | `d7af31242f2c6b06773711e36a6640f83a687e6b4b6b8f0b11031473a5f279b5` |
| macOS arm64 | `c1c3a69a5dfea0e6fd6ad2c46618b8c6d024c0cded2c9bcc76533e7b801518d8` |
| macOS x64 | `9b00152ec354c909717e827f47e89e9597ec7b49e08be807106be4e4ac58fa1c` |

## Remaining qualification

Physical phone USB/background/reconnect sessions, channel accuracy, desktop
adapter hardware, Steam Deck, interactive Windows/macOS and mixed-DPI acceptance
remain open. The desktop ELM/OBDLink window is a limited standard-PID test, not
qualified enhanced logging. Production flashing and live tuning are unavailable.
