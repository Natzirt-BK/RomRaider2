# RomRaider2 interface direction

## Visual target

RomRaider2 is moving toward a modern, dark-first ECU workbench rather than a
reskinned legacy window. The two August 2026 reference mockups establish the
desired information architecture without being treated as pixel-exact designs.

The structural reference is the three-column tuning workbench: persistent
navigation and favorites on the left, calibration and analysis in the center,
and contextual live data on the right. The visual hierarchy reference is the
cleaner panel-based concept with a persistent ROM status bar and an obvious,
well-separated Save ROM action.

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
- Later: user-configurable live-card sets and tuning assistant

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
