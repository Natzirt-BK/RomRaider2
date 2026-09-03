/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romraider.editor.workspace.LiveTunePlanProjectionService
import com.romraider.editor.workspace.RomChangeSummary
import com.romraider.livetune.LiveTuneDraft
import com.romraider.maps.Rom
import com.romraider.maps.Table
import com.romraider.platform.DimeModState
import com.romraider.platform.PlatformContext
import com.romraider.platform.RomModificationDetector
import com.romraider.platform.VehicleModule
import com.romraider.platform.VehiclePlatform

/** Read-only Compose review of bytes that could form a future live-tune plan. */
@Composable
internal fun LiveTunePreviewWorkspace(
    rom: Rom,
    selectedTable: Table?,
    revision: Long
) {
    var allChanged by remember(rom) { mutableStateOf(false) }
    val tables = remember(rom, selectedTable, revision, allChanged) {
        if (!allChanged) listOfNotNull(selectedTable)
        else RomChangeSummary.summarize(rom).mapNotNull { changed ->
            rom.getTableByName(changed.tableName)
        }
    }
    val result = remember(rom, tables, revision) {
        runCatching<LiveTuneDraft> {
            LiveTunePlanProjectionService.preview(rom, tables)
        }
    }
    val dark = rememberApplicationDarkTheme()
    MaterialTheme(colors = if (dark) darkColors(
        primary = Color(0xFF56AEA6), background = Color(0xFF10151B),
        surface = Color(0xFF171E26), onPrimary = Color.White,
        onBackground = Color(0xFFE4E8ED), onSurface = Color(0xFFE4E8ED)
    ) else lightColors(primary = Color(0xFF007F78))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Column(Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("LIVE TUNE PLAN", fontWeight = FontWeight.Bold,
                            fontSize = 20.sp)
                        Text("Offline review only — this screen cannot write " +
                            "to an ECU.", color = MaterialTheme.colors.onSurface
                            .copy(alpha = .62f), fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { allChanged = false }) {
                        Text("Selected table")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { allChanged = true }) {
                        Text("All changed")
                    }
                }

                ModificationSummary(rom)
                SafetySummary(result.getOrNull())

                result.fold(onSuccess = { draft ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("TABLES", draft.tableCount.toString())
                        MetricCard("BYTE RANGES", draft.changes.size.toString())
                        MetricCard("BYTES", draft.totalBytes.toString())
                        MetricCard("RAM RANGE", String.format("%06X–%06X",
                            draft.startAddress, draft.endAddress))
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(draft.changes) { change ->
                            Row(Modifier.fillMaxWidth()
                                .background(MaterialTheme.colors.surface,
                                    RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(change.tableName, Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold)
                                Text(String.format("0x%06X", change.address),
                                    Modifier.width(100.dp))
                                Text("${change.length} bytes",
                                    Modifier.width(80.dp))
                                Text(hex(change.expected) + "  →  " +
                                    hex(change.replacement),
                                    color = MaterialTheme.colors.primary)
                            }
                        }
                    }
                }, onFailure = { failure ->
                    Column(Modifier.weight(1f).fillMaxWidth()
                        .background(MaterialTheme.colors.surface,
                            RoundedCornerShape(7.dp)).padding(18.dp)) {
                        Text("Nothing staged", fontWeight = FontWeight.Bold)
                        Text(failure.message ?: "Change a calibration value " +
                            "to build an offline preview.",
                            color = MaterialTheme.colors.onSurface.copy(.62f))
                    }
                })
            }
        }
    }
}

@Composable
private fun ModificationSummary(rom: Rom) {
    val detected = remember(rom) {
        RomModificationDetector.detect(rom).filterValues { it.isDetected }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("ROM MODIFICATIONS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        if (detected.isEmpty()) Text("None identified from this definition",
            fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(.58f))
        detected.forEach { (modification, evidence) ->
            Text("${modification.displayName} · ${evidence.displayName}",
                color = MaterialTheme.colors.primary, fontSize = 11.sp,
                modifier = Modifier.background(MaterialTheme.colors.surface,
                    RoundedCornerShape(12.dp)).padding(horizontal = 9.dp,
                    vertical = 4.dp))
        }
    }
}

@Composable
private fun SafetySummary(draft: LiveTuneDraft?) {
    val context = PlatformContext.getInstance()
    val checks = listOf(
        (draft != null) to "Mapped changed bytes",
        (context.platform == VehiclePlatform.SUBARU &&
            context.module == VehicleModule.ENGINE_ECU) to "Subaru engine ECU",
        (context.dimeModState == DimeModState.ACTIVE) to "DimeMod runtime",
        context.isRamTuneRuntimeAvailable to "RAM Tune advertised",
        context.hasQualifiedRamTuneMetadata() to "Qualified RAM metadata"
    )
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colors.surface,
        RoundedCornerShape(7.dp)).padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        checks.forEach { (passed, label) ->
            Text((if (passed) "● " else "○ ") + label,
                color = if (passed) Color(0xFF52B982)
                else MaterialTheme.colors.onSurface.copy(.58f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Column(Modifier.background(MaterialTheme.colors.surface,
        RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, fontWeight = FontWeight.Bold, fontSize = 9.sp,
            color = MaterialTheme.colors.onSurface.copy(.5f))
    }
}

private fun hex(bytes: ByteArray): String = bytes.take(8).joinToString(" ") {
    "%02X".format(it.toInt() and 0xFF)
} + if (bytes.size > 8) " …" else ""
