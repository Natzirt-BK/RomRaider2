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
