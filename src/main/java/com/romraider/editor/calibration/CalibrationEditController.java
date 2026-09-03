/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

import java.util.List;

/** UI-neutral command boundary for one active calibration table. */
public interface CalibrationEditController extends AutoCloseable {
    CalibrationGridSnapshot getSnapshot();

    CalibrationEditResult setCellValue(int row, int column, String value)
            throws CalibrationEditException;

    CalibrationEditBatchResult setCellValues(List<CalibrationCellEdit> edits)
            throws CalibrationEditException;

    CalibrationAxisEditResult setAxisValue(CalibrationAxis axis, int index,
            String value) throws CalibrationEditException;

    CalibrationEditResult adjustCellValue(int row, int column,
            CalibrationAdjustment adjustment) throws CalibrationEditException;

    CalibrationEditBatchResult adjustCellValues(
            List<CalibrationCellCoordinate> cells,
            CalibrationAdjustment adjustment) throws CalibrationEditException;

    CalibrationEditResult restoreCellValue(int row, int column)
            throws CalibrationEditException;

    CalibrationEditBatchResult restoreCellValues(
            List<CalibrationCellCoordinate> cells)
            throws CalibrationEditException;

    CalibrationEditBatchResult interpolate(int startRow, int startColumn,
            int endRow, int endColumn, CalibrationInterpolation direction)
            throws CalibrationEditException;

    boolean canUndo();
    boolean canRedo();
    CalibrationGridSnapshot undo() throws CalibrationEditException;
    CalibrationGridSnapshot redo() throws CalibrationEditException;

    void addListener(CalibrationEditListener listener);
    void removeListener(CalibrationEditListener listener);

    @Override
    void close();
}
