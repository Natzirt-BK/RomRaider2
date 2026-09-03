/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

import com.romraider.editor.calibration.CalibrationGridSnapshot;
import com.romraider.editor.calibration.CalibrationEditController;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;

/** Immutable input passed to an optional replacement calibration view. */
public final class CalibrationWorkspaceContext {
    private final CalibrationGridSnapshot snapshot;
    private final CalibrationEditController editController;
    private final Rom rom;
    private final Table table;

    public CalibrationWorkspaceContext(CalibrationGridSnapshot snapshot) {
        this(snapshot, null);
    }

    public CalibrationWorkspaceContext(CalibrationGridSnapshot snapshot,
            CalibrationEditController editController) {
        this(snapshot, editController, null, null);
    }

    public CalibrationWorkspaceContext(CalibrationGridSnapshot snapshot,
            CalibrationEditController editController, Rom rom, Table table) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "A calibration grid snapshot is required");
        }
        this.snapshot = snapshot;
        this.editController = editController;
        this.rom = rom;
        this.table = table;
    }

    public CalibrationGridSnapshot getSnapshot() {
        return snapshot;
    }

    public CalibrationEditController getEditController() {
        return editController;
    }

    public Rom getRom() { return rom; }
    public Table getTable() { return table; }

    public boolean isEditable() {
        return editController != null;
    }
}
