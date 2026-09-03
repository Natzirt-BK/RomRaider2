/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import com.romraider.editor.calibration.CalibrationGridProjectionService
import com.romraider.editor.ecu.spi.CalibrationWorkspaceProvider
import com.romraider.maps.Rom
import com.romraider.maps.RomID
import com.romraider.maps.Scale
import com.romraider.maps.Table1D
import com.romraider.maps.Table3D
import com.romraider.swing.JProgressPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.util.ServiceLoader
import androidx.compose.ui.geometry.Size

class CalibrationPreviewModelTest {
    @Test
    fun calibrationDisplayRoundsWithoutChangingExactModelText() {
        assertEquals("54.93", calibrationDisplayText("54.9316404", 2))
        assertEquals("500.00", calibrationDisplayText("500.0", 2))
        assertEquals("-1.24", calibrationDisplayText("-1.235", 2))
        assertEquals("0.00", calibrationDisplayText("-0.004", 2))
        assertEquals("500", calibrationDisplayText("500.0", 0))
        assertEquals("Load", calibrationDisplayText("Load", 2))
    }

    @Test
    fun composeCalibrationProviderIsDiscoverable() {
        val providers = ServiceLoader.load(CalibrationWorkspaceProvider::class.java)
            .toList()

        assertTrue(providers.any {
                it is ComposeCalibrationWorkspaceProvider &&
                it.name.contains("Calibration Workspace")
        })
    }

    @Test
    fun heatFractionUsesTheActualTableRange() {
        val table = Table1D().apply {
            name = "Fuel Target"
            storageAddress = 0
            storageType = 1
            dataSize = 3
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(10, 20, 30), JProgressPane())
        val snapshot = CalibrationGridProjectionService.project(table)

        assertEquals(0f, heatFraction(snapshot, snapshot.cellAt(0, 0)))
        assertEquals(0.5f, heatFraction(snapshot, snapshot.cellAt(0, 1)))
        assertEquals(1f, heatFraction(snapshot, snapshot.cellAt(0, 2)))
    }

    @Test
    fun flatTablesUseTheMiddleOfTheHeatScale() {
        val table = Table1D().apply {
            name = "Flat Target"
            storageAddress = 0
            storageType = 1
            dataSize = 2
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(14, 14), JProgressPane())
        val snapshot = CalibrationGridProjectionService.project(table)

        assertEquals(0.5f, heatFraction(snapshot, snapshot.cellAt(0, 0)))
        assertEquals(0.5f, heatFraction(snapshot, snapshot.cellAt(0, 1)))
    }

    @Test
    fun displayPrecisionFollowsTheDefinitionScaleResolution() {
        val table = Table1D().apply {
            name = "Fractional Target"
            storageAddress = 0
            storageType = 1
            dataSize = 2
            val fractionalScale = Scale().apply {
                format = "0.00"
                expression = "x*.5"
                byteExpression = "x/.5"
            }
            addScale(fractionalScale)
            currentScale = fractionalScale
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(21, 22), JProgressPane())

        val snapshot = CalibrationGridProjectionService.project(table)

        assertEquals(2, snapshot.valueFractionDigits)
        assertEquals("10.5", snapshot.cellAt(0, 0).displayValue)
    }

    @Test
    fun identityRawScaleOmitsMeaninglessDecimalPlaces() {
        val table = Table1D().apply {
            name = "Integer Target"
            storageAddress = 0
            storageType = 1
            dataSize = 2
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(21, 22), JProgressPane())

        val snapshot = CalibrationGridProjectionService.project(table)

        assertEquals(0, snapshot.valueFractionDigits)
        assertEquals("21.0", snapshot.cellAt(0, 0).displayValue)
    }

    @Test
    fun keyboardSelectionMovesByRowsAndColumnsWithoutLeavingTheGrid() {
        val table = Table1D().apply {
            name = "Line"
            storageAddress = 0
            storageType = 1
            dataSize = 3
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(10, 20, 30), JProgressPane())
        val snapshot = CalibrationGridProjectionService.project(table)

        assertEquals(1, moveSelection(snapshot, 0, 0, 1))
        assertEquals(2, moveSelection(snapshot, 2, 0, 1))
        assertEquals(0, moveSelection(snapshot, 0, -1, -1))
    }

    @Test
    fun tabSeparatedClipboardValuesMapFromTheActiveCell() {
        val table = Table1D().apply {
            name = "Line"
            storageAddress = 0
            storageType = 1
            dataSize = 4
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(10, 20, 30, 40), JProgressPane())
        val snapshot = CalibrationGridProjectionService.project(table)

        val edits = calibrationBlockEdits(snapshot, 1, "21\t32\t43")

        assertEquals(3, edits.size)
        assertEquals(1, edits[0].column)
        assertEquals("21", edits[0].value)
        assertEquals(3, edits[2].column)
    }

    @Test
    fun rangeSelectionCopiesValuesInSpreadsheetOrder() {
        val table = Table1D().apply {
            name = "Line"
            storageAddress = 0
            storageType = 1
            dataSize = 4
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(10, 20, 30, 40), JProgressPane())
        val snapshot = CalibrationGridProjectionService.project(table)

        assertEquals(listOf(1, 2, 3),
            selectedCellIndices(snapshot, 1, 3))
        assertEquals("20.0\t30.0\t40.0",
            calibrationSelectionText(snapshot, 1, 3))
    }

    @Test
    fun clipboardBlockCannotRunPastTheCalibrationEdge() {
        val table = Table1D().apply {
            name = "Line"
            storageAddress = 0
            storageType = 1
            dataSize = 3
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(10, 20, 30), JProgressPane())
        val snapshot = CalibrationGridProjectionService.project(table)

        assertFailsWith<IllegalArgumentException> {
            calibrationBlockEdits(snapshot, 2, "31\t32")
        }
    }

    @Test
    fun twoDimensionalClipboardBlockPreservesRowsAndColumns() {
        val table = Table3D().apply {
            name = "Fuel Surface"
            storageAddress = 0
            storageType = 1
            sizeX = 3
            sizeY = 2
            xAxis.storageAddress = 6
            xAxis.storageType = 1
            xAxis.dataSize = 3
            yAxis.storageAddress = 9
            yAxis.storageType = 1
            yAxis.dataSize = 2
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(
            10, 20, 30, 40, 50, 60, 1, 2, 3, 4, 5), JProgressPane())
        val snapshot = CalibrationGridProjectionService.project(table)

        val edits = calibrationBlockEdits(snapshot, 1, "21\t31\n51\t61")

        assertEquals(4, edits.size)
        assertEquals(0, edits[0].row)
        assertEquals(1, edits[0].column)
        assertEquals(1, edits[2].row)
        assertEquals(1, edits[2].column)
        assertEquals("61", edits[3].value)
    }

    @Test
    fun surfaceProjectionSupportsRotationAndCellHitTesting() {
        val table = Table3D().apply {
            name = "Fuel Surface"
            storageAddress = 0
            storageType = 1
            sizeX = 2
            sizeY = 2
            xAxis.storageAddress = 4
            xAxis.storageType = 1
            xAxis.dataSize = 2
            yAxis.storageAddress = 6
            yAxis.storageType = 1
            yAxis.dataSize = 2
        }
        val rom = Rom(RomID())
        rom.addTableByName(table)
        rom.populateTables(byteArrayOf(10, 20, 30, 40, 1, 2, 3, 4),
            JProgressPane())
        val snapshot = CalibrationGridProjectionService.project(table)
        val size = Size(800f, 500f)
        val cell = snapshot.cellAt(1, 1)
        val point = surfacePoint(cell.row, cell.column, snapshot.rows,
            snapshot.columns, 1f, size, 0f, .72f, 1f)
        val rotated = surfacePoint(cell.row, cell.column, snapshot.rows,
            snapshot.columns, 1f, size, 90f, .72f, 1f)

        assertTrue(point != rotated)
        assertEquals(3, nearestSurfaceCell(snapshot, point, size,
            10.0, 30.0, 0f, .72f, 1f))
    }
}
