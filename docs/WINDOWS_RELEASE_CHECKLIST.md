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
- Build and bundle both x86 and x64 J2534 helper processes from the pinned,
  locked upstream source. Verify their source revision and MIT notice.
- Verify the launcher points to the bundled bridge directory and that neither
  helper is substituted for the signed vendor kernel driver.
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
- Narrow the Inspector and confirm long map names, categories, scale text, and
  live-data status wrap without overlap; expand it and confirm they return to
  fewer lines.
- In Dark theme, open the table toolbar's More popup and confirm the Live trace
  checkbox and group labels remain readable. Confirm Show 3D has a visible
  accent in both Light and Dark themes.
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

## J2534 architecture matrix

- With a registered 64-bit test DLL and the x64 application, confirm the DLL is
  loaded directly and no bridge process is started.
- With the official 32-bit Tactrix J2534 DLL and the x64 application, confirm
  the 32-bit bridge starts automatically and the DLL remains out of the Java
  process.
- Confirm discovery checks both the native and WOW6432 J2534 registry views and
  lists each physical/vendor installation once.
- Remove one bundled helper in a disposable copy and confirm the Logger gives
  an actionable reinstall/extract-complete-package error. It must not suggest
  Java installation, driver replacement, test signing, or disabling security.
- If a Windows x86 application image is later published, repeat the inverse
  mismatch test with a 64-bit test DLL and the bundled 64-bit bridge. Windows
  11 itself is x64-only; this is an application/DLL compatibility case, not a
  promise of a 32-bit Windows 11 edition.

## Connected Windows test

- Install the normal official OpenPort package. Keep its signed kernel driver
  and registered 32-bit vendor DLL unchanged; do not disable driver signing.
- Confirm RomRaider2 finds it through the 32-bit Windows J2534 registry view and
  reports that the 32-bit helper was selected for the x64 application.
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
