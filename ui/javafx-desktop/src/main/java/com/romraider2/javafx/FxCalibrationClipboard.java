/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.util.ArrayList;
import java.util.List;

import com.romraider.editor.calibration.CalibrationCellEdit;
import com.romraider.editor.calibration.CalibrationGridSnapshot;

/** Spreadsheet clipboard projection kept separate from the JavaFX system clipboard. */
final class FxCalibrationClipboard {
    private FxCalibrationClipboard() { }

    static String serialize(CalibrationGridSnapshot snapshot,
            List<int[]> selected) {
        if (snapshot == null || selected == null || selected.isEmpty()) {
            throw new IllegalArgumentException("Select at least one calibration value.");
        }
        int firstRow = selected.stream().mapToInt(value -> value[0]).min()
                .orElseThrow();
        int lastRow = selected.stream().mapToInt(value -> value[0]).max()
                .orElseThrow();
        int firstColumn = selected.stream().mapToInt(value -> value[1]).min()
                .orElseThrow();
        int lastColumn = selected.stream().mapToInt(value -> value[1]).max()
                .orElseThrow();
        StringBuilder block = new StringBuilder();
        for (int row = firstRow; row <= lastRow; row++) {
            if (row > firstRow) block.append('\n');
            for (int column = firstColumn; column <= lastColumn; column++) {
                if (column > firstColumn) block.append('\t');
                block.append(snapshot.cellAt(row, column).getDisplayValue());
            }
        }
        return block.toString();
    }

    static List<CalibrationCellEdit> parse(CalibrationGridSnapshot snapshot,
            int startRow, int startColumn, String clipboard) {
        if (snapshot == null) {
            throw new IllegalArgumentException("A calibration table is required.");
        }
        return parse(snapshot.getRows(), snapshot.getColumns(), startRow,
                startColumn, clipboard);
    }

    static List<CalibrationCellEdit> parse(int tableRows, int tableColumns,
            int startRow, int startColumn, String clipboard) {
        String value = clipboard == null ? "" : clipboard.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "The clipboard does not contain calibration values.");
        }
        String[] rows = value.split("\\R", -1);
        String[][] cells = new String[rows.length][];
        int width = -1;
        for (int row = 0; row < rows.length; row++) {
            cells[row] = rows[row].split("\\t", -1);
            if (width < 0) width = cells[row].length;
            if (cells[row].length != width) {
                throw new IllegalArgumentException(
                        "The pasted rows must have the same width.");
            }
        }
        if (startRow < 0 || startColumn < 0
                || startRow + rows.length > tableRows
                || startColumn + width > tableColumns) {
            throw new IllegalArgumentException(
                    "The pasted values do not fit in this table.");
        }
        List<CalibrationCellEdit> edits = new ArrayList<>();
        for (int row = 0; row < cells.length; row++) {
            for (int column = 0; column < cells[row].length; column++) {
                edits.add(new CalibrationCellEdit(startRow + row,
                        startColumn + column, cells[row][column].trim()));
            }
        }
        return edits;
    }
}
