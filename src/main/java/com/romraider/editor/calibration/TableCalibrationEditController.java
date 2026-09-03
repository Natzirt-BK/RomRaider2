/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.romraider.maps.DataCell;
import com.romraider.maps.Table;
import com.romraider.maps.Table2D;
import com.romraider.maps.Table3D;
import com.romraider.maps.UserLevelException;
import com.romraider.maps.history.EditTransaction;
import com.romraider.maps.history.EditHistoryListener;
import com.romraider.maps.history.RomEditHistory;

/** Applies replacement-UI commands through the established table and history. */
public final class TableCalibrationEditController
        implements CalibrationEditController {
    private final Table table;
    private final RomEditHistory history;
    private final List<CalibrationEditListener> listeners =
            new CopyOnWriteArrayList<CalibrationEditListener>();
    private final EditHistoryListener historyListener;
    private boolean historyAttached;

    public TableCalibrationEditController(Table table) {
        this(table, RomEditHistory.getInstance());
    }

    TableCalibrationEditController(Table table, RomEditHistory history) {
        if (table == null) {
            throw new IllegalArgumentException("A calibration table is required");
        }
        if (history == null) {
            throw new IllegalArgumentException("Edit history is required");
        }
        this.table = table;
        this.history = history;
        historyListener = rom -> {
            if (rom == table.getRom()) notifyListeners();
        };
    }

    @Override
    public CalibrationGridSnapshot getSnapshot() {
        return CalibrationGridProjectionService.project(table);
    }

    @Override
    public CalibrationEditResult setCellValue(int row, int column,
            String value) throws CalibrationEditException {
        ensureEditable();
        String normalized = validateNumber(value);
        CalibrationGridSnapshot before = getSnapshot();
        CalibrationCellSnapshot previous = before.cellAt(row, column);
        DataCell cell = cellAt(row, column);
        double oldRaw = cell.getBinValue();
        try (EditTransaction ignored = history.begin(table,
                "Set " + safeName(table.getName()) + " value")) {
            cell.setRealValue(normalized);
        } catch (UserLevelException failure) {
            throw new CalibrationEditException(
                    "Your current user level cannot edit this table.", failure);
        } catch (RuntimeException failure) {
            throw new CalibrationEditException(
                    "The calibration value could not be applied.", failure);
        }
        CalibrationGridSnapshot after = getSnapshot();
        CalibrationCellSnapshot current = after.cellAt(row, column);
        return new CalibrationEditResult(row, column,
                previous.getDisplayValue(), current.getDisplayValue(),
                Double.compare(oldRaw, cell.getBinValue()) != 0, after);
    }

    @Override
    public CalibrationEditBatchResult setCellValues(
            List<CalibrationCellEdit> edits) throws CalibrationEditException {
        ensureEditable();
        if (edits == null || edits.isEmpty()) {
            throw new CalibrationEditException(
                    "Paste at least one calibration value.");
        }
        List<PreparedEdit> prepared = new ArrayList<PreparedEdit>();
        Set<String> coordinates = new HashSet<String>();
        for (CalibrationCellEdit edit : edits) {
            if (edit == null) {
                throw new CalibrationEditException(
                        "The calibration block contains an empty cell edit.");
            }
            String coordinate = edit.getRow() + ":" + edit.getColumn();
            if (!coordinates.add(coordinate)) {
                throw new CalibrationEditException(
                        "The calibration block contains a duplicate cell.");
            }
            String value = validateNumber(edit.getValue());
            DataCell cell;
            try {
                cell = cellAt(edit.getRow(), edit.getColumn());
            } catch (IndexOutOfBoundsException outside) {
                throw new CalibrationEditException(
                        "The pasted values do not fit in this table.", outside);
            }
            prepared.add(new PreparedEdit(cell, value, cell.getBinValue()));
        }

        int changed = 0;
        try (EditTransaction ignored = history.begin(table,
                "Paste " + prepared.size() + " values into "
                        + safeName(table.getName()))) {
            try {
                for (PreparedEdit edit : prepared) {
                    edit.cell.setRealValue(edit.value);
                    if (Double.compare(edit.oldRaw,
                            edit.cell.getBinValue()) != 0) changed++;
                }
            } catch (UserLevelException failure) {
                rollback(prepared, failure);
                throw new CalibrationEditException(
                        "Your current user level cannot edit this table.",
                        failure);
            } catch (RuntimeException failure) {
                rollback(prepared, failure);
                throw new CalibrationEditException(
                        "The calibration block could not be applied.", failure);
            }
        }
        return new CalibrationEditBatchResult(prepared.size(), changed,
                getSnapshot());
    }

    @Override
    public CalibrationAxisEditResult setAxisValue(CalibrationAxis axis,
            int index, String value) throws CalibrationEditException {
        ensureEditable();
        if (axis == null) {
            throw new CalibrationEditException("Choose a calibration axis.");
        }
        String normalized = validateNumber(value);
        Table axisTable = axisTable(axis);
        if (axisTable.isLocked()) {
            throw new CalibrationEditException("This calibration axis is locked.");
        }
        if (index < 0 || index >= axisTable.getDataSize()) {
            throw new CalibrationEditException(
                    "The selected axis value is outside this calibration.");
        }
        CalibrationGridSnapshot before = getSnapshot();
        String previous = axisLabel(before, axis, index);
        DataCell cell = axisTable.getDataCell(index);
        double oldRaw = cell.getBinValue();
        String axisName = axis == CalibrationAxis.ROW ? "Y" : "X";
        try (EditTransaction ignored = history.begin(table,
                "Set " + safeName(table.getName()) + " " + axisName
                        + " axis value")) {
            cell.setRealValue(normalized);
        } catch (UserLevelException failure) {
            throw new CalibrationEditException(
                    "Your current user level cannot edit this axis.", failure);
        } catch (RuntimeException failure) {
            throw new CalibrationEditException(
                    "The calibration axis value could not be applied.", failure);
        }
        CalibrationGridSnapshot after = getSnapshot();
        return new CalibrationAxisEditResult(axis, index, previous,
                axisLabel(after, axis, index),
                Double.compare(oldRaw, cell.getBinValue()) != 0, after);
    }

    @Override
    public CalibrationEditResult adjustCellValue(int row, int column,
            CalibrationAdjustment adjustment) throws CalibrationEditException {
        ensureEditable();
        if (adjustment == null) {
            throw new CalibrationEditException(
                    "Choose a fine or coarse adjustment.");
        }
        CalibrationGridSnapshot before = getSnapshot();
        CalibrationCellSnapshot previous = before.cellAt(row, column);
        DataCell cell = cellAt(row, column);
        double oldRaw = cell.getBinValue();
        if (table.getCurrentScale() == null) {
            throw new CalibrationEditException(
                    "This table does not have an active scale.");
        }
        double step = Math.abs(adjustment.isCoarse()
                ? table.getCurrentScale().getCoarseIncrement()
                : table.getCurrentScale().getFineIncrement());
        if (!Double.isFinite(step) || step <= 0.0) {
            throw new CalibrationEditException(
                    "This table does not define a usable increment.");
        }
        try (EditTransaction ignored = history.begin(table,
                (adjustment.getDirection() > 0 ? "Increase " : "Decrease ")
                        + safeName(table.getName()))) {
            cell.increment(step * adjustment.getDirection());
        } catch (UserLevelException failure) {
            throw new CalibrationEditException(
                    "Your current user level cannot edit this table.", failure);
        } catch (RuntimeException failure) {
            throw new CalibrationEditException(
                    "The calibration value could not be adjusted.", failure);
        }
        CalibrationGridSnapshot after = getSnapshot();
        CalibrationCellSnapshot current = after.cellAt(row, column);
        return new CalibrationEditResult(row, column,
                previous.getDisplayValue(), current.getDisplayValue(),
                Double.compare(oldRaw, cell.getBinValue()) != 0, after);
    }

    @Override
    public CalibrationEditBatchResult adjustCellValues(
            List<CalibrationCellCoordinate> cells,
            CalibrationAdjustment adjustment) throws CalibrationEditException {
        ensureEditable();
        if (adjustment == null) {
            throw new CalibrationEditException(
                    "Choose a fine or coarse adjustment.");
        }
        if (table.getCurrentScale() == null) {
            throw new CalibrationEditException(
                    "This table does not have an active scale.");
        }
        double step = Math.abs(adjustment.isCoarse()
                ? table.getCurrentScale().getCoarseIncrement()
                : table.getCurrentScale().getFineIncrement());
        if (!Double.isFinite(step) || step <= 0.0) {
            throw new CalibrationEditException(
                    "This table does not define a usable increment.");
        }
        List<PreparedCell> prepared = prepareCells(cells);
        try (EditTransaction ignored = history.begin(table,
                (adjustment.getDirection() > 0 ? "Increase " : "Decrease ")
                        + prepared.size() + " " + safeName(table.getName())
                        + " values")) {
            try {
                for (PreparedCell edit : prepared) {
                    edit.cell.increment(step * adjustment.getDirection());
                }
            } catch (UserLevelException failure) {
                rollbackCells(prepared, failure);
                throw new CalibrationEditException(
                        "Your current user level cannot edit this table.",
                        failure);
            } catch (RuntimeException failure) {
                rollbackCells(prepared, failure);
                throw new CalibrationEditException(
                        "The selected calibration values could not be adjusted.",
                        failure);
            }
        }
        return batchResult(prepared);
    }

    @Override
    public CalibrationEditResult restoreCellValue(int row, int column)
            throws CalibrationEditException {
        ensureEditable();
        CalibrationGridSnapshot before = getSnapshot();
        CalibrationCellSnapshot previous = before.cellAt(row, column);
        DataCell cell = cellAt(row, column);
        double oldRaw = cell.getBinValue();
        try (EditTransaction ignored = history.begin(table,
                "Restore " + safeName(table.getName()) + " value")) {
            cell.setBinValue(cell.getOriginalValue());
        } catch (UserLevelException failure) {
            throw new CalibrationEditException(
                    "Your current user level cannot edit this table.", failure);
        } catch (RuntimeException failure) {
            throw new CalibrationEditException(
                    "The saved calibration value could not be restored.",
                    failure);
        }
        CalibrationGridSnapshot after = getSnapshot();
        CalibrationCellSnapshot current = after.cellAt(row, column);
        return new CalibrationEditResult(row, column,
                previous.getDisplayValue(), current.getDisplayValue(),
                Double.compare(oldRaw, cell.getBinValue()) != 0, after);
    }

    @Override
    public CalibrationEditBatchResult restoreCellValues(
            List<CalibrationCellCoordinate> cells)
            throws CalibrationEditException {
        ensureEditable();
        List<PreparedCell> prepared = prepareCells(cells);
        try (EditTransaction ignored = history.begin(table,
                "Restore " + prepared.size() + " "
                        + safeName(table.getName()) + " values")) {
            try {
                for (PreparedCell edit : prepared) {
                    edit.cell.setBinValue(edit.cell.getOriginalValue());
                }
            } catch (UserLevelException failure) {
                rollbackCells(prepared, failure);
                throw new CalibrationEditException(
                        "Your current user level cannot edit this table.",
                        failure);
            } catch (RuntimeException failure) {
                rollbackCells(prepared, failure);
                throw new CalibrationEditException(
                        "The selected calibration values could not be restored.",
                        failure);
            }
        }
        return batchResult(prepared);
    }

    private List<PreparedCell> prepareCells(
            List<CalibrationCellCoordinate> cells)
            throws CalibrationEditException {
        if (cells == null || cells.isEmpty()) {
            throw new CalibrationEditException(
                    "Select at least one calibration value.");
        }
        List<PreparedCell> prepared = new ArrayList<PreparedCell>();
        Set<String> coordinates = new HashSet<String>();
        for (CalibrationCellCoordinate coordinate : cells) {
            if (coordinate == null) {
                throw new CalibrationEditException(
                        "The calibration selection contains an empty cell.");
            }
            String key = coordinate.getRow() + ":" + coordinate.getColumn();
            if (!coordinates.add(key)) {
                throw new CalibrationEditException(
                        "The calibration selection contains a duplicate cell.");
            }
            try {
                DataCell cell = cellAt(coordinate.getRow(),
                        coordinate.getColumn());
                prepared.add(new PreparedCell(cell, cell.getBinValue()));
            } catch (IndexOutOfBoundsException outside) {
                throw new CalibrationEditException(
                        "The calibration selection does not fit in this table.",
                        outside);
            }
        }
        return prepared;
    }

    private CalibrationEditBatchResult batchResult(List<PreparedCell> cells) {
        int changed = 0;
        for (PreparedCell cell : cells) {
            if (Double.compare(cell.oldRaw, cell.cell.getBinValue()) != 0) {
                changed++;
            }
        }
        return new CalibrationEditBatchResult(cells.size(), changed,
                getSnapshot());
    }

    private static void rollbackCells(List<PreparedCell> cells,
            Throwable failure) {
        for (PreparedCell cell : cells) {
            try {
                cell.cell.setBinValue(cell.oldRaw);
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    @Override
    public CalibrationEditBatchResult interpolate(int startRow,
            int startColumn, int endRow, int endColumn,
            CalibrationInterpolation direction)
            throws CalibrationEditException {
        ensureEditable();
        if (direction == null) {
            throw new CalibrationEditException(
                    "Choose a horizontal, vertical, or combined interpolation.");
        }
        CalibrationGridSnapshot snapshot = getSnapshot();
        int firstRow = Math.min(startRow, endRow);
        int lastRow = Math.max(startRow, endRow);
        int firstColumn = Math.min(startColumn, endColumn);
        int lastColumn = Math.max(startColumn, endColumn);
        if (firstRow < 0 || lastRow >= snapshot.getRows()
                || firstColumn < 0
                || lastColumn >= snapshot.getColumns()) {
            throw new CalibrationEditException(
                    "The interpolation range does not fit in this table.");
        }
        boolean horizontal = direction != CalibrationInterpolation.VERTICAL
                && lastColumn - firstColumn > 1;
        boolean vertical = direction != CalibrationInterpolation.HORIZONTAL
                && lastRow - firstRow > 1;
        if (!horizontal && !vertical) {
            throw new CalibrationEditException(
                    "Select at least three cells across or down to interpolate.");
        }

        Map<DataCell, Double> originals = new LinkedHashMap<DataCell, Double>();
        if (vertical) {
            for (int column = firstColumn; column <= lastColumn; column++) {
                for (int row = firstRow + 1; row < lastRow; row++) {
                    remember(originals, cellAt(row, column));
                }
            }
        }
        if (horizontal) {
            for (int row = firstRow; row <= lastRow; row++) {
                for (int column = firstColumn + 1;
                        column < lastColumn; column++) {
                    remember(originals, cellAt(row, column));
                }
            }
        }

        try (EditTransaction ignored = history.begin(table,
                "Interpolate " + safeName(table.getName()))) {
            try {
                if (vertical) interpolateVertical(firstRow, lastRow,
                        firstColumn, lastColumn);
                if (horizontal) interpolateHorizontal(firstRow, lastRow,
                        firstColumn, lastColumn);
            } catch (UserLevelException failure) {
                rollbackRaw(originals, failure);
                throw new CalibrationEditException(
                        "Your current user level cannot interpolate this table.",
                        failure);
            } catch (RuntimeException failure) {
                rollbackRaw(originals, failure);
                throw new CalibrationEditException(
                        "The selected values could not be interpolated.", failure);
            }
        }
        int changed = 0;
        for (Map.Entry<DataCell, Double> entry : originals.entrySet()) {
            if (Double.compare(entry.getValue(),
                    entry.getKey().getBinValue()) != 0) changed++;
        }
        return new CalibrationEditBatchResult(originals.size(), changed,
                getSnapshot());
    }

    private void interpolateVertical(int firstRow, int lastRow,
            int firstColumn, int lastColumn) throws UserLevelException {
        for (int column = firstColumn; column <= lastColumn; column++) {
            double startPosition = rowPosition(firstRow);
            double endPosition = rowPosition(lastRow);
            double startValue = cellAt(firstRow, column).getBinValue();
            double endValue = cellAt(lastRow, column).getBinValue();
            for (int row = firstRow + 1; row < lastRow; row++) {
                cellAt(row, column).setBinValue(interpolate(
                        rowPosition(row), startPosition, endPosition,
                        startValue, endValue));
            }
        }
    }

    private void interpolateHorizontal(int firstRow, int lastRow,
            int firstColumn, int lastColumn) throws UserLevelException {
        for (int row = firstRow; row <= lastRow; row++) {
            double startPosition = columnPosition(firstColumn);
            double endPosition = columnPosition(lastColumn);
            double startValue = cellAt(row, firstColumn).getBinValue();
            double endValue = cellAt(row, lastColumn).getBinValue();
            for (int column = firstColumn + 1;
                    column < lastColumn; column++) {
                cellAt(row, column).setBinValue(interpolate(
                        columnPosition(column), startPosition, endPosition,
                        startValue, endValue));
            }
        }
    }

    private double rowPosition(int row) {
        if (table instanceof Table3D) {
            return ((Table3D) table).getYAxis().getDataCell(row).getBinValue();
        }
        return row;
    }

    private double columnPosition(int column) {
        if (table instanceof Table3D) {
            return ((Table3D) table).getXAxis().getDataCell(column)
                    .getBinValue();
        }
        if (table instanceof Table2D) {
            return ((Table2D) table).getAxis().getDataCell(column)
                    .getBinValue();
        }
        return column;
    }

    private static double interpolate(double position, double startPosition,
            double endPosition, double startValue, double endValue) {
        if (Double.compare(startPosition, endPosition) == 0) return startValue;
        return startValue + (position - startPosition)
                * (endValue - startValue) / (endPosition - startPosition);
    }

    private static void remember(Map<DataCell, Double> originals,
            DataCell cell) {
        if (!originals.containsKey(cell)) {
            originals.put(cell, cell.getBinValue());
        }
    }

    private static void rollbackRaw(Map<DataCell, Double> originals,
            Throwable failure) {
        for (Map.Entry<DataCell, Double> entry : originals.entrySet()) {
            try {
                entry.getKey().setBinValue(entry.getValue());
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    @Override
    public boolean canUndo() {
        return table.getRom() != null && history.canUndo(table.getRom());
    }

    @Override
    public boolean canRedo() {
        return table.getRom() != null && history.canRedo(table.getRom());
    }

    @Override
    public CalibrationGridSnapshot undo() throws CalibrationEditException {
        ensureRom();
        try {
            history.undo(table.getRom());
            return getSnapshot();
        } catch (UserLevelException failure) {
            throw new CalibrationEditException(
                    "The last calibration change could not be undone.", failure);
        }
    }

    @Override
    public CalibrationGridSnapshot redo() throws CalibrationEditException {
        ensureRom();
        try {
            history.redo(table.getRom());
            return getSnapshot();
        } catch (UserLevelException failure) {
            throw new CalibrationEditException(
                    "The calibration change could not be redone.", failure);
        }
    }

    @Override
    public synchronized void addListener(CalibrationEditListener listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        if (!historyAttached) {
            history.addListener(historyListener);
            historyAttached = true;
        }
    }

    @Override
    public synchronized void removeListener(CalibrationEditListener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) detachHistory();
    }

    @Override
    public synchronized void close() {
        listeners.clear();
        detachHistory();
    }

    private void notifyListeners() {
        CalibrationGridSnapshot snapshot = getSnapshot();
        for (CalibrationEditListener listener : listeners) {
            listener.calibrationChanged(snapshot);
        }
    }

    private void detachHistory() {
        if (!historyAttached) return;
        history.removeListener(historyListener);
        historyAttached = false;
    }


    private void ensureEditable() throws CalibrationEditException {
        ensureRom();
        if (table.isStaticDataTable()) {
            throw new CalibrationEditException(
                    "This preview cannot edit a text or switch table.");
        }
        if (table.isLocked()) {
            throw new CalibrationEditException("This table is locked.");
        }
    }

    private void ensureRom() throws CalibrationEditException {
        if (table.getRom() == null) {
            throw new CalibrationEditException(
                    "The table is not attached to an open ROM.");
        }
    }

    private DataCell cellAt(int row, int column) {
        if (table instanceof Table3D) {
            Table3D table3d = (Table3D) table;
            if (row < 0 || row >= table3d.getSizeY()
                    || column < 0 || column >= table3d.getSizeX()) {
                throw new IndexOutOfBoundsException(
                        "Calibration cell " + row + "," + column);
            }
            return table3d.get3dData()[column][row];
        }
        if (row != 0 || column < 0 || column >= table.getDataSize()) {
            throw new IndexOutOfBoundsException(
                    "Calibration cell " + row + "," + column);
        }
        return table.getDataCell(column);
    }

    private Table axisTable(CalibrationAxis axis)
            throws CalibrationEditException {
        if (table instanceof Table3D) {
            Table3D surface = (Table3D) table;
            return axis == CalibrationAxis.ROW
                    ? surface.getYAxis() : surface.getXAxis();
        }
        if (table instanceof Table2D && axis == CalibrationAxis.COLUMN) {
            return ((Table2D) table).getAxis();
        }
        throw new CalibrationEditException(axis == CalibrationAxis.ROW
                ? "This calibration does not have a row axis."
                : "This calibration does not have a column axis.");
    }

    private static String axisLabel(CalibrationGridSnapshot snapshot,
            CalibrationAxis axis, int index) {
        List<String> labels = axis == CalibrationAxis.ROW
                ? snapshot.getRowLabels() : snapshot.getColumnLabels();
        return labels.get(index);
    }

    private static void rollback(List<PreparedEdit> edits, Throwable failure) {
        for (PreparedEdit edit : edits) {
            if (Double.compare(edit.oldRaw, edit.cell.getBinValue()) == 0) {
                continue;
            }
            try {
                edit.cell.setBinValue(edit.oldRaw);
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static final class PreparedEdit {
        private final DataCell cell;
        private final String value;
        private final double oldRaw;

        private PreparedEdit(DataCell cell, String value, double oldRaw) {
            this.cell = cell;
            this.value = value;
            this.oldRaw = oldRaw;
        }
    }

    private static final class PreparedCell {
        private final DataCell cell;
        private final double oldRaw;

        private PreparedCell(DataCell cell, double oldRaw) {
            this.cell = cell;
            this.oldRaw = oldRaw;
        }
    }

    private static String validateNumber(String value)
            throws CalibrationEditException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new CalibrationEditException("Enter a calibration value.");
        }
        NumberFormat format = NumberFormat.getNumberInstance(
                Locale.getDefault());
        ParsePosition position = new ParsePosition(0);
        Number parsed = format.parse(normalized, position);
        if (parsed == null || position.getIndex() != normalized.length()
                || !Double.isFinite(parsed.doubleValue())) {
            throw new CalibrationEditException(
                    "Enter one complete finite number.");
        }
        return Double.toString(parsed.doubleValue());
    }

    private static String safeName(String name) {
        return name == null || name.trim().isEmpty()
                ? "calibration" : name.trim();
    }
}
