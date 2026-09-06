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

## Existing cache boundary

The remaining cache is **ECU-ID keyed, not fully ECU/session bound**:

- `LoggerDesktopRuntime.handleEcuInit` clears `dmInit` when the ID changes.
  Another initialization with the same ID retains it. Other initialization
  bytes, connection generation, transport and physical identity are not part
  of this cache key.
- The retained Swing logger follows the same ID comparison, but assigns its
  DimeMod cache through a queued UI callback. That path still needs a separate
  stale-callback/owner audit; the modern runtime repair does not cover it.
- `QueryManagerImpl.initConnection` identifies the ECU and then calls DimeMod
  initialization on each connection attempt. `SSMLoggerConnection.dmInit`
  obtains its cache from `getDmInit()`: a non-null entry takes the runtime-read
  path, whereas a null entry takes the legacy write-based discovery path.
  `needToInit()` is not consulted by this implementation.
- The Swing read-codes path obtains a connection and uses the logger's cached
  DimeMod object without a fresh ECU identification in that method. Its outer
  cache check and callback lookup are separate reads of mutable owner state.
  Freezing that lookup and verifying identity before cached-address reads need
  qualification; a cache miss must not silently turn a read-codes action into
  discovery writes.

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

Other advertised channel/RAM-tune address spans, cache identity, Swing callback
ownership, and negotiation cleanup remain open. See the
[metadata and discovery audit](DIMEMOD_CHANNEL_AUDIT.md) for completed bounds
checks and their limits. No production ECU-writing or live-tuning capability is
qualified by this lifecycle repair.
