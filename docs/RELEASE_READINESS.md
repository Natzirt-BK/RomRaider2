# Release readiness

RomRaider2 is preparing its first stable release. Candidates use a numeric
application version and the RC1 release stage. A rebuilt package needs its own
verification; earlier results do not automatically apply.

## Automated checks

- Run core, shared and platform tests, lint and version checks.
- Verify locked dependencies and matching platform-native libraries.
- Build desktop packages with Java 21 and package-owned settings.
- Check licenses, clean defaults, source revision and SHA-256 manifests.
- Verify Android application IDs, version metadata and signing certificate.
- Exclude private ROMs, definitions, profiles, logs and signing material.

## Desktop acceptance

- Launch and close a clean extracted package without a separate Java install.
- Check Editor load, edit, undo, save and reopen with synthetic test files.
- Check calibration selection, copy/paste, scaling and rejected input.
- Check Logger data, graphs, gauges and saved-log analysis.
- Verify light/dark themes, narrow screens, high DPI and window placement.
- Verify mounted gauges and returning to the normal workspace.
- Record Windows, Linux, macOS and SteamOS checks separately.

Use the [qualification record](RELEASE_QUALIFICATION_RECORD.md) for exact package
checksums, source revisions and results. Do not label an untested platform stable.

## Hardware acceptance

Track vehicle, protocol, adapter and operating system separately. Basic Forester
SSM logging has prior in-car evidence; sustained Android sessions, background
recording, reconnect/removal, external sensors and EVO MUT-II need further tests.
Automated transport tests do not replace parked hardware checks.

Production flashing and live tuning remain unavailable.

## Publication

Publish verified matching platform packages with checksums and concise known
limits. Keep one current release candidate on GitHub; retire older downloads
after the replacement is verified. Preserve source history.

Update the companion Linux installer's pinned URL and checksum only after the
matching asset is public, then rerun its installer tests. Never rename old
binaries to imply they contain newer source.
