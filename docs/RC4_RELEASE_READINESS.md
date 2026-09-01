# RomRaider2 1.1.0 RC4 release readiness

Status: public development prerelease. Final qualification is incomplete.

RC4 is the first release candidate with the replacement Logger workspace. It
also carries the reconnect work and the offline live-tuning foundation. Normal
ECU memory writing remains unavailable.

The Windows and Linux ZIPs must come from the same source commit. Results are
recorded against those exact files so a rebuilt package cannot inherit an old
pass.

## Automated checks

The Linux and Windows workflows must:

- compile and run the shared Java 21 test suite;
- run the Compose Logger tests and stage the matching native renderer;
- verify locked source and runtime dependencies;
- build a self-contained application image for that operating system;
- keep package settings, customization files, and logs isolated from older
  RomRaider installs and the launch directory;
- include the launchers, runtime, licenses, and required native libraries;
- reject ROMs, definitions, profiles, logs, private vehicle data, and retired
  Java3D/Graph3d files;
- record the source revision and verify every packaged file by SHA-256;
- publish the candidate ZIP and checksum as workflow artifacts.

## Desktop checks before publishing

- Linux clean-package launch, first-run definition prompt, Editor load/edit/
  save/reopen, and Logger Overview/Data/Graph/Dashboard checks.
- Linux Logger checks at normal and narrow sizes in Light and Dark themes.
- Windows clean-package and visual pass when the Windows VM is available.
- Confirm that launching either package beside an unrelated `settings.xml`
  still loads the package-owned settings.
- Confirm the About screens and startup log identify the build as RC4.

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
