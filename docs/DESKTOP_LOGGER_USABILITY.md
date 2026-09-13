# Desktop Logger usability and remaining parity

Included in 1.1.10 RC1. See the [package qualification record](RELEASE_1_1_10_QUALIFICATION.md).

## Recording and fullscreen setup

The Windows/Linux logger header has an optional **Log name** field. Enter or
leaving the field applies the filename prefix; clearing it restores automatic
naming. The timestamp and ECU ID remain automatic, and repeated recordings never
overwrite existing files. Invalid names are rejected and highlighted. A name
cannot change an active recording, and a failed preference save restores the
previous name. The same name is used by the header, menu, fullscreen controls
and vehicle-switch recording.

**Logger Setup → Recording** lists the selected protocol's defined switches in
a dropdown. The saved switch and the rear-window defogger default (`S20`) are
also shown. Switching definitions refreshes the choices in the background; Save
checks the actual loaded definition/ECU and replaces the recording monitor while
disconnected. An unavailable switch or failed save restores the old monitor,
preferences and channel selections. This only watches the switch: ON starts
recording and OFF stops it; no switch-actuation command is sent.

Opening Logger Setup in a fullscreen or maximized logger uses an in-window modal
panel, preserving the main window's geometry and fullscreen state. Cancel or
Escape dismisses the panel and restores focus; background logger controls are
disabled while setup is open. Normal windowed setup remains a separate dialog.

Desktop and Android CSV channel values now have exactly two decimal places,
rounded only when written/exported. Timestamps, headers, units and missing-value
cells retain their existing format. Live values, calculations and Android
recovery samples keep their original precision. Existing CSV files are unchanged.

## Serial-port selection

The Windows/Linux Logger's read-only ELM/OBDLink test and Logger Setup now use
an editable dropdown with **Refresh**. Available serial ports are enumerated on
a daemon worker when the dialog opens. The list shows each OS address and its
device description. Selecting a row passes only its address to the backend.

Enumeration never opens a port, probes baud rates or sends adapter/ECU commands.
It cannot establish device compatibility. No first/only port is automatically
selected. A saved selection or a manually typed path survives a refresh, even
if the device disappears. Empty/error results explain how to refresh or enter a
port manually. Edits made while a scan runs are retained; closed/cancelled scans
cannot replace current results. The adapter test locks settings while its worker
is active. Logger Setup keeps edits local until Save and skips enumeration when
the configured backend is J2534, which does not require a serial port.

This does not add Android serial discovery, Bluetooth pairing, baud detection,
ELM support for enhanced SSM/MUT-II, or additional verified adapter models.
The macOS Compose setup has not been converted to this JavaFX control.

## Troubleshooting

**Logger → Troubleshooting…** exposes the existing Normal (INFO), Detailed
(DEBUG) and Trace levels. These affect application diagnostic output, not CSV
channel selection or recording. Save applies the preference; Cancel leaves it
alone. A failed preference save restores the previous setting and effective
logging level. The modern desktop runtime now honors the saved preference on
startup, as the retained logger does.

The dialog shows the actual system-log directory and opens it on a worker rather
than blocking the UI. The default is `.RomRaider2/logs` under the user's home;
`romraider2.log.dir` overrides it. The retained logger's log-folder action now uses
the same resolver instead of the old `.RomRaider` directory.

Trace output can slow logging and produce large files. Return to Normal after
troubleshooting. Review `rr_system.log` before sharing: it can contain paths,
ECU identifiers and communication details. No automatic upload is introduced.

## Remaining diagnostic-tool review

The existing read-code worker is documented in
[background diagnostics](DESKTOP_ASYNC_DIAGNOSTICS.md). It is not a complete
migration of the other retained tools into the modern Logger.

- Learning-table reads already use a Swing worker, but the SSM implementation
  mutates shared transport/module settings, creates result UI from its worker,
  and restores connection settings only inside a connection's cleanup block.
  Connection-creation failure therefore needs separate restoration coverage.
- Global adjustments perform reads and writes around a modal Swing dialog;
  reset operations also issue ECU commands. They need captured connection
  ownership, responsive worker execution and explicit cancellation rules before
  being exposed as new modern Logger controls.
- Plugin connections are not uniformly OS serial ports: Innovate MTS uses a
  vendor port index, and Phidget InterfaceKit uses direct USB. A generic serial
  dropdown must not silently configure those as COM devices.

These are source-review findings, not results from connecting to a car. No new
reset, adjustment, flashing or learning-table command was run for this review.
The [active plan](ACTIVE_WORK_PLAN.md) groups the outstanding in-car checks into
one later acceptance session.

## Local verification

- Core: 759 tests, 756 passed and three optional skips; Linux and Windows core
  builds passed.
- Desktop UI: 337 JavaFX tests (336 passed, one optional skip) and 48 Compose
  tests passed, with native-window tests and compact audit captures enabled on
  an isolated display. Regression cases cover fullscreen/maximized setup,
  recording-switch replacement/rollback and filename validation/rollback.
- Shared checks passed, including 132 fitted-layout combinations and compact
  rendering bounds, channel/unit retention and explicit state/warning labels.
- Android: 137 JVM tests, lint and automation APK builds passed. Isolated emulator
  checks passed gauge recording controls, compact portrait/landscape rendering,
  all 25 seamless styles, 1–6 mounted layouts, fullscreen/keep-awake, demo toggling
  and recording continuity across views. Actual rendered captures were reviewed.
- Two-decimal CSV checks passed on desktop and Android, including missing values,
  negative/rounding-boundary values, unchanged source precision and recovery
  spools. Android export/recovery, gauge controls, compact rendering and live
  view-switch recording checks were rerun after the CSV change.

No physical adapter was opened and no vehicle was contacted by these checks.
The compact Android gauge work is described in the
[mounted-display guide](ANDROID_MOUNTED_DISPLAY.md).
