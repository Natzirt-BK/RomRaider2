# RomRaider2 1.1.0 RC3 release readiness

Status: code-complete candidate; not yet published as a GitHub Release.

RC3 is ready for manual qualification only when the GitHub Actions run for its
exact source commit passes both jobs and provides both candidate ZIP artifacts.
A tag, GitHub Release, or stable-release claim must not be created from a
different commit.

## Automated candidate gates

The repository CI enforces the following gates on Linux and Windows:

- compile application classes and bytecode with Java 21;
- run the shared regression suite on both hosted operating systems;
- verify the locked JNA, jSerialComm, Log4j, and source dependencies;
- build each application image natively on its target operating system;
- reject dirty source checkouts and mismatched source revisions;
- reject stale RC2 package metadata and record the exact source revision;
- verify bundled runtime, launchers, settings, customization assets, licenses,
  J2534 bridge files, and platform-native libraries;
- reject retired Java3D/Graph3d files, vehicle definitions, profiles, ROMs,
  captures, and other owner-specific content;
- verify every packaged file through the internal SHA-256 manifest and publish
  a separate checksum for each candidate ZIP.

The Linux and Windows artifacts use the same `1.1.0` application version and
`Release Candidate 3` package label. They may differ only where the platform
requires different launchers, runtimes, native libraries, or qualification
documentation.

## Manual sign-offs still required

These checks cannot be truthfully replaced by CI and remain open until their
results are recorded against the exact candidate checksums:

- Windows 10/11 x64 clean-machine visual and functional pass from
  `WINDOWS_RELEASE_CHECKLIST.md`;
- Windows OpenPort discovery, direct/bridged J2534 architecture cases,
  unexpected disconnect, reconnect, and shutdown with real hardware;
- Linux connected OpenPort USB-removal recovery and the remaining items in
  `LINUX_IN_CAR_QUALIFICATION.md`;
- Mitsubishi MUT-II vehicle qualification and a Windows COM-port external
  sensor check if those paths are claimed for this release.

Flashing and ECU memory writing remain unavailable and are outside RC3 scope.
Failure of a manual check requires a new candidate build and repetition of the
affected checklist; it must not be waived by relabeling the existing artifact.

## Publication rule

RC3 may be published as a prerelease only after the intended commit, CI run,
artifact checksums, and completed manual results are reviewed together. The
existing RC2 download links remain the public links until that publication.
Stable promotion requires the stricter promotion rules in the platform
qualification checklists.
