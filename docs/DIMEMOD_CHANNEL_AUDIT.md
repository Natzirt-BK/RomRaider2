# DimeMod channel/discovery audit

September 6, 2026. Repairs belong to **1.1.3 development source**; public 1.1.2
is unchanged. This is an offline audit of the retained desktop implementation,
not a vehicle test or a new Android discovery implementation.

## Reproduced channel defects

Synthetic discovery blocks and runtime flags reproduced four failures before
the repairs in [`DmInit`](../src/main/java/com/romraider/logger/ecu/comms/query/dimemod/DmInit.java):

| Finding | Repair |
| --- | --- |
| Runtime feature bits exposed channels whose discovery block was absent, leaving default addresses. | Knock-per-cylinder, MapSwitch, speed-density, ALS and valet channels require both their advertised block and active runtime bit. |
| Injector-flow and stoichiometric compensation channels were exposed without MAP_SWITCH metadata. | Require the address-bearing block. Preserve known compensation channels when switching is temporarily inactive; retain existing version/build boundaries. |
| Oil-input bits exposed zero-address channels on layouts older than 2.3. | Require a 2.3-or-later input layout as well as the corresponding input bit. |
| FFS external-trigger channels used the failsafe input bit. | DM916/DM917 now use the distinct FFS bit, independently of DM913/DM914. |

DM911 ethanol input remains independent of MapSwitch activation. The tests check
its version-specific address and the actual addresses/widths of DM017, DM019,
per-cylinder knock, speed-density, ALS and valet channels. Positive advertised
fixtures prevent the repair from simply disabling every dynamic channel.

`DmInitTest` covers ten test methods, including the retained discovery/error and
RAM-tune metadata checks, absent blocks, oil-layout versions, independent trigger
bits, seven compensation version/build boundaries, exact addresses and runtime
activation/deactivation. Fixtures contain generated addresses only; no private
ROM, profile, log or vehicle-specific runtime address was imported.

Local qualification: Ant Linux build and complete unit suite pass (the existing
native-J2534, optional CSV corpus and BMW XDF corpus checks remain skipped).
All ten focused DimeMod tests pass. Shared-core checks and the display-enabled
216 JavaFX / 35 Compose tests also pass. This is source/synthetic qualification,
not physical protocol or release-package acceptance.

## Discovery-response validation

The follow-up reader now validates discovery and runtime payloads through the
native protocol validators instead of copying unchecked frame offsets. K-line
headers, IDs, advertised frame lengths and checksums are checked; CAN uses its
native ID/type framing. Single-byte negotiation reads and normal write replies
must contain exactly one data byte. Runtime updates require the complete 14-byte
2.0 or 38-byte newer layout before mutating the cached discovery object.

The memory reader rejects zero progress, overlong chunks and a block that would
wrap the 24-bit address space. The discovery length is limited to the advertised
16-bit field, with at least four version-header bytes. Positive partial K-line
memory replies advance by their actual length; CAN address reads require one
result per requested address. The existing 96-byte memory/32-address CAN chunk
limits and interruption points remain. Only CAN's unsupported memory-command
construction selects the fallback, not arbitrary send/receive failures.

A block is returned only after all chunks validate. Unsupported major versions
remain visible as unsupported metadata, but no longer cause runtime reads at
default addresses. This is not a new wall-clock timeout or a complete validation
of the addresses contained inside a successfully received metadata block.

`SSMDmDiscoveryTest` adds twelve tests using native K-line/CAN framing and an
in-memory connection manager. They cover multi-chunk assembly, partial memory
progress, wrong IDs/types/checksums/lengths, zero-length and oversized payloads,
address boundaries, interruption, valid full handshakes, rejection before the
first negotiation write, late failure without publication, both runtime layouts,
and unsupported cached/newly discovered versions. No manager can open a device.
The twelve transport and ten channel tests pass together, as does the complete
Ant core suite and Linux build; the same three optional/native skips remain.
The shared checks and all 216 JavaFX / 35 Compose desktop tests also pass with
the updated reader; no physical protocol acceptance is inferred from them.

## Why Android discovery is still separate

The current desktop [`SSMLoggerConnection.dmInit`](../src/main/java/com/romraider/logger/ecu/comms/io/connection/SSMLoggerConnection.java)
is **not a write-free handshake**. When no cached discovery exists, it reads the
reset state, sends a write of `0xDE` to `0x000060`, expects `0xAD`, reads the
length/address response, and sends a write to `0x000000` to leave negotiation.
The fallback restores the saved reset state with another write. The normal
desktop [`QueryManagerImpl`](../src/main/java/com/romraider/logger/ecu/comms/manager/QueryManagerImpl.java)
calls this discovery after ECU identification when a DimeMod callback is present.
These are existing desktop behaviors, not newly enabled on hardware. The
synthetic handshake tests assert the same two successful negotiation writes
against an in-memory manager only.

Copying this sequence into Android's explicitly read-only logger would violate
that contract. This repair does not add write commands, run discovery on a real
adapter, or initiate production ECU writes. Cached discovery/runtime
reads are a distinct path, but the current mobile logger has no verified
discovery-origin/ECU-identity contract for importing those addresses.

## Remaining work

- Define and verify a genuinely read-only discovery source, or a separately
  authorized handshake with accurate UI wording; do not silently reuse the
  legacy write sequence under a read-only label.
- Continue malformed/truncated metadata, contained-address validation, cache
  identity and negotiation-cleanup audits before claiming robust dynamic
  discovery. The legacy CAN exception for a nonstandard negotiation-exit reply
  is retained; this pass does not invent new cleanup writes or prove all
  negotiation-error behavior safe. Frame/chunk checks do not validate every
  advertised metadata address.
- Portable signed/unsigned integer and float decoding already exists; the
  [typed-channel audit](PORTABLE_TYPED_CHANNELS.md) verifies definition-to-query
  behavior and native desktop comparisons. Runtime/version feature mapping and
  verified address provenance are still needed before claiming Android parity
  for DM019/DM911 and other generated channels.
- Keep external serial AEM input, injector-latency transfer and physical
  Forester/EVO/OpenPort acceptance as separate backlog items. No live-tuning or
  vehicle-write capability is established by these tests.
