# RomRaider2 1.1.0 RC4 release readiness

Status: public development prerelease. Final qualification is incomplete.

RC4 began as the first release candidate with a replacement Logger workspace.
The active desktop recovery work now uses JavaFX for the Windows and Linux
Editor and Logger. Existing public RC4 approvals do not transfer to rebuilt
JavaFX candidates. Normal ECU memory writing remains unavailable.

The Windows and Linux ZIPs must come from the same source commit. Results are
recorded against those exact files so a rebuilt package cannot inherit an old
pass.

## Automated checks

The Linux and Windows workflows must:

- compile and run the shared Java 21 test suite;
- run the JavaFX desktop tests and stage matching OpenJFX native modules;
- verify locked source and runtime dependencies;
- build a self-contained application image for that operating system;
- keep package settings, customization files, and logs isolated from older
  RomRaider installs and the launch directory;
- include the launchers, runtime, OpenJFX license, and required native libraries;
- reject ROMs, definitions, profiles, logs, private vehicle data, and retired
  Java3D/Graph3d files;
- record the source revision and verify every packaged file by SHA-256;
- publish the candidate ZIP and checksum as workflow artifacts.

## Desktop checks before publishing

- Linux clean-package launch, first-run definition prompt, Editor load/edit/
  save/reopen, and Logger Overview/Data/Graph/Dashboard/Analysis checks.
- Linux Logger checks at normal and narrow sizes in Light and Dark themes.
- Linux calibration grid and cell-editing checks at normal and narrow sizes in
  Light and Dark themes, including wrapped controls and horizontal scrolling.
- Confirm the JavaFX calibration grid uses the active table's real values,
  applies edits through shared scaling and undo history, and retains one
  calibration tab per open table.
- Confirm active-cell Ctrl+C/Ctrl+V and Ctrl+Z/Ctrl+Y work after clicking the
  JavaFX grid on both Linux and Windows.
- Confirm Shift+arrow and Shift+click range selection, spreadsheet-format range
  copy, and one-step block paste/undo on both Linux and Windows. Check that an
  oversized or malformed block leaves every ROM cell unchanged.
- Confirm Ctrl+A selects the full JavaFX grid after a calibration cell
  receives focus.
- Check rejected numeric input, locked tables, changed-cell outlines, and the
  Apply/Undo/Redo controls at normal and narrow widths.
- Windows clean-package and visual pass when the Windows VM is available.
- Confirm that launching either package beside an unrelated `settings.xml`
  still loads the package-owned settings.
- Confirm the startup log identifies the JavaFX provider and the candidate
  source revision; confirm Compose and Skia artifacts are absent.
- Obtain explicit user visual approval for the Linux, Windows, and Android
  candidates before replacing any public RC4 download.
- After the approved Linux ZIP is uploaded, update the pinned RC4 URL and
  SHA-256 in `subaru-ecu-tools-linux/linux/install-romraider2`, then rerun its
  installer test. Do not repoint the installer before the matching asset is
  public.

## Connected testing

No new in-car test is planned for RC4. RC4 does not expand the connected claims
already documented for RC3. OpenPort reconnect, unexpected USB removal,
external sensors, and Mitsubishi Lancer Evolution MUT-II vehicle qualification
resume during RC5 work.

## Release rule

A failed package or desktop check is fixed in source and tested in a newly
built candidate. Do not relabel an older artifact. An unfinished platform may
be published only as a clearly marked preview with its missing checks stated;
it cannot be described as qualified or stable. Flashing and production ECU
memory writing remain outside RC4.
