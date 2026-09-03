/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romraider.editor.compare.RomComparisonResult
import com.romraider.editor.compare.RomComparisonService
import com.romraider.editor.compare.TableComparison
import com.romraider.editor.compare.TableComparisonStatus
import com.romraider.editor.ecu.spi.RomComparisonWorkspaceContext
import com.romraider.maps.Rom

private val compareGraphite = Color(0xFF10151B)
private val compareSurface = Color(0xFF171E26)
private val compareRaised = Color(0xFF202934)
private val compareText = Color(0xFFE4E8ED)
private val compareSecondary = Color(0xFFA9B1BA)
private val compareTeal = Color(0xFF56AEA6)
private val compareRed = Color(0xFFD95C62)
private val compareAmber = Color(0xFFE0A84B)
private val compareGreen = Color(0xFF65B88A)

@Composable
internal fun RomComparisonWorkspace(context: RomComparisonWorkspaceContext) {
    val dark = rememberApplicationDarkTheme()
    val colors = if (dark) darkColors(
        primary = compareTeal,
        background = compareGraphite,
        surface = compareSurface,
        onPrimary = Color.White,
        onBackground = compareText,
        onSurface = compareText
    ) else lightColors(primary = Color(0xFF277E78))

    MaterialTheme(colors = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ComparisonContent(context, dark)
        }
    }
}

@Composable
private fun ComparisonContent(
    context: RomComparisonWorkspaceContext,
    dark: Boolean
) {
    val roms = context.roms
    var left by remember { mutableStateOf(roms.getOrNull(0)) }
    var right by remember { mutableStateOf(roms.getOrNull(1) ?: roms.getOrNull(0)) }
    var result by remember { mutableStateOf(RomComparisonService.compare(left, right)) }
    var differencesOnly by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<TableComparison?>(null) }
    LaunchedEffect(roms) {
        if (left !in roms) left = roms.getOrNull(0)
        if (right !in roms) right = roms.getOrNull(1) ?: roms.getOrNull(0)
        result = RomComparisonService.compare(left, right)
        selected = null
    }
    val rows = remember(result, differencesOnly) {
        comparisonRows(result, differencesOnly)
    }
    LaunchedEffect(rows) {
        if (selected !in rows || selected?.isAvailableInBoth != true) {
            selected = rows.firstOrNull { it.isAvailableInBoth }
        }
    }
    val secondary = if (dark) compareSecondary else Color(0xFF5E6872)
    val raised = if (dark) compareRaised else Color(0xFFF1F4F5)
    val outline = if (dark) Color(0xFF34404C) else Color(0xFFD5DBDE)

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Compare ROMs", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(
            "Compare calibration tables and open modified maps side by side.",
            color = secondary,
            fontSize = 13.sp
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 720.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RomSelectors(roms, left, right, { left = it }, { right = it })
                    CompareActions(differencesOnly, { differencesOnly = it }) {
                        result = RomComparisonService.compare(left, right)
                        selected = null
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RomSelectors(roms, left, right, { left = it }, { right = it })
                    CompareActions(differencesOnly, { differencesOnly = it }) {
                        result = RomComparisonService.compare(left, right)
                        selected = null
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .border(1.dp, outline, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = raised
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("STATUS", modifier = Modifier.width(150.dp), color = secondary,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("CALIBRATION TABLE", color = secondary, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                }
                Divider(color = outline)
                if (rows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No calibration differences found.", color = secondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(rows, key = { "${it.status}:${it.tableName}" }) { comparison ->
                            ComparisonRow(
                                comparison = comparison,
                                selected = selected === comparison,
                                dark = dark,
                                onSelect = { selected = comparison }
                            )
                            Divider(color = outline.copy(alpha = 0.65f))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(comparisonSummary(result), color = secondary, fontSize = 13.sp)
            Button(
                enabled = context.canOpenComparisons()
                    && selected?.isAvailableInBoth == true,
                onClick = {
                    selected?.let { context.openComparison(left, right, it) }
                }
            ) {
                Text("Open Selected Comparison")
            }
        }
    }
}

@Composable
private fun RomSelectors(
    roms: List<Rom>,
    left: Rom?,
    right: Rom?,
    onLeft: (Rom) -> Unit,
    onRight: (Rom) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RomPicker("Left ROM", left, roms, onLeft)
        RomPicker("Right ROM", right, roms, onRight)
    }
}

@Composable
private fun RomPicker(label: String, selected: Rom?, roms: List<Rom>, onSelect: (Rom) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.width(190.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(label, fontSize = 10.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.62f))
                Text(
                    selected?.fileName?.ifBlank { "Untitled ROM" } ?: "No ROM",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            roms.forEach { rom ->
                DropdownMenuItem(onClick = {
                    onSelect(rom)
                    expanded = false
                }) {
                    Text(rom.fileName.ifBlank { "Untitled ROM" })
                }
            }
        }
    }
}

@Composable
private fun CompareActions(
    differencesOnly: Boolean,
    onDifferencesOnly: (Boolean) -> Unit,
    onCompare: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = differencesOnly, onCheckedChange = onDifferencesOnly)
        Text("Differences only", fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Button(onClick = onCompare) { Text("Compare") }
    }
}

@Composable
private fun ComparisonRow(
    comparison: TableComparison,
    selected: Boolean,
    dark: Boolean,
    onSelect: () -> Unit
) {
    val statusColor = when (comparison.status) {
        TableComparisonStatus.DIFFERENT -> compareRed
        TableComparisonStatus.ONLY_LEFT, TableComparisonStatus.ONLY_RIGHT -> compareAmber
        TableComparisonStatus.EQUAL -> compareGreen
    }
    val selectedColor = if (dark) compareTeal.copy(alpha = 0.18f)
        else Color(0xFF277E78).copy(alpha = 0.12f)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) selectedColor else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            comparison.status.displayName,
            modifier = Modifier.width(150.dp),
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(comparison.tableName, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

internal fun comparisonRows(
    result: RomComparisonResult,
    differencesOnly: Boolean
): List<TableComparison> = result.tables.filter {
    !differencesOnly || it.status != TableComparisonStatus.EQUAL
}

internal fun comparisonSummary(result: RomComparisonResult): String =
    if (result.isIdentical) {
        "${result.equalCount} unchanged tables • ROM calibrations match"
    } else {
        "${result.differentCount} modified • ${result.missingCount} missing • " +
            "${result.equalCount} unchanged"
    }
