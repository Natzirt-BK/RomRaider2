# DimeMod cache and owner lifecycle audit

Current development: the [desktop reconnect contract](DESKTOP_DIMEMOD_RECONNECT.md)
supersedes the ID-only cache boundary and reconnect work described in the
historical checkpoints below. It verifies observed identification and metadata
on each connection without claiming whole-firmware or hardware authentication.

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
failed-definition registration boundary is addressed in the following section.
Neither captured Java object references nor
the existing ECU-ID cache key prove physical firmware identity. The modern
runtime serializes its definition reload under its owner monitor, which does not
establish that identity either. No discovery command or handshake is changed.

## Failed-definition catalog invalidation

A current missing/failed Swing definition load now clears ECU registrations from
the live, graph, gauge, MAF, injector and dyno brokers before reporting the error
or opening a definition dialog. Parameter/switch rows, analysis channel lists,
diagnostic-code search entries, module choices and connection properties are
removed. Failed loads are not reported as completed initialization reloads and
do not trigger an automatic profile restore.

External-sensor registrations are retained. Clearing a parameter table now
deregisters only its own rows: the old implementation cleared the entire shared
broker, including selected external and switch rows. Failed reloads do not
recreate an already loaded external catalog. This does not promise that a
successful user-requested reconfiguration retains every live selection or CSV
schema; the existing successful reload/profile workflow remains in use.

An owner-local availability state selects external-only operation while the
definition is unavailable. A successful definition reload restores the preceding
mode choice. Once the controller has actually stopped, shutdown also restores
that preference before saving settings, without lifting the invalid-profile
backup guard. Automatic recovery-profile backup is skipped while invalid, so
empty ECU rows cannot overwrite the earlier recovery profile. The automatic
recording-switch preference stays off until explicitly enabled again.

The controller/query manager now accept a null recording-switch monitor to
remove its old address. A volatile binding pairs each monitor with its query;
responses are delivered only to the same binding that was queried during that
cycle, at most once. Removal/replacement cannot make the new monitor consume an
old reply, and an active preference without a monitor produces no switch query.
The modern runtime also clears its monitor when the new definition has no
recording switch. Switch and external-only flags are visible across threads.

Synthetic tests cover selective table/broker cleanup, all six broker roles,
external registration retention, failure/recovery/preference/backup state, and
recording-switch removal/replacement/disabled-cycle response handling. They use
real brokers, row models and query selection with fake controllers/transmission;
they do not construct the Swing logger or qualify a physical reconnect. Existing
initialization tests cover stale reload rejection before invalidation can run.
A transport operation or response callback already executing is not rolled back.
Installer-worker provenance, full firmware identity and negotiated ECU cleanup
remain separate work; no adapter was opened to test these changes.

Qualification: seven added regressions pass, as do the full Ant suite/Linux
build, shared-core checks and all 293 desktop UI tests (40 Compose, 253 JavaFX).
Native-window checks were enabled, with no UI test skips. Both hosted desktop
builds for the preceding catalog-reload checkpoint `5cc1ca58` passed; those hosted
results do not cover this later invalidation change.

## Modern desktop failed-definition recovery

The modern runtime now removes its automatic recording-switch monitor before
parsing a replacement definition and on closure. Missing paths, missing files
and malformed XML clear the destination, connection properties and any partially
prepared ECU catalog. They disable automatic switch-controlled recording.
External channels remain selected and registered when ECU channels disappear.

Failed loads also prevent automatic backup from replacing the last usable
recovery profile. A subsequent valid load permits backup again. Starting a new
workspace intentionally without a definition is still valid external-only use,
not a failed replacement, and may save its profile normally.

Real-runtime synthetic regressions reproduced retained recording-switch bindings
after parse failure and closure, plus lost external selection, against the prior
core JAR. The added coverage exercises malformed XML, a deleted definition, a
cleared path, successful recovery, healthy monitor replacement and intentional
external-only startup. Fixtures isolate settings and use temporary definitions
and profiles; controllers remain stopped and no adapters are opened.

Qualification: the full Ant suite and Linux build pass, along with shared-core
checks and all 297 desktop UI tests (40 Compose, 257 JavaFX, no UI skips).
The four added regression methods pass within the 21-test setup-transfer suite.
Native-window checks were enabled; a local pass does not resolve the intermittent
hosted presentation failure described below.

The preceding source checkpoint `e1c28196` passed one hosted desktop build;
[the other run](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34074380631)
failed its Compose native-window test. Its retained XML shows Escape returned to
setup and released the awake lease, but restored Floating instead of Maximized.
That presentation-state race remains a separate follow-up, not a logger-parser
failure or evidence that both hosted builds passed.

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
- The local Swing read-codes follow-up below now checks a fresh ECU ID and
  initialization payload before cached-address reads. This addresses observed
  identity mismatch, but does not authenticate firmware or rebind the normal
  reconnect cache.

These findings come from the current call sites, not physical disconnect or
reflash tests. A matching ECU ID alone does not establish that previously
discovered dynamic addresses still belong to the connected firmware.

## Next contract work

The retained `InstallLoggerDefinitionAction` previously read live protocol/switch
settings in its worker and saved the installed path before requesting a fresh
catalog token. Its failure path also restored saved settings without checking
whether another action had changed them.

The local installer follow-up separates validation from installation: a prepared
definition owns a frozen byte snapshot, which can be parsed before replacing the
managed file. Installation captures the previous file at commit time. Rollback
uses that operation's snapshot instead of the shared `logger.previous.xml`, checks
per-JVM ownership, and rejects changed file contents or file identity when the
filesystem supplies one. A newer rollback does not revive an obsolete owner.
These checks do not provide cross-process locking or crash recovery.

The local UI follow-up now uses `DesktopDefinitionInstall` to capture protocol,
switch, ECU snapshot, settings owner/directory and relevant setup fields before
the chooser. The same catalog token guards confirmation, worker completion and
channel refresh. Preparation runs off the EDT and parses frozen bytes without
changing settings. Closure, initialization/catalog replacement and connection
startup invalidate the request; starting then stopping does not revive it.

The file/settings commit runs on the EDT under the initialization-owner monitor,
without dialogs or catalog callbacks. Initialization callbacks cannot intervene
between its ownership check and commit. Save failure rolls back that installation
and restores its prior path; validation failure never restores settings. After a
successful commit, UI/catalog refresh runs outside the monitor and checks ownership
and setup between stages. A stale refresh does not undo the committed installation.
This is an in-process Swing workflow boundary, not a transaction across independent
applications or a crash-safe settings writer. Native chooser/confirmation/shutdown
acceptance on physical systems and Windows/macOS remains pending. Linux
diagnostic-image checks are recorded in the [reliability checkpoint](DESKTOP_RELIABILITY_CHECKPOINT_2026-09-08.md).
These changes are separate from published 1.1.8 packages.

Local verification on September 8, 2026: `ant unittest` passed on Linux with
JDK 21 under Xvfb and a temporary user-settings directory. All 19 installer
tests passed, including concurrent and identical-content installations, changed
files, symlink replacement, backup independence, frozen preparation bytes and
unsupported-protocol rejection without settings changes. The optional BMW XDF
corpus test was skipped because its fixtures were not configured. No vehicle
files or adapters were used. Worker follow-up regression results are recorded below.

The worker follow-up passed the complete Ant suite: 675 tests reported, 672
passed and three optional tests skipped (configured native J2534 library, log
corpus and BMW XDF corpus). This includes 11 `DesktopDefinitionInstallTest`
cases, 24 `SwingLoggerInitializationTest` cases and the 19 installer tests above.
The owner tests also verify serialization against initialization callbacks during
commit and cancellation of later UI stages after setup changes. All 24
`FxLoggerSetupTransferTest` regressions passed against the rebuilt desktop test
JAR under Xvfb. The chooser itself and a packaged installation were not exercised
by these tests; these results do not qualify hardware or vehicle behavior.

## Diagnostic identity follow-up

`DmRuntimeReadRequest` captures paired ECU identification and DimeMod metadata
from the Swing initialization snapshot, together with a current-owner/setup
check. If metadata is present, it identifies the selected module on the diagnostic
connection before any dynamic address read. Both ECU ID and complete captured
initialization bytes must match. Missing, duplicate, changed or cancelled replies
cannot authorize the read. A cache miss still skips DimeMod, without discovery.
Runtime refresh uses a separate metadata snapshot and rejects empty results or
an owner/setup change before returning; it never publishes into the owner's cache.
The standard-code loop also rechecks the owner before displaying its result.

The SSM initialization object now owns its input bytes and returns copies,
preventing later array edits from changing an already captured identity. Short
replies that cannot contain the five-byte ECU ID are rejected explicitly.

Eleven synthetic diagnostic tests cover request ordering, changed ID or payload,
missing/duplicate/late replies, owner invalidation at each stage, cancellation,
unsupported refresh, missing results and frozen inputs. Their connection fixture
fails any discovery, reset or write call. These changes do not modify the normal
logger's discovery handshake or clear cached channels on reconnect.

The author's [current discovery implementation](https://github.com/DimeSPb/RomRaider/blob/master/src/main/java/com/romraider/logger/ecu/comms/io/connection/SSMLoggerConnection.java)
was rechecked: metadata discovery negotiates through writes, while a cached
metadata object takes the runtime-address read path. No independent firmware
identity check is supplied by that path. The remaining work must establish
metadata provenance and session binding without treating an ID match as firmware
proof or turning cache invalidation into implicit extra discovery writes.

Do not simply clear the cache on every reconnect: under the existing dispatcher
that would increase write-based discovery attempts. Define how cached data is
bound to observed ECU/module/transport/session identity, how stale or unbound
data becomes unavailable, and how users explicitly request any write-based
rediscovery. Preserve the distinction between read-only runtime refresh and
discovery negotiation. Android still needs a verified read-only discovery source
or a separately authorized, accurately labelled flow.

Published-channel wire spans are now checked by the linked metadata follow-up.
RAM-tune/uninterpreted address spans, cache/session identity, packaged installer
acceptance and negotiation cleanup remain open. See the
[metadata and discovery audit](DIMEMOD_CHANNEL_AUDIT.md) for completed bounds
checks and their limits. No production ECU-writing or live-tuning capability is
qualified by this lifecycle repair.
