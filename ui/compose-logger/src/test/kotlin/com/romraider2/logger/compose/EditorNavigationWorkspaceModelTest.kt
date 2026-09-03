/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import com.romraider.editor.document.EditorDocumentSession
import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceContext
import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceProvider
import com.romraider.maps.Rom
import com.romraider.maps.RomID
import com.romraider.maps.Table1D
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorNavigationWorkspaceModelTest {
    @Test
    fun navigationProjectionIncludesEveryOpenRomTable() {
        val rom = Rom(RomID()).apply {
            fileName = "road-tune.bin"
            addTableByName(Table1D().apply {
                name = "Boost Target"
                category = "Boost"
            })
            addTableByName(Table1D().apply {
                name = "Fuel Target"
                category = "Fuel"
            })
        }
        val session = EditorDocumentSession()
        try {
            session.openRom(rom)
            val context = EditorNavigationWorkspaceContext(session) { }

            val state = navigationState(context)

            assertEquals(listOf("Boost Target", "Fuel Target"),
                state.entries.map { it.table.name })
            assertTrue(state.entries.none { it.active })
        } finally {
            session.close()
        }
    }

    @Test
    fun composeNavigationProviderIsDiscoverableByDesktopHost() {
        val providers = ServiceLoader.load(
            EditorNavigationWorkspaceProvider::class.java).toList()

        assertTrue(providers.any {
            it is ComposeEditorNavigationWorkspaceProvider &&
                it.name == "Compose Desktop Editor Navigation"
        })
    }

    @Test
    fun calibrationCategoriesRestoreDefinitionSubmenus() {
        val rom = Rom(RomID())
        val entries = listOf(
            entry(rom, "Primary Open Loop Fueling", "Fueling//Open Loop"),
            entry(rom, "Closed Loop Target", "Fueling//Closed Loop"),
            entry(rom, "Boost Target", "Boost Control")
        )

        val groups = calibrationCategoryTree(entries)

        assertEquals(listOf("Fueling", "Boost Control"),
            groups.map { it.name })
        assertEquals(listOf("Open Loop", "Closed Loop"),
            groups.first().children.map { it.name })
        assertEquals("Primary Open Loop Fueling",
            groups.first().children.first().entries.single().table.name)
        assertEquals(2, groups.first().calibrationCount)
    }

    private fun entry(rom: Rom, name: String, category: String): CalibrationEntry {
        val table = Table1D().apply {
            this.name = name
            this.category = category
        }
        return CalibrationEntry(rom, table, "test-rom", "test.bin",
            favorite = false, changedCells = 0, active = false)
    }
}
