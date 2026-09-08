# Logger adapter compatibility

September 8, 2026. Implementation roadmap, not a supported-device list.

## Objective

Expand read-only logging beyond OpenPort 2.0 on desktop and Android. Preserve
the existing CSV format, unit labels, channel profiles and gauge behavior.
Adapter connectivity, ECU protocol support and channel accuracy are separate
qualification gates. This work does not enable flashing, live tuning, actuator
commands or DTC clearing on new adapters.

## Current source audit

| Path | Existing implementation | Remaining work |
| --- | --- | --- |
| Android OpenPort USB | `OpenPortUsbTransport` implements the shared `ReadOnlyLoggerTransport`; SSM and MUT-II are available. The owner has reported successful sessions on both vehicles. | Preserve behavior and repeat regression tests; reports are not exhaustive hardware qualification. |
| Android adapter ownership | `MainActivity` and `ReadOnlyLoggingService` accept OpenPort-specific types and USB identity. The recording/session core already accepts `ReadOnlyLoggerTransport`. | Generalize discovery, permission, identity, disconnect and service ownership without weakening cleanup. |
| Portable protocols | `PortableLoggerProtocol` currently contains SSM and MUT2 only. | Add a distinct generic OBD-II request/response and channel layer; do not route OBD through SSM/MUT-II definitions. |
| Desktop serial | `ConnectionManagerFactory` can select `SerialConnectionManager`. | Surface adapter choice consistently in modern setup; verify compatible K-line cables, baud rates and echo behavior per protocol. |
| Desktop ELM | `ElmConnectionManager` and `ELMOBDLoggerConnection` provide a legacy OBD path. | Repair and test identification, command framing, response parsing, initialization, deadlines and cancellation before advertising broader support. |
| Desktop J2534 | `J2534LibraryLocator` inspects Windows driver registry entries, including both registry views; transport and bridge code already exist. | Audit modern setup selection, vendor/protocol capabilities, architecture matching and failure reporting; qualify actual drivers. |

Specific ELM audit findings to reproduce with tests:

- Initialization requires the reset banner to contain `ELM327`; an identity
  string alone should neither reject a compatible implementation nor prove
  its capabilities.
- ECU presence is inferred from space-separated response length instead of
  validating a positive Mode 01 PID 00 reply.
- `ElmConnection.write` uses platform-dependent `println` framing.
- The logger response parser searches for a matching PID token without a
  complete service/header/length check and assumes spaces between bytes.
- Request collection and error paths need review for stale queries after
  `BUS INIT`, `STOPPED` and `NO DATA` responses.

These are source findings, not evidence that a particular adapter has failed
on a vehicle.

## Delivery order

1. **Adapter selection and compatibility checks.** Separate host link (USB,
   Bluetooth Classic, BLE or TCP), adapter command family and ECU protocol.
   Show supported, unverified or unavailable combinations with a reason before
   connecting. Preserve OpenPort defaults and selected-device identity. Never
   treat a detected USB serial chip as proof that it is an automotive adapter.
2. **ELM327/OBDLink generic OBD-II logging.** Start with desktop serial and
   Android USB/Bluetooth Classic. Add a tested command/response core, supported
   PID discovery, responder selection, explicit units and per-channel support.
   Keep standard OBD channels distinct from manufacturer-specific channels.
3. **USB K-line cables.** Evaluate FTDI-based KKL/VAG-COM-style pass-through
   cables first for Subaru SSM. Add Android USB serial drivers and test exact
   baud configuration, echo handling, timeouts and detach. MUT-II requires its
   own initialization and 15,625-baud validation; it is not inherited from SSM.
4. **Additional desktop J2534 devices.** Reuse the existing abstraction and
   qualify vendor drivers by OS, architecture and protocol. A Windows J2534
   driver does not establish Android or Linux support.
5. **BLE and Wi-Fi variants.** Add model-specific BLE services/characteristics
   and bounded TCP sessions after the command core is stable. Do not assume
   Bluetooth Classic and BLE are interchangeable. Use an explicitly selected
   endpoint; do not scan arbitrary networks or guess vehicle protocols.
6. **Enhanced OBDLink SSM/MUT-II investigation.** Evaluate raw ISO framing,
   checksum/echo control, actual baud and startup timing against the exact
   model/firmware. Keep unsupported combinations disabled until validated.

The first implementation slice is the desktop ELM command/response test
harness and fixes for the findings above, alongside the adapter-selection
contract. Android integration should reuse tested protocol code instead of
copying the legacy parser.

## Manufacturer and driver evidence

- [OBDLink developer resources](https://www.obdlink.com/developers/) document
  its AT/ST interface. The [family programming manual](https://www.obdlink.com/frpm)
  describes raw ISO mode, configurable baud, actual-baud readback and checksum
  controls. This makes enhanced-protocol investigation plausible, not proven
  RR2 SSM/MUT-II compatibility. Record model and firmware; a branding or version
  string is not sufficient qualification.
- [USB serial for Android](https://github.com/mik3y/usb-serial-for-android)
  supplies drivers for FTDI, PL2303, CP210x, CH340/CH341 and CDC/ACM devices.
  Review and pin the dependency/license before adoption. Chip support alone
  does not establish K-line electrical support or usable timing.
- [Ross-Tech's legacy virtual COM port documentation](https://www.ross-tech.com/vag-com/usb/virtual-com-port.php)
  distinguishes older pass-through interfaces from current hardware.
  [HEX-V2](https://www.ross-tech.com/vcds/hex-v2.php) does not emulate a dumb
  serial interface. Do not advertise blanket “VAG-COM support.”
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
  require version-appropriate runtime permission handling. Ask only for
  permissions needed by the chosen link; keep USB-only use available.

## Acceptance gates

- Offline transcript tests: fragmented/coalesced replies, echo on/off,
  CR/LF variants, spacing, prompt boundaries, malformed hex, negative replies,
  multiple ECU responders, unsupported PIDs, bounded buffers and deadlines.
- Lifecycle tests: denied permission, cancellation during connection/read,
  unplug/radio loss, background recording, reconnect without duplicate workers,
  stale-response isolation and correct file-close/status behavior.
- Per-device evidence: adapter model, firmware, host OS/link, vehicle/ECU,
  selected protocol, channel count, measured sample rate, startup and recovery.
- Preserve RomRaider CSV headers/units and channel-to-gauge mappings. Compare
  a small set of known channels before enabling larger selections.
- Clearly distinguish implemented, bench-tested and vehicle-tested support.
  Synthetic tests do not qualify baud timing or vehicle compatibility. No
  hardware commands or new adapter integration were performed for this roadmap.
