/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import com.romraider.editor.compare.RomComparisonService
import com.romraider.editor.compare.TableComparisonStatus
import com.romraider.editor.ecu.spi.RomComparisonWorkspaceProvider
import com.romraider.maps.Rom
import com.romraider.maps.RomID
import com.romraider.maps.Table1D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.ServiceLoader

class RomComparisonWorkspaceModelTest {
    @Test
    fun differencesFilterKeepsMissingRowsAndSummaryCounts() {
        val left = Rom(RomID()).apply {
            fileName = "left.bin"
            addTableByName(Table1D().apply { name = "Fuel Target" })
        }
        val right = Rom(RomID()).apply { fileName = "right.bin" }
        val result = RomComparisonService.compare(left, right)

        val rows = comparisonRows(result, differencesOnly = true)

        assertEquals(1, rows.size)
        assertEquals(TableComparisonStatus.ONLY_LEFT, rows.single().status)
        assertEquals("0 modified • 1 missing • 0 unchanged", comparisonSummary(result))
    }

    @Test
    fun matchingEmptyCatalogsUseClearMatchSummary() {
        val result = RomComparisonService.compare(Rom(RomID()), Rom(RomID()))

        assertEquals(emptyList(), comparisonRows(result, differencesOnly = true))
        assertEquals("0 unchanged tables • ROM calibrations match", comparisonSummary(result))
    }

    @Test
    fun composeComparisonProviderIsDiscoverableByDesktopHost() {
        val providers = ServiceLoader.load(RomComparisonWorkspaceProvider::class.java)
            .toList()

        assertTrue(providers.any {
            it is ComposeRomComparisonWorkspaceProvider
                && it.name == "Compose Desktop ROM Comparison"
        })
    }
}
