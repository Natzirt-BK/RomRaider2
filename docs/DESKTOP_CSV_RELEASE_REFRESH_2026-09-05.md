# Desktop CSV repair and RC4 refresh — 2026-09-05

The user authorized finishing B1, updating the public RC4 release, publishing
Android preview3, and retaining only the latest desktop RC and Android preview
release entries. This is approval to refresh **prereleases**, not evidence of
hardware qualification or a stable-release promotion.

## B1 repair

- CSV requests capture their own absolute source file and generation.
- Only the latest selected request may publish a dataset or error on the UI
  thread, including when an older completion was queued before a newer choice.
- Cancelling the chooser leaves an already accepted load intact.
- Closing Logger invalidates queued/in-flight completions; late results cannot
  create a replacement analysis pane or error dialog.
- A replacement pane is constructed before the previous pane is closed. The
  previous dataset remains usable while the new CSV loads, and owns its original
  marker sidecar throughout.
- Parser failures retain the current pane and report failure for the correct
  source. No CSV data is written by loading or marker operations.

## Verification

- Ten deterministic coordinator tests cover reversed/queued ordering, stale
  errors, latest failure, repeated paths, chooser cancellation, close/disposal,
  synchronous errors and invalid parser/presentation results. These run without
  a toolkit on both Linux and Windows CI.
- Three native JavaFX tests exercise the actual window/pane, replacement cleanup,
  and closing during successful/failed loads. A synthetic marker write lands only
  in the displayed CSV's sidecar; the other sidecar and CSV bytes stay unchanged.
- Local JavaFX suite: 43 tests passed with native Xvfb smoke enabled. No ECU,
  private ROM, definition or captured vehicle log was used.
- Candidate `8ff82511db593ca28b5df538dbd72ba858f9f2a3` passed both
  [Linux/Windows jobs](https://github.com/Natzirt-BK/RomRaider2/actions/runs/33977732753)
  and all four [platform preview jobs](https://github.com/Natzirt-BK/RomRaider2/actions/runs/33977732237).
  Windows bridge tests: 45 passed. Windows runs the ten new coordinator tests,
  but skips opt-in native JavaFX window tests; no new Windows VM UI pass is claimed.
- Downloaded all five desktop ZIPs and checked their SHA-256 sidecars. Linux's
  extracted package verifier and all internal checksums passed; Windows's package
  verifier passed on the native CI runner. macOS ZIP integrity and bundled Java
  21 metadata passed, without implying physical Mac testing.
- The SteamOS JavaFX workspace JAR exactly matches the Linux candidate's, and
  its launch configuration selects the SteamOS profile. Its packaging job checks
  Linux JavaFX modules, foreign-module exclusion, launchers and private-ROM exclusion.
- Re-ran all 21 packaged desktop repair assertions on a separate Linux diagnostic
  copy using its bundled JVM/application JARs at 853×533 Xvfb. Sentinel:
  `PACKAGED_DESKTOP_REPAIR_PASS`. This copy adds a diagnostic JAR and replaces
  its launcher main class; the released ZIP and stock extraction remain untouched.
  Native file choosers, real definitions and vehicle hardware are not exercised.

## Published artifacts and cleanup

The existing [desktop RC4 release](https://github.com/Natzirt-BK/RomRaider2/releases/tag/romraider2-1.1.0-rc4)
now contains the five new desktop ZIPs and their five checksum sidecars. It stays
a prerelease. All five are from the candidate commit above. The original RC4
tag remains unchanged; the release notes explicitly link matching candidate
source rather than claiming GitHub's original-tag source archive matches them.
Anonymous public downloads of all five ZIPs and their sidecars passed again after
replacement; their hashes match the CI artifacts and GitHub asset digests.

| Desktop ZIP | SHA-256 |
| --- | --- |
| Linux x64 | `ccdd15b1e27babd058517ece2760b90345ea700959c003dceb5c46ba3760c50c` |
| Windows x64 | `ee8b00f868969202766f67229729d5757df440de71db5b78bb9dda52b5a921a9` |
| SteamOS x64 | `b6f522a200dfe395541b549a2a958082b1f612bbe3d310d546f3a8bd2ed2f287` |
| macOS arm64 | `92803834c9df5645d589417cd346776e758d30cae1f5d0389d51c25dcaa779ff` |
| macOS x64 | `13c993a19c6113b7c985c4094a0206d593d0caaa1b200fb7a3311435d4ce221d` |

Android preview3 was present only as a CI artifact, while the public release and
README still exposed preview2. Published the already-tested APK from platform
run33954484900 / source3af9bd35, verified its public download, and corrected the
README. The desktop rebuild does not replace that separately verified APK.

Removed exactly four superseded release entries: desktop RC2/RC3 and Android
preview1/preview2. Their metadata and every uploaded asset were backed up locally
and verified against GitHub's SHA-256 asset digests first. All four source tags
remain. GitHub Releases now retains only desktop RC4 and Android preview3.

The Linux installer pin and its checksum assertion now match the public Linux
ZIP. Its README no longer points to the removed RC3 release. All 11 repository
test scripts passed locally, including the 14 silent music-lifetime cases. A
clean isolated installation from the actual downloaded public ZIP and an
idempotent second invocation passed. No real installation or vehicle data was
changed. This narrowly updates the pin/links; it does not publish the previously
separate definition-preservation work or qualify complete upgrade migration.
Installer commit `9139a35eb55b5ad774b9cf652c2106067b68407a` is pushed; both
[installer CI jobs](https://github.com/Natzirt-BK/subaru-ecu-tools-linux/actions/runs/33978286587)
passed, including the Debian 13 bridge build and repository tests. RomRaider2's
GitHub default `master` branch was fast-forwarded to the current source/audits,
so its front-page README now exposes preview3, not the removed preview2.

## Release boundaries

The earlier [desktop repair audit](DESKTOP_REPAIR_AUDIT_2026-09-04.md) remains the
source for the broader acceptance matrix. B1 is repaired here; B2 (optional
Windows Innovate LM-2 MTS/COM4J compatibility), physical DPI/accessibility,
representative real-file workflows and on-car MUT-II/OpenPort tests remain open.
Linux, Windows and SteamOS use JavaFX; the macOS previews retain the separate
Compose shell. Do not transfer JavaFX UI results to macOS.

Android preview3 has its own version, source and [audit](ANDROID_MUT2_AUDIT_2026-09-05.md).
It is not a stable release or a vehicle-qualified logger. Production ECU memory
writing and flashing remain unavailable.
