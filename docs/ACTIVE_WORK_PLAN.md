# Active work plan

Updated September 9, 2026.

The current candidate is [1.1.9 RC1](RELEASE_1_1_9.md), available for Android,
Windows, Linux, SteamOS Desktop Mode and macOS. The
[qualification record](RELEASE_1_1_9_QUALIFICATION.md) identifies passed automated
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

1. Complete physical acceptance of 1.1.9: phone button feel, portrait/landscape
   scaling, document-provider exports, adapter/background/reconnect sessions,
   Steam Deck control sizing and interactive Windows/macOS operation. Signed
   Android upgrade and export/reopen checks passed in an isolated emulator.
   Address findings in the next numeric patch release.
2. Finish desktop definition-installer acceptance across native pickers and
   platforms. Automated tests cover isolated rollback snapshots, frozen-byte
   validation, initiating logger/configuration ownership and stale completion.
   Linux diagnostic-image checks cover KDE and fallback pickers, confirmation,
   cancellation, validation and shutdown. Physical acceptance remains open.
3. Bind desktop DimeMod cached addresses to verified ECU/module/session identity.
   Diagnostic reads now compare a fresh ECU identification reply before reading
   cached addresses; full firmware identity and reconnect-cache binding remain
   open. Matching identification bytes alone do not establish either. Keep
   runtime reads distinct from discovery negotiation; reconnect must not silently
   increase write-based discovery attempts. See the
   [cache lifecycle contract](DIMEMOD_CACHE_LIFECYCLE.md#next-contract-work).
4. Review remaining desktop logger parity: native XML Save/Save As/Reload Profile,
   serial-port refresh and ELM discovery, plugin setup, logger debugging and
   diagnostic-tool entry points. Other retained diagnostic tools need a separate
   responsiveness review. Do not add controls for unimplemented workflows.
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
