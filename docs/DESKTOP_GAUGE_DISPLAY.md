# Desktop and handheld gauge display — 1.1.3 development source

Open **Gauges only** in JavaFX or the Compose logger to configure the display.
Choose a layout of 1–6 gauges, assign each slot a channel, and select its style.
**Use Logger Channels** explicitly copies up to six selected logger channels in
their current order. Loading a profile does not assign display slots.

The six assignments are saved separately from acquisition settings. Reducing
the visible count retains the hidden slots. Selecting an already assigned
channel swaps slots, so one channel cannot occupy two places. Empty slots do
not reserve screen space. Unknown or unlogged channels retain an unavailable
face rather than generating readings or starting an ECU request.

The fitted grid uses the same `GaugeDashboardLayout` algorithm as Android,
including balanced rows and full-width incomplete rows. It has no scroll
container or surrounding cards. Each face preserves its proportions within its
allocated area. Channel names, units, scales and unavailable states remain
visible. The setup controls may scroll on a small screen; the instrument grid
does not.

In JavaFX and the native Compose logger window, **Full screen** hides setup and
window/menu chrome. Tap the display to reveal **Exit full screen** and **Stop
recording**. The menu hides after five seconds; another tap resets it. Exit
restores setup and the previous window mode, without stopping the recording.
Escape also exits full-screen mode. Focus loss hides the temporary controls.

This is not yet full mobile parity: desktop display-awake inhibition remains
unimplemented. The legacy Swing-embedded Compose bridge has independent setup
and the fitted grid, but does not yet offer the native-window Full Screen
button. Neither limitation applies to Android's already verified full-screen
keep-awake behavior. Configure displays while parked.

Desktop settings store `gauge-display` schema 1, a count and six bounded channel
IDs. Old settings start with empty slots. Invalid display settings are ignored;
they never fall back to importing the logger's selection. Both desktop hosts
wire the immutable preference through the normal settings persistence path.

Verification covers immutable/swap/count/copy behavior, hidden-slot XML round
trips, persistence callbacks, and independent/unknown Compose display channels.
JavaFX native checks cover all six layouts at portrait, landscape and tiny
viewport sizes, missing readings, actual full-screen state, tap/reset/timeout/
exit, and retained synthetic session/selection/history. Compose native captures
cover setup and six fitted instruments; full native Compose menu/window/focus
automation is still a follow-up, alongside display-awake support.

September 6 verification: 252 JavaFX tests, 37 Compose tests and 18 focused core
settings/display tests pass. Render inspection caught and corrected missing
legacy channel labels and a Compose aspect-ratio overflow between fitted rows.
Native captures: [JavaFX setup](images/desktop-gauge-display-setup.png),
[JavaFX full screen with unavailable readings](images/desktop-gauge-display-fullscreen.png),
and [Compose setup with explicit synthetic values](images/handheld-gauge-display-setup.png).

This is development source, not a replacement for the public 1.1.2 RC1 packages.
No physical adapter or vehicle was used in these checks.
