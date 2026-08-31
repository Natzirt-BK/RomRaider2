# RomRaider2 flashing architecture

## Purpose and boundaries

RomRaider2 is intended to become a complete tuning suite in which editing,
logging, live data, diagnostics, ECU reading, and validated ECU writing share
one ROM workspace. Flashing is a RomRaider core capability; Swing is only a
client of that capability.

The implementation must be original, auditable, and based on documented or
independently validated protocol behavior. RomRaider2 must not copy,
decompile, bundle, or depend on proprietary EcuFlash code. Existing RomRaider
communications and J2534/OpenPort support may be reused behind clean transport
interfaces where technically appropriate.

Before implementing a major protocol, ECU family, checksum, transport, kernel,
or recovery path, review compatible open-source implementations first. Reuse
requires license and provenance review, retained attribution, correctness
review, and clean adaptation behind these contracts. Candidate and adopted
work is tracked in `OPEN_SOURCE_PROVENANCE.md`; an entry marked Candidate is not
permission to import its code.

Subaru is the first implementation priority. Read support may ship before
write support. A protocol must not advertise write, verify, or recovery
capability until that behavior is understood and validated for the applicable
ECU family.

## Layering

```text
Modern UI
  editor · logger · live data · dashboards · flash workflow · settings
                              |
                              v
Application services
  workspace · connection coordination · ROM workflow · flash orchestration
                              |
                              v
RomRaider core
  ROMs · definitions · checksums · logging · diagnostics · flashing
                              |
                              v
Transport adapters
  J2534/OpenPort · serial/K-line · CAN/ISO-TP · test transports
```

Swing components may issue commands and observe immutable state, progress, and
results. They must not contain protocol commands, erase/program loops, checksum
logic, device polling, sleeps, or recovery behavior.

## Core contracts

The flashing package will use explicit contracts with no UI dependencies:

- `FlashManager` selects a compatible registered protocol, coordinates
  preflight, owns background execution, and publishes session events.
- `FlashProtocol` describes one ECU family/protocol implementation and creates
  sessions. Protocol selection must be explicit and diagnosable.
- `FlashSession` owns one read, write, verify, or recovery attempt and its
  resources. Sessions have a monotonic state machine and a unique identifier.
- `FlashDevice` represents an opened transport/interface without exposing
  Swing or vendor-specific UI objects.
- `FlashCapabilities` explicitly advertises `canRead`, `canWrite`, `canVerify`,
  and `canRecover` for the selected target and device.
- `FlashProgress` reports a real state, message, and optional measured work
  units. A percentage exists only when the backend knows completed and total
  work; the UI otherwise shows indeterminate progress.
- `FlashResult` records the terminal state, operation result, failure point,
  protocol/device identity, and diagnostic-log reference.
- `FlashPreflight` returns individual pass, fail, or unavailable checks rather
  than one unexplained boolean.

Protocol implementations live below these contracts, for example under a
Subaru-specific package grouped by ECU family and transport. Protocol code must
not assume that all Subaru ECUs share programming commands or memory layouts.

## Capability and safety rules

Capabilities are positive declarations, not inferred defaults. The absence of
a capability keeps the corresponding command disabled. In particular:

- Reading may be enabled independently of writing.
- Writing requires a validated erase/program sequence and compatible memory
  layout.
- Verify requires a known verification strategy, not merely a successful
  transport response.
- Recovery requires a documented, tested recovery entry path.
- A generic J2534 connection does not imply that an ECU is flashable.

Pressing Write ECU begins preflight; it never immediately erases or programs.
The preflight model is extensible and is expected to include:

- interface connected and exclusively available;
- ECU communications and stable identification;
- supported ECU, protocol, and operation;
- expected ROM size and memory layout;
- ROM/ECU compatibility where determinable;
- checksum validity and file integrity;
- battery voltage when a trustworthy reading is available;
- required kernel, security, or programming prerequisites;
- recovery limitations acknowledged before a risky operation.

An unavailable mandatory check fails preflight. The UI may explain the failure
but may not silently waive it. Any future expert override must be explicit,
logged, narrowly scoped, and prohibited for invariants that would make the
operation unsafe.

## Session state model

A session reports actual backend states. Initial states include:

```text
Created
Preflight
Connecting
Identifying ECU
Entering programming mode
Uploading kernel
Reading
Erasing
Programming
Verifying
Resetting ECU
Reconnecting
Completed
Failed
Recovery required
Cancelled
```

Protocols may use more detailed internal substates, but user-facing state must
remain truthful. State transitions, ECU responses, retry decisions, byte
ranges, timing, and failures are written to the diagnostic session log. The
normal UI receives a concise message and real measured progress where known.

## Threading and cancellation

`FlashManager` executes sessions on a dedicated bounded executor. Device I/O,
timeouts, checksum work, and file operations never run on the Swing event
dispatch thread. UI observers are dispatched through a UI adapter that posts
immutable snapshots to the event thread.

Cancellation is state-aware. It may be accepted during safe phases such as
connection or reading and rejected during unsafe erase/program windows. Closing
a window does not abandon a live session. Application shutdown must surface an
active-session warning and follow the backend's safe shutdown decision.

## Diagnostics and recovery

Every session produces two information levels:

1. concise status, actionable errors, and real progress for normal users;
2. detailed timestamped protocol/session logging for development and support.

A failure result identifies the last completed state, current operation,
protocol, device, ECU identity if known, affected address/range when safe to
record, root exception, and whether recovery may be required. Sensitive values
must be redacted deliberately rather than by suppressing useful diagnostics.

Recovery is part of protocol design from the beginning. Even before recovery
is implemented, protocols declare the known recovery limitation, preserve the
session evidence needed to diagnose an interruption, and never advertise
`canRecover` speculatively.

## Integrated ROM workflow

The application service layer will coordinate this eventual flow:

```text
Connect interface
  -> Detect ECU
  -> Identify ECU/ROM where supported
  -> Read ECU
  -> Validate the read result
  -> Open it in the existing ROM workspace
  -> Edit, compare, log, and trace live cells
  -> Validate the selected ROM for the connected ECU
  -> Run write preflight
  -> Write and verify
  -> Reset and reconnect
```

The open ROM remains the shared document for editor, logger, comparison, live
trace, and flash commands. Connection identity and open-ROM identity are
separate states until compatibility is established; the UI must not imply a
match merely because both exist.

## Incremental delivery

1. Establish contracts, deterministic mock transports, and state-machine tests.
2. Survey and record proven compatible open-source work for the targeted ECU
   family, transport, kernel, checksum, and recovery path.
3. Add connection/device discovery without flash capability assumptions.
4. Implement and validate Subaru identification and read-only protocols.
5. Integrate read results into the ROM workspace and definition/checksum path.
6. Add write preflight and offline protocol simulations.
7. Enable write per explicitly validated ECU family only after controlled
   hardware testing of erase, program, verify, reset, failure, and recovery
   behavior.

## Current implementation status

The first core-contract milestone is present under `com.romraider.flash`:

- immutable target, request, capability, preflight, progress, and result models;
- explicit operation and session-state enums;
- a UI-independent protocol/device/session boundary;
- a guarded base session with state-aware cancellation;
- a bounded background manager that refuses unadvertised operations and failed
  preflight before creating a session;
- deterministic fake-device/session tests on the supported Java 21 runtime.

No production ECU protocol, command, security algorithm, RAM kernel, flash
layout, erase/program routine, or recovery implementation is included or
enabled by this milestone. Read ECU and Write ECU remain unavailable in the UI
until a separately researched and validated protocol positively advertises the
relevant capability.

The modern toolbar may reserve Read ECU and Write ECU commands, but availability
must come from backend capabilities and preflight state. Until a compatible
validated protocol exists, those commands remain unavailable and explain why.
