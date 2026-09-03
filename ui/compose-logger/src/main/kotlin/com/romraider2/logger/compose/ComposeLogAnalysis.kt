/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import com.romraider.Version
import com.romraider.logger.analysis.ChannelStatistics
import com.romraider.logger.analysis.LogDataset
import com.romraider.logger.analysis.LogRange
import com.romraider.logger.analysis.LogStatisticsService
import java.util.Locale

/** Compose-owned, read-only analysis of one captured CSV log. */
@Composable
internal fun OfflineLogAnalysisWindow(
    dataset: LogDataset,
    onClose: () -> Unit
) {
    val windowIcon = romRaiderWindowIcon()
    val statistics = LogStatisticsService.analyze(dataset, LogRange.all(dataset))
    Window(
        onCloseRequest = onClose,
        title = "${Version.PRODUCT_NAME} ${Version.VERSION} | ${dataset.sourceName}",
        icon = windowIcon,
        state = rememberFittedWindowState(1120, 720)
    ) {
        val dark = rememberApplicationDarkTheme()
        MaterialTheme(colors = if (dark) darkColors(
            primary = Color(0xFF56AEA6),
            background = Color(0xFF10151B),
            surface = Color(0xFF171E26)
        ) else lightColors(primary = Color(0xFF007F78))) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text("Offline log analysis", fontSize = 24.sp,
                        fontWeight = FontWeight.Bold)
                    Text(dataset.sourceName,
                        color = MaterialTheme.colors.onSurface.copy(.62f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("CHANNELS", dataset.channelCount.toString())
                        SummaryCard("SAMPLES", dataset.rowCount.toString())
                        SummaryCard("SOURCE", "CSV capture")
                    }
                    Spacer(Modifier.height(16.dp))
                    val horizontal = rememberScrollState()
                    Column(Modifier.fillMaxSize().horizontalScroll(horizontal)) {
                        StatisticsHeader()
                        LazyColumn(Modifier.width(1020.dp).fillMaxSize()) {
                            items(statistics, key = { it.channel.index }) { row ->
                                StatisticsRow(row)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String) {
    Column(Modifier.width(184.dp).background(MaterialTheme.colors.surface)
        .padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, color = MaterialTheme.colors.primary, fontSize = 10.sp,
            fontWeight = FontWeight.Bold)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatisticsHeader() {
    Row(Modifier.width(1020.dp).background(MaterialTheme.colors.surface)
        .padding(vertical = 9.dp)) {
        TableCell("Channel", 300.dp, FontWeight.Bold)
        TableCell("Units", 100.dp, FontWeight.Bold)
        TableCell("Minimum", 120.dp, FontWeight.Bold)
        TableCell("Mean", 120.dp, FontWeight.Bold)
        TableCell("Median", 120.dp, FontWeight.Bold)
        TableCell("Maximum", 120.dp, FontWeight.Bold)
        TableCell("Samples", 100.dp, FontWeight.Bold)
    }
}

@Composable
private fun StatisticsRow(statistics: ChannelStatistics) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        TableCell(statistics.channel.name, 300.dp, FontWeight.Medium)
        TableCell(statistics.channel.units.ifBlank { "—" }, 100.dp)
        TableCell(format(statistics.minimum), 120.dp)
        TableCell(format(statistics.mean), 120.dp)
        TableCell(format(statistics.median), 120.dp)
        TableCell(format(statistics.maximum), 120.dp)
        TableCell(statistics.sampleCount.toString(), 100.dp)
    }
}

@Composable
private fun TableCell(
    value: String,
    width: androidx.compose.ui.unit.Dp,
    weight: FontWeight = FontWeight.Normal
) {
    Text(value, Modifier.width(width).padding(horizontal = 10.dp),
        fontSize = 12.sp, fontWeight = weight, maxLines = 1,
        overflow = TextOverflow.Ellipsis)
}

private fun format(value: Double): String = if (value.isFinite())
    String.format(Locale.ROOT, "%.3f", value) else "—"
