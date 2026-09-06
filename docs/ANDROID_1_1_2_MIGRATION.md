# Android 1.1.1 to 1.1.2 migration

This checklist covers the signing-key transition in version 1.1.2. It is not
an instruction to uninstall your current app without verified backups. Check
the [latest release](https://github.com/Natzirt-BK/RomRaider2/releases/latest)
for package availability. No automatic uninstall or data clearing is
implemented. Desktop installations are not affected by Android certificates.

## Verified package identities

The cached public APK hashes were checked against the current GitHub release
asset digests. Both old APKs and both 1.1.2 APKs passed `apksigner verify`;
their actual package metadata was read with `aapt dump badging`.

| Package | Application ID | Earlier public 1.1.1 | Version 1.1.2 |
| --- | --- | --- | --- |
| Standard | `com.romraider.mobile.preview` | code 110405, old certificate | code 110406, permanent certificate |
| OpenPort Test | `com.romraider.mobile.preview.openporttest` | code 110405, old certificate | code 110406, permanent certificate |

Old certificate SHA-256:
`d450a406ddb707ead0e5ebac0b109465405e2b8d6249eb9991003255cf3d8fcb`

Permanent certificate SHA-256:
`e47ef588575ddc47e5e69bb02cdcb0e82a60a54bfa060a15ea584d8b40304f34`

The package IDs stay the same but this key transition has no old-key signing
lineage. Increasing the version number cannot make it an in-place update over
the old public app. An existing OpenPort Test installation signed with the old
key has the same problem; it is not a workaround for upgrading that installation.
An unused separate-test application ID can coexist with the standard app, but
does not automatically receive its private data or logger setup.

The final application change is `d5139993`; its
[Android regression run](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34023804384)
passes, including isolated same-key emulator upgrades. Package qualification is
recorded in the [1.1.2 release notes](RELEASE_1_1_2.md). File and emulator checks
are not a migration on your phone.

## Preserve your data before changing any installation

1. Stop logging and wait for recording/export work to finish. Keep the old app
   installed and do not clear its storage.
2. Use **Recover / export recordings** to export each retained recording through
   Android's document picker. Export any current live recording too. Choose a
   location independent of the app, such as Documents/Downloads, and make a
   second copy on your computer. App-private recovery files are not that backup.
3. Open every important exported CSV in desktop RomRaider2 or another CSV viewer.
   Check that expected channels and the start/end of the session are present.
   An export button press alone is not proof that the file finished writing.
4. If you have unsaved ROM work, use **SAVE COPY** to an independent location.
   Reopen and verify that saved copy before relying on it. The old public app
   has a documented save-completion defect; a success message is not enough.
   Keep the original ROM separately. Android does not repair ROM checksums;
   saved files are for review/desktop validation, not flashing.
5. Preserve the original ECU definition, logger definition/config and profile
   files outside app-private storage. Record the protocol, selected channels,
   order, units and any manual selection changes. CSV files do not contain a
   complete logger definition or restore a profile. The private saved-setup
   snapshot is not a user-exportable migration bundle.
6. If any required recording, ROM work or definition/profile cannot be recovered,
   leave the old installation in place. Do not uninstall hoping the new app can
   recover its private files afterward.

## Once a release is published and your backups are verified

- Prefer keeping the old standard app while checking an unused separate-test
  package. Import your definitions/profile separately; do not give two apps
  simultaneous USB access. If the test package is already installed under the
  old key, preserve its data too before considering its replacement.
- Replacing the old standard installation requires a deliberate manual migration
  after verified export. Uninstalling removes app-private recordings, recovery
  and settings. No tool in this repair pass performs that operation for you.
- In the new app, import the correct logger definition/config and profile,
  verify selected channels/units, and reopen the exported CSVs and saved ROM
  copies. Reopened CSVs are analysis files; they do not recreate the old private
  recovery list or automatically configure logging.
- Check restoration after closing/reopening the app without starting a vehicle
  session. USB permission may need granting again. Connection and logging do
  not automatically resume. Real Forester/EVO and phone/provider acceptance
  remain separate supervised tests.

Future increasing-version releases using the permanent identity are intended
to update its existing installation normally. [Same-key emulator checks](ANDROID_UPDATE_RELIABILITY.md#automated-checks)
do not prove a lossless migration from the old public certificate.

## Maintainer backup status

On September 6 UTC, an encrypted local signing backup was created and restored
for verification. The restored keystore/password signed a certificate request,
and its certificate matched the permanent identity. Temporary restored files
were removed; no private material was added to GitHub. The owner explicitly
accepted same-disk storage. An off-device copy is still recommended but is not
confirmed; this local backup does not protect against disk failure and does not
recover the old public signing key.
