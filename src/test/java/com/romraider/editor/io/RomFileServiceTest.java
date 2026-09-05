package com.romraider.editor.io;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.romraider.editor.calibration.TableCalibrationEditController;
import com.romraider.editor.recovery.RomRecoveryService;
import com.romraider.editor.workspace.RomChangeService;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.maps.*;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.swing.JProgressPane;

public class RomFileServiceTest {
    private interface CheckedAction { void run() throws Exception; }

    private static void assertThrows(Class<? extends Exception> type, CheckedAction action) throws Exception {
        try { action.run(); fail("Expected " + type.getSimpleName()); }
        catch (Exception failure) { if (!type.isInstance(failure)) throw failure; }
    }

    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Rom rom;

    @After public void cleanUp() {
        if (rom == null) return;
        RomRecoveryService.getInstance().markResolved(rom);
        RomChangeService.forget(rom);
        RomEditHistory.getInstance().clear(rom);
    }

    @Test public void saveSnapshotRetainsLaterEditsAndUndoTargetsSavedValues() throws Exception {
        Table1D table = new Table1D();
        table.setName("Synthetic value"); table.setStorageType(1);
        table.setStorageAddress(0); table.setDataSize(1);
        rom = new Rom(new RomID()); rom.addTableByName(table);
        rom.populateTables(new byte[] {10}, new JProgressPane());
        java.io.File original = temporary.newFile("original.bin");
        Files.write(original.toPath(), new byte[] {10});
        rom.setFullFileName(original);
        RomChangeService.rememberSavedBinary(rom);
        TableCalibrationEditController edits = new TableCalibrationEditController(table);
        edits.setCellValue(0, 0, "20");
        java.io.File target = temporary.getRoot().toPath().resolve("save-as.bin").toFile();
        RomFileService files = new RomFileService();
        RomFileService.PreparedSave prepared = files.prepare(rom, target);
        edits.setCellValue(0, 0, "30");
        files.writePrepared(prepared);
        files.complete(prepared);

        assertArrayEquals(new byte[] {20}, Files.readAllBytes(target.toPath()));
        assertArrayEquals(new byte[] {10}, Files.readAllBytes(original.toPath()));
        assertEquals(30, rom.getBinary()[0]);
        assertEquals(20.0, table.getData()[0].getOriginalValue(), 0.0);
        assertTrue(RomChangeService.hasBinaryChanges(rom));
        assertEquals(1, RomChangeSummary.countChangedCells(rom));
        assertEquals(target.getAbsoluteFile(), rom.getFullFileName());

        edits.undo();
        assertEquals(20, rom.getBinary()[0]);
        assertFalse(RomChangeService.hasBinaryChanges(rom));
        assertEquals(0, RomChangeSummary.countChangedCells(rom));
        edits.redo();
        assertTrue(RomChangeService.hasBinaryChanges(rom));
        edits.close();
    }

    @Test public void failedWriteDoesNotPublishNameOrSavedBaseline() throws Exception {
        rom = new Rom(new RomID());
        rom.populateTables(new byte[] {10}, new JProgressPane());
        java.io.File original = temporary.newFile("original.bin");
        rom.setFullFileName(original);
        RomChangeService.rememberSavedBinary(rom);
        rom.getBinary()[0] = 20;
        java.io.File target = temporary.newFile("existing.bin");
        Files.write(target.toPath(), new byte[] {99});
        RomFileService files = new RomFileService((file, output) -> {
            throw new IOException("Synthetic write failure");
        });
        RomFileService.PreparedSave prepared = files.prepare(rom, target);
        assertThrows(IOException.class, () -> files.writePrepared(prepared));
        assertThrows(IllegalStateException.class, () -> files.complete(prepared));
        assertArrayEquals(new byte[] {99}, Files.readAllBytes(target.toPath()));
        assertEquals(original, rom.getFullFileName());
        assertArrayEquals(new byte[] {10}, RomChangeService.snapshotSavedBinary(rom));
        assertTrue(RomChangeService.hasBinaryChanges(rom));
    }

    @Test public void snapshotIncludesSurfaceAndBothAxisRevertPoints() throws Exception {
        Table3D table = new Table3D();
        table.setName("Synthetic surface"); table.setStorageType(1);
        table.setStorageAddress(0); table.setSizeX(1); table.setSizeY(1);
        table.getXAxis().setStorageAddress(1); table.getXAxis().setStorageType(1);
        table.getXAxis().setDataSize(1);
        table.getYAxis().setStorageAddress(2); table.getYAxis().setStorageType(1);
        table.getYAxis().setDataSize(1);
        rom = new Rom(new RomID()); rom.addTableByName(table);
        rom.populateTables(new byte[] {10,20,30}, new JProgressPane());
        RomChangeService.rememberSavedBinary(rom);
        RomFileService files = new RomFileService();
        RomFileService.PreparedSave prepared = files.prepare(rom, temporary.newFile("surface.bin"));
        table.getXAxis().getData()[0].setBinValue(40);
        table.getYAxis().getData()[0].setBinValue(50);
        files.writePrepared(prepared); files.complete(prepared);
        assertEquals(20.0, table.getXAxis().getData()[0].getOriginalValue(), 0.0);
        assertEquals(30.0, table.getYAxis().getData()[0].getOriginalValue(), 0.0);
        assertEquals(2, RomChangeSummary.countChangedCells(rom));
        assertTrue(RomChangeService.hasBinaryChanges(rom));
    }
}
