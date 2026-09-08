# 1.1.5 RC1 qualification

September 8, 2026. Automated candidate checks passed. Physical-platform and
vehicle acceptance remain incomplete; this is not a stable-release sign-off.

## Provenance

All seven packages were built from the clean source commit
`863f14b520b77859df5b95882ce6263c9aa6f6b3`:

- [Windows/Linux build and tests](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34253587043): passed.
- [Android, SteamOS and both macOS packages](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34253586616): passed.
- [Android regression checks](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34253588739): passed.

Local uncommitted changes were not used to build distribution packages.
Each download has a matching SHA-256 sidecar. ZIP integrity and checksums were
rechecked after download; package paths were checked for traversal, signing
keys and vehicle-file content. Empty user-data directories are permitted.

| Package | SHA-256 |
| --- | --- |
| Android main | `61ba46047da8e7f74edef393f6a8a00a56d70c963c466bda4b40368b60a5cade` |
| Android OpenPort Test | `481107ecb052dd581a89a9549f45c2d48ed9c57a4089c994c506b0069d5f3886` |
| Linux x64 | `a1fc17d29a5ea674273a42f4a57167e09e3a8aa6e06a4da72284afe21c2bb0a8` |
| Windows x64 | `a2a79295afdd2cf71d41df2de949f1a9c83af0149b1144c017b1fc93d7a77270` |
| SteamOS x64 | `4b8c860b818a76e1b99614a7204d4ad1d3f638fd5d653606e7b7550dcfd55420` |
| macOS arm64 | `f4d60211e1da6e7e957df5905e35d809e13fd84cbf1c829eb14ebad2caab882d` |
| macOS x64 | `6b9f7131181887c944547b00ac0b00b952a1ed7562889dc0c362f25301c1576c` |

## Package checks

| Platform | Automated evidence | Not established |
| --- | --- | --- |
| Android, both APKs | Downloaded distribution certificate, application IDs, 1.1.5/110409 metadata, non-exported recording service and notices verified. Hosted unit/lint and isolated-emulator restart, upgrade, recording-preservation and CSV compatibility checks passed. | Physical USB/background/reconnect acceptance of these exact APKs. Emulator checks use isolated automation builds, not a real ECU. |
| Linux x64 | Packaged verifier, internal manifest and source stamp passed. Unmodified native launcher opened the editor/first-run definition dialog and closed normally from an unrelated directory, using its bundled Java 21 and package-owned settings. No startup/shutdown exceptions. | Real-definition file chooser/checksum acceptance, hardware and physical mixed DPI. |
| Windows x64 | Hosted unit/platform tests, native display-awake probe, architecture-specific J2534 helper builds and package verification passed. | Interactive Windows editor/logger acceptance and vendor-driver hardware tests. |
| SteamOS x64 | Hosted package checks passed. Unmodified wrapper launched and closed normally at 1280×800 under isolated Xvfb/Openbox, using bundled Java 21. Editor and first-run dialog fit the viewport. | Physical Steam Deck, Steam Input, Game Mode and actual display-idle acceptance. |
| macOS arm64 and x64 | Native-host tests, display-awake probes, Java 21 packaging, matching renderer checks and archive integrity passed independently. Packages are unsigned. | Interactive Mac, Gatekeeper and adapter acceptance. |

## Linux synthetic package probe

A separate copy of the verified Linux image ran `PackagedDesktopRepair` to its
`PACKAGED_DESKTOP_REPAIR_PASS` sentinel. Only that copy gained a diagnostic JAR
and a changed launcher main class/classpath; the archive stayed unchanged.
The probe used the bundled runtime and application JARs, disposable settings and
synthetic bytes, without connecting a logger or writing ECU memory.

Checks covered guarded close/save baselines, edit/undo/reopen, interpolation and
axis edits, offline live-tune byte preview, compact inspector/Dyno scrolling,
settings cancellation, asynchronous numeric sorting/source-sample identity,
range-linked statistics, touch/full-screen presentation and definition-dialog
placement. This modified-entry-point probe is separate from the unmodified
production launch/close check above.

The extra package checks caught two stale probe assumptions and a real runtime
shutdown defect before publication. The updated regression reproduces the old
shutdown failure and checks early close, closing with a modal dialog open and
interrupted lifecycle ownership. Native test-cache inputs now include the
window-smoke and logger-stress flags.

No hardware commands were sent. Production flashing and live tuning remain
unavailable. Broader adapter support and built-in TCU editing are follow-on work,
not features of this candidate.
