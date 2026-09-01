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
| `logger/api` | 13 | 0 | Live data, channel selection, session commands, preferences, and state are neutral |
| `flash` | 19 | 0 | Backend, capability, preflight, and progress contracts are neutral |
| `livetune` | 12 | 0 | Drafts, staging, preflight, session state, and mock verification are neutral |
| `activity` | 4 | 0 | Application activity state is neutral |
| `editor/calibration` | 3 | 0 | Immutable 1D/2D/3D grid snapshots are ready for a replacement view |
| `editor/workspace` | 9 | 1 | Only the presentation panel imports Swing/AWT after the first extraction slice |
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

### Logger control now has a neutral boundary

`LoggerSessionService` owns connect, disconnect, recording commands, explicit
session state, failure handling, and listener disposal. `LoggerChannelService`
owns the UI-neutral channel catalog and selection. `EcuLogger` remains the
compatibility host and adapts the existing parameter, switch, and external
source models into these services, but the replacement workspace no longer
drives Swing buttons or tables.

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

Status: complete for the first Logger replacement checkpoint. The existing
Swing workspaces and the Compose workspace share the same controller, channel
selection, received samples, and recording state.

### 6. Replacement workspace checkpoint

The first Compose Desktop checkpoint is now the Logger workspace described in
`ROMRAIDER2_UI_DIRECTION.md`. It uses the real channel, session, recording, and
live-data services. The visual fixture is test-only and is excluded from the
runtime jar. Compose and its matching native renderer are staged for both
Windows x64 and Linux x64 packages.

The Editor Tune inspector now consumes the same neutral draft projection and
shows changed ranges without adding a production write command. The projection
handles 3D row gaps and stages X/Y axis edits under the parent table instead of
silently omitting them. The portable
shared core also proves bounded ROM byte editing and logger CSV round-trips
without desktop or Android framework imports.

The full replacement shell and definition-backed calibration workspace remain
next. They must use a real ROM and neutral edit commands, not a copied
calibration model.
JavaFX remains the recorded fallback until the packaged Windows pass and the
broader accessibility, keyboard, scaling, and rendering gates are complete.

## Progress through the extraction plan

The first Editor slice converted workspace indexing and ROM comparison to the neutral
catalog and separated that catalog's storage from Swing nodes. It removes real
Swing dependencies from services and prepares search, compare, and the
replacement left rail without changing ROM bytes, logger behavior, or current
windows.

The Logger slice completed the neutral session and channel boundaries and added
the first packaged Compose workspace. The next Editor slice should move the
tree-node mirror out of `Rom`, followed by a Swing-only table-view registry.
These have more lifecycle risk and remain separate review and test checkpoints.

## Enforcement

- Add an architecture test that fails when `editor/compare`,
  `editor/recovery`, `logger/api`, `flash`, `livetune`, or `activity` gains a
  direct Swing or AWT import.
- New domain and controller packages must not import from `com.romraider.swing`
  or any `*.ui` package.
- Replacement views may depend on neutral services; neutral services must not
  depend on replacement or Swing views.
- Each extraction keeps the current application compiling and packageable on
  Java 21 for Windows x64 and Linux, with native macOS ARM64 and Intel builds
  checked separately.
- Connected-device and checksum behavior remains unchanged unless a separately
  reviewed safety change explicitly requires it.
