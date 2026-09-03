# Swing migration boundary audit

This audit tracks the incremental Swing exit. Compose Desktop now owns normal
Editor and Logger startup. The old Swing application is retained only as an
explicit compatibility mode while the remaining workflow gaps are closed.

Last reviewed: 2026-09-02

## Production startup boundary

- Normal Linux and Windows startup discovers and launches the Compose desktop
  provider.
- Startup no longer falls back to Swing when Compose is missing or fails.
- The former application can only be selected with
  `-Dromraider2.desktop.shell=swing` for compatibility testing.
- `ECUExec`, the desktop command router, document/session services, calibration
  commands, Logger runtime, and theme state have no Swing or AWT imports.
- The Java 21 Linux application image has been launched from its packaged
  location and confirmed to enter the Compose ECU Studio directly.

## Current boundary

The newer application-service packages already provide a useful neutral core.
The source inventory below counts Java files with direct `java.awt`,
`javax.swing`, or `com.romraider.swing` imports.

| Area | Java files | Direct Swing/AWT imports | Assessment |
| --- | ---: | ---: | --- |
| `editor/compare` | 4 | 0 | Neutral after the first extraction slice |
| `editor/recovery` | 3 | 0 | Ready for another UI toolkit |
| `logger/api` | 22 | 0 | Live data, messages, gauges, channel selection, session commands, preferences, and state are neutral |
| `flash` | 19 | 0 | Backend, capability, preflight, and progress contracts are neutral |
| `livetune` | 12 | 0 | Drafts, staging, preflight, session state, and mock verification are neutral |
| `activity` | 4 | 0 | Application activity state is neutral |
| `editor/calibration` | 17 | 0 | Grid snapshots, grouped edits, undo/redo, and change listeners are neutral |
| `editor/workspace` | 9 | 1 | Only the presentation panel imports Swing/AWT after the first extraction slice |
| `editor/search` | 4 | 1 | Service/model are neutral; panel is Swing |
| `maps` | 43 | 9 | Core ownership is neutral; classic Swing view classes remain beside it for compatibility |

The counts are a baseline, not a completion metric. Indirect dependencies also
matter: a class can avoid importing Swing while exposing a model that owns a
Swing object.

## Principal coupling points

### ROM and Table ownership is separated

`Rom`, `Table`, and `DataCell` no longer inherit from or own Swing tree nodes,
frames, views, or cell components. Toolkit-neutral table catalogs,
presentation listeners, and user-interaction services are now the shared
boundary. Swing-only registries maintain the old view associations when the
compatibility shell is selected.

### Compose owns the desktop document session

The normal Editor shell opens, saves, closes, activates, compares, and edits ROM
documents through neutral controllers. It provides unsaved-change handling,
definition selection, progress, theme switching, table navigation, grouped
cell edits, copy/paste, and shared undo/redo without constructing a Swing
window.

### Workspace indexing consumes Swing nodes

`Rom.getTableCatalog()` exposes an immutable, definition-ordered calibration
catalog without tree nodes. The first extraction slice changed
`EditorWorkspaceService.indexRom` and `RomComparisonService` to consume that
API rather than `TableTreeNode`. `Rom` now stores the neutral catalog and keeps
the old Swing tree nodes as a synchronized compatibility mirror. Moving that
mirror into a Swing adapter is the remaining ownership inversion.

### Logger runtime is independent of EcuLogger

`LoggerDesktopRuntime` now owns the controller, query path, live-data bus, CSV
writer, channel/profile catalog, session state, messages, preferences, DimeMod
reload, and external-source setup. The normal Compose Logger does not create a
hidden `EcuLogger` or drive Swing controls. It can connect, disconnect, record,
configure definitions and ports, select external sensors, review live data,
and open captured CSV logs.

### Legacy plug-ins expose Swing actions

The runtime plug-in contract is neutral. The old menu action is isolated in
`SwingExternalDataSource` and is only consumed by the legacy menu adapter.
Current plug-in implementations still implement both interfaces so the old
shell remains testable; removing their Swing-side methods is a cleanup task,
not a dependency of the Compose Logger runtime.

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

Status: the first command slice is complete. The replacement Compose grid
renders the active `Table`, applies scalar values through `DataCell` scaling
and range checks, and shares `RomEditHistory` with the classic grid. Changes,
undo, and redo made from either grid refresh the replacement snapshot. Locked,
static, invalid, and detached tables fail before an edit is recorded. Arrow
navigation, definition fine/coarse increments, and loaded-value restore all
use the same boundary. Shift+arrow or Shift+click range selection copies
tab/newline blocks, and grouped block paste is validated before it becomes one
history operation. Across, down, and two-direction interpolation use definition
axis breakpoints and commit as one validated history operation.

### 5. Neutral logger session controller

Separate connect, disconnect, parameter selection, polling, recording, and
failure state from `EcuLogger`. Extend the current `LoggerLiveDataBus` seam or
replace it with a session service whose state transitions are explicit. Adapt
legacy external-source actions at the Swing edge.

Gate: controller tests cover connect/cancel/disconnect, start/stop recording,
device failure, and listener disposal without a `JFrame` or Swing table model.

Status: complete for the production Logger runtime. The Compose shell owns its
controller and state; `EcuLogger` is now a legacy-only host.

### 6. Replacement workspace checkpoint

Compose Desktop is now the normal top-level Editor and Logger shell. It uses
the real document, calibration, Logger, and live-tune planning services. Visual
fixtures remain test-only. Compose and matching native renderers are staged
for Windows x64 and Linux x64 packages.

The Editor Tune inspector now consumes the same neutral draft projection and
shows changed ranges without adding a production write command. The projection
handles 3D row gaps and stages X/Y axis edits under the parent table instead of
silently omitting them. The portable
shared core also proves bounded ROM byte editing and logger CSV round-trips
without desktop or Android framework imports.

The definition-backed calibration workspace now ships in the normal Compose
Editor. It uses a real ROM and the neutral command boundary, not a copied
calibration model. Direct entry, keyboard range movement,
spreadsheet-format block copy/paste, fine/coarse changes, restore, interpolation,
axis-value editing, and shared undo/redo are working. The matched Windows build
passed its Java and Compose suites plus a native package launch and first-run
visual check. Broader accessibility remains open. JavaFX is no longer the
planned fallback.

## Progress through the extraction plan

The first Editor slice converted workspace indexing and ROM comparison to the neutral
catalog and separated that catalog's storage from Swing nodes. It removes real
Swing dependencies from services and prepares search, compare, and the
replacement left rail without changing ROM bytes, logger behavior, or current
windows.

The ownership extractions and default-shell cutover are complete. Calibration
interpolation, axis editing, and definition-priority management are also
Compose-owned. Remaining work is feature parity and deletion: add essential
settings workflows to Compose, decide which specialized legacy Logger tools
stay, complete accessibility work, then remove the compatibility shell and its
Swing-only adapters from release builds.

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
