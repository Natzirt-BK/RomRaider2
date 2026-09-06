# Full-screen mounted gauges — 1.1.3 development source

Configure channels and choose a gauge style while parked. Open **GAUGES**, then
tap **FULL SCREEN**. This hides the app tabs and Android status/navigation bars,
reduces outer padding, and keeps the display awake while this Activity is visible.
It works on phone/tablet portrait and landscape layouts. Display cutout insets
remain protected on modern Android; the OS can retain window controls in multi-window mode.

Tap **LAYOUT** to show **1, 2, 3, 4, 5 or 6 gauges**. The choice is saved between
launches. The display uses the first channels in logger order, up to the chosen
count; if fewer are available, it fits only those gauges, without empty slots.
All selected channels continue recording, including those not currently shown.
Returning to LOGGER or the regular Gauges view shows all channels again.

The mounted grid fills the available area below the compact controls, with no
scrolling. It compares balanced row arrangements for the current viewport and
face shape, choosing the largest combined face area. Incomplete rows use their
full width; faces scale uniformly without stretching or cropping. Portrait,
landscape and window-size changes trigger a fresh fit. Some space around a face
is intentional to preserve its proportions.

Full-screen gauges use a seamless presentation: no surrounding card backgrounds,
rectangular borders, header strips or decorative header/footer divider lines.
The instruments share one dark backdrop. Dial bezels, scales, numeric display
windows and style-specific artwork remain part of the gauge itself, and warning,
stopped/stale and unavailable-reading text stays visible. Exiting full screen
restores the regular cards without replacing gauge instances or the recording.
The same borderless presentation is used in JavaFX and Compose mounted views;
their normal workspace theme is restored on exit.

**EXIT** or Android Back restores the regular Gauges view. **STOP** remains
accessible and stops the same recording without leaving mounted mode. The display
stays awake even when stopped or displaying explicitly simulated data. Entering
mounted mode does not connect USB, identify an ECU, start recording, or create data.
Channel and style settings remain in LOGGER. View switches retain the gauge grid,
recording owner and CSV writer. Stopped/stale labels and unavailable readings are
not hidden by full-screen mode.

This is an awake application display, not Android's lock-screen Always On Display.
It does not change system timeout preferences, force maximum brightness, disable
the lock screen, or prevent deliberate power-button sleep. Going to Home releases
the display-awake flag; returning to the existing mounted view restores it.
Leaving full screen releases it unless a foreground recording independently
requires it. A new Activity/process starts in the normal LOGGER view; mounted mode
never causes an automatic logging restart. Ordinary orientation changes retain the
current Activity. Background recording is a [separate service feature](ANDROID_BACKGROUND_RECORDING.md).

Android 11+ uses `WindowInsetsController` with transient bars by edge swipe;
Android 8–10 use the legacy immersive-sticky fallback. Android 13+ registers a
predictive-back callback only while mounted, with the older Back override retained
for API 26–32. See Android's [immersive-mode guidance](https://developer.android.com/develop/ui/views/layout/immersive)
and [keep-screen-on contract](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).

The `mounted-fullscreen` automation phase checks hidden/restored system bars,
idle/stopped keep-awake, Home/return, Back/LOGGER exit and retained gauge identity.
The synthetic service tests also switch full screen during disk-backed capture.
The `mounted-layouts` phase checks every count in portrait and landscape with
both legacy and custom faces, retains hidden gauges/profile identity, uses the
native count picker, checks restoration, and captures 24 actual screenshots.
The `seamless-gauges` phase checks native transparent outer-edge pixels for all
21 Android styles and verifies that regular card pixels return on exit.
The `live-gauges` phase also changes counts during synthetic capture and verifies
the original session and exported CSV. Portable layout checks cover 108
viewport/count/face combinations, including tiny and zero-sized viewports.
These checks do not establish real-phone thermal behavior, sunlight readability,
USB stability or vehicle safety. Set up while parked; do not adjust it while driving.
This feature is not in the published 1.1.2 packages.

September 6 seamless-display checks: 65 Android unit tests, debug/automation
builds and lint pass (five existing lint warnings). Native emulator phases pass
for all 21 borderless styles, fullscreen lifecycle, all 24 count/orientation
layouts, live-session/CSV continuity and calculated gauges. Shared rendering
checks cover 1,120 card/seamless edge cases; the desktop suite passes 284 tests.

## Actual Android layouts

![Six STI Night gauges fitted into a landscape Android viewport](images/android-mounted-six-landscape.png)

[Five-gauge portrait screenshot](images/android-mounted-five-portrait.png).
These are native emulator screenshots with explicitly simulated values, not
vehicle-test results. The complete 24-image set is reproducible with the
`mounted-layouts` instrumentation phase.

## Illustration

![Illustrative parked-car scene with RomRaider2 STI Night gauges on a dashboard-mounted phone](images/dash-mounted-phone-mockup.png)

This is an AI-generated product mockup using an actual Android screenshot as its
screen reference, not a photograph of a vehicle test or exact hardware fitment.
It predates the count-picker controls and seamless presentation described above.
The app display is explicitly simulated. [Generation prompts and method](images/mounted-phone-image-prompt.md)
are retained with the image. See the [actual native gauge collection](images/all-mobile-gauge-styles.png)
for ungenerated gauge artwork.
