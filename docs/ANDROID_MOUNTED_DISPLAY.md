# Full-screen mounted gauges — 1.1.3 development source

Configure channels and choose a gauge style while parked. Open **GAUGES**, then
tap **FULL SCREEN**. This hides the app tabs and Android status/navigation bars,
reduces outer padding, and keeps the display awake while this Activity is visible.
It works on phone/tablet portrait and landscape layouts. Display cutout insets
remain protected on modern Android; the OS can retain window controls in multi-window mode.

**EXIT FULL** or Android Back restores the regular Gauges view. **STOP** remains
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
These checks do not establish real-phone thermal behavior, sunlight readability,
USB stability or vehicle safety. Set up while parked; do not adjust it while driving.
This feature is not in the published 1.1.2 packages.

## Illustration

![Illustrative parked-car scene with RomRaider2 STI Night gauges on a dashboard-mounted phone](images/dash-mounted-phone-mockup.png)

This is an AI-generated product mockup using an actual Android screenshot as its
screen reference, not a photograph of a vehicle test or exact hardware fitment.
The app display is explicitly simulated. [Generation prompts and method](images/mounted-phone-image-prompt.md)
are retained with the image. See the [actual native gauge collection](images/all-mobile-gauge-styles.png)
for ungenerated gauge artwork.
