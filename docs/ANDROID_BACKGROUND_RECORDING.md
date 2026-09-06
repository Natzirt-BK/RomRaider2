# Android background recording

Status: **implemented in 1.1.3 development source**. An explicitly started live
recording belongs to a non-exported foreground service, not the Activity. It can
continue when the screen is replaced, another app is opened or the display is
off. Public 1.1.2 downloads are unchanged and still stop when backgrounded.
Physical OpenPort/screen-off acceptance remains deferred.

## Implemented ownership boundary

`ReadOnlyRecording` is an Android-framework-independent, one-shot recording owner
used by `ReadOnlyLoggingService`. `MainActivity` displays its snapshots. It uses
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
an arbitrary blocking factory interruptible. The service rejects overlapping
recordings and remains busy through final transport release and foreground cleanup.
An adapter prepared by the Activity transfers exclusively to the service on an
accepted start. A lease closes that adapter even if recording preparation never
runs or fails before returning its resources. Prepare it again for a later run.

`stop()` and `close()` are cooperative and nonblocking. Stopping during queued
startup avoids acquisition; stopping during acquisition prevents subsequent ECU
identification. Stopping during a read does not publish the incomplete cycle.
The completed-log handle stays unavailable through final flush and full USB
release. Closing does not discard the spool. `awaitStopped()` is only for worker
or test code, never an Android lifecycle/main-thread wait.

Snapshots contain an immutable latest complete cycle, finite-only session peaks,
identity/readiness counts,
recorded-value count and original monotonic receipt time. They do not retain
screens, dispatch callbacks or accumulate a queue while no screen is observing.
Independent readers cannot restart capture or reset reading freshness. Android
hosts must supply `SystemClock::elapsedRealtimeNanos` and use the same clock for
stale detection so deep sleep counts toward age. The default JVM clock is for
non-Android hosts/tests; session CSV timestamps remain unchanged.

Peaks include completed cycles while the Activity is absent. Reset peaks changes
display state only, preserving CSV, sample count and the original receipt time.
The service retains no Activity or view callbacks; the visible Activity polls
snapshots at most ten times per second and stops polling when backgrounded.

The owner retains only the current snapshot; the recording's configured recent
sample buffer stays bounded while its disk spool retains the full capture.
`samples` counts individual recorded channel values, not CSV rows. Terminal
snapshots retain the actual last values but are explicitly STOPPED, never live.
Existing session cleanup now appends adapter/flush failures instead of replacing
the original read failure.

## Service integration

The service preserves these boundaries:

1. A non-exported, locally bound service owns the recording, USB connection and
   writer. The Activity renders snapshots and sends explicit commands. Foreground
   promotion must succeed before recording acquisition/ECU reads; the execution
   type is `connectedDevice`, with USB permission checked for the actual selected
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
   the service, but the service still must supply its notification. The first
   permission prompt does not start recording: press Start again afterward.
   If notifications are disabled, the app warns that Stop remains available in
   the app; it does not claim a visible notification action is available.
   [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
4. Activity replacement or view switches must attach to the same owner and
   preserve original receipt age, gauges, selected-channel ordering and CSV
   continuity. Activity cleanup must not close a service-owned USB connection.
   Setup/editor mutations stay blocked through PREPARING and STOPPING as well as
   active reading. USB detach is handled by the service, without reconnection.
5. The partial wake lock has a ten-minute timeout and is renewed at five-minute
   intervals only while foreground capture is progressing. Stopping or a
   two-minute absence of progress prevents renewal. Completion releases it
   immediately. No blanket battery-optimization exemption is requested. This
   does not guarantee survival of power loss, process termination or OEM policy.
   [Wake-lock release guidance](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/release).
6. Treat process death, force-stop and Android's Task Manager Stop differently
   from orderly notification Stop. Task Manager can kill the entire app without
   a cleanup callback. Retain and recover only what is actually on disk; do not
   promise final flush, complete last-cycle recovery or automatic resumption.
   [User-initiated app stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).

## Verification and remaining gates

Local qualification passed all 65 Android JVM tests in both debug and automation
variants, shared portable checks, debug/diagnostic/automation APK builds and lint.
Lint retains five existing SDK-update, allocation and text-format warnings;
there are no lint errors. The complete API 36 emulator lifecycle script passed,
including notification return and inspection of service state after process
death, before any test restart. Local reinstall uses the same APK; hosted CI
separately exercises an increasing-version, same-key upgrade. Production and
automation manifest checks passed, and deliberately crossed package/type checks
were rejected. None of these checks accessed a physical device or ECU.

Eighteen deterministic JVM cases exercise one-use/concurrent starts,
worker rejection, queued and in-flight cancellation, delayed identity/read
completion, independent snapshot readers, immutable values/original receipt
time, full-release completion ordering, storage/connection/cleanup failures,
unobserved peaks and freshness-preserving peak reset,
SSM and MUT-II CSV order, and 40,000 recorded values with one retained in-memory
sample and one latest-cycle snapshot. They run real session/conversion/spool
code with fake transports, not Android USB or a vehicle.

The isolated-emulator tests use the real service and Activity with fake
transports, exercising Home/screen-off capture, Activity recreation, all gauge
themes/calculated channels, notification and in-app Stop, denied notifications,
stale/duplicate/cancelled requests, read failure and process death. The deliberate
process kill expects a crashed instrumentation result after a readiness marker;
the next launch verifies the flushed spool prefix remains and no recording,
notification or USB session resumes. It does not certify a partially written
last row or prove recovery of bytes that had not reached storage.
The later [offline recovery path](ANDROID_RECORDING_RECOVERY.md) separately
validates completed records and offers reviewed omission of an unfinished tail;
the process-death fixture also checks conversion of its surviving records.

The automation-only manifest substitutes `dataSync` for `connectedDevice` so a
USB-free emulator can exercise Android's real foreground-service lifecycle. Only
the isolated automation package carries this substitution and its permission;
production remains USB-permission-gated `connectedDevice`. Fake factories enter
through test reflection, not through an exported component or intent. These
tests are not evidence that real USB permission, native driver lifetime or
vehicle traffic works while backgrounded.

Actual OpenPort permission/lifetime behavior, screen-off sustained capture and
Forester/EVO acceptance remain supervised physical tests. No ECU writing,
automatic vehicle access or new public package publication is authorized by
these software checks.
