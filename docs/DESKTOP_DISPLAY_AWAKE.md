# Full-screen display awake — 1.1.3 development source

JavaFX, the Compose-owned logger and its legacy Swing bridge request display-awake
protection only for **full-screen gauges in a visible, focused, non-minimized
window**. Ordinary Logger/Gauges setup does not request it. Exiting full screen,
losing focus, hiding/minimizing the window or closing the workspace releases the
request. Regaining focus reacquires it only if full-screen gauges are still active.
If minimization leaves native full screen, restoring the window alone does not
re-enable it. These decisions never connect, start, stop or replace a logger session.

The tap menu reports request status. **Screen awake requested** means the desktop
API accepted a request; it is not a promise to override power policy or deliberate
sleep. If the provider fails or disappears, **Screen awake unavailable** remains
visible even after the tap menu hides. Configure the display while parked.

## Platform mechanisms and limits

| Platform | Request | Important limit |
| --- | --- | --- |
| Windows | Thread execution state: continuous display/system required | Does not suppress Windows screensavers or deliberate sleep. |
| macOS | IOKit `PreventUserIdleDisplaySleep` assertion | Does not override lid closure, deliberate sleep or power policy. |
| Linux / SteamOS desktop | `org.freedesktop.ScreenSaver` session-bus inhibition | Requires GIO and a desktop service implementing this interface. |

Windows flags are acquired and restored on the same dedicated worker thread,
including restoration of the previous thread flags. No away-mode flag is used.
This follows Microsoft's [execution-state contract](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-setthreadexecutionstate).

The macOS request uses a named IOKit assertion at level 255. Temporary
CoreFoundation strings are released after creation; the assertion ID remains
owned until release. Apple's [display-sleep assertion documentation](https://developer.apple.com/documentation/iokit/kiopmassertiontypepreventuseridledisplaysleep)
also explains its idle-system-sleep effect and manual-sleep limits. The native
signatures/constants are defined in [IOPMLib.h](https://github.com/apple-oss-distributions/IOKitUser/blob/main/pwr_mgt.subproj/IOPMLib.h)
and [CFString.h](https://github.com/apple-oss-distributions/CF/blob/main/CFString.h).

Linux retains a private connection for the entire inhibition lifetime. It sends
`UnInhibit` to the original service owner and closes that private connection;
disconnect also releases outstanding/late cookies under the
[idle-inhibition contract](https://specifications.freedesktop.org/idle-inhibit/latest/).
It never borrows/closes GTK's shared connection, auto-starts a bus, spawns an
inhibition helper, or changes desktop settings. The service owner is checked
every two seconds so a restart cannot leave a stale success indicator.
`GDBus` [method calls](https://docs.gtk.org/gio/method.DBusConnection.call_sync.html)
and the [connection handshake](https://docs.gtk.org/gio/ctor.DBusConnection.new_for_address_sync.html)
each have a 1.5-second cancellable deadline on a worker, not the UI thread.

Some Linux environments, notably alternate compositors or SteamOS Gaming Mode,
may not provide this interface. They are **not qualified by a desktop-session
test**. Missing GIO/service/permission produces the unavailable indicator, not a
fallback that simulates input or changes system power preferences. Gaming Mode
and actual Windows/macOS/Deck idle-timeout behavior remain platform acceptance
checks. A request is not Android's lock-screen Always On Display.

## Ownership and failure behavior

`DesktopDisplayAwake` coalesces requested state and runs native operations on one
daemon worker per logger window. A generation check releases a late acquisition
after focus loss/close before it can be published as active. Rapid focus cycles
release stale requests before acquiring replacements. Repeated equal state does
not create more requests. Failed cleanup remains owned and is retried every two
seconds, including after workspace close. A failed acquisition is retried on the
next full-screen foreground activation, not continuously against a denying service.

The AWT binding observes native focus, visibility, minimization and close events;
JavaFX observes the equivalent stage properties. Native request status is surfaced
without modal dialogs or logger commands. No new Java dependency was added: JNA
is already part of the packaged desktop runtime. Android code is unchanged.

## Verification

- Nine focused controller/native-contract tests cover idempotence, single-thread
  ownership, late acquisition after close, rapid focus cycles, unavailable native
  libraries, failed cleanup retry, lost-service detection, Windows flag restoration,
  macOS assertion/string ownership and incomplete cleanup after failed acquisition.
  Windows/macOS contract tests use fakes.
- Seven native Linux scenarios run against isolated D-Bus sessions: normal lifetime,
  absent service, never-replied inhibition, incorrect reply type, service loss,
  abrupt process exit and a socket that never completes authentication. The fake
  service checks the same sender's unsigned cookie and verifies no inhibition leaks.
- Native UI tests inject fake leases; they do not inhibit the developer's real
  desktop. JavaFX covers setup/full-screen/focus/minimize/exit/close. The Swing-hosted
  production Compose workspace additionally retains the synthetic recording and
  arriving readings across menu, focus and native-window operations.
  The complete local suites passed: 253 JavaFX tests and 39 Compose tests.
  Native captures show the [request status in the tap menu](images/swing-gauge-display-menu.png)
  and the [unavailable indicator after the menu hides](images/swing-gauge-display-awake-unavailable.png).
- Hosted Windows and both macOS architectures have an opt-in transient native
  acquire/release probe. A successful probe establishes API acceptance/cleanup,
  not a physical display's idle-timeout behavior. Inspect the current workflow
  results before treating those platform checks as passed.

Linux fixture (after compiling its test probe):

```sh
/usr/bin/python3 packaging/test-display-awake.py "$JAVA_HOME/bin/java" \
  'build/test:lib/common/*:lib/testing/*'
```

This launches private test buses. UI suites set
`romraider2.displayAwake.disabled=true`; injected test backends remain usable.
Native UI runs require their existing Xvfb opt-in flags and separate displays.
JavaFX minimize/restore checks additionally use
`packaging/run-desktop-window-tests.sh`, which starts a private Xvfb display and
Openbox instance with a repository-owned minimal configuration. This requires
Openbox and `xprop` (`x11-utils` on Ubuntu). Bare Xvfb cannot complete a native
minimize/restore handshake and is not sufficient evidence for that lifecycle.
No adapter, vehicle, production ECU write or public release is involved.
