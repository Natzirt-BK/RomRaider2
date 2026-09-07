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

At that checkpoint the callback interface still carried no originating attempt
token. The next follow-up below adds that lifetime boundary. Full firmware/cache
identity binding and broader in-flight catalog/profile reload cancellation
remain open. No physical reconnect test or write-based discovery qualification
is claimed.

## Initialization-attempt lifetime

The query manager now creates a unique `InitializationAttempt` for each
`initConnection` call and binds both ECU and DimeMod callbacks to it. The token
expires on completion or failure, **before connection cleanup**, and immediately
when Stop is requested. Replacement of an active attempt expires its predecessor.
This is an initialization-operation token, not a physical ECU identity or the
lifetime of the later polling connection.

Callbacks arriving after expiry are ignored. Both desktop owners recheck the
token while holding their own state monitor, so a callback waiting for that
monitor cannot apply after its attempt expires. Scoped cache getters also check
the token under the owner monitor. An expired lookup throws rather than returning
null, which could otherwise select the legacy discovery handshake. Closing a
token does not clear the retained metadata or invalidate accepted UI work.
The ordinary same-ID metadata reuse policy and successful handshake bytes are
unchanged. SSM cached runtime refresh now works on an independent metadata copy:
a late reply cannot mutate the retained object's runtime arrays/channels before
callback rejection. A successful refresh publishes the new object, so a normal
reconnect may rebuild its channel catalog even if metadata bytes are unchanged.

The query manager checks cancellation before connection creation, before ECU
initialization, and before/after DimeMod initialization. A Stop during creation
closes the resulting connection without initializing it. Interruption during
DimeMod initialization stops the attempt, preserves the thread flag and returns
failure rather than success; cancellation does not report a false initialization
error or queue the retry delay. Other optional DimeMod failures still leave
standard logging available, preserving existing behavior.

Synthetic tests run the actual initialization orchestration with an injected
connection factory, without starting its worker. They cover successful cleanup,
late callbacks from a completed attempt, successive attempts on one owner,
Stop before/during connection creation and ECU initialization, existing thread
interruption, ECU failure, optional DimeMod failure, and interrupted DimeMod
initialization. Native SSM tests confirm that an expired token cannot start any
K-line/CAN discovery command. Swing and modern-desktop tests deliberately block
callbacks and cache lookups on the owner monitor, expire the token, then release
them; positive active-token and cache-reuse cases are also covered. Native
K-line/CAN 2.0/2.3 fixtures expire an attempt while returning a valid runtime
reply and assert that the retained errors, active inputs and channels do not
change; the corresponding successful cases publish independent fresh state.
Running those new native tests against the compiled pre-copy implementation
reproduced two failures: a cancelled reply changed cached error `1` to `0`, and
a successful reply published the original mutable object rather than a snapshot.

Qualification: all 66 focused initialization, runtime and metadata tests pass.
The full Ant unit suite and Linux/Windows core builds pass (three existing
optional/native skips). The combined desktop run passes 284 UI tests without
skips, including the concurrent seamless-gauge work. Shared checks and Linux
JavaFX staging also pass. All transport fixtures are synthetic.

The overloads retain compatibility with callers using unscoped callbacks, but
only the query-manager-bound path supplies this lifetime guarantee. Third-party
callback owners with their own locks must implement the documented inside-lock
check; the default overload alone cannot synchronize their state. A transport
operation already executing is not rolled back by token expiry. Firmware bytes,
module/transport/adapter identity, and dynamic-address validity across reconnects
are still not part of the retained metadata key.

## Interrupted initialization

Initialization now checks the thread's interrupt flag after connection creation,
ECU identification and DimeMod initialization, including ordinary returns and
unchecked failures. Cancellation stops retries, expires callbacks and preserves
the interrupt flag. It is not treated as an optional DimeMod failure that can
continue into standard logging. Already accepted state is retained; an in-flight
operation or a lower layer that consumes an interrupt is not rolled back.

Five synthetic regressions failed against the preceding implementation: interrupted
factory, ECU and DimeMod returns, plus interrupted ECU/DimeMod failures. The ECU
failure case also verifies that consuming the flag later does not re-enable a
stopped manager. Ordinary non-cancelled DimeMod failure still permits standard
logging, and checked DimeMod interruption retains its existing cancellation behavior.

Verification passes: 14 initialization tests, 71 focused logger tests overall,
the full Ant suite/Linux build, shared checks, and all 284 desktop UI tests.
No physical connection or ECU command was executed.

## Profile review after initialization changes

The Swing profile path now captures the initialization snapshot before parsing
the profile. Initialization-triggered restores carry their original snapshot
instead of adopting a newer one between the caller's check and file loading.
The production review gate checks it before confirmation and again immediately
before applying selections and units. A protocol-mismatch dialog can run a nested
Swing event loop; choosing Load after an ECU/DimeMod update or owner closure no
longer applies that obsolete profile. Dismissing the dialog also cancels rather
than implicitly accepting the mismatch.

Three regression tests exercise that gate with the real initialization owner:
stale/closed state before review, identity/metadata/closure changes inside a real
nested event loop, and unchanged-state approval/cancellation/same-ID cache reuse.
They do not instantiate the logger frame or access a device. The owner monitor is
not held while prompting. This is an entry guard, not rollback of a profile
application already executing, nor complete cancellation of definition/catalog
reloads. It does not change the discovery handshake or cache key.

Verification: all 18 Swing initialization tests, the full Ant unit suite/Linux
build, shared-core checks and all 293 desktop UI tests (40 Compose, 253 JavaFX)
pass. Native-window tests were enabled on an isolated Xvfb/Openbox display;
neither desktop UI suite skipped tests. No physical device was accessed.

## Catalog reload snapshots and cancellation

`EcuLogger.loadLoggerParams` now carries an owner-scoped reload token through its
definition, external-row, catalog and pending-focus stages. A newer reload
supersedes the previous token even if initialization has not changed. ECU or
DimeMod updates and owner closure invalidate it as well. Each stage checks the
token before entry, and the pipeline checks again after its final stage; obsolete
work is not recorded as the rendered initialization revision.

Normal definition parsing uses the captured snapshot's ECU identity and appends
only that snapshot's DimeMod channels. The merged list is a fresh copy, so repeated
use does not append dynamic channels into a preloaded definition's own list. A
check after parsing prevents stale preparation from starting model updates.
Missing-definition dialog continuations check their token before scheduling an
installation, changing external-only settings or continuing the reload. Stale
errors do not open another definition-error dialog. Queued catalog refreshes
reject a closed owner. External-source preparation checks before each source,
before reporting an error and before installing the prepared rows, so a nested
plugin-error dialog cannot resume into stale row installation.

Profile confirmation also captures the current catalog token. A nested reload
with the same initialization snapshot cancels the pending profile application;
an unchanged catalog still permits explicit approval.

Five added tests exercise the production reload/review gates and parameter
merger: nested identity/metadata/closure/reload replacement, ordered current
stages, rejection of stale starts without cancelling newer work, final-stage
invalidation, paired retained inputs, unchanged preloaded lists, and same-state
catalog replacement during profile review. They use synthetic metadata and a
real nested Swing event loop without constructing the logger or accessing an
adapter. All 23 initialization tests and the full Ant suite/Linux build pass.
The rebuilt core also passes shared-core checks and all 293 desktop UI tests
(40 Compose, 253 JavaFX), with native-window checks enabled and no UI test skips.
Both GitHub desktop builds for the preceding profile-review checkpoint
`57483b29` passed; those hosted results do not cover this later reload change.

This cancels subsequent stages; it is not transactional rollback of a stage
already executing. Preloaded definitions are still parsed by the installer's
separate worker, whose origin/configuration lifetime needs its own review. The
retained old catalog after a definition-load failure also needs explicit
availability/registration handling: `ParameterListTableModel.clear()` clears its
registration broker, but a parser failure never reaches `loadEcuParams` and its
model-clearing step. Neither captured Java object references nor
the existing ECU-ID cache key prove physical firmware identity. The modern
runtime serializes its definition reload under its owner monitor, which does not
establish that identity either. No discovery command or handshake is changed.

## Existing cache boundary

The remaining cache is **ECU-ID keyed, not fully ECU/session bound**:

- `LoggerDesktopRuntime.handleEcuInit` clears `dmInit` when the ID changes.
  Another initialization with the same ID retains it. Other initialization
  bytes, connection generation, transport and physical identity are not part
  of this cache key.
- The retained Swing logger follows the same ID comparison. Its owner-scoped
  synchronous cache and guarded UI notifications are qualified above; the
  query-manager path now binds initialization results to their attempt lifetime,
  but not cached addresses to a verified firmware/session identity.
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

Published-channel wire spans are now checked by the linked metadata follow-up.
RAM-tune/uninterpreted address spans, cache/session identity,
installer-worker lifetime, failed-definition catalog invalidation, and negotiation
cleanup remain open. See the
[metadata and discovery audit](DIMEMOD_CHANNEL_AUDIT.md) for completed bounds
checks and their limits. No production ECU-writing or live-tuning capability is
qualified by this lifecycle repair.
