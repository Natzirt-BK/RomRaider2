# JavaFX Logger channel controls and rolling statistics

Development work for the next release. This does not change the published
1.1.1 packages or the version policy.

## Channel rail

The JavaFX desktop Logger exposes All channels, Parameters, Switches and
External Sensors. Search matches channel name, ID and current units, using
locale-independent case folding. Changing category or search never selects or
deselects channels. Counts distinguish matching rows, category size and selected
channels in the category.

Clear category and Clear all require confirmation. Category clear includes
search-hidden channels in that category; Clear all includes every category.
Only the selected IDs captured before confirmation are submitted to the existing
channel service. Cancel does nothing. No Select all action is introduced.
Selection changes affect polling and the existing recorder, not saved CSV files.

Channels with multiple definition-provided conversions have a unit selector.
It sends the original option ID through the existing runtime conversion path,
not a display-only label change. Initial rendering does not issue unit commands.
Selectors are disabled while recording; the callback also checks the current
session state, including before a queued UI update arrives. Stop recording before
changing units. This work does not add conversions to definitions or implement
a new profile format.

## Data view

Minimum, maximum and average use the live bus's rolling history: up to 2,000
readings per channel, not lifetime/session totals. Non-finite values and values
with a different unit label are excluded. A channel with no valid history shows
an em dash, not a fabricated zero.

Reset statistics resets rolling history to each channel's latest reading, so
minimum/maximum/average begin again there. It also resets the shared recent
history used by graphs and automatic gauge ranges. It does not deselect channels,
disconnect, stop recording, modify custom gauge limits, or touch saved logs.
The connection timing/status footer is separate and is not reset by this action.

## Automated coverage and remaining qualification

- `FxLoggerChannelPaneTest`: category/search isolation, Turkish-locale search,
  exact category/all clears, cancellation, new channels during confirmation,
  individual selection, stable conversion IDs, and recording-state guards.
- `FxLoggerStatisticsTest`: finite/current-unit filtering, empty history,
  large opposite-sign values, and locale-independent formatting.
- `FxLoggerDisplaySmokeTest`: real JavaFX window, bus-to-table statistics and
  reset, retaining selection/latest readings without connecting hardware.
- `LoggerLiveDataBusTest`: history reset retains current values and recording
  state; later samples continue accumulating; empty reset is safe.

Run the core Ant unit tests and rebuild the core JAR first, then run
`:ui:javafx-desktop:test` with `RR2_FX_WINDOW_SMOKE=1` under a display (Xvfb is
sufficient on Linux). The normal build workflow runs these display tests on
Linux. Windows runs non-display tests and builds the same JavaFX sources;
Windows physical UI acceptance is separate.

These synthetic checks do not qualify a connected ECU, every conversion's
numerical definition, profile persistence across a real restart, or a recorded
hardware CSV after changing units. Those remain explicit acceptance checks.
This is JavaFX parity work (Linux/Windows/SteamOS), not a change to the separate
macOS Compose or Android interfaces. Gauge warning configuration/hysteresis and
the rest of the feature backlog remain separate work.
