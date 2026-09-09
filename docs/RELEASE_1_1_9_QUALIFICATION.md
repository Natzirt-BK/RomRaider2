# 1.1.9 RC1 qualification

September 9, 2026. Automated release checks passed. Physical-device and vehicle
acceptance remains incomplete; this is not a stable-release sign-off.

## Source and hosted checks

All seven packages were built from clean source commit
`ed422f4faa64926bddf8fd306c533c993d4e6976`.

- [Windows/Linux tests and packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34414013773): passed.
- [Android, SteamOS and macOS packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34414013663): passed.
- [Android regression checks](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34414013777): passed.

Local checks also passed: desktop core tests, 308 desktop workspace tests with
two optional skips, all 48 Compose tests, shared portable checks and 137 Android
unit tests. Android lint has no errors and two newer-SDK notices.

Android regression covers setup restoration, version-increment upgrade, channel
selection, recording continuity, gauge layouts, CSV review, notification Stop,
background capture and process-death recovery. Exported synthetic CSVs pass the
production desktop parser. No emulator test sent vehicle commands.

## Downloaded package checks

- All seven checksums, ZIP integrity and archive-path checks passed.
  No private ROM or signing-key files were included.
- Both APKs have version 1.1.9 / 110413, the existing application IDs and the
  distribution signing certificate. Service isolation and notices were verified.
- The actual signed main APK upgraded over the published 1.1.8 APK in a fresh
  emulator without uninstalling; app-private test data survived and 1.1.9 launched.
- A synthetic retained recording was exported through Android's system document
  picker. The resulting CSV contained the expected headers, timestamps and values.
- Linux internal checksums, source stamp and bundled defaults passed verification.
  Unmodified Linux and SteamOS launchers opened and closed normally on private
  displays using bundled Java 21. Startup and window-title versions matched.
- Windows package source/version stamps matched. Hosted tests, J2534 helper
  builds and the Windows package verifier passed.
- Both macOS packages passed their configured native-host builds/tests and archive
  checks. Application metadata and compiled core version match 1.1.9.
  macOS packages remain unsigned.

## Package SHA-256

| Package | SHA-256 |
| --- | --- |
| Android main | `a493b25cc0bceb35eea9ed7d735d1a8efc5310a1dbb34104cb6399d1d3432b39` |
| Android OpenPort Test | `026a2ec9c864e7126e612c66e0bd339fde7a38fe2b9da2ef13fcb31e67a2e6d3` |
| Linux x64 | `00189a0f241c70c9999666ebe2e534bf1d578fa37c9bba58b228164cf4e0f1e2` |
| Windows x64 | `98bd3e78f7046d4e80609c155e9028a54812a6e1b539c3b7183992a634dd57d7` |
| SteamOS x64 | `a0a67de067f9e6858ba29dc668bcfabc3a0b336be76e38974a61ed7543fd6048` |
| macOS arm64 | `cdd3974b7eea0bd28b33efcf7ff920b9ab187e58d9f84cd97fbda903b7bbf597` |
| macOS x64 | `f376e52e3cc747267ae475afec6416cf1d8be11adc87b1ce20b1c3ccd20de00b` |

## Remaining acceptance

Physical-phone haptic feel and display scaling, vendor document-provider behavior,
adapter/background/reconnect sessions, Steam Deck and interactive Windows/macOS
acceptance remain open. Prior in-car logging evidence is not qualification of
every channel or adapter. Android Bluetooth/KKL support is not included.
Production flashing and live tuning remain unavailable.
