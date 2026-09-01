# RomRaider2 interface direction

## Visual target

RomRaider2 is moving toward a modern ECU workbench instead of repeatedly
reskinning the inherited window. The Editor uses three clear areas: navigation
on the left, calibration and analysis in the center, and live/contextual data
on the right. The Logger follows the same idea with a channel rail, one active
workspace, and controls that belong to that workspace.

Dark and Light themes are equal targets. Status and primary actions stay easy
to find, while destructive or future ECU-write actions remain separated from
ordinary navigation.

## RomRaider2 interpretation

- Steel and graphite are the primary navigation and selection colors.
- Red is reserved for ECU writes, destructive actions, connection faults,
  warnings, and unsaved work—not ordinary navigation.
- The approved matte graphite/red RomRaider2 icon remains the visual anchor.
- Labels accompany important toolbar icons. Tooltips are supporting help, not
  the only way to identify an action.
- Information is grouped into bordered surfaces with clear hierarchy rather
  than long strips of unrelated controls.

## Application shell

The shell is a client of reusable application services. Connection, logging,
diagnostics, and flashing behavior must not be implemented in Swing classes.
Read ECU and Write ECU actions are driven by explicit backend capabilities;
Write ECU launches preflight and never begins programming directly. See
`FLASHING_ARCHITECTURE.md` for the core contracts and safety model.

### Left workspace rail

- Global fuzzy search
- ROM table tree and categories
- Favorites and recent tables
- Changed-table and realtime-capability indicators
- Back and forward navigation

### Center workspace

- Tabbed calibration tables
- Optional table/3D split view
- Compare, graph, and analysis tabs
- Dockable datalog timeline below the active calibration when requested
- Shared ROM context for editing, logging, live trace, compare, read, and flash

### Right inspector

- Active table identity, category, address, type, and definition description
- ROM identity and definition information
- Platform, module, connection, and realtime capability state
- Notes, real-sample live-value cards, searchable parameters, and change history
- Live traces use only received sample history; no guessed sensor ranges or fake
  progress is presented as ECU data
- Later: user-configurable live-card sets

### Persistent status and action bar

- Active ROM, platform, module, table count, and ROM size
- Current table context
- Logger access
- Clearly separated Save ROM action
- Actual interface/ECU connection state, never an inferred connected badge
- Capability-driven Read ECU and Write ECU commands
- Later: checksum, unsaved-change, and write-safety state

## Responsive and Touch behavior

Wide desktop modes show the full three-column shell. Below 1080 pixels the
inspector automatically collapses; it restores when the window returns to a
wide layout. Narrow map workspaces stack the calibration and visualization
panes vertically. Touch and In-Car modes start with the inspector collapsed so
the calibration surface remains usable. The inspector can be reopened from a
labeled command. Interactive targets remain at least 48 pixels with explicit
gaps between adjacent actions.

Dense desktop-only panels may collapse into tabs or drawers in Touch mode.
Safety-critical actions must never be placed immediately beside routine
navigation, and ECU-write actions must require an explicit verified workflow.

## Migration rule

Legacy behavior may change when the replacement is cleaner, safer, testable,
and produces a demonstrably better result. Protocol, definition, checksum, and
file-integrity behavior stays isolated from the presentation migration. Each
shell checkpoint must compile, pass focused tests, and receive Normal and Touch
visual checks before it is retained.

## Swing exit plan

Swing is a transition implementation, not the final RomRaider2 interface. The
migration starts during Windows qualification rather than waiting for every
legacy screen to be polished. Hardware and file-safety work remains a release
gate, but new product behavior must not deepen the dependency on Swing.
The current dependency inventory and ordered extraction gates are recorded in
`SWING_MIGRATION_BOUNDARY_AUDIT.md`.

Compose Multiplatform for Desktop is the selected replacement toolkit. The
Logger checkpoint proved gradual Swing interoperability, responsive layout,
custom drawing, shared state, and native Linux packaging. Windows x64 is part
of the same locked build and package path, with its visual run still required
when the VM is available. JavaFX remains the fallback if later accessibility,
input, scaling, or packaging gates fail. This is still an incremental
migration, not a whole-application rewrite.

The RC3 Logger pass remains available as the compatibility UI. For RC4, the
first replacement Logger workspace sits beside it and uses the same real
session, channel selection, recording state, and received samples. This makes
the toolkit migration visible without removing the proven fallback screens.

### Delivery sequence

1. **Neutral boundaries.** Keep ROM editing, logger sessions, search,
   navigation, activity, and flash preflight usable without Swing. Logger
   session and channel control are complete; Editor ownership extraction
   continues.
2. **Logger workspace.** The first Compose checkpoint replaces parameter
   selection, live values, graphs, dashboard cards, and recording controls
   while retaining the Swing workspaces as a compatibility path.
3. **Calibration workspace.** Replace the main shell, table tabs, responsive
   table/visualization layout, search, and Inspector. Editing continues through
   the existing tested ROM/table commands; the replacement view never writes
   ROM bytes directly. This is the next major user-visible migration checkpoint.
4. **Logger depth.** Move offline analysis, markers, advanced graph controls,
   and specialized tools after the live workspace passes both platform gates.
5. **Secondary tools.** Move diagnostics, settings, definition management,
   compare, learning views, and plugin configuration in capability-sized
   increments. A temporary Swing host may remain for an unmigrated tool.
6. **Removal.** Delete the compatibility host and Swing-only theme/layout code
   only after Editor and Logger parity, Windows/Linux package tests, keyboard
   and touch checks, and connected-device regression runs pass.

### Rules from this checkpoint onward

- New domain, transport, persistence, and safety behavior belongs in
  UI-neutral packages with no `java.awt` or `javax.swing` imports.
- Swing event handlers translate user intent into commands; they do not become
  the source of truth for application state.
- Do not port a legacy panel mechanically. Reconfirm its workflow against the
  workbench target and remove obsolete interaction patterns.
- Keep one releasable application throughout the migration. Replacement and
  compatibility surfaces may coexist, but there is one ROM/session state.
- A replacement checkpoint must be visibly better at normal, narrow, HiDPI,
  and Touch sizes before it becomes the default.

### Prototype acceptance gates

- Self-contained Java 21 Windows x64 and Linux packages remain reproducible.
- A real ROM opens into the replacement workspace without copying vehicle data
  into a second model.
- A calibration grid supports selection, keyboard editing, undo/redo, changed
  cells, tabs, and responsive resize without losing access to controls.
- The Java2D surface provider can remain behind its existing provider boundary
  until a replacement renderer proves equal or better.
- No J2534 handle, serial port, logger worker, checksum operation, or flash
  state machine is owned by the replacement UI toolkit.
- Normal, narrow, 150/200% scale, Touch, and High Contrast checks pass before
  expanding the migration beyond the prototype.
