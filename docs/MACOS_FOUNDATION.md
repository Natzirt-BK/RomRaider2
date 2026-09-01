# macOS foundation

RomRaider2 has one desktop source tree for Linux, Windows, and macOS. The first
macOS milestone is offline ROM editing, log review, and the replacement Logger
workspace. ECU writing is not part of this work.

## Architectures

- Apple Silicon uses a native ARM64 Java 21 runtime and the ARM64 Compose/Skia
  renderer.
- Intel uses a native x64 Java 21 runtime and the x64 Compose/Skia renderer.
- The packages are built separately. A renderer from another operating system
  or architecture fails package verification.

`ant build-macos` produces the shared application jar. On the target Mac,
`stageLoggerWorkspace` stages the native Compose renderer and
`packaging/java21/build-macos-app-image.sh` creates `RomRaider2.app`.
`jpackage` cannot create the macOS application image on Linux or Windows.

## User data

The application bundle is treated as read-only. On macOS, user settings default
to:

`~/Library/Application Support/RomRaider2/settings.xml`

The package includes neutral defaults that are copied there on first launch.
Logs continue to use the private RomRaider2 user-data directory. Definitions,
profiles, ROMs, and owner logs are not bundled.

## Logging boundary

jSerialComm provides the first architecture-neutral serial path. OpenPort/J2534
logging needs a separately validated macOS provider before it can be claimed as
supported. The macOS package does not include the Linux `j2534.so` or Windows
J2534 bridge.

## Still unverified

- Native launch, menus, file dialogs, typography, and scaling on both Mac
  architectures.
- Application signing, notarization, quarantine behavior, and DMG packaging.
- Serial-device permissions and real logging hardware.
- OpenPort support.

The GitHub workflow builds unsigned ARM64 and Intel application images for
testing. Signing and notarization remain a later release requirement.
