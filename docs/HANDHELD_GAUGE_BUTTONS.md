# Handheld gauge button sizing

The Steam Deck label-clipping report exposed two reproducible layout problems:

- A fixed-height Compose layout button gave a 24-pixel text line only 20 pixels
  at 150% text scale, even in a 1280×800 window.
- JavaFX shortened a gauge-channel button to `1: Manifold Relative Pres...`
  despite having room to wrap the complete name vertically.

Gauge buttons now use minimum heights instead of fixed heights. Setup commands
wrap onto another row, long channel/style names wrap, and narrow setup windows
stack the channel and style buttons. The style gallery also retains complete
style names. Compact Arrange controls use two rows, with additional card height,
so the role and size labels do not compete with the movement arrows for width.
JavaFX channel buttons keep their bounded width and grow vertically for text.

## Regression coverage

`GaugeButtonLayoutTest` measures actual native Compose text layout at 1280×800,
800×480 and 440×700, including 150% text scale. It also checks Arrange controls
at 160-, 210- and 282-pixel content widths. Assertions reject truncated lines,
ellipsis and overflowing text, allowing one pixel for fractional font rounding.

`FxGaugeDisplaySetupSmokeTest` checks the actual JavaFX skin's rendered channel
label at 1280×800 with larger text. Both clipping regressions failed before
their respective fixes. These tests use synthetic data and isolated windows;
they do not connect to a logger or an ECU.

The September 6 local Java 21 verification passed all 43 Compose and 258 JavaFX
tests with native-window checks enabled (zero failures or skips), plus the
shared-core checks. The core JAR was rebuilt earlier for the parked installer
audit; the label fixes themselves change only UI layout.

Physical Steam Deck acceptance, including the owner's SteamOS scaling settings,
remains untested. This is a source change, not an update already installed on
the owner's device.
