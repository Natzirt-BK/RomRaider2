# Release versioning

Desktop and Android share `major.minor.patch` versions:

- Patch updates: `1.1.1`, `1.1.2`, `1.1.3`.
- Milestones: `1.2.0`, followed by `1.2.x` updates.
- Rare major changes: `2.0.0`.

The current downloadable candidate is **RomRaider2 1.1.3 RC1**, with Android
versionCode **110407**. There is no stable release yet.

**RC1 describes the release stage.** Keep it separate from application versions,
package filenames and numeric Android versionName values. Release titles may use
`RomRaider2 X.Y.Z RC1`; GitHub marks them as prereleases. Routine development
updates change the numeric version, not an RC2/RC3/RC4 sequence. Do not use Preview
labels. Stable releases omit RC1 and are published only after qualification.

Keep only the current downloadable release, replacing it after the new packages
are verified. Preserve source tags and dated audit records as history. Never
rename an old binary to imply it contains newer source.

## Build safeguards

`version.properties` supplies the version to desktop and Android builds.
Android versionCode must increase for each published update. Preserve application
IDs and signing identity; historical `.preview` ID segments are installation
identifiers, not display labels.

`packaging/verify-version.sh` checks shared version components, build consumers,
release-note headings and artifact names. Development is now 1.1.4 / Android
110408, checked against the published baseline 1.1.3 / 110407. The published
1.1.3 tag retains its original checks and remains independently buildable.
`packaging/test-version-check.sh` tests rejection of inconsistent metadata.

Package checks inspect embedded versions, APK identity and signatures, not just
filenames. Signed builds must use the shared version; isolated automation builds
may override it for upgrade tests.

Before publishing, verify the source revision, platform checks, checksums and
Android signing certificate. Keep a recoverable signing-key backup outside Git.
Document unfinished hardware tests in the release notes. RC or stable labels do
not establish vehicle compatibility.
