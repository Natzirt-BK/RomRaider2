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

The legacy Swing-embedded Compose bridge also offers **Full screen**. It moves
the existing view into a borderless window on the host display, hiding the
surrounding logger tabs and controls. Exit returns that same view to its original
place; the host window's size and state are not changed. Closing the gauge window
returns to setup rather than closing the logger. Closing/removing the embedded
workspace cleans up its full-screen window and composition.

[Desktop screen-awake requests](DESKTOP_DISPLAY_AWAKE.md) now follow visible,
focused full-screen gauges in all three desktop hosts. The menu reports request
status and an unavailable warning stays visible if the desktop cannot provide
it. Native platform acceptance and Gaming Mode service availability remain
qualification work; an accepted request does not override deliberate sleep.
Android's already verified full-screen keep-awake behavior is unchanged.
Configure displays while parked.

Desktop settings store `gauge-display` schema 1, a count and six bounded channel
IDs. Old settings start with empty slots. Invalid display settings are ignored;
they never fall back to importing the logger's selection. Both desktop hosts
wire the immutable preference through the normal settings persistence path.

Verification covers immutable/swap/count/copy behavior, hidden-slot XML round
trips, persistence callbacks, and independent/unknown Compose display channels.
JavaFX native checks cover all six layouts at portrait, landscape and tiny
viewport sizes, missing readings, actual full-screen state, tap/reset/timeout/
exit, and retained synthetic session/selection/history. Compose native captures
cover setup and six fitted instruments. The Swing-embedded native checks below
cover Compose menu input and focus behavior. The separate Compose-owned window
now has a production-window lifecycle fixture, described below. Physical platform
acceptance and Gaming Mode service availability remain separate checks.

September 6 verification: 252 JavaFX tests, 37 Compose tests and 18 focused core
settings/display tests pass. Render inspection caught and corrected missing
legacy channel labels and a Compose aspect-ratio overflow between fitted rows.
Native captures: [JavaFX setup](images/desktop-gauge-display-setup.png),
[JavaFX full screen with unavailable readings](images/desktop-gauge-display-fullscreen.png),
and [Compose setup with explicit synthetic values](images/handheld-gauge-display-setup.png).

The Swing bridge's retained-transfer tests additionally check repeated window
transfers, new state rendering after each transfer, exact original composition
lifetime, restoration of the original host bounds, and owner-close cleanup.
Its production-workspace test drives a synthetic recording bus and real pointer
events through the menu timeout/reset cycle, renders newly arriving readings,
switches native focus away and back, and exits via menu, Escape and native window
close. All these view changes leave the recording state, sample identity and
display assignment intact without issuing a session command. No `LoggerDesktopRuntime`, USB
provider or vehicle is constructed. Run native checks in a separate Xvfb display
with `RR2_COMPOSE_WINDOW_SMOKE=1`; ordinary headless runs skip native-window tests.
CI runs these separately from JavaFX so competing test windows cannot steal focus.

The bridge uses Compose's SwingGraphics rendering path: testing the native
SkiaSurface transfer found a retained but non-refreshing scene. It also keeps
the current AWT window reference explicit across transfers, preserves the
composition only during reparenting, and restores normal disposal afterward.
Native window changes are queued outside composition/frame callbacks. These
choices are limited to the embedded Swing bridge; the Compose-owned shell's
renderer is unchanged. The upstream [ComposePanel lifecycle contract](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui/src/desktopMain/kotlin/androidx/compose/ui/awt/ComposePanel.desktop.kt)
describes retained removal and the application's cleanup responsibility.

The bridge checkpoint passes all 39 Compose tests, including both opted-in native
tests. Actual synthetic captures: [full screen](images/swing-gauge-display-fullscreen.png)
and [tap-revealed exit menu](images/swing-gauge-display-menu.png).

The Compose-owned logger uses `GaugeLoggerWindow` in both production and its
synthetic native fixture. The fixture verifies live readings after entering full
screen, tap/reset/timeout, focus loss/regain, exact saved floating-window geometry,
maximized restoration with Escape, externally initiated native exit, minimize/
restore, and closing while active. Recording state and sample identity are retained
without session commands. It constructs no `LoggerDesktopRuntime` or adapter.
The complete Compose suite passes all 40 tests with native checks enabled; runtime
staging also passes.

This qualification caught and corrected three presentation defects: a retained
native menu strip, loss of the saved window position, and Escape not reaching the
root keyboard handler. Restoring maximized mode is ordered after AWT's intermediate
floating-state callbacks. The screen-awake gate checks actual native full-screen
state as well as intent, focus, visibility and minimization. Native placement is
observed while full-screen gauges are active and external exits return to setup.

The native fixture checks that the actual on-screen **client canvas** exactly fills
the display. X11 AWT may retain hidden decoration insets in its outer frame bounds;
those cached bounds are not the visible canvas. Captures use the display bounds:
[full-screen Evolution-style fixture](images/compose-owned-gauge-display-fullscreen.png)
and [tap menu](images/compose-owned-gauge-display-menu.png).
Run this fixture with `packaging/run-desktop-window-tests.sh` and
`RR2_COMPOSE_WINDOW_SMOKE=1`; minimize/restore requires the isolated Openbox window
manager, not bare Xvfb. This native-window qualification runs on Linux/X11; it does
not establish physical Windows/macOS/Deck window-manager or idle-timeout behavior.

This is development source, not a replacement for the public 1.1.2 RC1 packages.
No physical adapter or vehicle was used in these checks.
