# Android MUT-II / OpenPort audit — 2026-09-05

Scope: Android preview3, the portable logger core, OpenPort USB transport,
foreground session lifecycle, and CSV retention. No ECU writing, release/pin
promotion, or private definition publication. This is implementation and
synthetic-test evidence, **not connected-car qualification**.

## Implemented and reviewed

| Area | Result |
| --- | --- |
| Protocol choice | Explicit SSM/MUT2 selection; changing protocol clears incompatible setup. No guessed protocol probing. |
| MUT-II transport | ISO9141/no adapter checksum, 15625 baud, fixed 8N1/timing, receive pass filter, loopback disabled. One-byte PID requests. |
| Identification | Plausibility check of battery PID 0x14 returns `MUT2_GENERIC`, never an invented calibration ID. UI explains the limitation. |
| Response validation | One value or matching PID echo plus value; reject empty, wrong-echo and oversized replies. Separate TX-loopback packets ignored. |
| USB framing | Complete CRLF acknowledgement required, fragmented packets bounded, binary ECU payload not scanned as ASCII command errors. |
| Definitions | Bounded MUT2 logcfg importer; validates PID, duplicates, RPN syntax and finite scaling over every byte value. Unknown hardware/config options rejected. |
| Selection | Phone channel chooser or imported profile; existing SSM batching retained; MUT2 requests deduplicated to individual PIDs. All selections recorded, up to eight gauges displayed. |
| Stop/failure | Cancellation between PID reads, during adapter setup and receive slices; incomplete cycles not recorded; channel cleanup and recording flush on exit. |
| UI/session ownership | Stopped callback owns its immutable recording and generation; old import completions ignored. Adapter preparation cannot publish after Activity destruction. |
| Simulation isolation | Live logging, offline preview, and gauge demo cannot interleave live/simulated gauge data. New sessions clear old gauges. |
| Recording | Unique files in app-private storage, bounded recent memory, per-cycle flush, writer closed on stop. Recovery/export list survives process restart; starting another session does not delete earlier logs. |
| Safety | No ECU write/reset/clear/pin-voltage API added; no automatic resume/reconnect of vehicle polling. |

Wire setup was cross-checked against the existing desktop ISO9141/MUT2 code and
the pinned [NikolaKozina J2534 driver](https://github.com/NikolaKozina/j2534).
Existing BSD-3-Clause attribution remains in place. Android permission handling
follows the [Android USB host guide](https://developer.android.com/develop/connectivity/usb/host).

## Verification

- Existing portable-core checks plus 69 new deterministic MUT2 assertions pass.
- The private EVO logger configuration imports all **33 channels**. Each
  conversion was evaluated across raw bytes 0–255 (8,448 compatibility checks).
  The private input was only read and was not copied into this repository.
- Android JVM session regressions cover converted full cycles, stop-before-run,
  stop-during-read/identification, partial responses, detach/failure, protocol
  mismatch, legacy profiles, once-only execution, cleanup failure, and CSV
  retention. The long synthetic run records 40,000 values with ten retained in
  memory; it is **not an eight-hour elapsed-time or hardware soak**.
- `:shared-core:check :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
  is the local gate and is included in the platform-preview workflow.
- Final build, emulator and GitHub CI results will be recorded below once complete.

## Remaining gates / audit findings

1. **Hardware gate remains open:** no phone, OpenPort or ECU was connected for
   this implementation. Confirm firmware command compatibility, USB-C host/OTG,
   permission grant/deny/regrant, ignition-on response, scaling against known
   values, stop/detach/reconnect, and real sample rate on the parked EVO VIII/IX.
   Do not call Android MUT-II fully vehicle-qualified before that evidence.
2. Logging is foreground-only. Backgrounding, workspace changes and detach stop
   it. Physical-device sleep/USB power and rotation behavior still need testing.
3. All selected MUT2 PIDs run each cycle; standalone `priority` is not a weighted
   scheduler. No unsupported cooling/air-temperature curves or ambiguous units
   were guessed. Raw channels remain raw.
4. Calculated parameter dependencies, external serial sensors, CAN, transmission
   sessions, and flashing remain unavailable. This is not desktop feature parity.
5. Protocol/theme persist, but definitions and selected channels require reload
   after Activity/process recreation. Recovery recordings remain available.
6. Retained recordings consume app storage and are not automatically deleted.
   Export before clearing app data/uninstalling. Abrupt process/power loss can
   lose or truncate the current cycle; per-cycle flush is not an fsync guarantee.
7. The long-form CSV can retain more values than the current mobile CSV review
   parser's 250,000-value import limit. Large exports remain usable externally.
8. Physical UI/accessibility, document providers, debug-key upgrades and actual
   Android USB I/O are not certified by the JVM tests. The production transport
   is not exercised against a mocked Android `UsbDeviceConnection` in that suite.

Next acceptance procedure: [Android preview testing](ANDROID_PREVIEW_TESTING.md).
Desktop B1/B2 findings from the prior desktop audit remain deferred, unchanged.
