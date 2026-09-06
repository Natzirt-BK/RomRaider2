# Android background-recording foundation

Status: **internal foundation in 1.1.3 development source, not an enabled
background-logging feature**. The Android Activity still owns its existing live
logger and stops it when leaving the foreground. No foreground service,
notification permission or wake lock has been added to the production manifest.
Public downloads remain 1.1.2.

## Implemented ownership boundary

`ReadOnlyRecording` is an Android-framework-independent, one-shot recording owner
for the upcoming service host. It is not yet wired into `MainActivity`. It uses
the existing real `ReadOnlyLoggerSession`, SSM/MUT-II query planning, complete
cycle conversion and `PortableLogSession` recording/export code.

The host supplies immutable definition/profile selections and a resource factory.
An explicit `start()` runs that factory and the session on one owned worker.
Constructing the owner, reading its status or closing it before start never
acquires an adapter or queries an ECU. Concurrent/repeated starts are rejected;
there is no restart/reconnect mechanism or persisted running state.

| Phase | Meaning |
| --- | --- |
| NEW | No start accepted; no resource acquisition |
| PREPARING | Accepted start, with resource preparation queued or running |
| CONNECTING | Read-only identification or waiting for the first completed cycle |
| RECORDING | At least one complete recorded cycle; receipt age still determines freshness |
| STOPPING | Cancellation or cleanup pending; no completed-log handle available |
| STOPPED | Worker cleanup finished; retained log and terminal message available |

The factory owns partial acquisitions until it returns a complete resource set.
After that, the recording owns the transport and writer exclusively. The factory
must honor cancellation and use finite device operations; the owner cannot make
an arbitrary blocking factory interruptible. The future host must also prevent
two different owners from acquiring the same adapter concurrently.

`stop()` and `close()` are cooperative and nonblocking. Stopping during queued
startup avoids acquisition; stopping during acquisition prevents subsequent ECU
identification. Stopping during a read does not publish the incomplete cycle.
The completed-log handle stays unavailable through final flush and full USB
release. Closing does not discard the spool. `awaitStopped()` is only for worker
or test code, never an Android lifecycle/main-thread wait.

Snapshots contain an immutable latest complete cycle, identity/readiness counts,
recorded-value count and original monotonic receipt time. They do not retain
screens, dispatch callbacks or accumulate a queue while no screen is observing.
Independent readers cannot restart capture or reset reading freshness. Android
hosts must supply `SystemClock::elapsedRealtimeNanos` and use the same clock for
stale detection so deep sleep counts toward age. The default JVM clock is for
non-Android hosts/tests; session CSV timestamps remain unchanged.

The owner retains only the current snapshot; the recording's configured recent
sample buffer stays bounded while its disk spool retains the full capture.
`samples` counts individual recorded channel values, not CSV rows. Terminal
snapshots retain the actual last values but are explicitly STOPPED, never live.
Existing session cleanup now appends adapter/flush failures instead of replacing
the original read failure.

## Service integration contract — next implementation

The following are project design requirements, not claims about implemented UI:

1. A non-exported, locally bound service owns the recording, USB connection and
   writer. The Activity renders snapshots and sends explicit commands. Foreground
   promotion must succeed before acquisition/ECU reads; the execution type will
   be `connectedDevice`, with USB permission checked for the actual selected
   adapter. Do not add unrelated network/Bluetooth permissions merely to bypass
   Android's foreground-service prerequisites. Android requires the appropriate
   type/permission and a two-stage foreground-service launch.
   [Service types](https://developer.android.com/develop/background-work/services/fgs/service-types),
   [launch requirements](https://developer.android.com/develop/background-work/services/fgs/launch).
2. Starting remains an explicit action from a visible screen. Binding, restoring
   setup, attaching USB, returning to the app and opening a notification must
   never start a new capture. Use one-use, process-local start requests and
   `START_NOT_STICKY`; reject absent, stale, duplicated or redelivered requests.
   Pending starts still require guards: `START_NOT_STICKY` alone does not eliminate
   pending intents after a process dies.
   [Service restart contract](https://developer.android.com/reference/android/app/Service#START_NOT_STICKY).
3. Show a low-importance ongoing notification with actual connection/recording
   state, open-app action and explicit Stop. Guard Stop against an older
   recording's action stopping a newer session. Handle notification denial
   truthfully: notification permission is not an OS prerequisite for starting
   the service, but the service still must supply its notification. Decide and
   test the user-facing denied/disabled-notification path before enabling this.
   [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
4. Activity replacement or view switches must attach to the same owner and
   preserve original receipt age, gauges, selected-channel ordering and CSV
   continuity. Activity cleanup must not close a service-owned USB connection.
   Setup/editor mutations stay blocked through PREPARING and STOPPING as well as
   active reading. USB detach is handled by the service, without reconnection.
5. Screen-off capture needs an explicit power/lifetime policy. Any partial wake
   lock must use a timeout, be tied to the active foreground recording, and be
   released on every terminal/error path. No indefinite idle wake lock or blanket
   battery-optimization exemption is planned.
   [Wake-lock release guidance](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/release).
6. Treat process death, force-stop and Android's Task Manager Stop differently
   from orderly notification Stop. Task Manager can kill the entire app without
   a cleanup callback. Retain and recover only what is actually on disk; do not
   promise final flush, complete last-cycle recovery or automatic resumption.
   [User-initiated app stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).

## Verification and remaining gates

Seventeen new deterministic JVM cases exercise one-use/concurrent starts,
worker rejection, queued and in-flight cancellation, delayed identity/read
completion, independent snapshot readers, immutable values/original receipt
time, full-release completion ordering, storage/connection/cleanup failures,
SSM and MUT-II CSV order, and 40,000 recorded values with one retained in-memory
sample and one latest-cycle snapshot. They run real session/conversion/spool
code with fake transports, not Android USB or a vehicle.

Local qualification passed all 64 Android unit tests in both debug and automation
variants, portable-core checks, debug/OpenPort-diagnostic/automation APK builds,
and their lint tasks. Lint has no errors; the existing SDK-version, gauge draw
allocation and status-text warnings remain. The isolated emulator passed setup
restoration, source removal, same-key reinstall continuity, retained-log export,
gauge/session/CSV continuity, calculated gauges, reviewed channel transfer,
empty/corrupt setup handling and no automatic logging. Reinstalling the same
local APK is not an increasing-version upgrade test; hosted automation separately
uses increasing version codes.

These tests do not establish Activity recreation or foreground-service behavior.
Service integration must add isolated-emulator lifecycle, notification Stop,
stale-intent, denied-authority and process-death tests. Do not weaken the
production permission model to make a USB-free emulator pass. Any automation-only
service fixture must be explicitly separated from the production manifest.

Actual OpenPort permission/lifetime behavior, screen-off sustained capture and
Forester/EVO acceptance remain supervised physical tests. No ECU writing,
automatic vehicle access or new public package publication is authorized by
these software checks.
