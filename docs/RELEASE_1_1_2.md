# RomRaider2 1.1.2

## Android update notice

**Back up recordings, definitions/profiles and unsaved ROM work before changing
your installation.** This release uses the permanent signing certificate, which
differs from the old 1.1.1 public certificate. Android will not accept a normal
in-place update over that old installation. Do not clear storage or uninstall
until independent exports have been checked.

The separate **RomRaider2 OpenPort Test** APK can coexist with the standard app
only if its own application ID is not already installed under the old key. It
does not copy the standard app's private data. See the
[migration checklist](https://github.com/Natzirt-BK/RomRaider2/blob/master/docs/ANDROID_1_1_2_MIGRATION.md).

## What's new

- Eleven added native gauge faces, including red-lit **STI Night** and
  **Evolution Night**, with premium dial shading and bounded needle motion.
  Existing styles remain available. Pointer animation never changes logged or
  displayed numeric readings.
- Gauges-only views on Android, JavaFX (including SteamOS) and Compose, with explicit
  simulated/live/stale/stopped states. Switching views preserves the logger
  session; Android still stops logging when the app leaves the foreground.
- A more compact JavaFX editor: centered/narrower cells, **Save Options**, less
  header space and collapsed calibration categories when a ROM opens.
- Selection-wide multiplication, custom fine/coarse steps, protected saved-ROM
  reload and copyable read-only ROM properties in JavaFX.
- Initial read-only MAF/injector saved-log analysis in JavaFX. Fitting, reviewed
  transfer into ROM tables and full legacy analysis parity remain follow-ups.
- Repairs for Android save completion, unavailable readings, gauge warning
  conversions, XML imports, shared calibration-cell refresh and checksum-warning
  presentation. Definition-size parsing now rejects overflow.
- Bundled Android license and brand notices, available through About / licenses.
  Mac packaging also verifies the application GPL and STI notices.

## Packages and verification scope

Desktop ZIPs include Java 21. Packages are provided for Windows x64, Linux x64,
SteamOS Desktop Mode x64, macOS Intel and macOS Apple silicon. Mac packages are
unsigned. Android 8.0+ has standard and separate-test APKs, versionCode 110406.
Every binary archive/APK has a SHA-256 sidecar.

Application source checkpoint: `d51399932e12fd917fe3980238c7590c75ec4529`.
Linux/Windows packages use `fe18d034` from
[desktop qualification](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34023980227);
Android/SteamOS use the same checkpoint from
[platform qualification](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34023979663).
Mac packages are rebuilt at `38a155f9` in
[Mac notice qualification](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34024327409)
for the packaging-only GPL notice repair.
Application code is identical across these checkpoints. The release tag includes
that Mac packaging correction; Linux/Windows `VERSION.txt` retains the actual
package source revision rather than being relabeled.

All seven downloaded packages pass SHA-256/ZIP CRC and archive-path checks.
GPL/STI notice content and clean default settings were checked (Windows notice
line endings normalized for comparison). Shared-cell/checksum class bytes agree
across the five desktop packages. Both APK identities, versions and permanent
signatures pass independent package-tool checks. The Linux package passes its
internal verifier and an isolated bundled-runtime synthetic desktop probe.

See the [gauge/editor and companion audit](https://github.com/Natzirt-BK/RomRaider2/blob/master/docs/GAUGE_EDITOR_AUDIT_2026-09-06.md)
for findings, repairs and the distinction between fresh automated checks and
retained application evidence.

## Important limits

- Basic Forester SSM logging has previous in-car confirmation; this release's
  software/emulator tests do not qualify sustained physical sessions, Android
  providers, EVO MUT-II, Windows adapters, Macs or Steam Deck hardware.
- Android does not yet support desktop calculated Logger channels, dynamic
  DimeMod channels or external serial AEM input. Unavailable profile entries are
  reported rather than replaced by guessed addresses or readings.
- Android does not repair ROM checksums. Neither mobile nor desktop RomRaider2
  enables production flashing/live tuning in this release. Definition matching
  and a successful file save are not authorization to flash.
- No private ROMs, vehicle logs, definitions or signing keys are included.
- STI branding is unofficial and does not imply manufacturer affiliation or
  endorsement; see the bundled brand notice.

Configure dashboards while parked. They do not replace the vehicle's instruments
or warning systems.
