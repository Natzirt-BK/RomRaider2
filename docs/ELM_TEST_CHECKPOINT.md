# 1.1.6 adapter-test checkpoint

September 8, 2026. Development test build; physical adapter and vehicle
qualification are pending. The public release remains 1.1.5 RC1.

## Source and automated evidence

Runtime source: `fbf4d34366c66e5ff9ea40ae875925c8b60cd8e3`.
The local Linux test package was built from a clean worktree.

- Shared protocol and streaming CSV checks passed, including fragmented
  responses, prompt synchronization, cancellation, timeout, invalid/stale
  replies, supported PIDs and documented slow/fast bus-init status messages.
- Clean local desktop checks: 289 JavaFX tests passed; one optional screenshot
  capture test skipped. All 48 Compose tests passed with native-window checks.
- The actual serial library passed six owned-PTY sessions and a 48-command
  allowlist, including close/reopen, malformed replies, timeout and cancellation.
  The probe was also repeated using the packaged runtime in a disposable copy;
  the distributable launcher and archives were not modified by that probe.
- The unmodified Linux launcher opened and closed normally with bundled Java 21
  and package-owned settings. ZIP integrity, internal manifests, source/version
  metadata and SHA-256 sidecars passed. No vehicle files or signing keys were
  included in the package.
- [Hosted Linux/Windows build](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34260455578): passed, including native Linux tests, the serial PTY probe, Windows J2534 helpers and both packages.
- [Android regression checks](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34260455618): passed. This does not add Android ELM connectivity.

The first hosted runs exposed stale floating-window geometry on full-screen
exit. Regressions reproduced both stale model coordinates and a later native
notification overwriting the first restore. The fix captures visible geometry
and briefly reconciles it, while preserving a new maximize action and releasing
control after settling. Logger sessions are not changed by window restoration.

## Local Linux test package

`RomRaider2_ECU_Studio_1.1.6_Linux_x64.zip`

SHA-256: `51939f702c9a040bc3d00d6c76e96e73c565a1c2a91caf75e726c11bb805bd40`

This hash identifies the local Java 21 build, not a separately compiled hosted
artifact. The package is a qualification build, not a stable-release sign-off.

## Next gate

Follow the [parked read-only test procedure](ELM_IN_CAR_TEST.md) with the exact
ELM/OBDLink serial model and firmware. Compare actual ECU values and review the
CSV, then test Stop, close, USB removal and reconnect. Multiple ECU responders
remain unsupported by this first test mode and must stop with an explicit error.

Existing OpenPort users should test the normal Logger, not the ELM test window.
Do not infer SSM/MUT-II-over-ELM, Android ELM, flashing or live-tuning support from
these offline results. No physical adapter or vehicle was used by the automation.
