/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.After;
import org.junit.Test;

import com.romraider.io.transport.EcuIdentity;
import com.romraider.livetune.LiveTuneChange;
import com.romraider.livetune.LiveTuneDraft;
import com.romraider.livetune.LiveTunePlan;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;
import com.romraider.maps.Table3D;
import com.romraider.swing.JProgressPane;

public class LiveTunePlanProjectionServiceTest {
    private Rom rom;

    @After
    public void forgetSavedState() {
        RomChangeService.forget(rom);
    }

    @Test
    public void projectsOnlyChangedTableBytesAtTheirRamAddress() {
        Table1D table = table("Primary Open Loop Fueling", 2, 4, 1);
        rom = romWith(table, new byte[] {0, 0, 10, 20, 30, 40, 0});
        RomChangeService.rememberSavedBinary(rom);
        rom.getBinary()[3] = 21;
        rom.getBinary()[4] = 31;

        LiveTunePlan plan = LiveTunePlanProjectionService.project(
                new EcuIdentity("ECU", "ROM"), rom,
                Collections.singletonList(table));

        assertEquals(2, plan.getTotalBytes());
        assertEquals(1, plan.getChanges().size());
        LiveTuneChange change = plan.getChanges().get(0);
        assertEquals(3, change.getAddress());
        assertArrayEquals(new byte[] {20, 30}, change.getExpected());
        assertArrayEquals(new byte[] {21, 31}, change.getReplacement());
    }

    @Test
    public void separatesNonContiguousChangedBytes() {
        Table1D table = table("Timing", 1, 4, 1);
        rom = romWith(table, new byte[] {0, 1, 2, 3, 4, 0});
        RomChangeService.rememberSavedBinary(rom);
        rom.getBinary()[1] = 9;
        rom.getBinary()[4] = 8;

        LiveTunePlan plan = LiveTunePlanProjectionService.project(
                new EcuIdentity("ECU", "ROM"), rom,
                Collections.singletonList(table));

        assertEquals(2, plan.getChanges().size());
        assertEquals(1, plan.getChanges().get(0).getAddress());
        assertEquals(4, plan.getChanges().get(1).getAddress());
    }

    @Test
    public void previewsChangesBeforeAnEcuIdentityIsAvailable() {
        Table1D table = table("Timing", 2, 3, 1);
        rom = romWith(table, new byte[] {0, 0, 10, 20, 30, 0});
        RomChangeService.rememberSavedBinary(rom);
        rom.getBinary()[3] = 25;

        LiveTuneDraft preview = LiveTunePlanProjectionService.preview(rom,
                Collections.singletonList(table));

        assertEquals(1, preview.getTableCount());
        assertEquals(1, preview.getTotalBytes());
        assertEquals(3, preview.getStartAddress());
        assertEquals(3, preview.getEndAddress());
        assertEquals(new EcuIdentity("ECU", "ROM"),
                preview.bindTo(new EcuIdentity("ECU", "ROM"))
                        .getExpectedIdentity());
    }

    @Test(expected = IllegalStateException.class)
    public void refusesUnchangedTables() {
        Table1D table = table("Boost", 1, 2, 1);
        rom = romWith(table, new byte[] {0, 1, 2, 0});
        RomChangeService.rememberSavedBinary(rom);

        LiveTunePlanProjectionService.project(
                new EcuIdentity("ECU", "ROM"), rom,
                Collections.singletonList(table));
    }

    @Test
    public void projectsThreeDimensionalSkippedCellsAndAxes() throws Exception {
        Table3D table = new Table3D();
        table.setName("Fuel Target");
        table.setStorageAddress(0);
        table.setStorageType(1);
        table.setSizeX(2);
        table.setSizeY(2);
        table.setSkipCells(1);
        table.getXAxis().setName("Load");
        table.getXAxis().setStorageAddress(8);
        table.getXAxis().setStorageType(1);
        table.getXAxis().setDataSize(2);
        table.getYAxis().setName("RPM");
        table.getYAxis().setStorageAddress(10);
        table.getYAxis().setStorageType(1);
        table.getYAxis().setDataSize(2);
        rom = new Rom(new RomID());
        rom.addTableByName(table);
        rom.populateTables(new byte[] {
                1, 2, 99, 3, 4, 99, 0, 0, 10, 20, 30, 40},
                new JProgressPane());
        RomChangeService.rememberSavedBinary(rom);

        table.get3dData()[0][1].setBinValue(9);
        table.getXAxis().getDataCell(1).setBinValue(22);

        LiveTuneDraft preview = LiveTunePlanProjectionService.preview(rom,
                Collections.singletonList(table));

        assertEquals(2, preview.getTotalBytes());
        assertEquals(2, preview.getChanges().size());
        assertEquals(3, preview.getChanges().get(0).getAddress());
        assertArrayEquals(new byte[] {3},
                preview.getChanges().get(0).getExpected());
        assertArrayEquals(new byte[] {9},
                preview.getChanges().get(0).getReplacement());
        assertEquals(9, preview.getChanges().get(1).getAddress());
        assertArrayEquals(new byte[] {20},
                preview.getChanges().get(1).getExpected());
        assertArrayEquals(new byte[] {22},
                preview.getChanges().get(1).getReplacement());
    }

    private static Table1D table(String name, int address, int cells,
            int storageType) {
        Table1D table = new Table1D();
        table.setName(name);
        table.setStorageAddress(address);
        table.setStorageType(storageType);
        table.setDataSize(cells);
        return table;
    }

    private static Rom romWith(Table1D table, byte[] binary) {
        Rom rom = new Rom(new RomID());
        rom.populateTables(binary, new JProgressPane());
        rom.addTableByName(table);
        return rom;
    }
}
