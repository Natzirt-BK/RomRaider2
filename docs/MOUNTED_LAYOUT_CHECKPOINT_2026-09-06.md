# One to six mounted gauges

Development source: **1.1.3 / Android 110407**. Public **1.1.2** is unchanged.

Android full-screen mode now saves a 1–6 gauge display count and fits the available
gauges without scrolling. It preserves the original grid, hidden gauge instances,
logger profile and recording owner. Display selection is the first channels in
logger order; it does not reduce the recorded channel set. Legacy and custom
faces both scale proportionally. See the [display guide](ANDROID_MOUNTED_DISPLAY.md).

## Verified locally

- Portable layout checks: 108 viewport/count/face combinations, candidate-area
  comparison, bounds, non-overlap, invalid input and empty data handling.
- Android automation, standard debug and OpenPort diagnostic builds/lint pass;
  JVM tests pass. Five existing lint warnings remain. Production connected-device
  and isolated automation service manifest guards pass.
- API 36 disposable emulator: the complete lifecycle script passes, including
  every count in both orientations for legacy/custom faces, native picker and
  count restoration, all-theme view/count changes during synthetic recording,
  original CSV writer continuity, background capture and interrupted-log recovery.
  The local upgrade check reinstalls the same automation APK; the hosted workflow
  separately tests increasing synthetic version codes.
- Captured 24 native layout screenshots; representative portrait/landscape
  images were visually inspected and two are included in the display guide.
  Capture waits for composition so files do not show the preceding layout or
  an unfinished rotation animation.
- Fresh normal and recovered synthetic Android CSV exports pass the production
  desktop parser's exact time/value/unit compatibility check.
- Shared version and whitespace checks pass. No physical adapter or vehicle was
  accessed; no production ECU writes were enabled.

## Regression repairs and remaining qualification

The previous hosted Android run exposed a race in the CSV test's one-slot worker
queue: submitting a gate did not prove it had started. Tests now await gate entry
and assert that imports were actually queued before checking cancellation. The
immediate-cancel case performs start/cancel within one UI turn. No production
cancellation or stale-result checks were weakened.

The desktop audit also exposed intermittent initial modal-window placement on
GTK. That investigation is separate from the verified Android layout work;
one passing rerun is not evidence that the intermittent problem is fixed.
Fresh hosted checks and real-device acceptance remain separate from these local
results. Physical phone power management, older Android fallback and vehicle
logging acceptance remain supervised tasks.
