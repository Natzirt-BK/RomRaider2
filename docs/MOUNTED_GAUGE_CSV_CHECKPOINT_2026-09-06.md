# Mounted display, vibrant gauges and mobile CSV checkpoint

Development version: **1.1.3 / Android 110407**. Public **1.1.2** is unchanged.

This checkpoint adds five selectable retro/vibrant faces across Android, JavaFX
and Compose, Android immersive mounted display with foreground keep-awake, and
bounded off-main-thread mobile CSV summary review. The detailed contracts are in
[gauge design](GAUGE_DESIGN.md), [mounted display](ANDROID_MOUNTED_DISPLAY.md) and
[mobile CSV review](ANDROID_CSV_REVIEW.md).

## Local verification

- Portable checks: sixteen custom faces across 560 invalid/edge-case renders,
  needle-motion rules, unavailable data and unit-aware scales. Streaming CSV checks
  pass fifty assertions including five million values with a 64 MiB heap.
- Android: 65 JVM unit tests; automation, standard debug and OpenPort diagnostic
  APK builds and lint pass. Five existing lint warnings remain: target/compile SDK
  updates, two drawing allocations and one text-localization warning. Production
  and automation manifest guards pass with their separate service types intact.
- API 36 disposable emulator: complete lifecycle regression passes, including
  portrait/landscape immersive mode, idle/stopped keep-awake, Home/return, Back,
  foreground logging's independent screen flag, same-owner/CSV continuation across
  every theme, mounted Stop, calculated channels, CSV import/cancel/paging,
  reviewed recording recovery and background/service/process-death checks.
- Native portrait/landscape galleries and the all-theme contact sheet pass. The
  screenshot harness acknowledges only Android's known first-use full-screen
  tutorial and still rejects other obstructing windows.
- Desktop: Ant Linux build/unit suite passes (optional external XDF corpus remains
  skipped); JavaFX/Compose total 232 tests pass without skips, including native
  window smoke tests. Shared version checks and whitespace checks pass.

Two harness issues were corrected without weakening production behavior: the
read-only view-switch test now awaits Activity attachment to the service's current
recording; Home-return tests do not wait for a new Activity when singleTop correctly
reuses the existing screen. Local reinstall tests reused one automation APK;
hosted regression separately exercises increasing synthetic version codes.

No physical adapter/ECU was polled. Real-phone power management, API 26–32 fallback,
sunlight/thermal behavior, document providers and Forester/EVO/OpenPort acceptance
remain unqualified by these synthetic checks. No production ECU writes or live
tuning were enabled. Hosted CI results are separate from these local results.
