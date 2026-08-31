# Windows x64 release checklist

RomRaider2's first Windows artifact is a portable, self-contained Java 21 x64
application image. It is a release-candidate preview until the clean-machine
and connected-hardware checks below pass. It must not be described as the
stable Windows release before then.

## Automated build gates

- Compile Windows classes and the application JAR with Java 21.
- Run the shared unit suite on Java 21.
- Verify every locked release dependency by SHA-256.
- Build the image on Windows with `jpackage`; cross-platform images are not
  accepted.
- Include independent Editor and Logger launchers with the approved icon.
- Bundle a Java 21 x64 runtime so users do not install or configure Java.
- Seed isolated user settings from the neutral release defaults so the first
  launch never waits on a missing-settings dialog.
- Include only the 64-bit Phidget native library and embedded native libraries
  supplied by audited JARs.
- Reject retired Graph3d/Java3D JARs and DLLs.
- Reject ROM, definition, profile, or other owner/vehicle-specific content.
- Confirm diagnostic reports filter personal data and are never sent automatically
  or log upload.
- Verify the complete release tree and publish a SHA-256 for the ZIP.

## Clean Windows test

- Extract the ZIP in a normal user-owned folder on Windows 10 or 11 x64 with no
  separately installed Java required.
- Launch `RomRaider2.exe`; confirm one Editor window, correct branding, working
  integrated window controls, resize behavior, status-bar baseline, and bottom
  resize grip.
- Launch `RomRaider2 Logger.exe`; confirm the Logger opens independently and
  remains usable while resizing.
- Select external editor/logger definitions stored outside the application;
  confirm settings persist below the release folder's `config/user` directory.
- Load, edit, save, close, reopen, and compare a non-sensitive test image.
- Confirm no Defender or SmartScreen result beyond the expected warning for an
  unsigned preview ZIP. Record the exact result; do not tell users to disable
  security software.
- Trigger a harmless test exception; confirm its report contains no username,
  local path, ROM filename, port, or network identifier and can only be shared
  through an explicit save/copy action.

## Connected Windows test

- Install the normal 64-bit OpenPort vendor driver and confirm RomRaider2 finds
  it through the Windows J2534 registry.
- Confirm ECU identification, start/stop logging, reconnect after unplugging,
  and clean shutdown without a stale connection.
- Confirm Mitsubishi MUT-II and Subaru K-line behavior separately; success on
  one protocol does not qualify the other.
- Validate a Windows COM-port external sensor with jSerialComm 2.11.4.
- Keep flashing disabled; this release does not qualify ECU writes.

## Promotion rule

The portable ZIP can be published as a Windows x64 preview after automated
gates pass. Promote it to a stable Windows release only after the clean Windows
test passes. Logger/J2534 support should remain marked preview until the
connected Windows test passes with real hardware.
