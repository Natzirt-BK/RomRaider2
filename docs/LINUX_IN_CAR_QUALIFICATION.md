# Linux in-car read-only qualification

Use this checklist for the exact release-candidate application image that will
be published. This pass qualifies Subaru OpenPort 2.0 logging only. It does not
qualify Mitsubishi Lancer Evolution MUT-II, Windows hardware, flashing, or ECU
memory writes.

## Safety and prerequisites

- Keep the vehicle parked, with the parking brake applied, and run the engine
  only outdoors or with approved exhaust extraction.
- Use a stable laptop battery and vehicle battery. Do not perform the test while
  driving.
- Use an extracted Logger definition that exactly matches the vehicle and keep
  ROMs, definitions, profiles, and captures outside the release image.
- Confirm that flashing and research-only RAM writing remain unavailable.
- Record the application-image name, operating-system version, vehicle model
  and year, ECU ID, OpenPort model, and Logger-definition version. Do not put a
  VIN, registration, owner name, or private ROM in repository evidence.

## 1. Package and adapter preflight

- [ ] Launch the candidate from its packaged `bin/RomRaider2` executable.
- [ ] Confirm the package creates `rr_system.log` in its own `logs` directory
      and does not create or update `~/.RomRaider/rr_system.log`.
- [ ] Open Logger and install the matching extracted definition through
      **Definitions > Install Logger Definition**.
- [ ] Open **Vehicle / Module**, select **Subaru** and **Engine ECU**, and
      confirm the Logger resolves SSM over ISO9141.
- [ ] Confirm OpenPort/J2534 is selected and no irrelevant serial-port selector
      is shown.

## 2. Ignition on, engine off

- [ ] Connect with ignition on and engine off; record the detected ECU ID.
- [ ] Select a small safe set such as engine speed, battery voltage, coolant
      temperature, and throttle position.
- [ ] Start and stop a two-minute capture without an exception, frozen UI, or
      stale connection state.
- [ ] Disconnect normally, reconnect, and repeat a short capture.
- [ ] With the vehicle still parked, disconnect USB during a short active
      capture. Confirm a controlled error, then reconnect the adapter and start
      a new session without restarting the computer.

## 3. Engine-running sustained log

- [ ] Start the engine in a ventilated location and confirm plausible live
      values before recording.
- [ ] Record at least 15 minutes at idle with the chosen parameter set.
- [ ] Confirm the UI remains responsive and the log has no unexplained long
      gaps, repeated stale values, or query-change desynchronization.
- [ ] Stop recording before disconnecting, then close and reopen Logger and
      verify one clean reconnect.

## 4. Capture and shutdown verification

- [ ] Open the completed CSV in Analysis and verify time graph, table, range,
      playback, and X/Y views load it.
- [ ] Confirm **Replay latest capture** offers the file from this session.
- [ ] Add and remove a marker, then confirm its sidecar is written beside the
      CSV rather than inside the application image.
- [ ] Close Logger and Editor. Confirm no RomRaider2 process remains and the
      OpenPort is released for another application.
- [ ] Review `rr_system.log` for uncaught exceptions, repeated reconnect loops,
      or absolute paths that should not be included in shared evidence.

## Pass rule and evidence

The Linux Subaru logging checkpoint passes only when every item above passes on
the exact candidate image. Keep the raw vehicle log and definitions private.
Record a sanitized text summary containing the candidate checksum, durations,
ECU ID with any sensitive suffix redacted, reconnect result, Analysis result,
and any relevant exception class or timestamp.

Any failure keeps the milestone open. Preserve the candidate and sanitized log
segment, reproduce while parked if safe, and fix the failure before building a
new candidate. A rebuilt candidate must repeat this checklist.

## Pre-RC2 findings

The August 30, 2026 parked test used a 2005 Forester XT, OpenPort 2.0,
SSM/ISO9141, and a v370 Logger definition. ECU identification, a two-minute
engine-off capture, normal disconnect/reconnect, and a second capture passed.
The ECU ID matched the installed definition; repository evidence keeps the
suffix redacted.

The USB-removal test found two failures in the earlier candidate: file capture
remained selected after the interface disconnected, and Reset Connection could
not restart the stopped query worker. RC2 closes and flushes the capture when
the connection stops and uses the real worker state when reconnecting.

The connected USB-removal retest is deferred to RC3. RC2 is qualified for the
normal connection, capture, stop, disconnect, and reconnect workflow. Its new
unexpected-disconnect recovery has automated coverage but is not claimed as a
completed connected-vehicle qualification.

## RC3 findings

The August 31, 2026 parked test used the published 1.1.0 RC3 Linux image
(`5f204ea527d6b0a648925ccb99d2667f944605642a359835c5e16569fa655356`), a
2005 Forester XT, OpenPort 2.0, SSM/ISO9141, and a v370 Logger definition. The
ECU ID matched the definition; only a redacted match is recorded here.

ECU identification and live values passed with engine speed, battery voltage,
coolant temperature, and throttle opening angle selected. The first capture
ran for 133.557 seconds and recorded 1,601 samples. A normal disconnect and
reconnect required no application restart, and the second capture ran for
44.702 seconds with 536 samples. Both files closed cleanly.

Removing the OpenPort USB connection during a third capture closed that file
cleanly after 18.279 seconds and 220 samples. Reconnecting USB restored live
polling without restarting the application or computer. While USB was absent,
however, the status remained at Connecting, retries ran once per second, full
initialization errors were repeatedly written to the system log, and an empty
serial-port fallback was attempted. Those recovery-state problems keep the
full checkpoint open. Engine-running and Analysis checks remain deferred.
