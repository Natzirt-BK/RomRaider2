# Active work plan

Updated September 9, 2026.

The current candidate is [1.1.8 RC1](RELEASE_1_1_8.md), available for Android,
Windows, Linux, SteamOS Desktop Mode and macOS. See the
[release qualification record](RELEASE_1_1_8_QUALIFICATION.md) for package checks
and outstanding hardware tests.

## Ready for testing

- Android: automatic DimeMod discovery, ECU-specific channel selection and
  removal of the offline logger. The visual gauge demo remains separate.
  See [channel discovery](ANDROID_CHANNEL_CATALOG.md).
- Android: standard XML profile saving, a separate scrollable CSV summary
  window and Close Log File. See [profiles](ANDROID_LOGGER_PROFILES.md) and
  [log review](ANDROID_CSV_REVIEW.md).
- Android: a [compact Logger layout](ANDROID_LOGGER_LAYOUT.md) with session
  status, Start/Stop, live readings and expandable setup sections.
- Desktop: a separate read-only ELM327/OBDLink serial adapter test and more
  reliable window restoration after fullscreen gauges. The
  [adapter test checkpoint](ELM_TEST_CHECKPOINT.md) records verification.

## Next priorities

### September 9 Android workflow review (local)

The phone recording identified weak button feedback, a start action above setup,
and Review mixed into Logger. Local changes add a workspace menu ordered Logger,
Gauges, Review, Editor; separate bottom START/STOP controls; visible pressed and
disabled feedback; and a post-recording Save CSV prompt that retains the internal
recovery copy. See [Android layout](ANDROID_LOGGER_LAYOUT.md).

The [local qualification checkpoint](ANDROID_WORKFLOW_CHECKPOINT_2026-09-09.md)
records 137 passing Android unit tests, emulator workflow/lifecycle coverage and
remaining physical acceptance. No updated public APK has been published.

Additional adapter requests are OBDLink LX/MX Bluetooth and KKL USB, with the
reference menu distinguishing K-Line and CAN paths. Track actual transport,
protocol and vehicle requirements before presenting these as supported choices;
the existing Android OpenPort transport does not support those devices merely
by adding a selector. Do not assume a third-party menu's year/patch claims apply
to RR2. These remain compatibility work, not part of this UI update.

A local [Bluetooth link foundation](ANDROID_BLUETOOTH_LINK.md) now connects a
supplied RFCOMM socket to the tested ELM session contract. Twelve synthetic
stream tests cover cancellation, bounded I/O and one supported-PID transaction.
This is not a selectable adapter or hardware compatibility claim. Bluetooth
device/permission UI and logger-service ownership remain to be implemented.

### September 9 desktop setup review (local)

The Forester test session identified clipped setup controls and unnecessary text
entry. Local UI changes add linked, non-editable Protocol/Transport/Module
selectors, using background reads of definition metadata and filtering out
protocol/transport implementations absent from the package. These are software
capability choices, not proof of compatibility with every vehicle or adapter.
Invalid or missing choices cannot be saved; opening the dialog does not connect.
Browse and Disconnect labels retain their preferred widths. Disconnect has a
tooltip explaining cancellation and the unchanged startup preference. The channel
browser defaults to Parameters without changing selected polling channels, with
cyan/amber/purple accents for parameters/switches/external sensors in both themes.
These changes are not installed into the running qualification image or published.

The header also has a separate Load Profile action for standard logger XML;
Import channel setup remains the `.rr2logger` envelope workflow. XML loading
reviews compatible selections, units, and unavailable IDs before replacement,
leaves the source untouched, and does not connect. Unavailable selections are
explicitly omitted in this first UI path; reload the original profile after
identification/discovery to include newly available channels. Automatic pending-ID
restoration is not implemented by this action. Profile/setup failures now show
an owned error dialog as well as status text, including the disconnected-state
requirement. Targeted tests cover cancellation, stale review, invalid profiles,
selection order, original-file preservation, and the header at 900/1024/1380px.

An elapsed recording display is implemented locally beside Start/Stop. Its
monotonic timer belongs to the file recorder, survives view changes, freezes on
stop/failure and resets only for a new successful capture. It does not alter CSV
timestamps or report sample coverage. Core tests and targeted desktop UI tests
passed after this addition. No running image was replaced. Logger Setup now has
a Recording tab with the filename prefix, Fast Polling, vehicle-switch recording,
absolute CSV time and the shared US numeric-format preference (restart required).
Capture changes require a disconnected runtime; definition/module support is
checked before persistence. Save failures restore the previous capture settings.
The channel-sidebar visibility control is now on the workspace bar with explicit
Show Channels / Hide Channels wording.

The fullscreen overlay now has separate Start recording and Stop recording
buttons plus elapsed time, using the existing session commands. Availability
follows live/recording state and pending commands; opening or exiting the display
does not start or stop capture. Assign channel now sits beside Cancel in the
channel dialog footer. Eight targeted gauge-display/header tests passed, including
state gating, unchanged polling selection, footer placement and overlay timeout.
These changes remain local, not installed in the test image.

The first-run duplicate definition prompt is fixed locally: each ROM load
snapshots the current configured definition list instead of retaining the list
from construction. A replacement-list regression test is included.

Dashboard keeps mixed live-data tiles (gauge, value, trend, alarm), with type and
size now in each tile's Customize menu rather than a separate selected-tile panel.
Gauge Display owns the 1–6-gauge layout, channel assignment, default/per-channel
style and limits/alerts controls; fullscreen remains its presentation mode.
Both views share channel-specific styles and limits and the existing logger feed.
Core unit tests passed. The desktop suite passed 308 tests with two optional
skips; the subsequent setup-layout checks also passed after increasing the
default dialog height and checking the actual scroll viewport for clipping.
Connection and Recording tabs were visually checked from isolated test captures.
No app image, installed application or public release was replaced.

The original-control comparison is not a claim of complete desktop parity.
Remaining workflows to review include native XML Save/Save As/Reload Profile,
the original serial-port auto-refresh and ELM discovery controls, plugin setup,
logger-specific debugging controls and diagnostic-tool entry points. Do not add
nonfunctional toggles for workflows the new runtime does not implement.

### Remaining qualification and development

Production adapter additions remain behind the earlier reliability and analysis
work. The Bluetooth transport foundation is isolated from normal logging;
hardware tests remain pending until a device or vehicle is available.

1. Complete cross-platform acceptance of the updated desktop definition installer.
   Local changes now isolate rollback snapshots, validate frozen bytes off-screen,
   and bind installation to the initiating Swing logger/configuration. Stale
   completion cannot enter the file/settings commit. Linux diagnostic-image tests
   now cover KDE and fallback pickers, confirmation, cancellation, validation and
   shutdown. Physical desktop and Windows/macOS acceptance remain pending.
   See the [lifecycle follow-up](DIMEMOD_CACHE_LIFECYCLE.md#next-contract-work).
2. Bind desktop DimeMod cached addresses to verified ECU/module/session identity.
   Diagnostic reads now compare a fresh ECU identification reply before reading
   cached runtime addresses. Full firmware identity and reconnect-cache binding
   are still open; matching identification bytes alone do not establish either.
   Preserve the distinction between runtime reads and discovery negotiation;
   reconnect must not silently increase write-based discovery attempts.
   The [standard diagnostic reader](DESKTOP_DIAGNOSTIC_READS.md) now handles
   shortened/reordered definitions and checks reply completeness between batches.
   Its synthetic protocol checks do not replace vehicle acceptance.
   The retained Read Codes action now has an [owned background workflow](DESKTOP_ASYNC_DIAGNOSTICS.md)
   with cancellation, captured adapter settings and success-only optional resume.
   Saved diagnostic CSVs and images now include both standard and DimeMod results.
   Other retained diagnostic tools still need a separate responsiveness review.
3. Continue analysis and fork-integration work, including reviewed injector
   transfer and external sensor support. A fitted injector intercept is not a
   voltage-dependent latency curve; no automatic calibration transfer is planned.
4. Qualify the Android changes in parked-vehicle sessions: channel availability,
   DimeMod discovery, saved profiles, recording exports, background capture and
   reconnect behavior. Check portrait/landscape usability on physical devices.
5. Complete physical acceptance of the candidate: same-key Android upgrades,
   desktop launch/shutdown, Steam Deck control sizing and macOS packages.
   Address findings in the next numeric patch release.
6. Resume the [adapter compatibility roadmap](ADAPTER_COMPATIBILITY_ROADMAP.md)
   after the earlier work, beginning with the existing
   [desktop ELM/OBDLink hardware test](ELM_IN_CAR_TEST.md). Android ELM support
   and enhanced SSM/MUT-II over ELM are not yet available.
7. Review transmission editing and diagnostics integration using the
   [5EAT, Atlas and ecuEdit findings](TCU_ATLAS_ECUEDIT_REVIEW_2026-09-08.md).
   Bundled vehicle-specific TCU editing and expanded module diagnostics remain
   future work, not current application capabilities.

## Existing features and documentation

The application includes 25 gauge styles, independent 1–6-gauge layouts,
per-channel style selection and mounted fullscreen controls. Android background
recording and reviewed recording recovery preserve the session independently of
the current screen. Desktop analysis includes reusable filter setups, curve
review, reviewed MAF-table transfer, log-to-map tracing, binned analysis and
saved-run comparisons. See the [documentation index](README.md) for guides and
the dated audit records for their verification scope.

Production flashing and live tuning are unavailable. They require
target-specific protocol work and bench qualification before vehicle use.
Automated tests do not replace physical adapter and vehicle testing.
Definition-installer changes are tracked separately from this release work.
See the [local reliability checkpoint](DESKTOP_RELIABILITY_CHECKPOINT_2026-09-08.md)
for installer, backup-profile, diagnostic-read, compact analysis and build
consistency checks after 1.1.8. A fresh Linux image passed normal launch/shutdown;
Windows/macOS and physical-device acceptance remain outstanding.
