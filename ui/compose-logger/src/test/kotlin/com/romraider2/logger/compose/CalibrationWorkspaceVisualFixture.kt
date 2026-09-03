/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import com.romraider.editor.calibration.CalibrationGridProjectionService
import com.romraider.editor.calibration.TableCalibrationEditController
import com.romraider.editor.ecu.spi.CalibrationWorkspaceContext
import com.romraider.maps.Rom
import com.romraider.maps.RomID
import com.romraider.maps.Table3D
import com.romraider.swing.JProgressPane
import com.romraider.ui.ThemeMode
import com.romraider.ui.UiThemeService
import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.system.exitProcess

/** Automated screenshot fixture; never included in a release package. */
fun main(args: Array<String>) {
    val theme = args.getOrNull(0)?.let {
        ThemeMode.valueOf(it.uppercase())
    } ?: ThemeMode.LIGHT
    val width = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(640) ?: 1100
    val height = args.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(520) ?: 720
    val holdMillis = args.getOrNull(3)?.toLongOrNull()
        ?.coerceIn(2_000L, 60_000L) ?: 12_000L
    UiThemeService.getInstance().apply(theme)
    val table = fixtureTable()
    val rom = Rom(RomID())
    rom.addTableByName(table)
    rom.populateTables(fixtureBinary(), JProgressPane())
    check(table.get3dData().size == 8 && table.get3dData().all {
        it.size == 6
    }) { "Calibration fixture did not populate its 8 x 6 surface" }
    table.get3dData()[3][2].setBinValue(126.0)
    val context = CalibrationWorkspaceContext(
        CalibrationGridProjectionService.project(table),
        TableCalibrationEditController(table), rom, table
    )
    lateinit var frame: JFrame
    SwingUtilities.invokeAndWait {
        frame = JFrame("RomRaider2 Calibration Workspace").apply {
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            layout = BorderLayout()
            add(ComposeCalibrationWorkspaceProvider().createWorkspace(context),
                BorderLayout.CENTER)
            setSize(width, height)
            setLocation(20, 20)
            isVisible = true
        }
    }
    Thread.sleep(holdMillis)
    SwingUtilities.invokeAndWait { frame.dispose() }
    context.editController?.close()
    exitProcess(0)
}

private fun fixtureTable() = Table3D().apply {
    name = "Primary Open Loop Fueling"
    category = "Fueling / Open Loop"
    description = "Requested fuel target by engine speed and load."
    storageAddress = 0x100
    storageType = 1
    sizeX = 8
    sizeY = 6
    xAxis.name = "Engine Load"
    xAxis.storageAddress = 0x180
    xAxis.storageType = 1
    xAxis.dataSize = 8
    yAxis.name = "Engine Speed"
    yAxis.storageAddress = 0x190
    yAxis.storageType = 1
    yAxis.dataSize = 6
}

private fun fixtureBinary(): ByteArray {
    val binary = ByteArray(0x200)
    repeat(6) { row ->
        repeat(8) { column ->
            binary[0x100 + row * 8 + column] =
                (92 + row * 5 + column * 2).toByte()
        }
    }
    repeat(8) { binary[0x180 + it] = (20 + it * 20).toByte() }
    repeat(6) { binary[0x190 + it] = (20 + it * 30).toByte() }
    return binary
}
