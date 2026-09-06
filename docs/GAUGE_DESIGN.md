# Dashboard instruments

RomRaider2 adds nine original instrument faces alongside its existing themes.
Choose a face in the Logger dashboard, then switch to **Gauges** on Android or
**Gauges only** in the JavaFX or Compose desktop/handheld logger. Configure the display while
parked. This view is not a replacement for the vehicle's instruments or warnings.

| Face | Visual structure |
| --- | --- |
| Rally Precision | Dark circular dial, white graduations, red pointer |
| Circuit Stack | Stepped amber column and large numeric reading |
| Retro VFD | Green seven-segment numerals and horizontal level bar |
| Club Sport | Light dial, dark graduations and red pointer |
| Sweep Ribbon | Rising horizontal ribbon, marker and large reading |
| Twin Arc | Cyan current-value arc and separately labeled violet measured-peak arc |
| Amber Matrix | Dot-matrix numerals and discrete level indicators |
| Vector HUD | Minimal numeric display with a vertical scale and marker |
| Turbo Pod | Offset circular dial paired with a separate digital display |

## Research and design decisions

The design study used manufacturer material, not copied gauge skins. Subaru's
[WRX/STI quick guide, gauges section](https://techinfo.subaru.com/stis/doc/ownerManual/MSA5B1905A_STIS.pdf)
separates the instruments from informational, caution and immediate-attention
indicators. Mitsubishi's indexed [2015 Evolution press kit](https://media.mitsubishicars.com/presskits/2015-lancer-evo-press-kit)
describes a high-contrast speedometer/tachometer cluster; direct access to that
press site was restricted during this review. Those references informed the
restrained dark-face, clear-graduation designs—not vehicle-specific limits.

The aftermarket references included [Defi ADVANCE A1](https://defi.nippon-seiki.co.jp/products/advance_a1/),
[HKS Direct Bright Meter](https://www.hks-power.co.jp/en/product/electronics/meter/d_b_meter/index.html)
and [AiM MXG](https://www.aim-sportline.com/en/products/mxg/index.htm).
HKS's contrasting light/dark dial options informed the light-face alternative.
AiM's separate configurable alarm and shift indicators reinforced a design rule:
decoration must not masquerade as a configured alarm. These are historical and
visual references, not endorsements, compatibility claims or reproduced artwork.

The green segmented and amber dot-matrix treatments are RR2's interpretation of
retro digital instruments. The ribbon, split pod and dual arcs provide different
information layouts, not merely alternate colors. All artwork is drawn in code;
no manufacturer face, badge, font asset or logo has been embedded.

## Data meaning

- Numbers and units come from the selected logger conversion.
- Reference scales are display ranges, **not safe operating ranges**. Unknown
  units use a recent measured range instead of assuming psi, Fahrenheit or AFR.
- Invalid readings show a dash, not zero. Mounted live views blank stopped or
  stale readings after three seconds without a displayed sample. Measured peaks
  remain labeled as historical values, including Twin Arc's inner peak ring.
- Desktop warnings use the user's existing conversion-bound limits. Mobile
  faces do not invent warning thresholds. A red pointer is not a warning.
- Demonstration data is explicitly labeled **SIMULATED**. Opening the gauges
  view never initiates a connection, starts recording or generates live data.
- Android switches reuse the existing gauge views, logger session and CSV writer.
  Returning to the logger does not stop the session. Leaving the Android app
  still stops foreground-only logging; this feature is not background recording.

## Implementation and validation

`GaugeFaceRenderer` in the portable module defines all nine vector faces. Android,
JavaFX and Compose adapt its drawing operations to their native canvases.
The portable check exercises every face with finite, missing, infinite and
out-of-range values and invalid scales. Native UI checks cover rendering and
view-switch behavior without a vehicle. Synthetic tests do not establish real
vehicle timing, outdoor sunlight readability, USB stability or driving safety;
those remain supervised acceptance checks.

Android's `gauges` instrumentation phase preserves the simulated session across
all 14 themes. `live-gauges` runs the actual `ReadOnlyLoggerSession` and streaming
CSV writer against an injected fake transport, checks that the file continues
growing, and asserts one identification and one deliberate transport close.
Both are part of the lifecycle regression script. `gauge-gallery` captures native
screens and rejects an obstructing window. JavaFX's mounted-gauge smoke test
checks published recording state, all 15 theme switches, stale data and recovery.
