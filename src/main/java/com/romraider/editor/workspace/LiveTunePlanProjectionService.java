/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.romraider.Settings;
import com.romraider.io.transport.EcuIdentity;
import com.romraider.livetune.LiveTuneChange;
import com.romraider.livetune.LiveTuneDraft;
import com.romraider.livetune.LiveTunePlan;
import com.romraider.maps.DataCell;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.maps.Table2D;
import com.romraider.maps.Table3D;

/** Projects changed bytes in real Editor tables into a staged mock plan. */
public final class LiveTunePlanProjectionService {
    private LiveTunePlanProjectionService() {
    }

    public static LiveTunePlan project(EcuIdentity expectedIdentity, Rom rom,
            Collection<Table> tables) {
        return preview(rom, tables).bindTo(expectedIdentity);
    }

    public static LiveTuneDraft preview(Rom rom,
            Collection<Table> tables) {
        if (rom == null || tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException(
                    "A ROM and at least one table are required");
        }
        byte[] saved = RomChangeService.snapshotSavedBinary(rom);
        byte[] current = rom.getBinary();
        if (saved == null || current == null || saved.length != current.length) {
            throw new IllegalStateException(
                    "The loaded ROM needs a valid saved-state snapshot");
        }
        Set<Table> unique = new LinkedHashSet<Table>(tables);
        if (unique.contains(null) || !rom.getTables().containsAll(unique)) {
            throw new IllegalArgumentException(
                    "Every projected table must belong to the loaded ROM");
        }
        List<LiveTuneChange> changes = new ArrayList<LiveTuneChange>();
        for (Table table : unique) {
            projectTable(table, saved, current, changes);
        }
        if (changes.isEmpty()) {
            throw new IllegalStateException(
                    "The selected tables have no changed bytes");
        }
        return new LiveTuneDraft(changes);
    }

    private static void projectTable(Table table, byte[] saved,
            byte[] current, List<LiveTuneChange> changes) {
        if (table.isStaticDataTable()) {
            throw new IllegalArgumentException(
                    "Static data tables cannot be staged for live tuning");
        }
        if (table instanceof Table3D) {
            Table3D surface = (Table3D) table;
            projectPart(table.getName(), surface, flatten(surface), saved,
                    current, changes);
            projectAxis(table.getName(), surface.getXAxis(), saved, current,
                    changes);
            projectAxis(table.getName(), surface.getYAxis(), saved, current,
                    changes);
            return;
        }
        projectPart(table.getName(), table, table.getData(), saved, current,
                changes);
        if (table instanceof Table2D) {
            projectAxis(table.getName(), ((Table2D) table).getAxis(), saved,
                    current, changes);
        }
    }

    private static void projectAxis(String parentName, Table axis,
            byte[] saved, byte[] current, List<LiveTuneChange> changes) {
        if (axis != null && !axis.isStaticDataTable()) {
            projectPart(parentName, axis, axis.getData(), saved, current,
                    changes);
        }
    }

    private static void projectPart(String displayName, Table part,
            DataCell[] cells, byte[] saved, byte[] current,
            List<LiveTuneChange> changes) {
        int width = storageWidth(part.getStorageType());
        Set<Integer> indexes = cellIndexes(part, cells);
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Table has no addressable data: " + displayName);
        }
        long segmentAddress = -1;
        long previousAddress = -2;
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        ByteArrayOutputStream replacement = new ByteArrayOutputStream();
        for (int cellIndex : indexes) {
            long cellAddress = Integer.toUnsignedLong(part.getStorageAddress())
                    + Math.multiplyExact((long) cellIndex, width);
            long cellFileOffset = cellAddress - part.getRamOffset();
            if (cellFileOffset < 0 || cellFileOffset + width > current.length) {
                throw new IllegalArgumentException(
                        "Table data falls outside the loaded ROM: "
                                + displayName);
            }
            for (int byteIndex = 0; byteIndex < width; byteIndex++) {
                long address = cellAddress + byteIndex;
                int fileOffset = Math.toIntExact(cellFileOffset + byteIndex);
                boolean changed = saved[fileOffset] != current[fileOffset];
                boolean canAppend = changed && address == previousAddress + 1
                        && expected.size() < LiveTunePlan.MAX_CHANGE_BYTES;
                if (!changed || (!canAppend && expected.size() > 0)) {
                    flush(displayName, segmentAddress, expected, replacement,
                            changes);
                    segmentAddress = -1;
                }
                if (changed) {
                    if (segmentAddress < 0) segmentAddress = address;
                    expected.write(saved[fileOffset]);
                    replacement.write(current[fileOffset]);
                    previousAddress = address;
                } else {
                    previousAddress = address;
                }
            }
        }
        flush(displayName, segmentAddress, expected, replacement, changes);
    }

    private static Set<Integer> cellIndexes(Table part, DataCell[] cells) {
        Set<Integer> indexes = new TreeSet<Integer>();
        if (cells != null) {
            for (DataCell cell : cells) {
                if (cell != null) indexes.add(cell.getIndexInTable());
            }
        }
        if (indexes.isEmpty()) {
            if (part instanceof Table3D) {
                Table3D surface = (Table3D) part;
                int rows = surface.getSwapXY()
                        ? surface.getSizeX() : surface.getSizeY();
                int columns = surface.getSwapXY()
                        ? surface.getSizeY() : surface.getSizeX();
                for (int row = 0; row < rows; row++) {
                    for (int column = 0; column < columns; column++) {
                        indexes.add(row * (columns + surface.getSkipCells())
                                + column);
                    }
                }
            } else {
                for (int index = 0; index < part.getDataSize(); index++) {
                    indexes.add(index);
                }
            }
        }
        return indexes;
    }

    private static DataCell[] flatten(Table3D table) {
        DataCell[][] grid = table.get3dData();
        if (grid == null) return null;
        List<DataCell> cells = new ArrayList<DataCell>();
        for (DataCell[] column : grid) {
            if (column != null) cells.addAll(Arrays.asList(column));
        }
        return cells.toArray(new DataCell[cells.size()]);
    }

    private static void flush(String displayName, long address,
            ByteArrayOutputStream expected, ByteArrayOutputStream replacement,
            List<LiveTuneChange> changes) {
        if (expected.size() == 0) return;
        changes.add(new LiveTuneChange(displayName, address,
                expected.toByteArray(), replacement.toByteArray()));
        expected.reset();
        replacement.reset();
    }

    private static int storageWidth(int storageType) {
        if (storageType == Settings.STORAGE_TYPE_FLOAT) return 4;
        if (storageType == Settings.STORAGE_TYPE_MOVI20
                || storageType == Settings.STORAGE_TYPE_MOVI20S) return 3;
        if (storageType == 1 || storageType == 2 || storageType == 4) {
            return storageType;
        }
        throw new IllegalArgumentException(
                "Unsupported live-tune storage type: " + storageType);
    }
}
