/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

import com.romraider.maps.Rom;
import com.romraider.maps.DataCell;
import com.romraider.maps.Table;
import com.romraider.maps.Table2D;
import com.romraider.maps.Table3D;
import com.romraider.maps.UserLevelException;
import com.romraider.maps.history.EditTransaction;
import com.romraider.maps.history.RomEditHistory;

/** UI-independent saved-state and reset operations for an open ROM. */
public final class RomChangeService {
    private static final Map<Rom, byte[]> savedBinary =
            new WeakHashMap<Rom, byte[]>();
    private static final Set<Rom> forcedUnsaved = Collections.newSetFromMap(
            new WeakHashMap<Rom, Boolean>());

    private RomChangeService() {
    }

    public static void resetToSaved(Rom rom) throws UserLevelException {
        if (rom == null) return;
        try (EditTransaction edit = RomEditHistory.getInstance().begin(
                rom, "Reset ROM changes")) {
            for (Table table : rom.getTables()) table.undoAll();
        }
    }

    public static void markSaved(Rom rom) {
        if (rom == null) return;
        for (Table table : rom.getTables()) table.setRevertPoint();
        rememberSavedBinary(rom);
    }

    /** Capture on the document-owning thread, before starting background I/O. */
    public static SavedState captureSavedState(Rom rom, byte[] output) {
        Map<DataCell, Double> cells = new IdentityHashMap<DataCell, Double>();
        for (Table table : rom.getTables()) captureCells(table, cells);
        return new SavedState(output.clone(), cells);
    }

    private static void captureCells(Table table, Map<DataCell, Double> cells) {
        if (table == null) return;
        if (table instanceof Table3D) {
            Table3D surface = (Table3D) table;
            for (DataCell[] row : surface.get3dData()) captureCells(row, cells);
            captureCells(surface.getXAxis(), cells);
            captureCells(surface.getYAxis(), cells);
        } else {
            captureCells(table.getData(), cells);
            if (table instanceof Table2D) captureCells(((Table2D) table).getAxis(), cells);
        }
    }

    private static void captureCells(DataCell[] values, Map<DataCell, Double> cells) {
        if (values == null) return;
        for (DataCell cell : values) {
            if (cell != null) cells.put(cell, cell.getBinValue());
        }
    }

    /** Complete on the document-owning thread; later edits remain dirty. */
    public static void markSaved(Rom rom, SavedState saved) {
        saved.cells.forEach(DataCell::setRevertPoint);
        synchronized (RomChangeService.class) {
            savedBinary.put(rom, saved.binary.clone());
            forcedUnsaved.remove(rom);
        }
    }

    public static final class SavedState {
        private final byte[] binary;
        private final Map<DataCell, Double> cells;

        private SavedState(byte[] binary, Map<DataCell, Double> cells) {
            this.binary = binary;
            this.cells = cells;
        }
    }

    /** Keeps a recovered workspace dirty until it is explicitly saved. */
    public static synchronized void markUnsaved(Rom rom) {
        if (rom != null) forcedUnsaved.add(rom);
    }

    /** Captures the file state after a ROM has finished loading. */
    public static synchronized void rememberSavedBinary(Rom rom) {
        if (rom == null) return;
        byte[] binary = rom.getBinary();
        savedBinary.put(rom, binary == null ? null : binary.clone());
        forcedUnsaved.remove(rom);
    }

    /** Detects every binary change, including data not represented by a table. */
    public static synchronized boolean hasBinaryChanges(Rom rom) {
        if (rom == null || !savedBinary.containsKey(rom)) return false;
        return forcedUnsaved.contains(rom)
                || !Arrays.equals(savedBinary.get(rom), rom.getBinary());
    }

    /** Returns a defensive copy of the last loaded or explicitly saved state. */
    public static synchronized byte[] snapshotSavedBinary(Rom rom) {
        byte[] binary = savedBinary.get(rom);
        return binary == null ? null : binary.clone();
    }

    public static synchronized void forget(Rom rom) {
        if (rom != null) {
            savedBinary.remove(rom);
            forcedUnsaved.remove(rom);
        }
    }
}
