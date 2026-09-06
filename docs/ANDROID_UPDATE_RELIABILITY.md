# Android update reliability and saved logger setup

Signing and saved-setup protections for version 1.1.2 and later. See the
[release candidate](https://github.com/Natzirt-BK/RomRaider2/releases/tag/romraider2-1.1.2)
for downloads.

## Persistent signing

Distribution builds use one dedicated RSA-3072 signing identity. The public
certificate fingerprint is in `packaging/android/signing-certificate.sha256`.
Private key material and passwords must never be committed or uploaded as build
artifacts. Git ignores common keystore filenames as an additional safeguard.

The manual platform-package workflow signs Android only from `master`, restores
the key from GitHub encrypted secrets, checks the resulting certificate against
the pinned fingerprint, and removes its temporary key file. Required secrets:

- `RR2_ANDROID_KEYSTORE_BASE64`: base64 encoding of the password-protected PKCS12 keystore.
- `RR2_ANDROID_KEYSTORE_PASSWORD`: the password without a trailing newline.

The fixed alias is `romraider2`; this PKCS12 key uses the same key/store password.
Base64 is transport encoding, **not encryption**. GitHub's secret storage protects
the encoded value. Do not echo any secret or enable shell tracing when using it.

For a local distribution build, set `RR2_ANDROID_SIGNING_REQUIRED=true`,
`RR2_ANDROID_KEYSTORE`, `RR2_ANDROID_KEYSTORE_PASSWORD`, `RR2_ANDROID_KEY_ALIAS`,
and `RR2_ANDROID_KEY_PASSWORD`. Run Gradle with `--no-configuration-cache` so
credentials are not serialized into the configuration cache. Missing or partial
credentials fail explicitly; distribution must never fall back to a random debug
key. Ordinary local developer builds without these variables still use a debug
key and are not distribution artifacts.

`packaging/android/create-signing-key.sh` generates a new private identity only
into a previously nonexistent absolute directory. **Do not run it for each
release.** Preserve the existing identity and keep a secure offline backup of
the keystore and password. Losing it prevents signing compatible future updates.

Compatible updates require the same application ID and signing identity, plus an
increasing versionCode. Standard and separate-test packages have distinct IDs.
Android packages remain debuggable. Back up recordings and saved work before
uninstalling or clearing app data; never do either automatically to fix an update.

References: [Android app signing](https://developer.android.com/studio/publish/app-signing)
and [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

## Saved setup

The app saves a private, bounded snapshot containing protocol, a copy of the
definition, its display name, the selected profile name, ordered channel IDs,
selected units, and unsupported external entries. It does not depend on continued
access to the originally chosen XML/logcfg files. A new definition file must be
imported explicitly if its contents change elsewhere.

Selection edits, including **Clear all**, persist. Switching protocol clears the
previous setup and saves the new protocol; it never reuses SSM channels for MUT-II
or vice versa. Definition imports are capped at 32 MiB. Saving uses a synced
temporary file and atomic replacement; a failed replacement retains the previous
snapshot. Corrupt/unsupported snapshots fail closed with an import-again message.
Import failures clear the corresponding active setup rather than silently
restoring a previously selected definition/profile after restart.

Restore callbacks cannot replace a newer user import or protocol choice. Restoring
and saving use process-wide storage ordering, so closing and reopening an Activity
cannot let an older pending save overwrite a newer setup. Long CSV exports do
not delay setup storage. Restoring setup does not restore a USB connection,
permission, ECU identity, running state,
or simulated/live logger session. Logging always requires an explicit start.
The snapshot stays inside app-private storage with Android backup disabled, like
the existing recordings. Uninstalling or clearing data still removes that data.

## Automated checks

- 12 store unit tests cover both protocols, exact selections/units, unsupported
  inputs, missing/corrupt/truncated files, definition bounds, empty selection,
  profile-before-definition, failed-save preservation, and defensive copies.
- Existing six import-state and 14 read-only-session tests remain in place.
- `LoggerSetupInstrumentation` exercises the actual Activity import and channel
  selection callbacks, process restart, original-file removal, same-key APK
  upgrade, reopening an Activity during a pending save, empty selection, corrupt
  restore, and retained CSV export.
- `packaging/android/check-lifecycle.sh` requires an emulator and the distinct
  `com.romraider.mobile.automation` package. It refuses physical-device targets
  and normal/test user packages. It never uninstalls an app or clears app data.
- A synthetic CSV exported on Android is read by the real desktop CSV parser.
- `.github/workflows/android-checks.yaml` runs offline regression checks without
  distribution secrets. Its consecutive synthetic versions exist only in the
  automation package, not as public releases.

For local automation builds, use `-Prr2AndroidTestBuildType=automation`,
`:app:testAutomationUnitTest`, `:app:assembleAutomation`, and
`:app:assembleAutomationAndroidTest`. Build the upgrade with a larger
`-Prr2AndroidVersionCode` and numeric `-Prr2AndroidVersionName`, retaining the same
signing key. Then run `check-lifecycle.sh` with the emulator serial, initial APK,
instrumentation APK, and upgraded APK.

These are offline/synthetic tests, not Forester or EVO vehicle qualification.
