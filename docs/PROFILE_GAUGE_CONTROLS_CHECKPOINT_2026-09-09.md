# Profile and gauge controls checkpoint

September 9, 2026. Development after the published Android 1.1.9 / 110414 refresh.
No distribution APK or desktop package was replaced during this work.

## Desktop profiles

The Windows/Linux/SteamOS logger File menu now includes XML Save Profile,
Save Profile As and Reload Profile alongside Load Profile. Named-file ownership
is separate from automatic recovery and lasts for the current logger window.
Save and Save As have standard keyboard shortcuts; Reload is disabled until a
named file has been explicitly loaded or saved.

Checks cover ordered channels/units, switches, empty selection, rejected
destinations, cancelled/stale review, external file edits, failed writes,
unavailable-channel disclosure and channels becoming available before saving.
Atomic replacement and interruption checks preserve previous files on failure.
Existing definitions/settings XML cannot be overwritten through profile saving.
See the [profile guide](DESKTOP_LOGGER_PROFILES.md) for behavior and limitations.

## Android gauge recording controls

Gauges and its fullscreen overlay now use the same recording actions as Logger:
green START, red STOP, text/accessibility labels, touch feedback and visibly
disabled states. START cannot toggle an active session off. Unknown service state
disables both controls; loading setup disables START; STOP is disabled while idle
or already stopping. The visual demo does not enable recording STOP.

Portrait testing caught baseline alignment clipping the two-line Full Screen
button. The control row now disables baseline alignment and centers equal-height
controls. Fullscreen menu timeout, exit, keep-awake and session ownership remain
unchanged. The menu is transient, not a persistent border around the gauges.

## Verification

- Desktop core unit tests passed, including interrupted and rejected atomic saves.
- Desktop UI suites: 369 tests, 357 passed, 12 optional skips, no failures/errors
  (321 desktop workspace tests and 48 Compose tests).
- Shared portable checks passed, including profiles, MUT-II and CSV handling.
- Android unit tests and lint passed; lint retains the two existing newer-SDK
  notices and no errors.
- Isolated Android instrumentation passed recording-control state/color checks,
  portrait/landscape and fullscreen control bounds, demo Show/Hide, mounted
  exit/keep-awake behavior, Logger layout and recording continuity across views.
  Screen captures were reviewed. No physical adapter was opened.

One local run was obstructed by the emulator's System UI not-responding dialog;
the emulator was restarted and checks repeated. This is separate from the
baseline-alignment issue fixed in application code.

Physical-phone touch/haptic feel, vendor file pickers and physical
Windows/Steam Deck acceptance remain pending. Automatic tests do not establish
adapter or vehicle compatibility. The published packages remain unchanged.

## Desktop CI follow-up

The first hosted desktop runs exposed two test synchronization defects:

- Closing a profile review may cancel its preparation future before completion.
  The close test now waits for worker termination and drains late UI callbacks,
  instead of requiring the deliberately cancelled future to succeed. A separate
  queued-save test forces cancellation and verifies that no file is written.
- The fullscreen test sampled restoration before the window manager finished
  minimizing the window. The failed runner still reported `minimized=true`, for
  which releasing the screen-awake request was correct. The test now waits for
  settled window states on the UI thread and retries native restoration within
  its existing deadline. Awake ownership checks remain intact, with transition
  names and window state included in timeout failures.

These corrections affect tests only; application behavior and release packages
are unchanged.
