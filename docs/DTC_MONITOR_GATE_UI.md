# DTC monitor-gate presentation

Some definitions expose a diagnostic monitor entry gate, not every path capable
of reporting a code. Multiple monitor paths can report the same code.
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

This UI change is not included in the already-published 1.1.3 RC1 packages.
