/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import static org.junit.Assert.*;
import com.romraider.maps.*;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.editor.calibration.CalibrationGridProjectionService;
import com.romraider.swing.JProgressPane;
import java.util.List;
import org.junit.Test;

public class LogMapTraceTest {
    private Table2D curve(String... axis) {
        Table2D table = new Table2D(); table.setName("Synthetic curve"); table.setDataSize(axis.length);
        table.getAxis().setName("Voltage"); table.getAxis().setDataSize(axis.length);
        for (int i = 0; i < axis.length; i++) { table.addStaticDataCell(Integer.toString(10 + i)); table.getAxis().addStaticDataCell(axis[i]); }
        Scale values = new Scale(); values.setUnit("g/s"); table.addScale(values);
        Scale units = new Scale(); units.setUnit("V"); table.getAxis().addScale(units); return table;
    }
    private Table3D surface() {
        Table3D table = new Table3D(); table.setName("Synthetic surface"); table.setSizeX(2); table.setSizeY(2); table.setStaticDataTable(true);
        table.getXAxis().setDataSize(2); table.getYAxis().setDataSize(2);
        table.getXAxis().addStaticDataCell("1"); table.getXAxis().addStaticDataCell("3");
        table.getYAxis().addStaticDataCell("4000"); table.getYAxis().addStaticDataCell("2000");
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) table.get3dData()[x][y] = new DataCell(table, Integer.toString(y * 2 + x + 10), null);
        return table;
    }
    @Test public void curvesHandleAscendingDescendingExactAndOutsideWithoutClamping() {
        LogMapTrace ascending = LogMapTrace.capture(curve("1", "2", "4"));
        assertEquals(List.of(new LogMapTrace.Neighbor(0, 1, .5), new LogMapTrace.Neighbor(0, 2, .5)), ascending.locate(3, Double.NaN).neighbors());
        assertEquals(List.of(new LogMapTrace.Neighbor(0, 1, 1)), ascending.locate(2, 0).neighbors());
        assertEquals(LogMapTrace.State.OUT_OF_RANGE, ascending.locate(.9, 0).state());
        assertEquals(LogMapTrace.State.OUT_OF_RANGE, ascending.locate(4.1, 0).state());
        assertEquals(LogMapTrace.State.MISSING, ascending.locate(Double.NaN, 0).state());
        assertEquals(LogMapTrace.State.MISSING, ascending.locate(Double.POSITIVE_INFINITY, 0).state());
        assertEquals(List.of(new LogMapTrace.Neighbor(0, 0, .5), new LogMapTrace.Neighbor(0, 1, .5)), LogMapTrace.capture(curve("4", "2", "1")).locate(3, 0).neighbors());
    }
    @Test public void surfacesProduceGeometricNeighborsInDisplayedCoordinates() {
        LogMapTrace map = LogMapTrace.capture(surface());
        assertTrue(map.isSurface()); assertEquals(13, map.valueAt(1, 1), 0);
        assertEquals(List.of(new LogMapTrace.Neighbor(0, 0, .375), new LogMapTrace.Neighbor(0, 1, .125),
                new LogMapTrace.Neighbor(1, 0, .375), new LogMapTrace.Neighbor(1, 1, .125)), map.locate(1.5, 3000).neighbors());
        assertEquals(List.of(new LogMapTrace.Neighbor(1, 1, 1)), map.locate(3, 2000).neighbors());
        assertEquals(LogMapTrace.State.OUT_OF_RANGE, map.locate(2, 1000).state());
        assertEquals(LogMapTrace.State.MISSING, map.locate(2, Double.NaN).state());
    }
    @Test public void ambiguousOrUnavailableAxesAreRejectedWithoutOrdinalFallback() {
        reject(() -> LogMapTrace.capture(curve("1", "1")));
        reject(() -> LogMapTrace.capture(curve("1", "3", "2")));
        reject(() -> LogMapTrace.capture(curve("NaN", "2")));
        reject(() -> LogMapTrace.capture(curve("Idle", "Cruise")));
        reject(() -> LogMapTrace.capture(new Table1D()));
        Table2D broken = curve("1", "2"); broken.getAxis().setDataSize(3); reject(() -> LogMapTrace.capture(broken));
        Table2D tooWide = new Table2D(); tooWide.setDataSize(LogMapTrace.MAX_AXIS + 1); reject(() -> LogMapTrace.capture(tooWide));
        Table3D tooLarge = new Table3D(); tooLarge.setSizeX(256); tooLarge.setSizeY(33); reject(() -> LogMapTrace.capture(tooLarge));
    }
    @Test public void exactNumericAxesDoNotUseRoundedDisplayLabelsAndExtremeDifferencesRemainFinite() {
        LogMapTrace precise = LogMapTrace.capture(curve("1.0000000001", "1.0000000002"));
        assertEquals(1.0000000001, precise.xAt(0), 0);
        assertEquals(LogMapTrace.State.COVERED, precise.locate(1.00000000015, 0).state());
        LogMapTrace wide = LogMapTrace.capture(curve(Double.toString(-Double.MAX_VALUE), Double.toString(Double.MAX_VALUE)));
        assertEquals(List.of(new LogMapTrace.Neighbor(0, 0, .5), new LogMapTrace.Neighbor(0, 1, .5)), wide.locate(0, 0).neighbors());
    }
    @Test public void singletonOnlyCoversItsExactBreakpoint() {
        LogMapTrace map = LogMapTrace.capture(curve("2"));
        assertEquals(List.of(new LogMapTrace.Neighbor(0, 0, 1)), map.locate(2, 0).neighbors());
        assertEquals(LogMapTrace.State.OUT_OF_RANGE, map.locate(Math.nextUp(2.0), 0).state());
    }
    @Test public void capturedGeometryValuesAndLabelsRemainFrozenAfterModelEdits() {
        Table2D table = curve("1", "2"); LogMapTrace map = LogMapTrace.capture(table);
        table.getData()[0] = new DataCell(table, "99", null); table.getAxis().getData()[0] = new DataCell(table.getAxis(), "-8", null);
        table.setName("Edited"); table.getAxis().getCurrentScale().setUnit("Other");
        assertEquals(10, map.valueAt(0, 0), 0); assertEquals(1, map.xAt(0), 0); assertEquals("V", map.xUnits()); assertEquals("Synthetic curve", map.name());
        try { map.locate(1, 0).neighbors().clear(); fail("Mutable neighbors"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void capturesAllStorageOrientationsLikeEditorWithoutWritingBytesOrHistory() throws Exception {
        for (int flags = 0; flags < 8; flags++) {
            Table3D table = new Table3D(); table.setName("Orientation"); table.setSizeX(2); table.setSizeY(2);
            table.setStorageType(1); table.setStorageAddress(0); table.setSwapXY((flags & 1) != 0); table.setFlipX((flags & 2) != 0); table.setFlipY((flags & 4) != 0);
            table.getXAxis().setStorageType(1); table.getXAxis().setStorageAddress(4); table.getXAxis().setDataSize(2);
            table.getYAxis().setStorageType(1); table.getYAxis().setStorageAddress(6); table.getYAxis().setDataSize(2);
            Rom rom = new Rom(new RomID()); rom.addTableByName(table); rom.populateTables(new byte[] {1, 2, 3, 4, 10, 20, 30, 40}, new JProgressPane());
            try {
                byte[] before = rom.getBinary().clone(); LogMapTrace map = LogMapTrace.capture(table);
                var editor = CalibrationGridProjectionService.project(table);
                for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++) assertEquals(editor.cellAt(row, column).getRealValue(), map.valueAt(row, column), 0);
                assertArrayEquals(before, rom.getBinary()); assertEquals(0, RomEditHistory.getInstance().undoDepth(rom));
            } finally { RomEditHistory.getInstance().clear(rom); }
        }
    }
    private static void reject(Runnable action) { try { action.run(); fail("Expected invalid geometry rejection"); } catch (IllegalArgumentException expected) { } }
}
