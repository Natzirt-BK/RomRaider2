# RomRaider2 implementation status

## Foundation checkpoint

- [x] Isolated DimeSPb-based development branch
- [x] Canonical handoff and approved icon assets imported
- [x] Pinned-source audit documented
- [x] Pinned DimeSPb baseline compiled on Linux
- [x] Latest official keyboard/table edit fix forward-ported
- [x] RomRaider2 product, build, icon, title, and About branding
- [x] Subaru and Lancer Evolution VIII / IX platform model
- [x] Platform-driven module capabilities
- [x] Independent DimeMod status model
- [x] Definition-aware DimeMod feature inventory with truthful mapped/runtime state
- [x] Platform-gated diagnostics and hidden research-only RAM-tune entry point
- [x] Shared Editor/Logger platform selector UI
- [x] Mock ECU transport with deterministic failure scenarios
- [x] Automated platform and mock-transport tests
- [x] Read-only Evo VIII / IX MUT-II protocol with synthetic parser coverage
- [x] DimeSPb discovery output connected to the independent DimeMod state
- [x] Combined platform, transport, definition, and MUT-II protocol tests
- [x] Versioned platform/module settings persistence and safe migration
- [x] Isolated 32-bit Editor and Logger GUI smoke tests on CachyOS
- [x] Reproducible preview package with bundled runtime, source, and checksums
- [x] Centralized UI scaling with Automatic and 75% through 300% presets
- [x] Dedicated Touch, Compact, Normal, Garage, Dyno, and In Car display modes
- [x] Centralized Dark, Light, System, and High Contrast semantic theme tokens
- [x] Versioned display preference persistence and safe migration
- [x] Modern scalable, theme-aware Editor menu and toolbar action icons
- [x] Touch/In-Car minimum 48-pixel action targets with adjacent-control spacing
- [x] Persistent table favorites, recent tables, and per-ROM open-table state
- [x] Direct favorite removal and weighted, resizable saved-table sections
- [x] Restored table tabs, active-table state, and persistent drag ordering
- [x] Scoped map-tab close, close-others, and close-all context actions
- [x] Live per-map unsaved indicators and state-rich tab tooltips
- [x] Live changed-map browser with direct navigation and cell counts
- [x] Unsaved-cell badges in the main calibration hierarchy
- [x] Standard active-tab and all-map keyboard close shortcuts
- [x] Recently closed map recovery with Ctrl+Shift+T
- [x] Table back/forward navigation service
- [x] Fuzzy table filtering across names, categories, and descriptions
- [x] Search ranking foundation with alias support and deterministic limits
- [x] Unified map, logger-parameter, DTC, setting, and command search index
- [x] Keyboard-first global search palette with safe editor command routing
- [x] Responsive labeled Editor command bar with RomRaider2 identity
- [x] Responsive command-bar overflow that preserves every action at narrow widths
- [x] Three-column desktop workbench with collapsible context inspector
- [x] Persistent hide/show control for the complete calibration navigation rail
- [x] Touch workbench that preserves the central calibration canvas
- [x] Persistent ROM/platform/table status and primary-action bar
- [x] Single-row status bar with left session/ROM state, centered platform, and slim reset action
- [x] Non-wrapping responsive status text for long ROM names and narrow windows
- [x] Themed minimize, maximize/restore, and close controls integrated into the application menu row
- [x] Symmetric lower-left/lower-right direct resize grips with minimum-bound clamping
- [x] UI-independent application activity model with measured/indeterminate states
- [x] Idle-collapsing activity capsule with elapsed time and recent-task drawer
- [x] Context inspector for table, ROM, platform, module, and realtime state
- [x] Narrow Inspector word wrapping for selection titles and detail values
- [x] Verified tabbed calibration workspace replacing visible MDI windows
- [x] Integrated map, visualization, and datalog document layout
- [x] Java 21-safe interactive Java2D map-surface provider
- [x] Value-labeled Java2D surfaces with click-to-select map integration
- [x] Modern map identity header with preserved editing and compare menus
- [x] Compact, collapsible Inspector with searchable Live Data parameters
- [x] Compact real-sample live-value cards and truthful mini traces in Inspector
- [x] Compact live-card paging without expanding or hiding the calibration canvas
- [x] Resizable split and columns for the constrained right-side live-data rail
- [x] Session-aware offline, connecting, and connected live-data empty states
- [x] Persistent per-ROM/table notes and real changed-cell status
- [x] Per-table unsaved-change breakdown and affected-map status tooltip
- [x] Bounded per-ROM edit history with grouped multi-cell undo and redo
- [x] Map-header undo/redo controls, shortcuts, and live history state
- [x] Undoable whole-ROM reset with confirmation and saved-state tracking
- [x] Save, discard, or cancel guard for every destructive ROM close path
- [x] Bottom-bar checksum management and actionable unsaved-change state
- [x] UI-independent indexed ROM comparison service
- [x] Responsive ROM comparison document hosted in the main workspace
- [x] Modified-map handoff into existing side-by-side table comparison
- [x] Integrated launch path from the workspace into the existing Logger
- [x] UI-independent live-data bus fed by existing logger samples and state
- [x] Live logger status/values connected to Inspector and datalog docks
- [x] Bounded real-sample history and lightweight integrated datalog traces
- [x] Integrated real-sample dashboard with selectable live traces
- [x] Direct live-data and global-search navigation to Logger data items
- [x] Compact calibration navigation with visible saved-table counts
- [x] Modern calibration hierarchy rows, icons, selection accents, and help
- [x] Heatmap-preserving cell selection and persistent changed-cell markers
- [x] Expanded calibration color scale with intermediate value labels
- [x] Direct active-map favorite control with live saved state
- [x] Active-map header category, address, and changed-cell context
- [x] Live selected-cell count and value range in the edit toolbar
- [x] Selection-aware value controls that prevent silent no-op edits
- [x] Direct undoable selected-cell revert with changed-selection awareness
- [x] Per-cell current, original/compared, and delta tooltip context
- [x] Responsive value toolbar with explicit secondary-control overflow
- [x] Theme-refreshed table-toolbar overflow and accented Show 3D action
- [x] Bounded, atomic ROM crash-recovery snapshots with integrity validation
- [x] Live recovery status with safe cleanup after save or intentional discard
- [x] Startup recovery discovery with restore, discard, and keep-for-later review
- [x] Recovered ROMs isolated as unsaved workspaces that require explicit Save As
- [x] Inspector edit timeline with direct undo, redo, and changed-map navigation
- [x] Narrow Inspector change-count visibility and fully collapsible Changes/History split
- [x] Java 8, 11, 17, and 21 compatibility baseline and migration audit
- [x] Java 21-only source, bytecode, testing, and release baseline
- [x] Java 8 bootclasspath plumbing and Java 21 deprecation warnings removed
- [x] JNA 3.4 replaced by licensed JNA 5.19.1 with J2534 ABI layout tests
- [x] jSerialComm 2.9.1 replaced by licensed 2.11.4 with provider smoke check
- [x] Release packaging hash-locks audited JNA, serial, and logging artifacts
- [x] End-of-life Log4j 1 backend replaced by licensed Log4j 2.26.1
- [x] Reproducible self-contained Linux Java 21 application image
- [x] Native KDE and Windows file selection with themed cross-desktop fallback
- [x] Managed Logger definition install, validation, backup, activation, and reload workflow
- [x] Transactional Logger definition activation rollback and cross-platform native-dialog override
- [x] Scoped Logger-definition identifiers accepted across independent protocols
- [x] Connection-scoped vehicle/module selection with protocol and target persistence
- [x] Reversible display-mode transitions that restore baseline Normal control metrics
- [x] Secure external-entity-free runtime XML parsing for user-supplied content
- [x] Editor/Logger listener and serial-refresh lifecycle cleanup across window reopen
- [x] Portable-package log isolation with explicit bundled Log4j configuration
- [x] Single actionable Dyno missing-profile state with unavailable controls disabled
- [x] Native Windows/Linux chrome for ordinary dialogs without Metal stippled title bars
- [x] Searchable, responsive Definition File Manager with priority and file-status details
- [x] Light first-run default and runtime refresh of custom tab-header theme colors
- [x] Theme-aware Logger status bar plus readable missing-definition state in About
- [x] Centered switch-state workspace and responsive offline-analysis statistics table
- [x] Seeded and verified initial Linux user settings matching the Windows package
- [x] Launch-directory-independent customization assets with package verification
- [x] Legacy Java3D/Graph3d binaries excluded from the Java 21 application image
- [x] UI-independent flash device/provider registry and discovery descriptors
- [x] Truthful connection-center UI with guarded Read/Write entry points
- [x] Java 21 x64 OpenPort 2.0 discovery and Subaru ISO9141 identification
- [x] Complete-message J2534 receive path with SSM query-change resynchronization
- [x] Sustained in-car Subaru SSM/ISO9141 logging without receive gaps
- [x] Connection-loss file capture shutdown and restartable Logger query worker
- [x] Release-packaged J2534 discovery metadata with definition-neutral defaults
- [x] Architecture-aware Windows J2534 discovery across native/WOW6432 registry views
- [x] Automatic direct or isolated x86/x64 J2534 DLL routing with pinned helper builds
- [x] Cross-bitness protocol coverage for raw transmit flags, timeouts, and ISO15765 flow control
- [x] Software-only application packaging with no vehicle definitions, profiles, ROMs, or logs
- [x] Truthful J2534 interface presentation without an irrelevant COM-port selector
- [x] External-sensor configuration guard and live port-selection reconnect
- [x] Receive-only AEM UEGO 9600-baud hardware/data-format validation
- [x] Operating-system gating for the Windows-only Innovate MTS plugin
- [x] Immutable offline log dataset with strict RomRaider CSV channel alignment
- [x] Missing-sample-aware range statistics with median, deviation, and percentiles
- [x] Read-only Logger Analysis workspace with background loading and sample ranges
- [x] Optional parser regression over a separately supplied CSV corpus
- [x] Shared offline sample cursor with bounded seek and step behavior
- [x] Clock-driven 0.25x through 8x playback with no owned background thread
- [x] Theme-aware five-channel time graph linked to playback and table selection
- [x] Direct graph and timeline seeking with range-aware cursor clamping
- [x] Immediate replay offer for the latest successfully completed Logger capture
- [x] Persistent typed log markers stored beside CSV captures without modifying them
- [x] Linked configurable X/Y graph with shared cursor, range, and marker navigation
- [x] Explicit incremental Swing exit sequence with prototype and removal gates
- [x] Swing dependency boundary audit with ordered extraction and enforcement gates
- [x] Workspace indexing and ROM comparison detached from Swing `TableTreeNode`
- [x] Neutral ROM table catalog stored independently from its Swing compatibility tree

## Safety state

No production ECU memory-write path was added. The new mock transport is the
only RomRaider2 transport that implements simulated writes. The existing
legacy RAM-tune test code remains research-only and is not exposed through the
normal Editor UI.

## Next checkpoints

- Repeat the connected USB-removal recovery test for RC3 and complete the
  remaining Linux Subaru checklist in `docs/LINUX_IN_CAR_QUALIFICATION.md`.
- Move the Swing tree-node mirror out of `Rom`, then build the packaged
  replacement-shell prototype defined in
  `docs/ROMRAIDER2_UI_DIRECTION.md`; promote it only after the calibration-grid
  and cross-platform acceptance gates pass.
- Evaluate compatible external renderers behind the visualization-provider API.
- Extend history grouping to preset and future bulk-analysis transformations.
- Add analysis overlays and reusable channel/graph workspace presets.
- Continue reimplementing selected blicraft analysis features as isolated services.
- Configure and validate simultaneous external AEM sampling with Subaru logging.
