# Active work plan

Updated September 13, 2026.

The current candidate is [1.1.11 RC1](RELEASE_1_1_11.md), published for Android,
Windows, Linux, SteamOS Desktop Mode and macOS. The
[qualification record](RELEASE_1_1_11_QUALIFICATION.md) identifies automated
checks and outstanding physical-device tests.

## Shipped in 1.1.9

- Android: a workspace menu ordered Logger, Gauges, Review, Editor; bottom
  START/STOP buttons; pressed and disabled feedback; and a Save CSV prompt after
  recording. The internal recovery copy remains available. See
  [Logger layout](ANDROID_LOGGER_LAYOUT.md) and [CSV review](ANDROID_CSV_REVIEW.md).
- Windows/Linux desktop logger: linked Protocol/Transport/Module selectors,
  unclipped setup controls, channel accents, Load Profile for standard logger XML,
  recording preferences and elapsed time. Import channel setup remains the
  separate `.rr2logger` workflow. XML loading reviews compatible selections before
  replacement and does not connect; unavailable IDs must be reloaded after
  identification/discovery, not silently restored.
- Desktop gauges: fullscreen Start/Stop recording and elapsed time, channel
  assignment beside Cancel, and clearer ownership of Dashboard tile controls
  versus Gauge Display's 1–6-gauge layout and styles. View changes do not change
  polling selection or start/stop capture.
- Desktop reliability: refreshed definition selection on ROM load, profile-backup
  fixes, isolated definition-installer rollback, fresh identification checks before
  diagnostic reads and cancellable background Read Codes. See
  [diagnostic reads](DESKTOP_DIAGNOSTIC_READS.md) and
  [background diagnostics](DESKTOP_ASYNC_DIAGNOSTICS.md).

Platform interfaces differ; not every desktop control is present in macOS's
Compose/Swing interface. Dated checkpoints record development stages, not the
current publication status.

## Next priorities

1.1.11 package qualification and publication are complete for confirmed SSM
channel filtering, side-by-side startup, restored editor tools and the DTC-tab fix.
Keep vehicle tests together in the acceptance session below.

Next editor feature: [Android ROM checksum support](ANDROID_ROM_CHECKSUMS.md).
Definition-backed Subaru correction is implemented and locally tested for the
next update, including desktop algorithm parity, editor emulator checks, malformed
ranges, disabled checksums and failed-save preservation. Physical-phone provider
acceptance and the exact current Forester ROM remain to be checked. Unsupported
schemes remain explicitly unsupported. The packaged 1.1.11 Android editor still
requires desktop checksum validation; correction is not part of that download.

Version 1.1.10 includes [desktop XML Save/Save As/Reload](DESKTOP_LOGGER_PROFILES.md)
with named-file ownership separate from automatic recovery. Android includes
matching green START/red STOP controls in Gauges and fullscreen, sharing
Logger's session and disabled states. Native file-picker and physical-device
acceptance remains pending despite the completed automated/package checks.

Desktop DimeMod now verifies full identification and discovery
metadata on reconnect and on the polling connection, without turning automatic
retry/cache rejection into extra discovery writes. See the
[reconnect contract](DESKTOP_DIMEMOD_RECONNECT.md). Physical acceptance remains
open; this is observed metadata verification, not whole-firmware authentication.

In-car tests are deferred into the single acceptance session below. Work that
can be verified offline continues first; deferral does not count as a test pass.
Custom ECU firmware/ROM-patch development is on hold until RR2 is stable.

Version 1.1.10 includes [serial-port dropdowns and troubleshooting
controls](DESKTOP_LOGGER_USABILITY.md). The same
review records the remaining retained-tool lifecycle issues; no new ECU-write
controls are exposed by those usability changes.

The release also includes fullscreen/maximized in-window Logger Setup,
a definition-backed recording-switch selector, a main-window filename prefix
and two-decimal CSV values on desktop and Android. Vehicle-switch operation
and physical window-manager behavior belong to the
consolidated acceptance session below.

1. Finish remaining desktop reliability before adding adapter transports:
   plugin setup and retained diagnostic-tool entry points. Review retained diagnostic
   tools for responsiveness. Do not add controls for unimplemented workflows.
2. Continue desktop definition-installer acceptance across native pickers and
   platforms. Automated tests cover isolated rollback snapshots, frozen-byte
   validation, initiating logger/configuration ownership and stale completion.
   Linux diagnostic-image checks cover KDE and fallback pickers, confirmation,
   cancellation, validation and shutdown. Physical acceptance remains open.
3. Run offline regression and package checks, plus device-only acceptance where
   equipment is available: phone button feel, portrait/landscape scaling,
   document-provider exports, Steam Deck control sizing and interactive
   Windows/macOS operation. Signed Android upgrade and export/reopen checks
   already passed in an isolated emulator; this does not replace physical UI
   acceptance. Prepare, but do not call untested packages vehicle-qualified.
4. Complete one consolidated in-car acceptance session, when available:
   - Forester desktop: identification, DimeMod channels, automatic reconnect,
     explicit Disconnect/Connect, selection/profile retention and fresh CSV data.
   - Forester Android: ECU-specific channels, profile edits, START/STOP from Logger
     and Gauges, fullscreen exit, background recording, save/export and reconnect.
   - Exercise unplug/stop/restart and review saved timestamps, units, missing
     values and elapsed time. Use parked tests; do not operate controls driving.
   - Run the separate [ELM/OBDLink test](ELM_IN_CAR_TEST.md) only if the appropriate
     adapter is available. OpenPort results do not qualify ELM or KKL hardware.
   - Record any platform/adapter checks not performed as outstanding. Fix findings,
     rerun the relevant gates, then publish the next numeric patch.
5. Continue analysis and fork integration, including reviewed injector transfer
   and external sensors. A fitted injector intercept is not a voltage-dependent
   latency curve; automatic calibration transfer is not planned.
6. Resume the [adapter roadmap](ADAPTER_COMPATIBILITY_ROADMAP.md) after the earlier
   reliability work. Begin with the existing
   [desktop ELM/OBDLink hardware test](ELM_IN_CAR_TEST.md). Android OBDLink LX/MX
   Bluetooth and KKL USB requests require actual transport/protocol qualification.
   The [Bluetooth link foundation](ANDROID_BLUETOOTH_LINK.md) has twelve synthetic
   stream tests, but device selection, permissions and logger-service integration
   remain unimplemented. It is not a selectable adapter. Android ELM support and
   enhanced SSM/MUT-II over ELM are not available.
7. Review transmission editing and expanded diagnostics using the
   [5EAT, Atlas and ecuEdit findings](TCU_ATLAS_ECUEDIT_REVIEW_2026-09-08.md).
   Bundled vehicle-specific TCU editing and expanded module diagnostics remain
   future work, not current application capabilities.

## Existing features and limits

Android includes automatic DimeMod discovery, ECU-specific channel selection,
standard XML profiles, separate CSV review and Close Log File. The visual gauge
demo is independent of logging; the offline logger was removed. See
[channel discovery](ANDROID_CHANNEL_CATALOG.md),
[profiles](ANDROID_LOGGER_PROFILES.md) and [recording recovery](ANDROID_RECORDING_RECOVERY.md).

The application includes 25 gauge styles, independent 1–6-gauge layouts,
per-channel styles and mounted fullscreen controls. Android background recording
preserves the session independently of the current screen. Desktop analysis
includes reusable filters, curve review, reviewed MAF-table transfer, log-to-map
tracing, binned analysis and saved-run comparisons. See the
[documentation index](README.md) for platform-specific guides.

Production flashing and live tuning are unavailable. They require target-specific
protocol work and bench qualification before vehicle use. Automated tests do not
replace physical adapter and vehicle testing; software capability selectors do
not establish compatibility with every adapter or vehicle.
