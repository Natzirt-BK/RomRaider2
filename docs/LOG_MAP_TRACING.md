# Saved-log map tracing — 1.1.3 development source

JavaFX Log Analysis has a **Map trace** tab. It follows the existing saved-log
cursor over a read-only, explicitly captured editor-table snapshot. It does not
read an ECU, alter a calibration or claim to reproduce an ECU's table-lookup code.
Public 1.1.2 packages do not contain this addition.

## Capture, map, then trace

1. Open the matching ROM in the desktop editor and open/select the intended
   numeric 2D curve or 3D table. Finish any pending document operation.
2. Load a CSV into Log Analysis and open **Map trace**. Select **Capture selected
   editor table…** and review the document, table dimensions and axis names/units.
   Cancel preserves the previous snapshot. A closed or changed editor selection
   during review rejects capture.
3. Explicitly choose the CSV channel for the horizontal axis and, for a 3D table,
   the vertical axis. Channel choices include column numbers to distinguish
   duplicate labels. Confirm the unit correspondence; no channels or unit
   conversions are inferred. Selecting another channel clears confirmation.
4. Seek, step or play the saved log using the normal Log Analysis controls.
   The highlighted table neighbors and sample label follow that same cursor.

The captured identity remains prominently labeled **frozen snapshot**. Later
ROM edits, changing editor tables or closing the editor do not silently replace
its values or axes. Capture and review again to refresh it; a new capture clears
the axis mappings and confirmation. Loading a different CSV disposes the old
trace along with its Log Analysis pane. The snapshot/mappings are not exported
in fuel-analysis setup files.

## What the highlight means

For a 2D curve, a covered sample identifies one exact breakpoint or two adjacent
breakpoints. For a 3D table, it identifies up to four neighboring cells. The
displayed linear/bilinear weights describe the geometric position between the
captured axes. They are **not verified ECU interpolation weights** or proof
that this table was active during the recorded sample. No interpolated target,
fuel correction, tuning recommendation or ROM edit is generated.

Rows and columns use explicit R/C indices that correspond to the displayed
table orientation, including storage swap/flip arrangements. Ascending and
descending axes are supported. Exact breakpoints collapse to the appropriate
cell/edge without duplicate neighbors. A singleton axis covers only its exact
breakpoint. The highlight is a separate read-only grid; it does not move the
editor's selection or modify its table.

Nonfinite mapped readings clear the highlight and show a missing-reading state.
Values outside either captured axis clear it and show an out-of-range state:
there is no guessed endpoint clamping or extrapolation. Duplicate, folded,
nonnumeric or missing axes are rejected rather than replaced with ordinal
indices. Numeric static axes are supported. Nonfinite/non-numeric table values
are shown as unavailable; axis geometry does not invent numeric values for them.

Tracing uses the exact numeric breakpoints, not rounded editor labels. Compact
display values use nine significant digits; header/cell tooltips retain the
underlying double values and units. Opposite extreme finite breakpoints are
handled without overflowing the interpolation denominator.

## Range and resource boundaries

The normal applied Log Analysis range bounds playback. A pending shared-range
draft clears the trace and asks for Apply; applying restores tracing at the
range-clamped cursor. The trace uses the Log Analysis cursor, not the separate
MAF/Injector accepted-row subset, and applies no fuel-analysis filters itself.

Capture allows up to 256 breakpoints per axis and 8,192 displayed cells. The
snapshot retains numeric arrays and labels, not a live ROM/table reference.
Capture runs on the editor's serialized UI thread; each cursor update locates
at most four neighbors within the bounded axes. No extra playback clock,
hardware polling or background vehicle session is created. This does not make
the general CSV loader or whole-log analysis bounded/asynchronous.

Synthetic tests cover both axis directions, exact/edge/outside/missing values,
singleton and ambiguous axes, precision/extreme values, frozen snapshots,
resource-limit refusal and all eight 3D storage orientations against the editor
projection. Native checks cover mapping confirmation, reviewed capture/cancel,
stale editor selection, playback/range linkage, actual highlighted cells in a
1,000 × 640 workspace, editor-target lifetimes and unchanged ROM bytes/history.
These are software checks, not vehicle or calibration acceptance.

Local qualification passed seven core geometry tests, the full Ant unit suite
(optional private-corpus cases remain skipped), Linux core packaging, 138 JavaFX
tests (six dedicated map-trace cases), 35 Compose tests, portable-core checks and
Linux JavaFX staging. The actual compact rendering was inspected after testing.
