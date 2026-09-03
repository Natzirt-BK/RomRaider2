/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.romraider.maps.Rom;
import com.romraider.maps.Table;

/** Immutable ROM document state exposed to replacement desktop views. */
public final class EditorDocument {
    private final Rom rom;
    private final List<Table> openTables;
    private final Table activeTable;
    private final int changedCells;
    private final boolean binaryChanged;
    private final boolean canUndo;
    private final boolean canRedo;

    EditorDocument(Rom rom, List<Table> openTables, Table activeTable,
            int changedCells, boolean binaryChanged, boolean canUndo,
            boolean canRedo) {
        this.rom = rom;
        this.openTables = Collections.unmodifiableList(
                new ArrayList<Table>(openTables));
        this.activeTable = activeTable;
        this.changedCells = changedCells;
        this.binaryChanged = binaryChanged;
        this.canUndo = canUndo;
        this.canRedo = canRedo;
    }

    public Rom getRom() { return rom; }
    public String getName() {
        String name = rom == null ? null : rom.getFileName();
        return name == null || name.trim().isEmpty() ? "Untitled ROM" : name;
    }
    public List<Table> getOpenTables() { return openTables; }
    public Table getActiveTable() { return activeTable; }
    public int getChangedCells() { return changedCells; }
    public boolean hasBinaryChanges() { return binaryChanged; }
    public boolean isDirty() { return changedCells > 0 || binaryChanged; }
    public boolean canSave() { return isDirty(); }
    public boolean canUndo() { return canUndo; }
    public boolean canRedo() { return canRedo; }
}
