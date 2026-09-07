# DTC monitor-gate presentation

Some definitions expose a diagnostic monitor entry gate, not every path capable
of reporting a code. The Evo `88780008` extension has two separate P0130 paths.
Showing either as simply "Code disabled" overstates the scope of that setting.

JavaFX and Compose now recognize the definition's explicit `monitor gate` unit
(case-insensitive, with surrounding whitespace ignored). The DTC view identifies
the setting as a monitor gate, qualifies action/status text and Compose switch accessibility text,
and displays: "This switch controls one monitor gate. Other paths may still
report this code." Other DTC definitions retain their existing presentation.
This changes no ROM addresses, masks or logger behavior.

JavaFX also extracts the code using the matched group, so leading whitespace
in a definition name no longer corrupts the displayed code.

## Verification

The JavaFX native control regression failed before the presentation fix and
passes afterward. It checks the P0130 label, scope warning, masked toggle,
neighboring-byte preservation and Restore saved state. The Compose model checks
explicit-unit recognition and retains the existing ordinary-switch behavior.
The complete local desktop suites then passed on a private Xvfb/Openbox display:
259 JavaFX tests and 46 Compose tests, with no failures, errors or skips.

An additional local probe loads the complete private Evo DTC v7 definition
through the actual core loader and opens the actual JavaFX editor at 1280 × 800:

- all eight DTC variant categories are visible at user level 1 and start collapsed;
- all 88 named switches appear in code order;
- all eleven variant-0 controls have a usable monitor-gate toggle and scope note;
- each UI toggle changes only its selected bit and Undo restores the exact bytes;
- the source ROM remains unchanged; no file save or logger callback occurs.

The successful probe used the published 1.1.3 core and dependencies plus the new
local JavaFX build. Its screenshot is an actual editor render, not a mockup.
It ran on a private Xvfb display with fresh settings. It does not qualify physical
Steam Deck display behavior, Android DTC editing, or EcuFlash parity.

Source harness: Evo-Definitions workspace, `tools/EvoDtcDesktopProbe.java`.
Compile it with `javac -proc:none` into a fresh directory before running: Java
source-launch mode uses a separate class loader and cannot access the editor's
package-private classes. The probe always exits its toolkit, including on failure.

This UI change is not included in the already-published 1.1.3 RC1 packages.
