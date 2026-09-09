# Active work plan

Updated September 8, 2026.

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

1. Qualify the Android changes in parked-vehicle sessions: channel availability,
   DimeMod discovery, saved profiles, recording exports, background capture and
   reconnect behavior. Check portrait/landscape usability on physical devices.
2. Complete [desktop ELM/OBDLink hardware testing](ELM_IN_CAR_TEST.md), including
   adapter identification, supported standard PIDs and cancellation. Android ELM
   support and enhanced SSM/MUT-II over ELM are not yet available.
3. Complete physical acceptance of the candidate: same-key Android upgrades,
   desktop launch/shutdown, Steam Deck control sizing and macOS packages.
   Address findings in the next numeric patch release.
4. Continue the [adapter compatibility roadmap](ADAPTER_COMPATIBILITY_ROADMAP.md)
   using verified transport capabilities and identifiable hardware targets.
5. Review transmission editing and diagnostics integration using the
   [5EAT, Atlas and ecuEdit findings](TCU_ATLAS_ECUEDIT_REVIEW_2026-09-08.md).
   Bundled vehicle-specific TCU editing and expanded module diagnostics remain
   future work, not current application capabilities.
6. Continue analysis and fork-integration work, including reviewed injector
   transfer and external sensor support. A fitted injector intercept is not a
   voltage-dependent latency curve; no automatic calibration transfer is planned.

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
