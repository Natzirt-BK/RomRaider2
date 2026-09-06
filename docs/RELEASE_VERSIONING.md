# Release versioning

Desktop and mobile use the same `major.minor.patch` numbering policy.

- Routine fixes and smaller updates increment the last number: `1.1.1`, `1.1.2`.
- Significant milestones increment the middle number: `1.2.0`, followed by `1.2.x` fixes.
- Substantial changes may rarely increment the first number: `2.0.0` (the 2.0 milestone).
- Do not add Preview, RC, or other development-stage suffixes to version numbers,
  application display names, release titles, or downloadable package names.
- Platform/architecture and packaging distinctions such as `Android-debug` and
  `side-by-side-test` describe artifacts, not separate version sequences.
- Publish matching platform artifacts together under `romraider2-X.Y.Z`, retaining
  only the latest public release after replacement downloads are verified.
- Keep historical source tags and dated audit documents as historical evidence.

Android's internal integer versionCode must increase with each published update.
Keep existing application IDs and signing identities where possible; changing
display version syntax must not silently relocate or discard saved recordings.
The legacy `.preview` package-ID segments are retained solely for installation
identity, not as user-facing version labels.

Signing, hardware qualification, and unfinished functionality must still be
documented explicitly. Numbering does not imply safety certification or completed
in-car testing. Third-party dependency versions are independent of app versions.

## Development safeguards

Current development source is **1.1.2**, Android versionCode **110406**. The public
release is still 1.1.1; preparing source/build metadata does not publish it.

`version.properties` supplies the desktop version components, build number and
Android versionCode. All three portable/desktop Gradle modules and the separate
Android Gradle build consume that file. Unsigned automation builds may override
their version for upgrade tests; signed distribution builds must use the shared
values. The generated desktop What's New heading follows the application version.

Run `bash packaging/verify-version.sh` to check the shared components, Gradle
consumers, release-notes heading and explicit artifact labels listed in
`packaging/versioned-paths.txt`. The check enforces numeric naming and a version
and Android code above the known public 1.1.1 baseline (110405). Update that
baseline deliberately with future publication work; this is not a live GitHub
release lookup. Negative fixtures in `packaging/test-version-check.sh` verify
rejection of unchanged codes, stage suffixes and stale verifier/workflow names.
Desktop, Android and manual platform-package CI run these checks.

Application-image builders inspect the actual core jar's embedded version before
packaging. Android distribution CI verifies the built APK's application ID,
versionName and versionCode against the shared metadata, separately from its
cryptographic signing check. A correctly named stale artifact is not sufficient.

Local verification passed the version rejection fixtures, core/portable tests,
60 JavaFX and 34 Compose tests, and Android unit/build/lint checks. Both rebuilt
APKs reported 1.1.2/110406; the guard rejected a retained old 1.1.1 APK and a
swapped application ID. A synthetic signed configuration with an old versionCode
was rejected before any key access. Android CI repeats that negative check.

Before publication, finish the exact-commit platform qualification, verify all
download checksums and signing identity, document the one-time old-key Android
migration, and confirm an offline backup of the permanent signing key/password.
Do not overwrite public 1.1.1 with these development artifacts. No automatic
uninstall, user-data clearing, key regeneration or GitHub release deletion is
part of these safeguards.
