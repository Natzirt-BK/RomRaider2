/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.romraider.maps.DataCell;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.maps.Table2D;
import com.romraider.maps.Table3D;

/** Calculates real calibration changes without depending on Swing views. */
public final class RomChangeSummary {
    private RomChangeSummary() {
    }

    public static int countChangedCells(Rom rom) {
        if (rom == null) return 0;
        int changed = 0;
        for (Table table : rom.getTables()) changed += countChangedCells(table);
        return changed;
    }

    /** Returns only ROMs with real calibration changes, preserving UI order. */
    public static List<Rom> changedRoms(Iterable<? extends Rom> roms) {
        if (roms == null) return Collections.emptyList();
        List<Rom> changed = new ArrayList<Rom>();
        for (Rom rom : roms) {
            if (rom != null && (countChangedCells(rom) > 0
                    || RomChangeService.hasBinaryChanges(rom))) changed.add(rom);
        }
        return Collections.unmodifiableList(changed);
    }

    public static List<TableChangeSummary> summarize(Rom rom) {
        if (rom == null) return Collections.emptyList();
        return summarizeTables(rom.getTables());
    }

    public static List<TableChangeSummary> summarizeTables(
            Iterable<? extends Table> tables) {
        if (tables == null) return Collections.emptyList();
        List<TableChangeSummary> summaries = new ArrayList<TableChangeSummary>();
        for (Table table : tables) {
            int changed = countChangedCells(table);
            if (changed > 0) {
                String name = table.getName();
                if (name == null || name.trim().isEmpty()) name = "Unnamed table";
                summaries.add(new TableChangeSummary(name, changed));
            }
        }
        Collections.sort(summaries, (first, second) -> {
            int byCount = Integer.compare(second.getChangedCells(),
                    first.getChangedCells());
            return byCount != 0 ? byCount
                    : first.getTableName().compareToIgnoreCase(second.getTableName());
        });
        return Collections.unmodifiableList(summaries);
    }

    public static int countChangedCells(Table table) {
        if (table == null) return 0;
        if (table instanceof Table3D) {
            Table3D surface = (Table3D) table;
            int changed = count(surface.get3dData());
            changed += count(surface.getXAxis() == null
                    ? null : surface.getXAxis().getData());
            changed += count(surface.getYAxis() == null
                    ? null : surface.getYAxis().getData());
            return changed;
        }
        int changed = count(table.getData());
        if (table instanceof Table2D) {
            Table2D curve = (Table2D) table;
            changed += count(curve.getAxis() == null
                    ? null : curve.getAxis().getData());
        }
        return changed;
    }

    private static int count(DataCell[] cells) {
        if (cells == null) return 0;
        int changed = 0;
        for (DataCell cell : cells) {
            if (cell != null && Double.compare(cell.getBinValue(),
                    cell.getOriginalValue()) != 0) changed++;
        }
        return changed;
    }

    private static int count(DataCell[][] cells) {
        if (cells == null) return 0;
        int changed = 0;
        for (DataCell[] row : cells) changed += count(row);
        return changed;
    }
}
