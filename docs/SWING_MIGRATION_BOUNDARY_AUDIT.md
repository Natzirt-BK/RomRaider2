# Swing migration boundary audit

This audit turns the Swing exit direction into an incremental extraction plan.
It records the current dependencies rather than treating the replacement UI as
a rewrite. The existing Swing application remains usable while one shared ROM
and logger state is made available to both the compatibility UI and the future
shell.

## Current boundary

The newer application-service packages already provide a useful neutral core.
The source inventory below counts Java files with direct `java.awt`,
`javax.swing`, or `com.romraider.swing` imports.

| Area | Java files | Direct Swing/AWT imports | Assessment |
| --- | ---: | ---: | --- |
| `editor/compare` | 4 | 0 | Neutral after the first extraction slice |
| `editor/recovery` | 3 | 0 | Ready for another UI toolkit |
| `logger/api` | 6 | 0 | Read-only live data and session-state seam exists |
| `flash` | 19 | 0 | Backend, capability, preflight, and progress contracts are neutral |
| `activity` | 4 | 0 | Application activity state is neutral |
| `editor/workspace` | 8 | 1 | Only the presentation panel imports Swing/AWT after the first extraction slice |
| `editor/search` | 4 | 1 | Service/model are neutral; panel is Swing |
| `maps` | 43 | 9 | Domain and presentation are still coupled |

The counts are a baseline, not a completion metric. Indirect dependencies also
matter: a class can avoid importing Swing while exposing a model that owns a
Swing object.

## Principal coupling points

### ROM owns presentation state

`Rom` stores `TableTreeNode` instances rather than a toolkit-neutral table
catalog. It also performs confirmation dialogs, locates Swing windows, accepts
`JProgressPane`, and closes `TableFrame`/`TableView` instances. This prevents a
replacement shell from opening and closing a ROM without participating in the
Swing object graph.

### Table owns its Swing view

`Table` stores both `TableView` and `TableFrame`. Scale validation can invoke a
popup through `TableView`. A calibration table therefore acts as both the ROM
data model and a presentation registry. This is the first ownership cycle to
remove; the replacement UI must render the existing table data without being
stored by it.

### Workspace indexing consumes Swing nodes

`Rom.getTableCatalog()` exposes an immutable, definition-ordered calibration
catalog without tree nodes. The first extraction slice changed
`EditorWorkspaceService.indexRom` and `RomComparisonService` to consume that
API rather than `TableTreeNode`. `Rom` now stores the neutral catalog and keeps
the old Swing tree nodes as a synchronized compatibility mirror. Moving that
mirror into a Swing adapter is the remaining ownership inversion.

### Logger control is still concentrated in the frame

`logger/api` exposes neutral live samples and observed session state, but
`EcuLogger` still combines window construction, controller lifecycle, selected
parameters, connect/disconnect commands, recording controls, and dialogs. A
new UI can display live data now; it cannot safely control a complete logger
session until those commands move behind a neutral controller facade.

### Legacy plug-ins expose Swing actions

Several external logger data sources publish `javax.swing.Action` objects.
Those are a later logger-migration boundary. Plug-ins should eventually expose
command descriptors and execute methods; the Swing host may adapt them to
`Action` during compatibility.

## Extraction order

### 1. Neutral ROM table catalog

Expose a read-only, definition-ordered table catalog and move its storage away
from `TableTreeNode`. Search, compare, recovery, change summaries, and new UI
code use the neutral catalog. The storage split is complete; `getTableNodes()`
remains temporarily for the Swing compatibility tree.

Gate: the neutral catalog preserves definition order, lookup behavior, table
identity, and duplicate-name handling. Existing ROM load tests and new catalog
tests pass without constructing a Swing component.

### 2. Remove view ownership from Table

Move `TableView`/`TableFrame` association into a Swing-only registry keyed by
ROM and table identity. Replace the scale-validation popup with a neutral
validation result or exception that the active shell presents. Do not copy
cell data into a second calibration model.

Gate: table population, edits, undo/redo, compare values, serialization, and
cleanup pass in headless tests. Closing a Swing table still releases all view
references through the compatibility registry.

### 3. Neutral document/session commands

Introduce one editor document session responsible for active ROM, open tables,
selection, dirty state, save eligibility, and navigation events. Add neutral
ports for file selection, progress, confirmation, and error reporting. Existing
open/save/checksum code can remain behind those ports initially.

Gate: a headless test can open a ROM fixture, activate a table, edit and undo a
cell, observe dirty state, request a save, and close the document without
constructing Swing.

### 4. Calibration grid projection

Expose immutable grid snapshots and explicit edit commands over the existing
`Table`/`DataCell` data. The snapshot contains labels, display values, changed
flags, selection coordinates, scaling metadata, and revision identity. Commands
perform selection, numeric edit, interpolation, undo, and redo; views never
write ROM bytes directly.

Gate: Swing and prototype views pass the same command-contract tests and show
the same changed cells after a scripted edit sequence.

### 5. Neutral logger session controller

Separate connect, disconnect, parameter selection, polling, recording, and
failure state from `EcuLogger`. Extend the current `LoggerLiveDataBus` seam or
replace it with a session service whose state transitions are explicit. Adapt
legacy external-source actions at the Swing edge.

Gate: controller tests cover connect/cancel/disconnect, start/stop recording,
device failure, and listener disposal without a `JFrame` or Swing table model.

### 6. Replacement shell spike

After steps 1 through 4 establish one shared state, build the Compose Desktop
spike described in `ROMRAIDER2_UI_DIRECTION.md`. It should use a real ROM and
the neutral commands, not mock calibration data. Keep JavaFX as the recorded
fallback until Windows and Linux packaging, accessibility, keyboard, scaling,
and rendering gates pass.

## First implementation slice

The first slice converted workspace indexing and ROM comparison to the neutral
catalog and separated that catalog's storage from Swing nodes. It removes real
Swing dependencies from services and prepares search, compare, and the
replacement left rail without changing ROM bytes, logger behavior, or current
windows.

The next slice should move the tree-node mirror out of `Rom`, followed by a
Swing-only table-view registry. These have more lifecycle risk and remain
separate review and test checkpoints.

## Enforcement

- Add an architecture test that fails when `editor/compare`,
  `editor/recovery`, `logger/api`, `flash`, or `activity` gains a direct Swing
  or AWT import.
- New domain and controller packages must not import from `com.romraider.swing`
  or any `*.ui` package.
- Replacement views may depend on neutral services; neutral services must not
  depend on replacement or Swing views.
- Each extraction keeps the current application compiling and packageable on
  Java 21 for Windows x64 and Linux.
- Connected-device and checksum behavior remains unchanged unless a separately
  reviewed safety change explicitly requires it.
