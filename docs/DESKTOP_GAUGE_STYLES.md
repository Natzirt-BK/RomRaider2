# Desktop and handheld gauge styles — 1.1.3 development source

JavaFX and the Compose desktop/SteamOS workspace offer searchable galleries of
the 25 selectable styles. Thumbnails are native instrument drawings labeled
**SAMPLE**, not live readings. Configure while parked.

In the dashboard, use **Default gauge style** (JavaFX) or the current style's
**Choose** button (Compose) to change the default. Each channel card has a
**Style** button for its own choice. **Use default** removes that override;
Cancel leaves it unchanged. Global style changes retain channel overrides.
Both regular cards and gauges-only views resolve the same saved channel style.

Styles are presentation settings. Picking a face does not change selected
logger channels, conversions, acquisition requests, recording ownership or CSV
columns. JavaFX's five legacy choices now have distinct native dials instead of
all using the same generic arc. Missing/stale/stopped readings remain unavailable.

An optional `gauge-theme` attribute is stored on each desktop settings XML
`dashboard-layout/tile`, keyed by parameter ID. Old settings remain readable;
missing or unknown theme names follow the current default without discarding
the tile's layout. Role, size, order, color and custom-size edits preserve the
override. Compose's **Reset layout** also preserves it. This desktop setting is
not an Android profile import or a protocol-scoped mobile display assignment.

Verification covers actual XML round trips for every selectable style, legacy
and unknown attributes, all tile copy operations, native gallery search/cancel/
default actions, distinct legacy renderings, missing-data states, and mixed
mounted faces retaining the published synthetic session. Native gallery captures
use synthetic readings and isolated virtual displays; no vehicle is accessed.

September 6 checks pass: 251 JavaFX tests, 36 Compose tests, and 14 focused
core settings/style tests. The Compose gallery capture uses software rendering
and X11 Robot capture under Xvfb, with a 45-second fixture timeout. These fixture
settings do not change production rendering or screenshot behavior.

Actual native galleries: [JavaFX](images/desktop-gauge-style-picker.png) and
[Compose desktop/handheld](images/handheld-gauge-style-picker.png).

Desktop/handheld now also have [independent slots and fitted 1–6 layouts](DESKTOP_GAUGE_DISPLAY.md),
with a temporary full-screen exit menu in their native logger windows. Remaining
qualification and display-awake limitations are documented there. These changes
are not in the public 1.1.2 RC1 downloads.
