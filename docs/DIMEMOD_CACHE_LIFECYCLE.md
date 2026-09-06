# DimeMod cache and owner lifecycle audit

September 6, 2026; 1.1.3 development source. No vehicle session was initiated.

## Closed-owner repair

The modern desktop runtime's ECU and DimeMod callbacks could still change
retained identity/metadata and the shared platform state after `close()`.
The channel-reload method already rejected a closed owner, but that guard ran
after the identity or platform state had been changed. In particular, a late
callback from an old workspace could overwrite a reopened workspace's DimeMod
status without changing its own channel catalog.

Both callback handlers now reject a closed owner before any state update.
They use the same runtime monitor as `close()`, so a callback waiting for closure
to finish also observes the closed state. This does not add connection commands,
change the discovery handshake, clear an open owner's cache or enable Android
discovery.

Four added tests use the real callbacks registered with the desktop query
manager, isolated definitions/profiles and generated ECU/DimeMod data. They
never start the controller or connect an external sensor. Three reproduced the
old failure: late ECU identification, late/forced DimeMod metadata, and old
callbacks after reopening a workspace. The fourth preserves positive behavior:
an open owner retains its same-ID cache and clears it when the ECU ID changes.

Qualification: all four new lifecycle tests and their complete 15-test setup
class pass. Ant Linux build/unit tests, shared-core checks and 35 Compose tests
pass. The full display-enabled JavaFX run is **not all green**: 225 of 226 pass;
the existing modal user-resize test observes width 1,920 instead of the requested
500. Its first-show/settled-fit boundary tests still pass. This separate native
resize failure remains under investigation; the lifecycle checkpoint does not
claim to resolve it or qualify a release.
The [window-placement follow-up](WINDOW_PLACEMENT_AUDIT.md#maximized-state-and-redundant-modal-fit)
subsequently separated remembered maximization from a redundant queued startup
fit, corrected the restore-before-resize test sequence, and removed the queued
modal fit. The failed lifecycle-checkpoint run remains recorded above.

## Read-codes runtime-only boundary

The retained Swing read-codes action previously checked the mutable owner cache
and looked it up again inside `dmInit`. Clearing it between those reads could
select the legacy discovery writes. Result decoding then looked up the owner
again, potentially displaying a different object's errors.

`LoggerConnection.readDmRuntime` now provides a separate read-only contract.
Unsupported implementations fail explicitly; the default never delegates to
`dmInit`. The SSM implementation requires supported cached metadata and a module,
copies the metadata into a new object, and reads only the existing runtime
addresses. Missing metadata cannot select discovery. The owner cache is not
mutated. Read-codes captures its cache once and uses the returned snapshot for
both the no-codes decision and decoded results. With no cache it continues the
standard DTC operation without attempting DimeMod discovery.

The shared runtime parser rejects wrong response types as failures, rather than
silently returning without a callback. All runtime data still requires valid
native framing and exactly 14 bytes for 2.0 or 38 bytes for later 2.x layouts.
Read failures propagate to the existing failed-operation path; interruption is
preserved there. The legacy initialization handshake itself is unchanged.

Synthetic qualification covers both native K-line/CAN framing and 2.0/2.3
metadata: a single A8 runtime read with no negotiation writes, all current and
memorized error values, independent snapshots, unchanged cached channels/state,
missing/unsupported inputs, invalid/truncated replies, and cancellation before
I/O. The production read-codes cache helper is also exercised with an owner
cache cleared or replaced during refresh, a missing cache, an unsupported
connection, and read/cancellation failures. Those helper tests do not construct
the Swing logger, open an adapter, or qualify its entire UI/connection lifecycle.

Qualification: 39 focused tests pass (17 native SSM discovery/runtime, four
read-codes cache-boundary, eight metadata and ten channel tests). The full Ant
Linux build/unit suite passes with its three existing optional/native skips.
Shared-core checks, all 246 display-enabled JavaFX tests and all 35 Compose
tests pass, with no skipped UI tests; Linux JavaFX staging also succeeds.

This closes the read-codes write-fallback race, **not cache identity validation**.
No fresh ECU identification is added to read-codes; previously discovered dynamic
addresses are still not proven to belong to the currently attached firmware.

## Retained Swing owner and queued notifications

`EcuLogger` now registers the callbacks owned by `SwingLoggerInitialization`.
Accepted ECU/DimeMod state is stored synchronously under the owner monitor;
queued Swing tasks never assign that cache. Each notification carries a state
revision and is discarded if superseded or closed. A combined notification
contains both ECU identity and DimeMod status, so coalescing an identity event
with its following DimeMod result cannot lose the identity labels.

The same-ID cache behavior is retained. A changed ID immediately clears DimeMod
metadata and publishes `UNKNOWN` capabilities, even with a blocked event thread.
Shared platform-state publication remains synchronous and serialized with owner
updates/closure; only UI work is deferred. The publisher does not wait for the
event thread or perform I/O. Existing platform listeners were inspected: their
background notifications queue Swing work instead of waiting for it.

Unchanged DimeMod metadata avoids a catalog/profile reload. Forced channel
updates advance a separate channel revision; a first absent result updates
status without reloading unchanged channels. After a definition load that may
open a nested event loop, the caller checks its revision again before restoring
the profile.

`handleExit` marshals to the event thread, closes the callback owner before
stopping workers, and ignores repeated closure. Late callbacks cannot mutate
retained state or platform capabilities, and pending UI notifications are
discarded. Closed callback cache lookups throw rather than returning a null
cache that might request legacy discovery. This is a pre-operation guard, not
cancellation of a transport request already in flight.

Tests exercise the production callback owner with controlled EDT ordering,
native labels, a genuinely blocked Swing queue, and the actual platform-state
publisher without constructing a logger frame, starting its controller, or
opening any adapter. They cover immediate cache visibility, identity changes,
same-ID reuse, reordered notifications, combined identity/status delivery,
forced/absent metadata, close/reopen isolation, repeated closure, closed cache
lookups, unbound metadata, and immediate capability invalidation. One initial
test failure was an incorrect expected version-label format (`2.3.100` rather
than the existing `2.3 build 100`); the production label format is unchanged.

Qualification: all 13 Swing initialization tests pass. The full Ant Linux
build/unit suite passes with the three existing optional/native skips.
Shared-core checks and all 281 desktop UI tests (246 JavaFX, 35 Compose) pass,
with no UI test skips; Linux JavaFX staging succeeds.

The callback interface still carries **no originating session token**. These
guards reject superseded queued UI work and closed owners; they cannot identify
an old transport result first delivered after a newer ECU callback on an open
owner. Full ECU/module/transport/session binding remains open, as does broader
in-flight catalog/profile reload cancellation. No physical reconnect test or
write-based discovery qualification is claimed.

## Existing cache boundary

The remaining cache is **ECU-ID keyed, not fully ECU/session bound**:

- `LoggerDesktopRuntime.handleEcuInit` clears `dmInit` when the ID changes.
  Another initialization with the same ID retains it. Other initialization
  bytes, connection generation, transport and physical identity are not part
  of this cache key.
- The retained Swing logger follows the same ID comparison. Its owner-scoped
  synchronous cache and guarded UI notifications are qualified above; the
  callback interface still does not bind results to their originating session.
- `QueryManagerImpl.initConnection` identifies the ECU and then calls DimeMod
  initialization on each connection attempt. `SSMLoggerConnection.dmInit`
  obtains its cache from `getDmInit()`: a non-null entry takes the runtime-read
  path, whereas a null entry takes the legacy write-based discovery path.
  `needToInit()` is not consulted by this implementation.
- The Swing read-codes path obtains a connection and reads a snapshot using the
  logger's cached DimeMod metadata without fresh ECU identification in that
  method. Its runtime-only boundary above prevents discovery writes, but
  verifying identity before cached-address reads remains open.

These findings come from the current call sites, not physical disconnect or
reflash tests. A matching ECU ID alone does not establish that previously
discovered dynamic addresses still belong to the connected firmware.

## Next contract work

Do not simply clear the cache on every reconnect: under the existing dispatcher
that would increase write-based discovery attempts. Define how cached data is
bound to observed ECU/module/transport/session identity, how stale or unbound
data becomes unavailable, and how users explicitly request any write-based
rediscovery. Preserve the distinction between read-only runtime refresh and
discovery negotiation. Android still needs a verified read-only discovery source
or a separately authorized, accurately labelled flow.

Other advertised channel/RAM-tune address spans, cache/session identity,
in-flight UI reload cancellation, and negotiation cleanup remain open. See the
[metadata and discovery audit](DIMEMOD_CHANNEL_AUDIT.md) for completed bounds
checks and their limits. No production ECU-writing or live-tuning capability is
qualified by this lifecycle repair.
