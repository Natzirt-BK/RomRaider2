# Evo MUT-II connection timeout investigation

September 6, 2026. Initial source inspected at `946b0717`. The diagnosis below
records that checkpoint; the subsequent 1.1.4 implementation is documented at
the end. No vehicle access or public release replacement was performed.

## Report and confirmed error path

The owner reports Android USB permission granted for OpenPort, followed by
“The ECU did not answer the read-only MUT2 request.” Ignition position, installed
app version, and whether any live values appeared before the error are still
unconfirmed. This report is not a successful Evo hardware qualification.
The owner subsequently confirmed the same error on retry and specifically
suggested a five-baud wake-up before fast polling. The repeated error does not
by itself resolve the outstanding ignition, version or failure-stage questions.

`OpenPortUsbTransport` throws that error when its 2,500 ms vehicle-response
deadline expires without a completed frame from `OpenPortKLineFrameDecoder`.
The wording does **not** establish that zero USB bytes arrived: incomplete or
unrecognized framing can also reach that deadline.

Before this point, opening the transport requires firmware identification and
adapter preparation acknowledgements. Opening MUT2 then requires channel,
timing/format and pass-filter acknowledgements. USB permission alone proves none
of those stages, but reaching this specific exception means execution passed
their checks for that request.

`ReadOnlyLoggerSession.run()` calls `identifyEcu()` before resolving selected
channels and beginning the logging cycle. For MUT2, identification sends battery
PID `0x14`, independently of the loaded parameter IDs. If the error occurs at
startup before identification, changing channel count or conversion formulas
cannot repair that probe. The same timeout is also used for subsequent reads;
the message alone does not identify which request failed.

## Startup evidence and its limits

At the initial checkpoint, Android opens ISO9141 without adapter checksum at 15,625 baud, sets
timing/8N1/filtering, and directly polls the PID. It has no diagnostic-entry line
sequence. The desktop `MUT2LoggerConnection.open()` is also empty; its comment
claims no five-baud or fast-init sequence is required. That comment is not
independent evidence of compatibility with this Evo.

An independent implementation supplies contrary evidence to a blanket
no-startup assumption: [libmut's `mut_init.c`](https://github.com/harshadura/libmut/blob/7cd3f1da9bea00652f0ee9dfd40258882a7080f7/libmut/mut_init.c)
sets serial control lines, waits 1,800 ms, changes a line, waits another 500 ms,
then changes RTS before probing RPM. Its helper named `set_break` actually
changes DTR through POSIX ioctls; it is not a portable OpenPort 2.0 command.
The [author's project page](https://www.cs.unm.edu/~donour/cars/libmut/)
describes FTDI-backed OpenPort support. Neither source establishes the electrical
mapping or required startup for the owner's OP2/Evo combination.

The [NikolaKozina J2534 driver](https://github.com/NikolaKozina/j2534/blob/master/j2534/j2534.c)
used as a framing reference returns not-supported from
`PassThruSetProgrammingVoltage`. Its K-line decoder accumulates normal data until
a receive-end indication, consistent with our decoder's completion requirement.
This comparison establishes neither a missing OP2 initialization command nor
that the phone received correctly framed ECU data.

Working hypothesis: missing diagnostic-entry initialization is a credible
software cause, not a confirmed diagnosis. Incorrect ignition state, connection
conditions and unobserved receive framing remain alternatives. Do not substitute
Subaru SSM qualification or synthetic MUT2 responses for Evo connection evidence.

## Next discriminating evidence

1. Confirm ignition fully ON (not ACC), installed numeric version, and whether
   the failure precedes all live values/identification.
2. Obtain a user-authorized diagnostic capture distinguishing adapter setup,
   initial PID probe and subsequent polling, including received packet framing.
   Do not initiate discovery or vehicle access automatically.
3. Establish the OP2-specific diagnostic-entry sequence from authoritative
   documentation or a known-working, read-only logging trace before implementing
   it. Legacy FTDI control-line calls cannot simply be copied.
4. If a transport change is justified, verify command ordering, acknowledgement
   handling, cancellation and cleanup offline, preserve SSM behavior, then seek
   separate parked Evo acceptance. No flash voltages, fault clearing, resets,
   ECU writes or manual pin-bridging instructions belong in this diagnosis.

The delivered `88780008_OpenPort2_MUTII_logcfg.txt` remains unchanged. Its importer
and conversion checks establish file compatibility, not successful ECU startup.

## Installed OpenPort DLL follow-up

An offline retry successfully loaded the installed `op20pt32.dll` in a fresh
Wine prefix inside Bubblewrap with private PID/network/device namespaces. The
loader calls only `LoadLibrary`, export-address lookup, memory inspection and
`FreeLibrary`; it calls no PassThru function. The recorded `/dev` contains only
synthetic standard devices, not USB/serial devices. No ROM, host Wine prefix,
network or native J2534 bridge is mounted. The loader and shell wrapper are
`Evo-Definitions/tools/inspect_openport_dll.c` and `probe_openport_dll.sh`.
The C loader builds with `-Wall -Wextra -Werror`; shell syntax and the isolated
probe pass. This is DLL inspection, not adapter-command execution.

Pinned installed DLL SHA-256:
`f432084801762d919a3c31974616e097562424470003edc4f4fb843df34103cf`.
The successful capture, `openport-memory.bin`, is kept locally and is not distributed.
Its SHA-256 is
`37f5095eb628ea0949f6ed4b0165a7aba8dbd00f0772d4453ac9534e35d2e9fe`;
image base `0x77640000`, size `0x458000`, no unreadable bytes. This is the hash
of that particular relocated runtime capture, not a stable hash across loads.

Disassembly establishes the following path (addresses below are image-relative
RVAs, not ECU addresses):

- Export `PassThruIoctl` is at `0x4E10`. Its channel path calls `0x1AC90`.
- The dispatch table at `0x1ADC0` maps IOCTL 4 (`FIVE_BAUD_INIT`) to `0x1ACE3`,
  which calls the implementation at `0x16C30`.
- That implementation requires one input byte and non-null input/output buffers.
  It allocates a request identifier using `0x1A640`, a counter wrapping from
  `0xFFFF` to 1.
- At `0x16CA6` it supplies command letter `w` to the format string at `0x6551C`:
  `at%c%d %d %u\r\n`. The other arguments are the mapped protocol number,
  input address byte and request identifier. For ISO9141 the mapped number is 3.
  Thus this installed driver constructs `atw3 <address> <request-id>\r\n`,
  rather than the two-field command in the experimental reference below.
- It queues that command, waits through `0x1A7B0` with 5,000 ms, and on success
  copies two returned bytes to the output and sets its length to two. The reply
  parser follow-up below resolves its field placement, but not the expected
  keyword values for this Evo.

The [experimental driver reference](https://github.com/Aiden-korbs/openport2-winarm-j2534/blob/fd42c8e29e46b8e21bb0b7e821ec24a667a8c1f3/j2534/j2534.c)
constructs `atw<channel> <address>\r\n` and parses an `arw` response. Its README
explicitly calls five-baud support experimental. The installed implementation
shows why copying that command/parser directly would be premature: it supplies
an additional request identifier and uses its common reply machinery.

This establishes that the installed OP2 driver implements a startup operation
missing from Android's initial path. It does **not** by itself establish the correct
Evo engine address, diagnostic-pin policy, timing configuration, expected reply
keyword values, or the cause of the owner's particular timeout. At this diagnosis
checkpoint no application transport changes or new APK had been made.

### Startup reply and correlation follow-up

Further disassembly of the same pinned capture identifies the receive side:

- The receive loop at `0x184B5` checks `a`, then `r`, then dispatches on the third
  character using the byte map at `0x1907C` and target table at `0x19044`.
  Character `w` selects index 11 and branch `0x18EA3`.
- That branch reads the remainder through a newline, then parses four decimal
  fields with `%d %d %d %u` at `0x65310`. All four conversions must succeed.
- The fourth field becomes the reply record's request identifier; the second
  and third fields become its two result bytes. The first integer is parsed but
  not stored in this branch. This supports the successful-reply shape
  `arw<first-integer> <keyword-1> <keyword-2> <request-id>`; the first integer's
  channel interpretation is not proven by this parser alone.
- The branch marks the reply successful and enqueues it through `0x1A6D0`.
  `0x1A7B0` and its lookup helper `0x1A660` match the outstanding request ID,
  copy the matching record, and remove it. Expiration yields status 9; the
  disconnected path yields status 8. The caller uses only successful replies
  to populate its two-byte output.

This is stronger than looking for an arbitrary `arw` prefix or accepting any two
numbers: the installed driver correlates the startup reply with its command.
The original Android control-response parser has no corresponding startup
operation at all. Increasing its 2,500 ms PID-response deadline would not add
that exchange. Correct Evo initialization still requires verification of entry
conditions and keyword policy; no hardware probe was introduced by this analysis.

## 1.1.4 Android implementation and qualification

The Android transport now runs this fixed sequence after channel setup and
before the battery PID probe. Each command has a nonzero transaction identifier;
only the matching acknowledgement advances startup.

1. `ats3 33 3 <id>` selects five-baud mode 3 (no inverse keyword/address exchange).
2. `atv 1 -2 <id>` grounds diagnostic pin 1.
3. `atw3 0 <id>` requests five-baud engine initialization, waiting up to five
   seconds for `arw3 <keyword-1> <keyword-2> <id>`.
4. `ats3 1 15625 <id>` restores the MUT-II polling baud rate after autobaud.
5. Stop, cancellation or failed startup sends `atv 1 -1 <id>` to release pin 1.

The [adapter vendor's initialization documentation](https://www.scandoc.net/en/develop/j2534/pt_ioctl.html)
describes mode 3, autobaud and the two-byte result. Engine address zero is inferred
from the legacy MUT initialization sequence, not confirmed by this Evo. No exact
keyword values are guessed. The
[Tactrix header](https://github.com/jasongaunt/can-opener/blob/master/CANlogger/common/j2534_tactrix.h)
defines ground as `0xFFFFFFFE` and off as `0xFFFFFFFF`, not zero volts.
In the pinned DLL, `PassThruSetProgrammingVoltage` at RVA `0x57E0` calls
`0x19A90`, which uses `at%c %d %d %u` at `0x654D4`; this explains the signed
`-2` and `-1` arguments. Success and error handlers correlate `aro <id>` and
`are<error> <id>`. No arbitrary voltage/pin API is exposed by the application.

The parser skips length-framed binary packets before examining control replies,
bounds received bytes and tolerates fragmentation. Failed startup never proceeds
to PID polling. Cleanup is attempted even when cancellation is set or the ground
acknowledgement was lost. Failed release remains retryable and is reported;
closing the transport still closes USB resources. Subaru SSM does not use this
Mitsubishi sequence. Desktop MUT-II startup is unchanged by this patch.

Validation: all 81 Android JVM tests pass, including 16 new startup/parser and
production-transport tests. They cover fragmented replies, stale IDs, delayed
initialization, adapter errors, short writes, cancellation, release failure and
SSM isolation. Shared-core checks, Android lint, and main/diagnostic APK builds
also pass. These use synthetic USB input, not an ECU or adapter. The first
startup tests failed to compile before the implementation existed, then passed.

Remaining gate: a user-initiated, parked Evo test with ignition fully ON (not
ACC), engine initially off, MUT2 selected and a small channel selection. Record
the complete startup error if it fails. The existing logger definition is
unchanged; ECU identification remains a plausible battery response, not a ROM
identity check. This candidate is not proof that the reported timeout is fixed.
