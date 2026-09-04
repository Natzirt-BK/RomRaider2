/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.romraider.editor.calibration.CalibrationCellEdit;

class FxCalibrationClipboardTest {
    @Test
    void parsesSpreadsheetBlockFromActiveCell() {
        List<CalibrationCellEdit> edits = FxCalibrationClipboard.parse(
                2, 3, 0, 1, "21\t22\n31\t32");

        assertEquals(4, edits.size());
        assertEquals(0, edits.get(0).getRow());
        assertEquals(1, edits.get(0).getColumn());
        assertEquals("21", edits.get(0).getValue());
        assertEquals(1, edits.get(3).getRow());
        assertEquals(2, edits.get(3).getColumn());
    }

    @Test
    void rejectsRaggedAndOversizedBlocks() {
        assertThrows(IllegalArgumentException.class, () ->
                FxCalibrationClipboard.parse(2, 3, 0, 0, "1\t2\n3"));
        assertThrows(IllegalArgumentException.class, () ->
                FxCalibrationClipboard.parse(2, 3, 1, 2, "1\t2"));
    }
}
