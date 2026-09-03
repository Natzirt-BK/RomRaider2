/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.document;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceContext;
import com.romraider.editor.workspace.TableLocation;
import com.romraider.maps.Rom;
import com.romraider.maps.RomID;
import com.romraider.maps.Table1D;

public final class EditorNavigationWorkspaceContextTest {
    @Test
    public void exposesSessionCatalogAndRoutesOpenCommandsWithoutSwing() {
        Rom rom = new Rom(new RomID());
        rom.setFileName("road-tune.bin");
        Table1D table = new Table1D();
        table.setName("Boost Target");
        rom.addTableByName(table);
        EditorDocumentSession session = new EditorDocumentSession();
        session.openRom(rom);
        final TableLocation[] opened = {null};
        EditorNavigationWorkspaceContext context =
                new EditorNavigationWorkspaceContext(session,
                        location -> opened[0] = location);

        assertSame(rom, context.getSnapshot().getActiveRom());
        assertEquals(1, context.getSnapshot().getDocuments().size());
        context.open(rom, table);
        assertEquals("Boost Target", opened[0].getTableName());
        assertEquals(com.romraider.editor.workspace.EditorWorkspaceService
                .romIdentity(rom), opened[0].getRomId());

        context.toggleFavorite(rom, table);
        assertTrue(context.isFavorite(rom, table));
        context.removeFavorite(opened[0]);
        session.close();
    }
}
