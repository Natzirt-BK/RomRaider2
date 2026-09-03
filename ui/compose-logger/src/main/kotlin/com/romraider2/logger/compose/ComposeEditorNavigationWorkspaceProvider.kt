/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romraider.editor.document.EditorDocument
import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceContext
import com.romraider.editor.workspace.EditorWorkspaceService
import com.romraider.editor.workspace.TableLocation
import com.romraider.maps.Rom
import com.romraider.maps.Table

private enum class NavigationSection(val label: String) {
    ALL("All"), FAVORITES("Favorites"), CHANGED("Changed"), RECENT("Recent")
}

internal data class CalibrationEntry(
    val rom: Rom,
    val table: Table,
    val romId: String,
    val romName: String,
    val favorite: Boolean,
    val changedCells: Int,
    val active: Boolean
)

internal data class NavigationState(
    val entries: List<CalibrationEntry>,
    val favorites: List<TableLocation>,
    val recent: List<TableLocation>,
    val changed: Map<TableLocation, Int>,
    val canGoBack: Boolean,
    val canGoForward: Boolean
)

internal data class CalibrationCategoryNode(
    val name: String,
    val path: String,
    val entries: List<CalibrationEntry>,
    val children: List<CalibrationCategoryNode>
) {
    val calibrationCount: Int
        get() = entries.size + children.sumOf { it.calibrationCount }
    val changedCount: Int
        get() = entries.sumOf { it.changedCells } +
            children.sumOf { it.changedCount }
    val containsActive: Boolean
        get() = entries.any { it.active } || children.any { it.containsActive }
}

private val navGraphite = Color(0xFF10151B)
private val navSurface = Color(0xFF171E26)
private val navRaised = Color(0xFF202934)
private val navText = Color(0xFFE4E8ED)
private val navSecondary = Color(0xFFA9B1BA)
private val navAccent = Color(0xFF56AEA6)
private val navDanger = Color(0xFFD95B66)

@Composable
internal fun EditorNavigationSurface(
    context: EditorNavigationWorkspaceContext,
    refreshRequest: Int,
    focusRequest: Int
) {
    val dark = rememberApplicationDarkTheme()
    val colors = if (dark) darkColors(
        primary = navAccent,
        background = navGraphite,
        surface = navSurface,
        onPrimary = Color.White,
        onBackground = navText,
        onSurface = navText
    ) else lightColors(
        primary = Color(0xFF007F78),
        background = Color(0xFFF2F5F8),
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = Color(0xFF1C2228),
        onSurface = Color(0xFF1C2228)
    )
    var localRefresh by remember { mutableIntStateOf(0) }
    val state = remember(refreshRequest, localRefresh) {
        navigationState(context)
    }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf(NavigationSection.ALL) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) focusRequester.requestFocus()
    }

    MaterialTheme(colors = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colors.surface)
                    .padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CALIBRATIONS", color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            CompactHistoryButton("‹", state.canGoBack) {
                                context.goBack()
                            }
                            CompactHistoryButton("›", state.canGoForward) {
                                context.goForward()
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(query, { query = it },
                        Modifier.fillMaxWidth().focusRequester(focusRequester),
                        label = { Text("Search maps") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        NavigationSection.entries.forEach { candidate ->
                            SectionButton(candidate, section == candidate) {
                                section = candidate
                            }
                        }
                    }
                }
                NavigationContent(context, state, section, query, dark,
                    expandedGroups,
                    onChanged = { localRefresh++ },
                    modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun CompactHistoryButton(label: String, enabled: Boolean,
    action: () -> Unit) {
    OutlinedButton(action, enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 11.dp, vertical = 1.dp),
        modifier = Modifier.height(30.dp)) {
        Text(label, fontSize = 19.sp)
    }
}

@Composable
private fun RowScope.SectionButton(section: NavigationSection, selected: Boolean,
    action: () -> Unit) {
    val modifier = Modifier.weight(1f).height(32.dp)
    if (selected) {
        Button(action, modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 1.dp, vertical = 1.dp)) {
            Text(section.label, fontSize = 10.sp, maxLines = 1)
        }
    } else {
        TextButton(action, modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 1.dp, vertical = 1.dp)) {
            Text(section.label, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun NavigationContent(
    context: EditorNavigationWorkspaceContext,
    state: NavigationState,
    section: NavigationSection,
    query: String,
    dark: Boolean,
    expandedGroups: MutableMap<String, Boolean>,
    onChanged: () -> Unit,
    modifier: Modifier
) {
    val normalized = query.trim().lowercase()
    val entries = when (section) {
        NavigationSection.ALL -> state.entries
        NavigationSection.FAVORITES -> state.entries.filter { it.favorite }
        NavigationSection.CHANGED -> state.entries.filter { it.changedCells > 0 }
        NavigationSection.RECENT -> state.recent.mapNotNull { location ->
            state.entries.firstOrNull { it.romId == location.romId &&
                it.table.name == location.tableName }
        }
    }.filter { entry ->
        normalized.isEmpty() || entry.table.name.lowercase().contains(normalized) ||
            entry.table.category.orEmpty().lowercase().contains(normalized) ||
            entry.romName.lowercase().contains(normalized)
    }

    if (entries.isEmpty()) {
        Column(modifier.padding(18.dp)) {
            Text(if (state.entries.isEmpty()) "Open a ROM to browse its maps."
                else "No maps match this view.", color = secondaryNav(dark),
                fontSize = 13.sp)
        }
        return
    }

    LazyColumn(modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        entries.groupBy { it.rom }.forEach { (_, romEntries) ->
            val heading = romEntries.first()
            item("rom:${heading.romId}") { RomHeading(heading, dark) }
            calibrationCategoryTree(romEntries).forEach { category ->
                categoryItems(category, heading.romId, 0,
                    normalized.isNotEmpty(), expandedGroups, dark, context,
                    onChanged)
            }
        }
    }
}

private fun LazyListScope.categoryItems(
    category: CalibrationCategoryNode,
    romId: String,
    depth: Int,
    searching: Boolean,
    expandedGroups: MutableMap<String, Boolean>,
    dark: Boolean,
    context: EditorNavigationWorkspaceContext,
    onChanged: () -> Unit
) {
    val key = "$romId:${category.path}"
    val expanded = searching ||
        (expandedGroups[key] ?: category.containsActive)
    item("category:$key") {
        CategoryRow(category, depth, expanded, dark) {
            expandedGroups[key] = !expanded
        }
    }
    if (!expanded) return
    category.children.forEach { child ->
        categoryItems(child, romId, depth + 1, searching, expandedGroups,
            dark, context, onChanged)
    }
    category.entries.forEach { entry ->
        item("map:$romId:${System.identityHashCode(entry.table)}") {
            CalibrationRow(entry, depth + 1, dark,
                open = { context.open(entry.rom, entry.table) },
                toggleFavorite = {
                    context.toggleFavorite(entry.rom, entry.table)
                    onChanged()
                })
        }
    }
}

@Composable
private fun CategoryRow(
    category: CalibrationCategoryNode,
    depth: Int,
    expanded: Boolean,
    dark: Boolean,
    toggle: () -> Unit
) {
    Row(Modifier.fillMaxWidth()
        .padding(start = (depth * 11).dp)
        .background(raisedNav(dark).copy(alpha = if (depth == 0) .72f else .42f),
            RoundedCornerShape(6.dp))
        .clickable(onClick = toggle)
        .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (expanded) "▾" else "▸", Modifier.width(18.dp),
            color = MaterialTheme.colors.primary, fontSize = 12.sp,
            fontWeight = FontWeight.Bold)
        Text(category.name, Modifier.weight(1f), fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        if (category.changedCount > 0) {
            Text("${category.changedCount} changed", color = navDanger,
                fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
        }
        Text(category.calibrationCount.toString(), color = secondaryNav(dark),
            fontSize = 10.sp)
    }
}

@Composable
private fun RomHeading(entry: CalibrationEntry, dark: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp,
        start = 6.dp, end = 6.dp)) {
        Text(entry.romName, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(entry.romId, color = secondaryNav(dark), fontSize = 10.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CalibrationRow(entry: CalibrationEntry, depth: Int, dark: Boolean,
    open: () -> Unit, toggleFavorite: () -> Unit) {
    val accent = if (entry.changedCells > 0) navDanger
        else MaterialTheme.colors.primary
    Row(Modifier.fillMaxWidth()
        .padding(start = (depth * 11).dp)
        .background(if (entry.active) raisedNav(dark) else Color.Transparent,
            RoundedCornerShape(7.dp))
        .clickable(onClick = open).padding(start = 9.dp, top = 7.dp,
            bottom = 7.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(entry.table.name, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            fontWeight = if (entry.active) FontWeight.Bold else FontWeight.Normal)
        if (entry.changedCells > 0) {
            Text(entry.changedCells.toString(), color = accent, fontSize = 9.sp,
                fontWeight = FontWeight.Bold)
        }
        TextButton(toggleFavorite,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.width(32.dp).height(32.dp)) {
            Text(if (entry.favorite) "★" else "☆", color = accent,
                fontSize = 17.sp)
        }
    }
}

internal fun calibrationCategoryTree(
    entries: List<CalibrationEntry>
): List<CalibrationCategoryNode> {
    val root = MutableCalibrationCategory("", "")
    entries.forEach { entry ->
        val segments = entry.table.category.orEmpty().split("//")
            .map(String::trim).filter(String::isNotEmpty)
            .ifEmpty { listOf("Uncategorized") }
        var parent = root
        segments.forEach { segment ->
            val path = if (parent.path.isEmpty()) segment
                else "${parent.path}//$segment"
            parent = parent.children.getOrPut(segment) {
                MutableCalibrationCategory(segment, path)
            }
        }
        parent.entries += entry
    }
    return root.children.values.map(MutableCalibrationCategory::freeze)
}

private class MutableCalibrationCategory(
    val name: String,
    val path: String
) {
    val entries = mutableListOf<CalibrationEntry>()
    val children = linkedMapOf<String, MutableCalibrationCategory>()

    fun freeze(): CalibrationCategoryNode = CalibrationCategoryNode(
        name, path, entries.toList(), children.values.map { it.freeze() })
}

internal fun navigationState(
    context: EditorNavigationWorkspaceContext
): NavigationState {
    val snapshot = context.snapshot
    val changed = context.changedTables
    val entries = buildList {
        snapshot.documents.forEach { document: EditorDocument ->
            val rom = document.rom
            val romId = EditorWorkspaceService.romIdentity(rom)
            rom.tableCatalog.forEach { table ->
                val location = TableLocation(romId, table.name)
                add(CalibrationEntry(rom, table, romId, document.name,
                    context.isFavorite(rom, table), changed[location] ?: 0,
                    snapshot.activeRom === rom && snapshot.activeTable === table))
            }
        }
    }
    return NavigationState(entries, context.favorites, context.recent, changed,
        context.canGoBack(), context.canGoForward())
}

private fun secondaryNav(dark: Boolean) = if (dark) navSecondary
    else Color(0xFF5A6570)

private fun raisedNav(dark: Boolean) = if (dark) navRaised
    else Color(0xFFE2E9ED)
