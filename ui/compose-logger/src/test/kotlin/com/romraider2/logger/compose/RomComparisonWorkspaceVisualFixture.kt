/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import com.romraider.editor.ecu.spi.RomComparisonWorkspaceContext
import com.romraider.maps.DataCell
import com.romraider.maps.Rom
import com.romraider.maps.RomID
import com.romraider.maps.Table1D
import com.romraider.ui.ThemeMode
import com.romraider.ui.UiThemeService
import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.system.exitProcess

/** Automated comparison screenshot fixture; excluded from release packages. */
fun main(args: Array<String>) {
    val theme = args.getOrNull(0)?.let { ThemeMode.valueOf(it.uppercase()) }
        ?: ThemeMode.DARK
    val width = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(640) ?: 1040
    val height = args.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(520) ?: 700
    val holdMillis = args.getOrNull(3)?.toLongOrNull()
        ?.coerceIn(2_000L, 60_000L) ?: 12_000L
    UiThemeService.getInstance().apply(theme)
    val left = fixtureRom("stock-rom.bin",
        fixtureTable("Boost Target", 10.0),
        fixtureTable("Fuel Target", 12.0),
        fixtureTable("Closed Loop Fueling", 1.0))
    val right = fixtureRom("road-tune.bin",
        fixtureTable("Boost Target", 12.5),
        fixtureTable("Fuel Target", 12.0),
        fixtureTable("Launch Control", 2.0))
    val context = RomComparisonWorkspaceContext(listOf(left, right)) { _, _, _ -> }
    lateinit var frame: JFrame
    SwingUtilities.invokeAndWait {
        frame = JFrame("RomRaider2 ROM Comparison").apply {
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            layout = BorderLayout()
            add(ComposeRomComparisonWorkspaceProvider().createWorkspace(context),
                BorderLayout.CENTER)
            setSize(width, height)
            setLocation(20, 20)
            isVisible = true
        }
    }
    Thread.sleep(holdMillis)
    SwingUtilities.invokeAndWait { frame.dispose() }
    exitProcess(0)
}

private fun fixtureRom(file: String, vararg tables: Table1D) =
    Rom(RomID()).apply {
        fileName = file
        tables.forEach(::addTableByName)
    }

private fun fixtureTable(tableName: String, value: Double) = Table1D().apply {
    name = tableName
    data = arrayOf<DataCell>(FixtureValueCell(this, value))
}

private class FixtureValueCell(
    table: Table1D,
    private val fixtureValue: Double
) : DataCell(table, null as Rom?) {
    override fun equals(other: Any?): Boolean =
        other is FixtureValueCell && fixtureValue == other.fixtureValue

    override fun hashCode(): Int = fixtureValue.hashCode()
}
