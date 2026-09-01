/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.Colors
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romraider.logger.api.LiveDataSample
import com.romraider.logger.api.LoggerChannel
import com.romraider.logger.api.LoggerChannelKind
import com.romraider.logger.api.LoggerLiveDataListener
import com.romraider.logger.api.LoggerSessionState
import com.romraider.logger.api.LoggerWorkspaceView
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceProvider
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.sqrt

class ComposeLoggerWorkspaceProvider : LoggerWorkspaceProvider {
    override fun getName(): String = "Compose Desktop Logger"

    override fun createWorkspace(context: LoggerWorkspaceContext): JComponent =
        ComposePanel().apply {
            setContent { LoggerWorkspace(context) }
        }
}

private val graphite = Color(0xFF202934)
private val steel = Color(0xFF5F7185)
private val recordGreen = Color(0xFF24784B)
private val faultRed = Color(0xFFD71920)
private val graphColors = listOf(
    Color(0xFF3E8ED0), Color(0xFFF0A63A), Color(0xFF42A66C),
    Color(0xFFB56BD5), Color(0xFFE05B65), Color(0xFF42B8B2)
)

@Composable
private fun LoggerWorkspace(context: LoggerWorkspaceContext) {
    var channels by remember { mutableStateOf(context.channels.channels) }
    var samples by remember {
        mutableStateOf(context.liveData.latestSamples.associateBy {
            it.parameterId
        })
    }
    var history by remember {
        mutableStateOf(context.liveData.recentSamples.mapValues {
            it.value.toList()
        })
    }
    var sessionState by remember { mutableStateOf(context.session.state) }
    var activeView by remember {
        mutableStateOf(context.preferences.view)
    }
    var darkTheme by remember {
        mutableStateOf(context.preferences.isDarkTheme)
    }
    var filter by remember { mutableStateOf("") }
    var graphPaused by remember { mutableStateOf(false) }
    var pausedHistory by remember {
        mutableStateOf<Map<String, List<LiveDataSample>>>(emptyMap())
    }
    val rootFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(context) {
        rootFocus.requestFocus()
    }

    DisposableEffect(context) {
        val channelListener = Consumer<List<LoggerChannel>> { next ->
            onUiThread { channels = next.toList() }
        }
        val stateListener = Consumer<LoggerSessionState> { next ->
            onUiThread { sessionState = next }
        }
        val liveListener = object : LoggerLiveDataListener {
            override fun sessionStateChanged(state: LoggerSessionState) {
                onUiThread { sessionState = state }
            }

            override fun sampleUpdated(sample: LiveDataSample) {
                onUiThread {
                    samples = samples + (sample.parameterId to sample)
                    val next = history.toMutableMap()
                    next[sample.parameterId] =
                        (next[sample.parameterId].orEmpty() + sample)
                            .takeLast(240)
                    history = next
                }
            }

            override fun parameterRemoved(parameterId: String) {
                onUiThread {
                    samples = samples - parameterId
                    history = history - parameterId
                }
            }
        }
        context.channels.addListener(channelListener)
        context.session.addStateListener(stateListener)
        context.liveData.addListener(liveListener)
        onDispose {
            context.channels.removeListener(channelListener)
            context.session.removeStateListener(stateListener)
            context.liveData.removeListener(liveListener)
        }
    }

    val colors = workspaceColors(darkTheme)
    MaterialTheme(colors = colors) {
        Surface(
            Modifier.fillMaxSize()
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else if (event.isCtrlPressed) {
                        val next = workspaceForShortcut(event.key)
                        when {
                            next != null -> {
                                activeView = next
                                context.preferences.setView(next)
                                true
                            }
                            event.key == Key.F -> {
                                searchFocus.requestFocus()
                                true
                            }
                            else -> false
                        }
                    } else if (event.key == Key.Escape && filter.isNotEmpty()) {
                        filter = ""
                        true
                    } else {
                        false
                    }
                },
            color = colors.background
        ) {
            Column(Modifier.fillMaxSize()) {
                SessionBar(
                    state = sessionState,
                    selectedCount = channels.count { it.isSelected },
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        darkTheme = !darkTheme
                        context.preferences.setDarkTheme(darkTheme)
                    },
                    onConnect = { context.session.connect() },
                    onDisconnect = { context.session.disconnect() },
                    onStartRecording = { context.session.startRecording() },
                    onStopRecording = { context.session.stopRecording() }
                )
                Divider(color = colors.onSurface.copy(alpha = .14f))
                SessionTelemetry(channels, samples, history)
                Divider(color = colors.onSurface.copy(alpha = .14f))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    if (maxWidth < 820.dp) {
                        Column(Modifier.fillMaxSize()) {
                            ChannelRail(
                                channels, filter, { filter = it },
                                context, searchFocus,
                                Modifier.fillMaxWidth().heightIn(
                                    min = 170.dp, max = 205.dp)
                            )
                            Divider(color = colors.onSurface.copy(alpha = .14f))
                            WorkspaceBody(
                                activeView, channels, samples, history,
                                graphPaused, pausedHistory,
                                onToggleGraphPause = {
                                    if (graphPaused) {
                                        graphPaused = false
                                    } else {
                                        pausedHistory = history.mapValues {
                                            it.value.toList()
                                        }
                                        graphPaused = true
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            ChannelRail(
                                channels, filter, { filter = it }, context,
                                searchFocus,
                                Modifier.width(286.dp).fillMaxHeight())
                            Divider(
                                Modifier.fillMaxHeight().width(1.dp),
                                color = colors.onSurface.copy(alpha = .14f)
                            )
                            WorkspaceBody(
                                activeView, channels, samples, history,
                                graphPaused, pausedHistory,
                                onToggleGraphPause = {
                                    if (graphPaused) {
                                        graphPaused = false
                                    } else {
                                        pausedHistory = history.mapValues {
                                            it.value.toList()
                                        }
                                        graphPaused = true
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                WorkspaceNavigation(activeView) {
                    activeView = it
                    context.preferences.setView(it)
                }
            }
        }
    }
}

@Composable
private fun SessionTelemetry(
    channels: List<LoggerChannel>,
    samples: Map<String, LiveDataSample>,
    history: Map<String, List<LiveDataSample>>
) {
    val metrics = sessionMetrics(channels, samples, history)
    val scroll = rememberScrollState()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll)
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetricText("Selected", metrics.selectedChannels.toString())
        MetricText("Live", metrics.liveChannels.toString())
        MetricText("Received", "${metrics.receivedPoints} points")
        MetricText("Window", metrics.windowLabel)
    }
}

@Composable
private fun MetricText(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), color = MaterialTheme.colors.onSurface.copy(.48f),
            fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SessionBar(
    state: LoggerSessionState,
    selectedCount: Int,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val connected = state == LoggerSessionState.LIVE_ECU ||
        state == LoggerSessionState.LIVE_EXTERNAL ||
        state == LoggerSessionState.RECORDING
    val busy = state == LoggerSessionState.CONNECTING ||
        state == LoggerSessionState.RECONNECTING
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 700.dp
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (compact) "LOGGER" else "LOGGER WORKSPACE",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1)
                if (!compact) {
                    Text("$selectedCount channels selected",
                        color = MaterialTheme.colors.onSurface.copy(alpha = .66f),
                        fontSize = 12.sp)
                }
            }
            StatusPill(state, compact)
            Button(
                onClick = if (connected || busy) onDisconnect else onConnect,
                enabled = state != LoggerSessionState.RECORDING,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = steel)
            ) {
                Text(if (connected || busy) "Disconnect" else "Connect")
            }
            Button(
                onClick = if (state == LoggerSessionState.RECORDING)
                    onStopRecording else onStartRecording,
                enabled = connected,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = recordGreen)
            ) {
                Text(
                    if (state == LoggerSessionState.RECORDING) {
                        if (compact) "Stop" else "Stop recording"
                    } else {
                        if (compact) "Record" else "Start recording"
                    }
                )
            }
            TextButton(onClick = onToggleTheme, Modifier.height(48.dp)) {
                Text(if (darkTheme) "Light" else "Dark")
            }
        }
    }
}

@Composable
private fun StatusPill(state: LoggerSessionState, compact: Boolean = false) {
    val color = when (state) {
        LoggerSessionState.LIVE_ECU,
        LoggerSessionState.LIVE_EXTERNAL -> recordGreen
        LoggerSessionState.RECORDING -> faultRed
        LoggerSessionState.CONNECTING,
        LoggerSessionState.RECONNECTING -> Color(0xFFD18A22)
        LoggerSessionState.STOPPED -> MaterialTheme.colors.onSurface.copy(.42f)
    }
    Row(
        Modifier.background(color.copy(alpha = .16f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = .55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(7.dp))
        Text(if (compact && state == LoggerSessionState.LIVE_ECU) "Live"
            else stateLabel(state), fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp)
    }
}

@Composable
private fun ChannelRail(
    channels: List<LoggerChannel>,
    filter: String,
    onFilterChanged: (String) -> Unit,
    context: LoggerWorkspaceContext,
    searchFocus: FocusRequester,
    modifier: Modifier
) {
    val needle = filter.trim().lowercase()
    val visible = channels.filter {
        needle.isEmpty() || it.name.lowercase().contains(needle) ||
            it.parameterId.lowercase().contains(needle) ||
            it.units.lowercase().contains(needle)
    }
    val selected = channels.filter { it.isSelected }
    Column(modifier.background(MaterialTheme.colors.surface)) {
        Text("CHANNELS", Modifier.padding(start = 14.dp, top = 13.dp),
            fontWeight = FontWeight.Bold, fontSize = 13.sp)
        TextField(
            value = filter,
            onValueChange = onFilterChanged,
            placeholder = { Text("Search channels · Ctrl+F") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(10.dp)
                .focusRequester(searchFocus)
                .semantics { contentDescription = "Search Logger channels" }
        )
        if (channels.isEmpty()) {
            EmptyState(
                "No Logger channels are available",
                "Add a Logger definition, then return here to choose channels.",
                Modifier.weight(1f)
            )
        } else if (visible.isEmpty()) {
            EmptyState(
                "No matching channels",
                "Try a name, parameter ID, or unit.",
                Modifier.weight(1f)
            )
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                LoggerChannelKind.entries.forEach { kind ->
                    val group = visible.filter { it.kind == kind }
                    if (group.isNotEmpty()) {
                        item(key = "heading-${kind.name}") {
                            Text(
                                kind.displayName.uppercase(),
                                Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colors.background)
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                color = MaterialTheme.colors.onSurface.copy(.62f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(group, key = { it.parameterId }) { channel ->
                            val colorIndex = selected.indexOf(channel)
                            ChannelRow(
                                channel,
                                colorIndex.takeIf { it >= 0 }?.let {
                                    graphColors[it % graphColors.size]
                                }
                            ) {
                                context.channels.setSelected(
                                    channel.parameterId, it)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LoggerChannel,
    accentColor: Color?,
    onSelected: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .toggleable(
                value = channel.isSelected,
                role = Role.Checkbox,
                onValueChange = onSelected
            )
            .semantics(mergeDescendants = true) {
                contentDescription = channelAccessibilityLabel(channel)
            }
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(channel.isSelected, onCheckedChange = null)
        Box(
            Modifier.width(3.dp).height(30.dp)
                .background(
                    accentColor ?: MaterialTheme.colors.onSurface.copy(.12f),
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(end = 6.dp)) {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp)
            Text(
                listOf(channel.parameterId, channel.units)
                    .filter { it.isNotBlank() }.joinToString("  •  "),
                color = MaterialTheme.colors.onSurface.copy(.57f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WorkspaceBody(
    activeView: LoggerWorkspaceView,
    channels: List<LoggerChannel>,
    samples: Map<String, LiveDataSample>,
    history: Map<String, List<LiveDataSample>>,
    graphPaused: Boolean,
    pausedHistory: Map<String, List<LiveDataSample>>,
    onToggleGraphPause: () -> Unit,
    modifier: Modifier
) {
    val selected = channels.filter { it.isSelected }
    val graphHistory = if (graphPaused) pausedHistory else history
    Box(modifier.padding(14.dp)) {
        when (activeView) {
            LoggerWorkspaceView.OVERVIEW -> OverviewWorkspace(
                selected, samples, history, graphHistory, graphPaused,
                onToggleGraphPause)
            LoggerWorkspaceView.DATA -> DataWorkspace(selected, samples, history)
            LoggerWorkspaceView.GRAPH -> GraphWorkspace(
                selected, graphHistory, graphPaused, onToggleGraphPause)
            LoggerWorkspaceView.DASHBOARD -> DashboardWorkspace(
                selected, samples, history)
        }
    }
}

@Composable
private fun OverviewWorkspace(
    selected: List<LoggerChannel>,
    samples: Map<String, LiveDataSample>,
    liveHistory: Map<String, List<LiveDataSample>>,
    graphHistory: Map<String, List<LiveDataSample>>,
    graphPaused: Boolean,
    onToggleGraphPause: () -> Unit
) {
    if (selected.isEmpty()) {
        EmptyState(
            "Build a session overview",
            "Choose channels to create live cards and received-sample trends."
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        SectionTitle(
            "Session overview",
            "Live values, measured peaks, and trends in one workspace"
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardHeight = if (maxWidth < 700.dp) 108.dp else 142.dp
            LazyRow(
                Modifier.fillMaxWidth().height(cardHeight),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(selected.take(6),
                    key = { "overview-${it.parameterId}" }) { channel ->
                    LiveValueCard(
                        channel,
                        samples[channel.parameterId],
                        liveHistory[channel.parameterId].orEmpty(),
                        graphColors[selected.indexOf(channel) % graphColors.size],
                        Modifier.width(210.dp).fillMaxHeight()
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GraphWorkspace(
            selected, graphHistory, graphPaused, onToggleGraphPause,
            Modifier.weight(1f).fillMaxWidth(), "Live trends"
        )
    }
}

@Composable
private fun DataWorkspace(
    selected: List<LoggerChannel>,
    samples: Map<String, LiveDataSample>,
    history: Map<String, List<LiveDataSample>>
) {
    if (selected.isEmpty()) {
        EmptyState(
            "Choose channels to begin",
            "Selected channels appear here with current values and measured statistics."
        )
        return
    }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        SectionTitle("Live data", "Statistics use received samples only")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.horizontalScroll(scroll).requiredWidth(1050.dp)) {
                DataHeader()
                LazyColumn(Modifier.weight(1f)) {
                    items(selected, key = { it.parameterId }) { channel ->
                        DataRow(
                            channel,
                            samples[channel.parameterId],
                            statistics(history[channel.parameterId].orEmpty())
                        )
                    }
                }
            }
            HorizontalScrollbar(
                adapter = androidx.compose.foundation.rememberScrollbarAdapter(scroll),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DataHeader() {
    Row(
        Modifier.fillMaxWidth().background(graphite)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell("Channel", 230.dp, Color.White, FontWeight.Bold)
        TableCell("Current", 110.dp, Color.White, FontWeight.Bold)
        TableCell("Minimum", 110.dp, Color.White, FontWeight.Bold)
        TableCell("Maximum", 110.dp, Color.White, FontWeight.Bold)
        TableCell("Average", 110.dp, Color.White, FontWeight.Bold)
        TableCell("Median", 110.dp, Color.White, FontWeight.Bold)
        TableCell("Std dev", 110.dp, Color.White, FontWeight.Bold)
        TableCell("Samples", 90.dp, Color.White, FontWeight.Bold)
    }
}

@Composable
private fun DataRow(
    channel: LoggerChannel,
    latest: LiveDataSample?,
    stats: SampleStatistics?
) {
    Row(
        Modifier.fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colors.onSurface.copy(.12f))
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(230.dp)) {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(channel.units,
                color = MaterialTheme.colors.onSurface.copy(.55f),
                fontSize = 11.sp)
        }
        TableCell(latest?.displayValue ?: "—", 110.dp)
        TableCell(stats?.minimum?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.maximum?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.average?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.median?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.standardDeviation?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.count?.toString() ?: "0", 90.dp)
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    width: Dp,
    color: Color = MaterialTheme.colors.onSurface,
    weight: FontWeight = FontWeight.Normal
) {
    Text(text, Modifier.width(width).padding(horizontal = 7.dp),
        color = color, fontWeight = weight, fontSize = 12.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun GraphWorkspace(
    selected: List<LoggerChannel>,
    history: Map<String, List<LiveDataSample>>,
    paused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    title: String = "Live graph"
) {
    val plotted = selected.filter { history[it.parameterId].orEmpty().size > 1 }
    if (plotted.isEmpty()) {
        EmptyState(
            "Waiting for live samples",
            "Select channels and connect. Lines are drawn only from received data.",
            modifier
        )
        return
    }
    val displayed = plotted.take(graphColors.size)
    Column(modifier) {
        GraphHeader(title, paused, onTogglePause)
        if (plotted.size > displayed.size) {
            Text(
                "Showing ${displayed.size} of ${plotted.size} selected channels",
                Modifier.padding(top = 7.dp),
                color = MaterialTheme.colors.onSurface.copy(.60f),
                fontSize = 11.sp
            )
        }
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(displayed, key = { it.parameterId }) { channel ->
                val index = displayed.indexOf(channel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(
                        graphColors[index], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(5.dp))
                    Text(channel.name, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val gridColor = MaterialTheme.colors.onSurface.copy(alpha = .10f)
        Canvas(
            Modifier.weight(1f).fillMaxWidth()
                .semantics {
                    contentDescription = "Live graph showing ${displayed.size} " +
                        "channels from received samples"
                }
                .background(MaterialTheme.colors.surface,
                    RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colors.onSurface.copy(.14f),
                    RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            for (line in 0..5) {
                val y = size.height * line / 5f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }
            for (line in 0..8) {
                val x = size.width * line / 8f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            displayed.forEachIndexed { index, channel ->
                val values = history[channel.parameterId].orEmpty()
                    .map { it.rawValue }
                val minimum = values.minOrNull() ?: return@forEachIndexed
                val maximum = values.maxOrNull() ?: return@forEachIndexed
                val span = (maximum - minimum).takeIf { it != 0.0 } ?: 1.0
                val path = Path()
                values.forEachIndexed { sampleIndex, value ->
                    val x = if (values.size == 1) 0f else
                        size.width * sampleIndex / (values.size - 1f)
                    val y = size.height -
                        (size.height * ((value - minimum) / span)).toFloat()
                    if (sampleIndex == 0) path.moveTo(x, y)
                    else path.lineTo(x, y)
                }
                drawPath(path, graphColors[index], style = Stroke(
                    width = 2.5f, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun GraphHeader(
    title: String,
    paused: Boolean,
    onTogglePause: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (paused) "Paused snapshot · received samples remain unchanged"
                else "Each channel is scaled to its measured range",
                color = MaterialTheme.colors.onSurface.copy(.57f),
                fontSize = 12.sp
            )
        }
        TextButton(
            onClick = onTogglePause,
            modifier = Modifier.height(48.dp)
        ) {
            Text(if (paused) "Resume graph" else "Pause graph")
        }
    }
}

@Composable
private fun DashboardWorkspace(
    selected: List<LoggerChannel>,
    samples: Map<String, LiveDataSample>,
    history: Map<String, List<LiveDataSample>>
) {
    if (selected.isEmpty()) {
        EmptyState(
            "Build a live dashboard",
            "Choose channels from the rail to create live value cards."
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        SectionTitle("Dashboard", "Current values and measured session peaks")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            gridItems(selected, key = { it.parameterId }) { channel ->
                LiveValueCard(
                    channel,
                    samples[channel.parameterId],
                    history[channel.parameterId].orEmpty(),
                    graphColors[selected.indexOf(channel) % graphColors.size],
                    Modifier.fillMaxWidth().height(154.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveValueCard(
    channel: LoggerChannel,
    sample: LiveDataSample?,
    samples: List<LiveDataSample>,
    accentColor: Color,
    modifier: Modifier
) {
    val stats = statistics(samples)
    Column(
        modifier.background(MaterialTheme.colors.surface, RoundedCornerShape(9.dp))
            .border(1.dp, MaterialTheme.colors.onSurface.copy(.13f),
                RoundedCornerShape(9.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = liveValueAccessibilityLabel(
                    channel, sample, stats)
            }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(
                accentColor, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(7.dp))
            Text(channel.name, Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.parameterId,
                color = MaterialTheme.colors.onSurface.copy(.45f),
                fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(sample?.displayValue ?: "—", Modifier.weight(1f),
                fontSize = 27.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.units.isNotBlank()) {
                Text(channel.units,
                    color = MaterialTheme.colors.onSurface.copy(.60f),
                    fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        MeasuredRangeBar(sample?.rawValue, stats)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("MIN  ${stats?.minimum?.formatValue() ?: "—"}",
                Modifier.weight(1f),
                color = MaterialTheme.colors.onSurface.copy(.55f),
                fontSize = 10.sp)
            Text("MAX  ${stats?.maximum?.formatValue() ?: "—"}",
                color = MaterialTheme.colors.onSurface.copy(.55f),
                fontSize = 10.sp)
        }
    }
}

@Composable
private fun MeasuredRangeBar(current: Double?, stats: SampleStatistics?) {
    val track = MaterialTheme.colors.onSurface.copy(alpha = .10f)
    val fill = MaterialTheme.colors.primary
    Canvas(
        Modifier.fillMaxWidth().height(7.dp)
            .semantics { contentDescription = "Position in measured range" }
    ) {
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
        if (current != null && stats != null) {
            val span = stats.maximum - stats.minimum
            val progress = if (span == 0.0) 1f else
                ((current - stats.minimum) / span).coerceIn(0.0, 1.0).toFloat()
            drawRoundRect(
                fill,
                size = androidx.compose.ui.geometry.Size(size.width * progress,
                    size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(detail, color = MaterialTheme.colors.onSurface.copy(.57f),
            fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Box(modifier.padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(detail, color = MaterialTheme.colors.onSurface.copy(.62f),
                fontSize = 13.sp)
        }
    }
}

@Composable
private fun WorkspaceNavigation(
    active: LoggerWorkspaceView,
    onSelect: (LoggerWorkspaceView) -> Unit
) {
    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = .14f))
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(MaterialTheme.colors.surface)
    ) {
        val showShortcuts = maxWidth >= 720.dp
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            LoggerWorkspaceView.values().forEachIndexed { index, view ->
                TextButton(
                    onClick = { onSelect(view) },
                    modifier = Modifier.height(48.dp).padding(horizontal = 5.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (view == active) steel
                        else MaterialTheme.colors.onSurface.copy(.72f))
                ) {
                    Text(
                        if (showShortcuts) {
                            "${view.displayName}  Ctrl+${index + 1}"
                        } else view.displayName,
                        fontWeight = if (view == active) FontWeight.Bold
                        else FontWeight.Normal
                    )
                }
            }
        }
    }
}

internal fun workspaceForShortcut(key: Key): LoggerWorkspaceView? = when (key) {
    Key.One -> LoggerWorkspaceView.OVERVIEW
    Key.Two -> LoggerWorkspaceView.DATA
    Key.Three -> LoggerWorkspaceView.GRAPH
    Key.Four -> LoggerWorkspaceView.DASHBOARD
    else -> null
}

internal fun channelAccessibilityLabel(channel: LoggerChannel): String {
    val details = listOf(channel.name, "ID ${channel.parameterId}", channel.units)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    return "$details, ${if (channel.isSelected) "selected" else "not selected"}"
}

internal data class SampleStatistics(
    val minimum: Double,
    val maximum: Double,
    val average: Double,
    val median: Double,
    val standardDeviation: Double,
    val count: Int
)

internal data class SessionMetrics(
    val selectedChannels: Int,
    val liveChannels: Int,
    val receivedPoints: Int,
    val windowLabel: String
)

internal fun sessionMetrics(
    channels: List<LoggerChannel>,
    samples: Map<String, LiveDataSample>,
    history: Map<String, List<LiveDataSample>>
): SessionMetrics {
    val timestamps = history.values.asSequence().flatten()
        .map { it.timestampMillis }.toList()
    val duration = if (timestamps.size < 2) null
        else (timestamps.maxOrNull()!! - timestamps.minOrNull()!!) / 1000.0
    return SessionMetrics(
        channels.count { it.isSelected },
        samples.size,
        history.values.sumOf { it.size },
        duration?.let { "%.1f s".format(it) } ?: "—"
    )
}

internal fun liveValueAccessibilityLabel(
    channel: LoggerChannel,
    sample: LiveDataSample?,
    stats: SampleStatistics?
): String {
    val current = sample?.displayValue ?: "no current value"
    val units = channel.units.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    val range = if (stats == null) "no measured range" else
        "measured minimum ${stats.minimum.formatValue()} and maximum " +
            stats.maximum.formatValue()
    return "${channel.name}, $current$units, $range"
}

internal fun statistics(samples: List<LiveDataSample>): SampleStatistics? {
    if (samples.isEmpty()) return null
    val values = samples.map { it.rawValue }.sorted()
    val average = values.average()
    val median = if (values.size % 2 == 0) {
        val upper = values.size / 2
        (values[upper - 1] + values[upper]) / 2.0
    } else values[values.size / 2]
    val variance = values.sumOf { (it - average) * (it - average) } /
        values.size
    return SampleStatistics(
        values.first(), values.last(), average, median, sqrt(variance),
        values.size
    )
}

private fun Double.formatValue(): String {
    val magnitude = kotlin.math.abs(this)
    return when {
        magnitude >= 1000 -> "%.0f".format(this)
        magnitude >= 100 -> "%.1f".format(this)
        else -> "%.2f".format(this)
    }
}

private fun stateLabel(state: LoggerSessionState): String = when (state) {
    LoggerSessionState.STOPPED -> "Disconnected"
    LoggerSessionState.CONNECTING -> "Connecting"
    LoggerSessionState.RECONNECTING -> "Reconnecting"
    LoggerSessionState.LIVE_ECU -> "Live ECU"
    LoggerSessionState.LIVE_EXTERNAL -> "Live external"
    LoggerSessionState.RECORDING -> "Recording"
}

private fun workspaceColors(dark: Boolean): Colors = if (dark) {
    darkColors(
        primary = steel,
        primaryVariant = graphite,
        secondary = recordGreen,
        background = Color(0xFF10151B),
        surface = Color(0xFF171E26),
        error = faultRed,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFFE4E8ED),
        onSurface = Color(0xFFE4E8ED),
        onError = Color.White
    )
} else {
    lightColors(
        primary = graphite,
        primaryVariant = steel,
        secondary = recordGreen,
        background = Color(0xFFF4F6F8),
        surface = Color.White,
        error = faultRed,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1E252D),
        onSurface = Color(0xFF1E252D),
        onError = Color.White
    )
}

private inline fun onUiThread(crossinline action: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) action()
    else SwingUtilities.invokeLater { action() }
}
