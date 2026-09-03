/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import com.romraider.editor.document.EditorDocumentSession
import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceContext
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

/** Automated editor-navigation fixture; excluded from release packages. */
fun main(args: Array<String>) {
    val theme = args.getOrNull(0)?.let { ThemeMode.valueOf(it.uppercase()) }
        ?: ThemeMode.DARK
    val width = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(220) ?: 300
    val height = args.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(480) ?: 760
    val holdMillis = args.getOrNull(3)?.toLongOrNull()
        ?.coerceIn(2_000L, 60_000L) ?: 12_000L
    UiThemeService.getInstance().apply(theme)
    val rom = Rom(RomID()).apply {
        fileName = "2008 STI road tune.bin"
        addTableByName(table("Boost Target", "Boost Control//Targets"))
        addTableByName(table("Primary Open Loop Fueling", "Fueling//Open Loop"))
        addTableByName(table("Closed Loop Target", "Fueling//Closed Loop"))
        addTableByName(table("Wastegate Duty Maximum", "Boost Control//Wastegate"))
        addTableByName(table("Ignition Timing Advance", "Ignition//Base Tables"))
        addTableByName(table("AVCS Intake Advance", "Cam Control//Intake"))
        addTableByName(table("Rev Limit", "Limiters"))
    }
    val session = EditorDocumentSession().apply { openRom(rom) }
    val context = EditorNavigationWorkspaceContext(session) { }
    val workspace = ComposeEditorNavigationWorkspaceProvider()
        .createWorkspace(context)
    lateinit var frame: JFrame
    SwingUtilities.invokeAndWait {
        frame = JFrame("RomRaider2 Editor Navigation").apply {
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            layout = BorderLayout()
            add(workspace.component, BorderLayout.CENTER)
            setSize(width, height)
            setLocation(20, 20)
            isVisible = true
        }
    }
    Thread.sleep(holdMillis)
    SwingUtilities.invokeAndWait {
        frame.dispose()
        workspace.close()
        session.close()
    }
    exitProcess(0)
}

private fun table(name: String, group: String) = Table1D().apply {
    this.name = name
    category = group
}
