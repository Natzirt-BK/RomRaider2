/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.romraider.logger.api.*

/** Actual setup and fitted display composables, fed only explicit synthetic readings. */
@Composable
internal fun GaugeDisplayVisualContent() {
    val channels = remember { (0..5).map { index -> LoggerChannel("fixture-$index", "Synthetic ${index + 1}",
        if (index == 1) "V" else "rpm", LoggerChannelKind.PARAMETER, true) } }
    val preferences = remember { LoggerWorkspacePreferences(LoggerWorkspaceView.DASHBOARD, true) { _, _ -> } }
    var display by remember { mutableStateOf(LoggerGaugeDisplay().useLoggerChannels(channels)) }
    var theme by remember { mutableStateOf(LoggerGaugeTheme.STI_NIGHT) }
    var revision by remember { mutableStateOf(0) }
    var mounted by remember { mutableStateOf(false) }
    val received = remember { System.nanoTime() }
    val samples = remember { channels.associate { it.parameterId to LiveDataSample(it.parameterId, it.name,
        if (it.units == "V") 13.4 else 4210.0, if (it.units == "V") "13.4" else "4210", it.units, 1) } }
    val alerts = remember { LoggerGaugeAlertTracker() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            Text("SYNTHETIC DISPLAY FIXTURE", Modifier.weight(1f))
            TextButton(onClick = { mounted = !mounted }) { Text(if (mounted) "Show setup" else "Fit display") }
        }
        if (!mounted) GaugeDisplaySetup(channels, display, preferences, theme,
            { display = it }, { revision++ }, { theme = it })
        Box(Modifier.fillMaxWidth().weight(1f)) {
            MountedInstruments(channels, samples, samples.mapValues { listOf(it.value) }, theme,
                LoggerSessionState.RECORDING, channels.associate { it.parameterId to received }, received,
                preferences, alerts, display, revision)
        }
    }
}
