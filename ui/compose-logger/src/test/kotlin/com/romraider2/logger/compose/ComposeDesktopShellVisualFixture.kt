/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.romraider.editor.document.EditorDocumentController
import com.romraider.editor.document.EditorDocumentSession
import com.romraider.maps.Rom
import com.romraider.maps.RomID
import com.romraider.maps.Table1D
import com.romraider.maps.Table3D
import com.romraider.ui.ThemeMode
import com.romraider.ui.UiThemeService
import kotlinx.coroutines.delay

/** Screenshot-only fixture for the Compose-owned production Editor shell. */
fun main(args: Array<String>) {
    val theme = args.getOrNull(0)?.let {
        ThemeMode.valueOf(it.uppercase())
    } ?: ThemeMode.DARK
    val width = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(900) ?: 1360
    val height = args.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(620) ?: 860
    val hold = args.getOrNull(3)?.toLongOrNull()
        ?.coerceIn(2_000L, 60_000L) ?: 12_000L
    UiThemeService.getInstance().apply(theme)

    val controller = EditorDocumentController()
    val rom = fixtureShellRom()
    controller.session.openRom(rom)
    controller.openTable(rom, rom.getTableByName("Primary Open Loop Fueling"))

    application {
        var snapshot by remember {
            mutableStateOf(controller.session.snapshot())
        }
        val listener = remember {
            EditorDocumentSession.Listener { snapshot = it }
        }
        DisposableEffect(controller) {
            controller.session.addListener(listener)
            onDispose {
                controller.session.removeListener(listener)
                controller.close()
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "RomRaider2 Compose Editor Shell",
            state = WindowState(width = width.dp, height = height.dp)
        ) {
            ComposeEditorShell(
                snapshot, controller, "Ready", 100, 3, 0,
                open = { }, saveNow = { }, saveAs = { },
                manageDefinitions = { }
            )
        }
        LaunchedEffect(Unit) {
            delay(hold)
            exitApplication()
        }
    }
}

private fun fixtureShellRom(): Rom {
    val rom = Rom(RomID()).apply { fileName = "STI_Stage2_Test.bin" }
    val fuel = Table3D().apply {
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
    val boost = Table1D().apply {
        name = "Target Boost"
        category = "Turbo / Boost Control"
        description = "Requested boost target."
        storageAddress = 0x1A0
        storageType = 1
    }
    val timing = Table1D().apply {
        name = "Base Timing"
        category = "Ignition Timing"
        storageAddress = 0x1B0
        storageType = 1
    }
    rom.addTableByName(fuel)
    rom.addTableByName(boost)
    rom.addTableByName(timing)
    val binary = ByteArray(0x240)
    repeat(6) { row ->
        repeat(8) { column ->
            binary[0x100 + row * 8 + column] =
                (92 + row * 5 + column * 2).toByte()
        }
    }
    repeat(8) { binary[0x180 + it] = (20 + it * 20).toByte() }
    repeat(6) { binary[0x190 + it] = (20 + it * 30).toByte() }
    binary[0x1A0] = 18
    binary[0x1B0] = 12
    rom.populateTables(binary) { _, _ -> }
    fuel.get3dData()[3][2].setBinValue(126.0)
    return rom
}
