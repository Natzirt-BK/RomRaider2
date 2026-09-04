/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.AlertDialog
import androidx.compose.material.Checkbox
import androidx.compose.material.Colors
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romraider.logger.api.LiveDataSample
import com.romraider.logger.api.LoggerChannel
import com.romraider.logger.api.LoggerChannelKind
import com.romraider.logger.api.LoggerGaugeTheme
import com.romraider.logger.api.LoggerGaugeConfiguration
import com.romraider.logger.api.LoggerGaugeLayout
import com.romraider.logger.api.LoggerDashboardTile
import com.romraider.logger.api.LoggerDashboardTileRole
import com.romraider.logger.api.LoggerDashboardTileSize
import com.romraider.logger.api.LoggerWorkspacePreferences
import com.romraider.logger.api.LoggerLiveDataListener
import com.romraider.logger.api.LoggerMessageSnapshot
import com.romraider.logger.api.LoggerSessionState
import com.romraider.logger.api.LoggerWorkspaceView
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext
import com.romraider.ui.RuntimeUiProfile
import java.awt.EventQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val graphite = Color(0xFF17212B)
private val steel = Color(0xFF50667C)
private val brandRed = Color(0xFFD92632)
private val recordGreen = Color(0xFF24784B)
private val faultRed = Color(0xFFD71920)
private const val RECENT_SAMPLE_LIMIT = 2_000
private val graphColors = listOf(
    Color(0xFF3D91E8), Color(0xFFF39A2B), Color(0xFF42B876),
    Color(0xFFB66BE3), Color(0xFFEF5967), Color(0xFF35BFC0),
    Color(0xFFE958A0), Color(0xFFF0C744)
)

@Composable
internal fun LoggerWorkspace(
    context: LoggerWorkspaceContext,
    onOpenSetup: (() -> Unit)? = null
) {
    val steamOs = remember { RuntimeUiProfile.isSteamOs() }
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
    var runtimeMessage by remember {
        mutableStateOf(context.messages.snapshot)
    }
    var activeView by remember {
        mutableStateOf(context.preferences.view)
    }
    var darkTheme by remember {
        mutableStateOf(steamOs || context.preferences.isDarkTheme)
    }
    var gaugeTheme by remember {
        mutableStateOf(if (steamOs) LoggerGaugeTheme.HANDHELD
            else context.preferences.gaugeTheme)
    }
    var gaugeLayout by remember {
        mutableStateOf(if (steamOs) LoggerGaugeLayout.STANDARD
            else context.preferences.gaugeLayout)
    }
    var filter by remember { mutableStateOf("") }
    var channelRailVisible by remember {
        mutableStateOf(context.preferences.isChannelRailVisible)
    }
    var graphPaused by remember { mutableStateOf(false) }
    var pausedHistory by remember {
        mutableStateOf<Map<String, List<LiveDataSample>>>(emptyMap())
    }
    val rootFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val pendingSamples = remember(context) {
        ConcurrentLinkedQueue<LiveDataSample>()
    }

    LaunchedEffect(context) {
        rootFocus.requestFocus()
        while (isActive) {
            delay(33)
            val received = mutableListOf<LiveDataSample>()
            while (true) {
                val next = pendingSamples.poll() ?: break
                received += next
            }
            if (received.isNotEmpty()) {
                val latest = linkedMapOf<String, LiveDataSample>()
                val nextHistory = history.toMutableMap()
                received.forEach { sample ->
                    latest[sample.parameterId] = sample
                    nextHistory[sample.parameterId] =
                        (nextHistory[sample.parameterId].orEmpty() + sample)
                            .takeLast(RECENT_SAMPLE_LIMIT)
                }
                samples = samples + latest
                history = nextHistory
            }
        }
    }

    DisposableEffect(context) {
        val channelListener = Consumer<List<LoggerChannel>> { next ->
            onUiThread { channels = next.toList() }
        }
        val stateListener = Consumer<LoggerSessionState> { next ->
            onUiThread { sessionState = next }
        }
        val messageListener = Consumer<LoggerMessageSnapshot> { next ->
            onUiThread { runtimeMessage = next }
        }
        val liveListener = object : LoggerLiveDataListener {
            override fun sessionStateChanged(state: LoggerSessionState) {
                onUiThread { sessionState = state }
            }

            override fun sampleUpdated(sample: LiveDataSample) {
                pendingSamples.add(sample)
            }

            override fun parameterRemoved(parameterId: String) {
                onUiThread {
                    pendingSamples.removeIf {
                        it.parameterId == parameterId
                    }
                    samples = samples - parameterId
                    history = history - parameterId
                }
            }
        }
        context.channels.addListener(channelListener)
        context.session.addStateListener(stateListener)
        context.liveData.addListener(liveListener)
        context.messages.addListener(messageListener)
        onDispose {
            pendingSamples.clear()
            context.channels.removeListener(channelListener)
            context.session.removeStateListener(stateListener)
            context.liveData.removeListener(liveListener)
            context.messages.removeListener(messageListener)
        }
    }

    val colors = workspaceColors(darkTheme, steamOs)
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
                if (!context.hasHostSessionControls()) {
                    SessionBar(
                        state = sessionState,
                        selectedCount = channels.count { it.isSelected },
                        darkTheme = darkTheme,
                        allowThemeChange = !steamOs,
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
                }
                SessionTelemetry(
                    channels, samples, history,
                    runtimeMessage,
                    themeLabel = if (context.hasHostSessionControls() && !steamOs) {
                        if (darkTheme) "Light workspace" else "Dark workspace"
                    } else null,
                    onToggleTheme = if (context.hasHostSessionControls() && !steamOs) {
                        {
                            darkTheme = !darkTheme
                            context.preferences.setDarkTheme(darkTheme)
                        }
                    } else null,
                    onOpenSetup = onOpenSetup,
                    onResetStatistics = {
                        pendingSamples.clear()
                        history = samples.mapValues { listOf(it.value) }
                        if (graphPaused) {
                            pausedHistory = history.mapValues {
                                it.value.toList()
                            }
                        }
                    }
                )
                Divider(color = colors.onSurface.copy(alpha = .14f))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val compactRailHeight = (maxHeight * .50f).coerceIn(
                        210.dp, 300.dp)
                    if (maxWidth < 820.dp) {
                        Column(Modifier.fillMaxSize()) {
                            if (channelRailVisible) {
                                ChannelRail(
                                    channels, samples, filter, { filter = it },
                                    context, searchFocus,
                                    onHide = {
                                        channelRailVisible = false
                                        context.preferences
                                            .setChannelRailVisible(false)
                                    },
                                    Modifier.fillMaxWidth().height(
                                        compactRailHeight),
                                    compact = true
                                )
                            } else {
                                ShowChannelsControl(
                                    compact = true,
                                    onShow = {
                                        channelRailVisible = true
                                        context.preferences
                                            .setChannelRailVisible(true)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Divider(color = colors.onSurface.copy(alpha = .14f))
                            WorkspaceBody(
                                activeView, channels, samples, history,
                                graphPaused, pausedHistory, gaugeTheme,
                                gaugeLayout,
                                allowGaugeThemes = !steamOs,
                                onGaugeTheme = {
                                    gaugeTheme = it
                                    context.preferences.setGaugeTheme(it)
                                },
                                onGaugeLayout = {
                                    gaugeLayout = it
                                    context.preferences.setGaugeLayout(it)
                                },
                                onToggleGraphPause = {
                                    if (graphPaused) {
                                        graphPaused = false
                                    } else {
                                        pausedHistory = history.mapValues {
                                            it.value.toList()
                                        }
                                        graphPaused = true
                                    }
                                }, preferences = context.preferences,
                                modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            if (channelRailVisible) {
                                ChannelRail(
                                    channels, samples, filter, { filter = it },
                                    context, searchFocus,
                                    onHide = {
                                        channelRailVisible = false
                                        context.preferences
                                            .setChannelRailVisible(false)
                                    },
                                    Modifier.width(304.dp).fillMaxHeight())
                            } else {
                                ShowChannelsControl(
                                    compact = false,
                                    onShow = {
                                        channelRailVisible = true
                                        context.preferences
                                            .setChannelRailVisible(true)
                                    },
                                    modifier = Modifier.width(68.dp)
                                        .fillMaxHeight()
                                )
                            }
                            Divider(
                                Modifier.fillMaxHeight().width(1.dp),
                                color = colors.onSurface.copy(alpha = .14f)
                            )
                            WorkspaceBody(
                                activeView, channels, samples, history,
                                graphPaused, pausedHistory, gaugeTheme,
                                gaugeLayout,
                                allowGaugeThemes = !steamOs,
                                onGaugeTheme = {
                                    gaugeTheme = it
                                    context.preferences.setGaugeTheme(it)
                                },
                                onGaugeLayout = {
                                    gaugeLayout = it
                                    context.preferences.setGaugeLayout(it)
                                },
                                onToggleGraphPause = {
                                    if (graphPaused) {
                                        graphPaused = false
                                    } else {
                                        pausedHistory = history.mapValues {
                                            it.value.toList()
                                        }
                                        graphPaused = true
                                    }
                                }, preferences = context.preferences,
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
    history: Map<String, List<LiveDataSample>>,
    runtimeMessage: LoggerMessageSnapshot,
    themeLabel: String?,
    onToggleTheme: (() -> Unit)?,
    onOpenSetup: (() -> Unit)?,
    onResetStatistics: () -> Unit
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
        if (runtimeMessage.message.isNotBlank()) {
            Text(runtimeMessage.message,
                color = if (runtimeMessage.isError)
                    MaterialTheme.colors.error
                else MaterialTheme.colors.onSurface.copy(alpha = .72f),
                fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 420.dp))
        }
        if (themeLabel != null && onToggleTheme != null) {
            TextButton(onClick = onToggleTheme, Modifier.height(34.dp)) {
                Text(themeLabel, fontSize = 11.sp)
            }
        }
        if (onOpenSetup != null) {
            TextButton(onClick = onOpenSetup, Modifier.height(34.dp)) {
                Text("Logger definition", fontSize = 11.sp)
            }
        }
        TextButton(onClick = onResetStatistics, Modifier.height(34.dp)) {
            Text("Reset statistics", fontSize = 11.sp)
        }
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
    allowThemeChange: Boolean,
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
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.background(MaterialTheme.colors.primary,
                            RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("RR2", color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp)
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(if (compact) "LOGGER" else "ROMRAIDER2 LOGGER",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1)
                    if (!compact) {
                        Text("$selectedCount channels selected",
                            color = MaterialTheme.colors.onSurface.copy(alpha = .58f),
                            fontSize = 11.sp)
                    }
                }
            }
            StatusPill(state, compact)
            Button(
                onClick = if (connected || busy) onDisconnect else onConnect,
                enabled = state != LoggerSessionState.RECORDING,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = steel)
            ) {
                Text(if (connected || busy) "Disconnect" else "Connect")
            }
            Button(
                onClick = if (state == LoggerSessionState.RECORDING)
                    onStopRecording else onStartRecording,
                enabled = connected,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(7.dp),
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
            if (allowThemeChange) {
                TextButton(onClick = onToggleTheme, Modifier.height(48.dp)) {
                    Text(if (darkTheme) "Light mode" else "Dark mode",
                        maxLines = 1)
                }
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
    samples: Map<String, LiveDataSample>,
    filter: String,
    onFilterChanged: (String) -> Unit,
    context: LoggerWorkspaceContext,
    searchFocus: FocusRequester,
    onHide: () -> Unit,
    modifier: Modifier,
    compact: Boolean = false
) {
    var activeKind by remember { mutableStateOf(LoggerChannelKind.PARAMETER) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val visible = remember(channels, activeKind, filter) {
        filterChannels(channels, activeKind, filter)
    }
    val channelColors = remember(channels) {
        channels.filter { it.isSelected }.mapIndexed { index, channel ->
            channel.parameterId to graphColors[index % graphColors.size]
        }.toMap()
    }
    val categoryChannels = remember(channels, activeKind) {
        channels.filter { it.kind == activeKind }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeKind, filter) {
        listState.scrollToItem(0)
    }
    Column(modifier.background(MaterialTheme.colors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp,
                top = if (compact) 5.dp else 13.dp,
                end = 14.dp, bottom = if (compact) 2.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CHANNELS", Modifier.weight(1f),
                fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "${categoryChannels.count { it.isSelected }} / " +
                    "${categoryChannels.size} selected",
                color = MaterialTheme.colors.onSurface.copy(.58f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onHide, Modifier.height(32.dp)) {
                Text("Hide", fontSize = 10.sp)
            }
        }
        ChannelKindSelector(channels, activeKind) { activeKind = it }
        TextField(
            value = filter,
            onValueChange = onFilterChanged,
            placeholder = {
                Text("Search ${activeKind.displayName.lowercase()}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 10.dp, vertical = if (compact) 4.dp else 10.dp)
                .focusRequester(searchFocus)
                .semantics { contentDescription = "Search Logger channels" }
        )
        if (!compact) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp,
                    bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(
                    onClick = {
                        context.channels.setSelected(
                            categoryChannels.map { it.parameterId }, false)
                    },
                    enabled = categoryChannels.any { it.isSelected },
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Text("Clear category", fontSize = 10.sp, maxLines = 1)
                }
                TextButton(
                    onClick = { confirmClearAll = true },
                    enabled = channels.any { it.isSelected },
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Text("Clear all", fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        if (channels.isEmpty()) {
            EmptyState(
                "No Logger channels are available",
                "Add a Logger definition, then return here to choose channels.",
                Modifier.weight(1f)
            )
        } else if (categoryChannels.isEmpty()) {
            EmptyState(
                "No ${activeKind.displayName.lowercase()} available",
                if (activeKind == LoggerChannelKind.EXTERNAL)
                    "Configure an external sensor, then return here to select its channels."
                else "The current Logger definition does not provide this channel type.",
                Modifier.weight(1f)
            )
        } else if (visible.isEmpty()) {
            EmptyState(
                "No matching ${activeKind.displayName.lowercase()}",
                "Try a name, parameter ID, or unit.",
                Modifier.weight(1f)
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .padding(end = 10.dp)
                        .pointerInput(listState) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                listState.dispatchRawDelta(-dragAmount)
                            }
                        }
                ) {
                    items(visible, key = { it.parameterId }) { channel ->
                        ChannelRow(
                            channel,
                            samples[channel.parameterId],
                            channelColors[channel.parameterId],
                            onSelected = {
                                context.channels.setSelected(
                                    channel.parameterId, it)
                            },
                            onUnitSelected = {
                                context.channels.setUnitOption(
                                    channel.parameterId, it)
                            }
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .width(10.dp)
                        .fillMaxHeight()
                        .padding(vertical = 3.dp)
                )
            }
        }
    }
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear all Logger channels?") },
            text = {
                Text("This removes all ${channels.count { it.isSelected }} " +
                    "selected channels from the current Logger session.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    context.channels.setSelected(
                        channels.map { it.parameterId }, false)
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ShowChannelsControl(
    compact: Boolean,
    onShow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.background(MaterialTheme.colors.surface)
            .clickable(role = Role.Button, onClick = onShow)
            .semantics { contentDescription = "Show Logger channels" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (compact) "Show channels" else "›\nCHANNELS",
            color = MaterialTheme.colors.primary,
            fontSize = if (compact) 10.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = if (compact) 1 else 2,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            modifier = if (compact) Modifier.padding(vertical = 10.dp)
                else Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ChannelKindSelector(
    channels: List<LoggerChannel>,
    active: LoggerChannelKind,
    onSelect: (LoggerChannelKind) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        LoggerChannelKind.entries.forEach { kind ->
            val selected = kind == active
            val count = channels.count { it.kind == kind }
            Column(
                Modifier.weight(1f).height(45.dp)
                    .background(
                        if (selected) MaterialTheme.colors.primary.copy(.18f)
                        else MaterialTheme.colors.background,
                        RoundedCornerShape(7.dp)
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colors.primary
                        else MaterialTheme.colors.onSurface.copy(.16f),
                        RoundedCornerShape(7.dp)
                    )
                    .clickable(role = Role.Button) { onSelect(kind) }
                    .semantics {
                        contentDescription =
                            "Show ${kind.displayName}, $count available"
                    }
                    .padding(horizontal = 3.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (kind == LoggerChannelKind.EXTERNAL)
                        "External\nSensors" else kind.displayName,
                    color = if (selected) MaterialTheme.colors.primary
                    else MaterialTheme.colors.onSurface.copy(.74f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LoggerChannel,
    sample: LiveDataSample?,
    accentColor: Color?,
    onSelected: (Boolean) -> Unit,
    onUnitSelected: (String) -> Unit
) {
    var unitMenuExpanded by remember(channel.parameterId) {
        mutableStateOf(false)
    }
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
                fontSize = 13.sp, fontWeight = if (channel.isSelected)
                    FontWeight.SemiBold else FontWeight.Normal,
                color = accentColor ?: MaterialTheme.colors.onSurface)
            Text(
                listOf(
                    channel.parameterId,
                    channel.units.takeIf { channel.unitOptions.size <= 1 }
                        .orEmpty()
                )
                    .filter { it.isNotBlank() }.joinToString("  •  "),
                color = MaterialTheme.colors.onSurface.copy(.57f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (channel.unitOptions.size > 1) {
            Box {
                TextButton(
                    onClick = { unitMenuExpanded = true },
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("${channel.units} ▾", fontSize = 11.sp,
                        maxLines = 1)
                }
                DropdownMenu(
                    expanded = unitMenuExpanded,
                    onDismissRequest = { unitMenuExpanded = false }
                ) {
                    channel.unitOptions.forEach { option ->
                        DropdownMenuItem(onClick = {
                            unitMenuExpanded = false
                            onUnitSelected(option.id)
                        }) {
                            Text(
                                (if (option.isSelected) "✓  " else "") +
                                    option.label,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
        if (sample != null) {
            Text(
                sample.displayValue,
                color = accentColor ?: MaterialTheme.colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 64.dp)
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
    gaugeTheme: LoggerGaugeTheme,
    gaugeLayout: LoggerGaugeLayout,
    allowGaugeThemes: Boolean,
    onGaugeTheme: (LoggerGaugeTheme) -> Unit,
    onGaugeLayout: (LoggerGaugeLayout) -> Unit,
    onToggleGraphPause: () -> Unit,
    preferences: LoggerWorkspacePreferences,
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
                selected, samples, history, gaugeTheme, allowGaugeThemes,
                onGaugeTheme, gaugeLayout, onGaugeLayout, preferences)
            LoggerWorkspaceView.ANALYSIS -> AnalysisWorkspace(
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
            "Live values, recent peaks, and trends in one workspace"
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
            Column(Modifier.horizontalScroll(scroll).requiredWidth(1270.dp)) {
                DataHeader()
                LazyColumn(Modifier.weight(1f)) {
                    items(selected, key = { it.parameterId }) { channel ->
                        val index = selected.indexOf(channel)
                        DataRow(
                            channel,
                            samples[channel.parameterId],
                            statistics(history[channel.parameterId].orEmpty()),
                            graphColors[index % graphColors.size],
                            index % 2 == 1
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
        TableCell("P05", 110.dp, Color.White, FontWeight.Bold)
        TableCell("P95", 110.dp, Color.White, FontWeight.Bold)
        TableCell("Samples", 90.dp, Color.White, FontWeight.Bold)
    }
}

@Composable
private fun DataRow(
    channel: LoggerChannel,
    latest: LiveDataSample?,
    stats: SampleStatistics?,
    accentColor: Color,
    alternate: Boolean
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (alternate)
                MaterialTheme.colors.onSurface.copy(.025f)
                else Color.Transparent)
            .border(0.5.dp, MaterialTheme.colors.onSurface.copy(.12f))
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.width(230.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(32.dp)
                .background(accentColor, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = accentColor)
                Text(channel.units,
                    color = MaterialTheme.colors.onSurface.copy(.55f),
                    fontSize = 11.sp)
            }
        }
        TableCell(latest?.displayValue ?: "—", 110.dp, accentColor,
            FontWeight.Bold)
        TableCell(stats?.minimum?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.maximum?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.average?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.median?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.standardDeviation?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.percentile05?.formatValue() ?: "—", 110.dp)
        TableCell(stats?.percentile95?.formatValue() ?: "—", 110.dp)
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(displayed, key = { it.parameterId }) { channel ->
                val index = displayed.indexOf(channel)
                val last = history[channel.parameterId].orEmpty().lastOrNull()
                Row(
                    Modifier.background(
                        graphColors[index].copy(alpha = .10f),
                        RoundedCornerShape(7.dp)
                    ).border(1.dp, graphColors[index].copy(alpha = .28f),
                        RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).background(
                        graphColors[index], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(5.dp))
                    Text(channel.name, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (last != null) {
                        Spacer(Modifier.width(7.dp))
                        Text(last.displayValue,
                            color = graphColors[index],
                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (channel.units.isNotBlank()) {
                            Spacer(Modifier.width(3.dp))
                            Text(channel.units,
                                color = MaterialTheme.colors.onSurface.copy(.52f),
                                fontSize = 10.sp)
                        }
                    }
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
                val points = history[channel.parameterId].orEmpty()
                val minimum = points.minOfOrNull { it.rawValue }
                    ?: return@forEachIndexed
                val maximum = points.maxOfOrNull { it.rawValue }
                    ?: return@forEachIndexed
                val span = (maximum - minimum).takeIf { it != 0.0 } ?: 1.0
                val firstTimestamp = points.first().timestampMillis
                val elapsed = (points.last().timestampMillis - firstTimestamp)
                    .coerceAtLeast(0L)
                val path = Path()
                points.forEachIndexed { sampleIndex, sample ->
                    val x = if (elapsed == 0L) {
                        if (points.size == 1) 0f else
                            size.width * sampleIndex / (points.size - 1f)
                    } else size.width *
                        (sample.timestampMillis - firstTimestamp) /
                        elapsed.toFloat()
                    val y = size.height -
                        (size.height * ((sample.rawValue - minimum) / span))
                            .toFloat()
                    if (sampleIndex == 0) path.moveTo(x, y)
                    else path.lineTo(x, y)
                }
                drawPath(path, graphColors[index], style = Stroke(
                    width = 2.7f, cap = StrokeCap.Round))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Oldest", color = MaterialTheme.colors.onSurface.copy(.46f),
                fontSize = 10.sp)
            Text("RECENT TIME WINDOW",
                color = MaterialTheme.colors.onSurface.copy(.46f),
                fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Latest", color = MaterialTheme.colors.onSurface.copy(.46f),
                fontSize = 10.sp)
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
    history: Map<String, List<LiveDataSample>>,
    gaugeTheme: LoggerGaugeTheme,
    allowGaugeThemes: Boolean,
    onGaugeTheme: (LoggerGaugeTheme) -> Unit,
    gaugeLayout: LoggerGaugeLayout,
    onGaugeLayout: (LoggerGaugeLayout) -> Unit,
    preferences: LoggerWorkspacePreferences
) {
    var showPeaks by remember { mutableStateOf(false) }
    var editingChannel by remember { mutableStateOf<LoggerChannel?>(null) }
    var configurationRevision by remember { mutableStateOf(0) }
    var layoutRevision by remember { mutableStateOf(0) }
    var arranging by remember { mutableStateOf(false) }
    if (selected.isEmpty()) {
        EmptyState(
            "Build a live dashboard",
            "Choose channels from the rail to create live value cards."
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        SectionTitle("Dashboard", if (arranging)
            "Arrange tiles, choose a role, and set each tile footprint"
            else "Current values and measured recent peaks")
        GaugeControls(
            gaugeTheme, allowGaugeThemes, showPeaks,
            history.values.any { it.isNotEmpty() },
            onGaugeTheme, { showPeaks = !showPeaks },
            gaugeLayout, onGaugeLayout, arranging,
            { arranging = !arranging },
            {
                preferences.dashboardTiles.keys.forEach {
                    preferences.setDashboardTile(it, null)
                }
                layoutRevision++
            }
        )
        Spacer(Modifier.height(14.dp))
        val ordered = dashboardChannels(selected,
            preferences.getDashboardTiles(), layoutRevision)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gaugeLayout.minimumWidth()),
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            gridItems(ordered, key = {
                "${it.parameterId}:$configurationRevision:$layoutRevision"
            }, span = { channel ->
                val tile = dashboardTile(channel.parameterId,
                    ordered.indexOf(channel), preferences)
                GridItemSpan(when (tile.size) {
                    LoggerDashboardTileSize.STANDARD -> 1
                    LoggerDashboardTileSize.WIDE -> minOf(2, maxLineSpan)
                    LoggerDashboardTileSize.LARGE -> maxLineSpan
                })
            }) { channel ->
                val index = ordered.indexOf(channel)
                val tile = dashboardTile(channel.parameterId, index,
                    preferences)
                LiveGaugeCard(
                    channel,
                    samples[channel.parameterId],
                    history[channel.parameterId].orEmpty(),
                    graphColors[index % graphColors.size],
                    gaugeTheme,
                    showPeaks,
                    preferences.getGaugeConfiguration(channel.parameterId),
                    { editingChannel = channel },
                    tile, arranging,
                    index > 0, index < ordered.lastIndex,
                    {
                        moveDashboardTile(ordered, index, -1, preferences)
                        layoutRevision++
                    }, {
                        moveDashboardTile(ordered, index, 1, preferences)
                        layoutRevision++
                    }, {
                        preferences.setDashboardTile(channel.parameterId,
                            tile.withRole(tile.role.next()))
                        layoutRevision++
                    }, {
                        preferences.setDashboardTile(channel.parameterId,
                            tile.withSize(tile.size.next()))
                        layoutRevision++
                    },
                    Modifier.fillMaxWidth().height(
                        tile.size.cardHeight(gaugeLayout) +
                            if (arranging) 44.dp else 0.dp)
                )
            }
        }
    }
    editingChannel?.let { channel ->
        GaugeConfigurationDialog(
            channel,
            preferences.getGaugeConfiguration(channel.parameterId),
            onDismiss = { editingChannel = null },
            onSave = { configuration ->
                preferences.setGaugeConfiguration(
                    channel.parameterId, configuration)
                configurationRevision++
                editingChannel = null
            }
        )
    }
}

@Composable
private fun GaugeControls(
    selected: LoggerGaugeTheme,
    allowThemes: Boolean,
    showPeaks: Boolean,
    hasSamples: Boolean,
    onSelect: (LoggerGaugeTheme) -> Unit,
    onTogglePeaks: () -> Unit,
    gaugeLayout: LoggerGaugeLayout,
    onGaugeLayout: (LoggerGaugeLayout) -> Unit,
    arranging: Boolean,
    onToggleArrange: () -> Unit,
    onResetLayout: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VALUES", color = MaterialTheme.colors.onSurface.copy(.50f),
                fontSize = 10.sp, fontWeight = FontWeight.Bold)
            GaugeControlChip(
                if (showPeaks) "Peak recall" else "Live values",
                showPeaks, hasSamples, onTogglePeaks
            )
        }
        if (allowThemes) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("GAUGE STYLE",
                    color = MaterialTheme.colors.onSurface.copy(.50f),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                LoggerGaugeTheme.values().filter {
                    it != LoggerGaugeTheme.HANDHELD
                }.forEach { theme ->
                    val active = theme == selected
                    GaugeControlChip(theme.displayName, active, true) {
                        onSelect(theme)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (allowThemes) {
                Text("GAUGE SIZE",
                    color = MaterialTheme.colors.onSurface.copy(.50f),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                LoggerGaugeLayout.values().forEach { layout ->
                    GaugeControlChip(layout.displayName,
                        layout == gaugeLayout, true) { onGaugeLayout(layout) }
                }
                Spacer(Modifier.width(5.dp))
            }
            Text("LAYOUT",
                color = MaterialTheme.colors.onSurface.copy(.50f),
                fontSize = 10.sp, fontWeight = FontWeight.Bold)
            GaugeControlChip(if (arranging) "Done" else "Arrange",
                arranging, true, onToggleArrange)
            if (arranging) {
                GaugeControlChip("Reset layout", false, true, onResetLayout)
            }
        }
    }
}

private fun dashboardTile(
    parameterId: String,
    fallbackOrder: Int,
    preferences: LoggerWorkspacePreferences
): LoggerDashboardTile = preferences.getDashboardTile(parameterId)
    ?: LoggerDashboardTile(LoggerDashboardTileRole.GAUGE,
        LoggerDashboardTileSize.STANDARD, fallbackOrder)

internal fun dashboardChannels(
    selected: List<LoggerChannel>,
    saved: Map<String, LoggerDashboardTile>,
    revision: Int = 0
): List<LoggerChannel> {
    @Suppress("UNUSED_VARIABLE") val observedRevision = revision
    val original = selected.withIndex().associate { it.value.parameterId to it.index }
    return selected.sortedWith(compareBy<LoggerChannel> {
        saved[it.parameterId]?.order ?: Int.MAX_VALUE
    }.thenBy { original[it.parameterId] ?: Int.MAX_VALUE })
}

private fun moveDashboardTile(
    ordered: List<LoggerChannel>,
    from: Int,
    direction: Int,
    preferences: LoggerWorkspacePreferences
) {
    val to = from + direction
    if (from !in ordered.indices || to !in ordered.indices) return
    val next = ordered.toMutableList()
    val moving = next.removeAt(from)
    next.add(to, moving)
    next.forEachIndexed { index, channel ->
        val current = dashboardTile(channel.parameterId, index, preferences)
        if (current.order != index) {
            preferences.setDashboardTile(channel.parameterId,
                current.withOrder(index))
        }
    }
}

private fun LoggerGaugeLayout.minimumWidth(): Dp = when (this) {
    LoggerGaugeLayout.COMPACT -> 188.dp
    LoggerGaugeLayout.STANDARD -> 238.dp
    LoggerGaugeLayout.LARGE -> 310.dp
}

private fun LoggerGaugeLayout.cardHeight(): Dp = when (this) {
    LoggerGaugeLayout.COMPACT -> 210.dp
    LoggerGaugeLayout.STANDARD -> 250.dp
    LoggerGaugeLayout.LARGE -> 304.dp
}

private fun LoggerDashboardTileSize.cardHeight(
    density: LoggerGaugeLayout
): Dp = density.cardHeight() + when (this) {
    LoggerDashboardTileSize.STANDARD -> 0.dp
    LoggerDashboardTileSize.WIDE -> 10.dp
    LoggerDashboardTileSize.LARGE -> 70.dp
}

@Composable
private fun GaugeControlChip(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(36.dp)
            .background(
                if (active) MaterialTheme.colors.primary.copy(.16f)
                else MaterialTheme.colors.surface,
                RoundedCornerShape(18.dp)
            )
            .border(
                1.dp,
                if (active) MaterialTheme.colors.primary.copy(.75f)
                else MaterialTheme.colors.onSurface.copy(.15f),
                RoundedCornerShape(18.dp)
            )
    ) {
        Text(label,
            color = when {
                !enabled -> MaterialTheme.colors.onSurface.copy(.32f)
                active -> MaterialTheme.colors.primary
                else -> MaterialTheme.colors.onSurface.copy(.72f)
            },
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AnalysisWorkspace(
    selected: List<LoggerChannel>,
    samples: Map<String, LiveDataSample>,
    history: Map<String, List<LiveDataSample>>
) {
    if (selected.isEmpty()) {
        EmptyState(
            "No recent data to analyze",
            "Choose channels and collect samples to build a recent summary."
        )
        return
    }
    val summary = analysisSummary(selected, history)
    Column(Modifier.fillMaxSize()) {
        SectionTitle("Recent analysis",
            "A read-only summary calculated from the retained sample window")
        LazyRow(Modifier.fillMaxWidth().height(86.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            item { AnalysisMetricCard("Channels", summary.channels.toString(),
                "with received data", graphColors[0]) }
            item { AnalysisMetricCard("Samples", summary.samples.toString(),
                "channel values", graphColors[1]) }
            item { AnalysisMetricCard("Duration", summary.durationLabel,
                "captured window", graphColors[2]) }
            item { AnalysisMetricCard("Coverage", summary.coverageLabel,
                "selected channels", graphColors[3]) }
        }
        Spacer(Modifier.height(11.dp))
        DataWorkspace(selected, samples, history)
    }
}

@Composable
private fun AnalysisMetricCard(
    label: String,
    value: String,
    detail: String,
    accent: Color
) {
    Column(
        Modifier.width(190.dp).fillMaxHeight()
            .background(MaterialTheme.colors.surface, RoundedCornerShape(9.dp))
            .border(1.dp, accent.copy(.28f), RoundedCornerShape(9.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Text(label.uppercase(), color = accent, fontSize = 10.sp,
            fontWeight = FontWeight.Bold)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1)
        Text(detail, color = MaterialTheme.colors.onSurface.copy(.52f),
            fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun LiveGaugeCard(
    channel: LoggerChannel,
    sample: LiveDataSample?,
    samples: List<LiveDataSample>,
    accentColor: Color,
    gaugeTheme: LoggerGaugeTheme,
    showPeak: Boolean,
    configuration: LoggerGaugeConfiguration?,
    onConfigure: () -> Unit,
    tile: LoggerDashboardTile,
    arranging: Boolean,
    canMovePrevious: Boolean,
    canMoveNext: Boolean,
    onMovePrevious: () -> Unit,
    onMoveNext: () -> Unit,
    onCycleRole: () -> Unit,
    onCycleSize: () -> Unit,
    modifier: Modifier
) {
    val stats = statistics(samples)
    val standardRange = standardGaugeRange(channel)
    val inferredRange = standardRange ?: paddedRange(stats)
    val range = if (configuration?.hasCustomScale() == true) GaugeRange(
        configuration.scaleMinimum, configuration.scaleMaximum)
        else inferredRange
    val displayedRaw = if (showPeak) stats?.maximum else sample?.rawValue
    val displayedValue = if (showPeak) stats?.maximum?.formatValue()
        else sample?.displayValue
    val progress = gaugeProgress(displayedRaw, range)
    val style = gaugeStyle(gaugeTheme)
    var alertState by remember(channel.parameterId, configuration) {
        mutableStateOf(LoggerGaugeConfiguration.AlertState.NORMAL)
    }
    LaunchedEffect(displayedRaw, configuration) {
        if (displayedRaw != null && configuration != null) {
            alertState = configuration.alertState(displayedRaw, alertState)
        } else {
            alertState = LoggerGaugeConfiguration.AlertState.NORMAL
        }
    }
    val alerting = alertState != LoggerGaugeConfiguration.AlertState.NORMAL
    Column(
        modifier.background(MaterialTheme.colors.surface, RoundedCornerShape(10.dp))
            .border(if (alerting) 2.dp else 1.dp,
                if (alerting) faultRed
                else MaterialTheme.colors.onSurface.copy(.14f),
                RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = liveValueAccessibilityLabel(
                    channel, sample, stats, showPeak) + if (alerting) {
                    ", ${alertState.name.lowercase()} warning active"
                } else ""
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(accentColor,
                RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(7.dp))
            Text(channel.name, Modifier.weight(1f),
                color = accentColor, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (alerting) {
                Text("!  ${alertState.name}", color = faultRed,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
            }
            if (!arranging) {
                TextButton(onClick = onConfigure,
                    modifier = Modifier.requiredWidth(44.dp).height(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("SET", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (arranging) {
            Row(Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TileArrangeButton("←", canMovePrevious, onMovePrevious,
                    Modifier.width(42.dp), "Move previous")
                TileArrangeButton("→", canMoveNext, onMoveNext,
                    Modifier.width(42.dp), "Move next")
                TileArrangeButton(tile.role.displayName, true, onCycleRole,
                    Modifier.weight(1f),
                    "Change role; current ${tile.role.displayName}")
                TileArrangeButton(tile.size.displayName, true, onCycleSize,
                    Modifier.weight(1f),
                    "Change size; current ${tile.size.displayName}")
            }
        }
        when (tile.role) {
            LoggerDashboardTileRole.GAUGE -> GaugeTileBody(
                progress, style, displayedValue, showPeak, channel, range,
                when {
                    configuration?.hasCustomScale() == true -> "CUSTOM SCALE"
                    standardRange != null -> "REFERENCE SCALE"
                    stats != null -> "RECENT SCALE"
                    else -> "AUTO SCALE · WAITING FOR DATA"
                })
            LoggerDashboardTileRole.VALUE -> ValueTileBody(
                displayedValue, showPeak, channel, accentColor, progress)
            LoggerDashboardTileRole.TREND -> TrendTileBody(
                displayedValue, showPeak, channel, samples, accentColor)
            LoggerDashboardTileRole.ALARM -> AlarmTileBody(
                displayedValue, showPeak, channel, configuration,
                alertState, alerting)
        }
        SessionStatsStrip(stats)
    }
}

@Composable
private fun TileArrangeButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label
) {
    Box(modifier.height(42.dp)
            .background(MaterialTheme.colors.surface, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colors.onSurface.copy(.16f),
                RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = accessibilityLabel }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center) {
        Text(label,
            color = MaterialTheme.colors.onSurface.copy(
                if (enabled) .76f else .28f),
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ColumnScope.GaugeTileBody(
    progress: Float?,
    style: GaugeStyle,
    displayedValue: String?,
    showPeak: Boolean,
    channel: LoggerChannel,
    range: GaugeRange,
    scaleLabel: String
) {
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val faceWidth = minOf(maxWidth, maxHeight * 1.55f)
        if (maxWidth > 650.dp) {
            GaugeRangeMarker("SCALE LOW", range.minimum, channel.units,
                style.primaryColor, Modifier.align(Alignment.CenterStart))
            GaugeRangeMarker("SCALE HIGH", range.maximum, channel.units,
                style.primaryColor, Modifier.align(Alignment.CenterEnd))
        }
        Box(Modifier.width(faceWidth).fillMaxHeight()
            .align(Alignment.Center)) {
            GaugeFace(progress, style)
            Text(range.minimum.formatScaleTick(),
                color = style.tickColor.copy(.76f),
                fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 11.dp))
            Text(range.maximum.formatScaleTick(),
                color = style.tickColor.copy(.76f),
                fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 11.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
                    .background(style.faceColor.copy(alpha = .94f),
                        RoundedCornerShape(7.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                TileValue(displayedValue, style.valueColor, showPeak, channel)
            }
            Text(scaleLabel, color = style.tickColor.copy(.46f),
                fontSize = 8.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun GaugeRangeMarker(
    label: String,
    value: Double,
    units: String,
    color: Color,
    modifier: Modifier
) {
    Column(modifier.width(150.dp)
        .background(color.copy(.06f), RoundedCornerShape(8.dp))
        .border(1.dp, color.copy(.20f), RoundedCornerShape(8.dp))
        .padding(11.dp)) {
        Text(label, color = color.copy(.72f), fontSize = 9.sp,
            fontWeight = FontWeight.Bold)
        Text(value.formatScaleTick(), color = MaterialTheme.colors.onSurface,
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
        Text(units.ifBlank { "FULL SCALE" },
            color = MaterialTheme.colors.onSurface.copy(.45f), fontSize = 9.sp)
    }
}

@Composable
private fun SessionStatsStrip(stats: SampleStatistics?) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 440.dp).fillMaxWidth()
            .background(MaterialTheme.colors.onSurface.copy(.035f),
                RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text("RECENT MIN  ${stats?.minimum?.formatValue() ?: "—"}",
                Modifier.weight(1f),
                color = MaterialTheme.colors.onSurface.copy(.52f),
                fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text("RECENT MAX  ${stats?.maximum?.formatValue() ?: "—"}",
                Modifier.weight(1f),
                color = MaterialTheme.colors.onSurface.copy(.52f),
                fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun ColumnScope.ValueTileBody(
    displayedValue: String?,
    showPeak: Boolean,
    channel: LoggerChannel,
    valueColor: Color,
    progress: Float?
) {
    Column(Modifier.fillMaxWidth().weight(1f)
        .background(Color(0xFF0B1218), RoundedCornerShape(9.dp))
        .border(1.dp, valueColor.copy(.32f), RoundedCornerShape(9.dp))
        .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (showPeak) "PEAK RECALL" else "LIVE SIGNAL",
                    color = valueColor.copy(.78f), fontSize = 9.sp,
                    fontWeight = FontWeight.Bold)
                Text(displayedValue ?: "— —", color = valueColor,
                    fontSize = 48.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, maxLines = 1)
            }
            Text(channel.units.ifBlank { "VALUE" },
                color = Color.White.copy(.62f), fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 9.dp))
        }
        Spacer(Modifier.height(7.dp))
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            drawRoundRect(valueColor.copy(.16f), cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
            drawRoundRect(valueColor.copy(.70f), size = Size(size.width *
                (progress ?: 0f).coerceIn(0f, 1f),
                size.height), cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
        }
    }
}

@Composable
private fun ColumnScope.TrendTileBody(
    displayedValue: String?,
    showPeak: Boolean,
    channel: LoggerChannel,
    samples: List<LiveDataSample>,
    accent: Color
) {
    Column(Modifier.fillMaxWidth().weight(1f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(displayedValue ?: "—", color = accent, fontSize = 29.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text((if (showPeak) "PEAK " else "") + channel.units,
                color = MaterialTheme.colors.onSurface.copy(.52f),
                fontSize = 10.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        TileSparkline(samples, accent, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun TileSparkline(
    samples: List<LiveDataSample>,
    color: Color,
    modifier: Modifier
) {
    val grid = MaterialTheme.colors.onSurface.copy(.08f)
    Canvas(modifier.background(color.copy(.05f), RoundedCornerShape(7.dp))
        .padding(8.dp)) {
        drawLine(grid, Offset(0f, size.height / 2f),
            Offset(size.width, size.height / 2f), 1f)
        val values = samples.takeLast(120).map { it.rawValue }
        if (values.size < 2) return@Canvas
        val minimum = values.minOrNull() ?: return@Canvas
        val maximum = values.maxOrNull() ?: return@Canvas
        val span = (maximum - minimum).takeIf { it != 0.0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1f)
            val y = size.height -
                (size.height * ((value - minimum) / span)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.8f, cap = StrokeCap.Round))
    }
}

@Composable
private fun ColumnScope.AlarmTileBody(
    displayedValue: String?,
    showPeak: Boolean,
    channel: LoggerChannel,
    configuration: LoggerGaugeConfiguration?,
    alertState: LoggerGaugeConfiguration.AlertState,
    alerting: Boolean
) {
    Box(Modifier.fillMaxWidth().weight(1f)
        .background(if (alerting) faultRed.copy(.12f)
            else MaterialTheme.colors.onSurface.copy(.035f),
            RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (alerting) alertState.name else "MONITORING",
                color = if (alerting) faultRed
                    else MaterialTheme.colors.onSurface.copy(.55f),
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(displayedValue ?: "—",
                color = if (alerting) faultRed else MaterialTheme.colors.onSurface,
                fontSize = 42.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(alarmLimits(configuration, channel.units, showPeak),
                color = MaterialTheme.colors.onSurface.copy(.52f),
                fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun alarmLimits(
    configuration: LoggerGaugeConfiguration?,
    units: String,
    showPeak: Boolean
): String {
    if (showPeak) return "PEAK RECALL  $units"
    if (configuration?.hasWarnings() != true) return "SET WARNING LIMITS"
    val low = configuration.lowWarning?.formatValue()?.let { "LOW $it" }
    val high = configuration.highWarning?.formatValue()?.let { "HIGH $it" }
    return listOfNotNull(low, high).joinToString("   ·   ") +
        units.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
}

@Composable
private fun TileValue(
    displayedValue: String?,
    color: Color,
    showPeak: Boolean,
    channel: LoggerChannel
) {
    Text(displayedValue ?: "—", color = color,
        fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    Text((if (showPeak) "PEAK  " else "") +
        channel.units.ifBlank { if (showPeak) "" else "CURRENT" },
        color = MaterialTheme.colors.onSurface.copy(.55f),
        fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun GaugeConfigurationDialog(
    channel: LoggerChannel,
    existing: LoggerGaugeConfiguration?,
    onDismiss: () -> Unit,
    onSave: (LoggerGaugeConfiguration?) -> Unit
) {
    var scaleMinimum by remember(channel.parameterId, existing) {
        mutableStateOf(existing?.scaleMinimum.toInput())
    }
    var scaleMaximum by remember(channel.parameterId, existing) {
        mutableStateOf(existing?.scaleMaximum.toInput())
    }
    var lowWarning by remember(channel.parameterId, existing) {
        mutableStateOf(existing?.lowWarning.toInput())
    }
    var highWarning by remember(channel.parameterId, existing) {
        mutableStateOf(existing?.highWarning.toInput())
    }
    var hysteresis by remember(channel.parameterId, existing) {
        mutableStateOf(existing?.hysteresis?.toInput() ?: "0")
    }
    val empty = scaleMinimum.isBlank() && scaleMaximum.isBlank() &&
        lowWarning.isBlank() && highWarning.isBlank()
    val result = runCatching {
        if (empty) null else LoggerGaugeConfiguration(
            scaleMinimum.optionalDouble(), scaleMaximum.optionalDouble(),
            lowWarning.optionalDouble(), highWarning.optionalDouble(),
            hysteresis.ifBlank { "0" }.toDouble()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Gauge settings", fontWeight = FontWeight.Bold)
                Text(channel.name, color = MaterialTheme.colors.primary,
                    fontSize = 13.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Leave a warning blank to keep it off. RomRaider2 does " +
                    "not guess safety limits for your vehicle or setup.",
                    color = MaterialTheme.colors.onSurface.copy(.68f),
                    fontSize = 12.sp)
                GaugeInputRow("CUSTOM SCALE", scaleMinimum, scaleMaximum,
                    { scaleMinimum = it }, { scaleMaximum = it })
                GaugeInputRow("WARNINGS", lowWarning, highWarning,
                    { lowWarning = it }, { highWarning = it })
                TextField(
                    hysteresis, { hysteresis = it },
                    Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Warning hysteresis  [${channel.units}]") }
                )
                Text("Hysteresis keeps an alert active until the value moves " +
                    "safely back past the limit.",
                    color = MaterialTheme.colors.onSurface.copy(.55f),
                    fontSize = 11.sp)
                result.exceptionOrNull()?.message?.let { message ->
                    Text(message, color = faultRed, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                if (existing != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("Use automatic scale and no warnings")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(result.getOrNull()) },
                enabled = result.isSuccess) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun GaugeInputRow(
    label: String,
    first: String,
    second: String,
    onFirst: (String) -> Unit,
    onSecond: (String) -> Unit
) {
    Column {
        Text(label, color = MaterialTheme.colors.onSurface.copy(.50f),
            fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(first, onFirst, Modifier.weight(1f), singleLine = true,
                label = { Text(if (label == "WARNINGS") "Low" else "Minimum") })
            TextField(second, onSecond, Modifier.weight(1f), singleLine = true,
                label = { Text(if (label == "WARNINGS") "High" else "Maximum") })
        }
    }
}

private fun String.optionalDouble(): Double? =
    trim().takeIf { it.isNotEmpty() }?.toDouble()

private fun Double?.toInput(): String = this?.let {
    if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
} ?: ""

@Composable
private fun GaugeFace(progress: Float?, style: GaugeStyle) {
    Canvas(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 5.dp)) {
        val radius = minOf(size.width * .40f, size.height * .46f)
        val center = Offset(size.width / 2f, size.height * .51f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val start = 145f
        val sweep = 250f
        drawCircle(style.bezelColor.copy(.40f), radius * 1.10f, center)
        drawCircle(style.bezelColor, radius * 1.055f, center,
            style = Stroke(width = 5f))
        drawCircle(style.faceColor, radius * 1.01f, center)
        drawCircle(Color.White.copy(.055f), radius * .95f, center,
            style = Stroke(width = 2f))
        drawArc(style.secondaryColor.copy(.28f), start, sweep, false,
            topLeft, arcSize, style = Stroke(width = 10f,
                cap = StrokeCap.Round))
        drawArc(style.warningColor.copy(.76f), start + sweep * .84f,
            sweep * .16f, false, topLeft, arcSize,
            style = Stroke(width = 10f, cap = StrokeCap.Round))
        repeat(style.tickCount) { index ->
            val angle = (start + sweep * index /
                (style.tickCount - 1f)) * PI / 180.0
            val major = index % style.majorTickEvery == 0
            val outer = Offset(
                center.x + cos(angle).toFloat() * radius * .91f,
                center.y + sin(angle).toFloat() * radius * .91f
            )
            val innerRadius = radius * if (major) .77f else .83f
            val inner = Offset(
                center.x + cos(angle).toFloat() * innerRadius,
                center.y + sin(angle).toFloat() * innerRadius
            )
            drawLine(style.tickColor.copy(if (major) .92f else .52f),
                inner, outer, strokeWidth = if (major) 2.5f else 1.25f,
                cap = StrokeCap.Round)
        }
        if (progress != null) {
            if (style.segmented) {
                repeat(31) { index ->
                    val fraction = index / 30f
                    val angle = (start + sweep * fraction) * PI / 180.0
                    val point = Offset(
                        center.x + cos(angle).toFloat() * radius,
                        center.y + sin(angle).toFloat() * radius
                    )
                    drawCircle(
                        if (fraction <= progress) style.primaryColor
                        else style.secondaryColor.copy(.35f),
                        if (index % 5 == 0) 3.6f else 2.8f,
                        point
                    )
                }
            } else if (style.glow) {
                drawArc(style.primaryColor.copy(.12f), start,
                    sweep * progress, false, topLeft, arcSize,
                    style = Stroke(width = 22f, cap = StrokeCap.Round))
                drawArc(style.primaryColor.copy(.26f), start,
                    sweep * progress, false, topLeft, arcSize,
                    style = Stroke(width = 14f, cap = StrokeCap.Round))
            }
            if (!style.segmented) {
                drawArc(style.primaryColor, start, sweep * progress, false,
                    topLeft, arcSize,
                    style = Stroke(width = 8f, cap = StrokeCap.Round))
            }
            val angle = (start + sweep * progress) * PI / 180.0
            val tip = Offset(
                center.x + cos(angle).toFloat() * radius * .78f,
                center.y + sin(angle).toFloat() * radius * .78f
            )
            if (style.showNeedle) {
                drawLine(style.needleColor.copy(.28f),
                    center + Offset(2f, 2f), tip + Offset(2f, 2f),
                    strokeWidth = 6f, cap = StrokeCap.Round)
                drawLine(style.needleColor, center, tip, strokeWidth = 4f,
                    cap = StrokeCap.Round)
            }
            drawCircle(style.faceColor, 8f, center)
            drawCircle(style.needleColor, 5f, center)
        }
    }
}

private data class GaugeStyle(
    val faceColor: Color,
    val bezelColor: Color,
    val primaryColor: Color,
    val secondaryColor: Color,
    val warningColor: Color,
    val tickColor: Color,
    val valueColor: Color,
    val needleColor: Color,
    val glow: Boolean,
    val segmented: Boolean,
    val showNeedle: Boolean,
    val tickCount: Int,
    val majorTickEvery: Int
)

private fun gaugeStyle(theme: LoggerGaugeTheme): GaugeStyle = when (theme) {
    LoggerGaugeTheme.RR2_CLASSIC -> GaugeStyle(
        Color(0xFF151C24), Color(0xFF2D3945), brandRed,
        Color(0xFF718397), Color(0xFFD92632), Color.White,
        Color.White, brandRed,
        false, false, true, 26, 5)
    LoggerGaugeTheme.RALLY_HERITAGE -> GaugeStyle(
        Color(0xFF101318), Color(0xFF68717A), Color(0xFFFF3447),
        Color(0xFFE7EDF2), Color(0xFFFF3447),
        Color(0xFFF7FAFC), Color.White,
        Color(0xFFFF3447), true, false, true, 31, 5)
    LoggerGaugeTheme.AMBER_GT -> GaugeStyle(
        Color(0xFF090A0C), Color(0xFF4A3214), Color(0xFFFFA31A),
        Color(0xFF6C4514), Color(0xFFFF4B22),
        Color(0xFFFFD68A), Color(0xFFFFB13B),
        Color(0xFFFFA31A), true, true, false, 16, 5)
    LoggerGaugeTheme.CENTRAL_TACH -> GaugeStyle(
        Color(0xFFF2EEE3), Color(0xFFB7B0A4), Color(0xFFD71920),
        Color(0xFF252525), Color(0xFFD71920),
        Color(0xFF171717), Color(0xFF111111),
        Color(0xFFD71920), false, false, true, 31, 5)
    LoggerGaugeTheme.NEON_CIRCUIT -> GaugeStyle(
        Color(0xFF06131D), Color(0xFF43216B), Color(0xFF22E8FF),
        Color(0xFF8A2BE2), Color(0xFFFF3CAC),
        Color(0xFF9AF5FF), Color(0xFF53F0FF),
        Color(0xFFFF3CAC), true, false, true, 37, 6)
    LoggerGaugeTheme.HANDHELD -> GaugeStyle(
        Color(0xFF111820), Color(0xFF2A475E), Color(0xFF66C0F4),
        Color(0xFF2A475E), Color(0xFFFFB44A),
        Color(0xFFD8EDF8), Color(0xFFECF8FF),
        Color(0xFF66C0F4), true, false, true, 31, 5)
}

internal fun measuredProgress(current: Double?, stats: SampleStatistics?): Float? {
    if (current == null || stats == null) return null
    val span = stats.maximum - stats.minimum
    return if (span == 0.0) 1f else
        ((current - stats.minimum) / span).coerceIn(0.0, 1.0).toFloat()
}

internal data class GaugeRange(
    val minimum: Double,
    val maximum: Double
)

internal fun gaugeProgress(current: Double?, range: GaugeRange): Float? {
    if (current == null) return null
    val span = range.maximum - range.minimum
    if (span <= 0.0) return null
    return ((current - range.minimum) / span).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Stable defaults keep a needle meaningful during a session. Unknown channels
 * receive one padded range from their captured history rather than using the
 * current value as an implied percentage.
 */
internal fun gaugeRange(
    channel: LoggerChannel,
    stats: SampleStatistics?
): GaugeRange = standardGaugeRange(channel) ?: paddedRange(stats)

internal fun standardGaugeRange(channel: LoggerChannel): GaugeRange? {
    val identity = "${channel.parameterId} ${channel.name}".lowercase()
    val units = channel.units.trim().lowercase()
    return when {
        identity.contains("rpm") || identity.contains("engine speed") ->
            GaugeRange(0.0, 9000.0)
        units.contains("lambda") || identity.contains("lambda") ->
            GaugeRange(0.6, 1.4)
        units.contains("afr") || identity.contains("air/fuel") ||
            identity.contains("wideband") || identity.contains("afr") ->
            GaugeRange(8.0, 22.0)
        identity.contains("egt") || identity.contains("exhaust gas") ->
            if (units.contains("c")) GaugeRange(200.0, 1000.0)
            else GaugeRange(400.0, 1800.0)
        identity.contains("boost") || identity.contains("manifold relative") ->
            when {
                units.contains("kpa") -> GaugeRange(-100.0, 200.0)
                units == "bar" -> GaugeRange(-1.0, 2.0)
                else -> GaugeRange(-15.0, 30.0)
            }
        identity.contains("oil pressure") || identity.contains("fuel pressure") ->
            when {
                units.contains("kpa") -> GaugeRange(0.0, 800.0)
                units == "bar" -> GaugeRange(0.0, 10.0)
                else -> GaugeRange(0.0, 120.0)
            }
        identity.contains("temperature") || identity.contains("temp") ->
            if (units.contains("c")) GaugeRange(40.0, 140.0)
            else GaugeRange(100.0, 280.0)
        identity.contains("voltage") || units == "v" ->
            GaugeRange(8.0, 18.0)
        identity.contains("load") && units == "%" ->
            GaugeRange(0.0, 300.0)
        identity.contains("throttle") || identity.contains("duty") ||
            units == "%" -> GaugeRange(0.0, 100.0)
        identity.contains("ignition") || identity.contains("timing") ->
            GaugeRange(-20.0, 60.0)
        identity.contains("knock") -> GaugeRange(-12.0, 12.0)
        else -> null
    }
}

private fun paddedRange(stats: SampleStatistics?): GaugeRange {
    if (stats == null) return GaugeRange(0.0, 1.0)
    val span = stats.maximum - stats.minimum
    val padding = if (span > 0.0) span * .12
        else maxOf(kotlin.math.abs(stats.maximum) * .20, 1.0)
    return GaugeRange(stats.minimum - padding, stats.maximum + padding)
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
        MeasuredRangeBar(sample?.rawValue, stats, accentColor)
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
private fun MeasuredRangeBar(
    current: Double?,
    stats: SampleStatistics?,
    accentColor: Color
) {
    val track = MaterialTheme.colors.onSurface.copy(alpha = .10f)
    val fill = accentColor
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
            LoggerWorkspaceView.entries.forEachIndexed { index, view ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(
                        onClick = { onSelect(view) },
                        modifier = Modifier.height(43.dp).padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(7.dp),
                        colors = ButtonDefaults.textButtonColors(
                            backgroundColor = if (view == active)
                                MaterialTheme.colors.primary.copy(alpha = .09f)
                            else Color.Transparent,
                            contentColor = if (view == active)
                                MaterialTheme.colors.primary
                            else MaterialTheme.colors.onSurface.copy(.68f))
                    ) {
                        Text(
                            if (showShortcuts) {
                                "${view.displayName}  Ctrl+${index + 1}"
                            } else view.displayName,
                            fontWeight = if (view == active) FontWeight.Bold
                            else FontWeight.Normal
                        )
                    }
                    Box(Modifier.width(34.dp).height(3.dp).background(
                        if (view == active) MaterialTheme.colors.primary
                        else Color.Transparent,
                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                    ))
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
    Key.Five -> LoggerWorkspaceView.ANALYSIS
    else -> null
}

internal fun channelAccessibilityLabel(channel: LoggerChannel): String {
    val details = listOf(channel.name, "ID ${channel.parameterId}", channel.units)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    return "$details, ${if (channel.isSelected) "selected" else "not selected"}"
}

internal fun filterChannels(
    channels: List<LoggerChannel>,
    kind: LoggerChannelKind,
    filter: String
): List<LoggerChannel> {
    val needle = filter.trim().lowercase()
    return channels.filter { channel ->
        channel.kind == kind && (needle.isEmpty() ||
            channel.name.lowercase().contains(needle) ||
            channel.parameterId.lowercase().contains(needle) ||
            channel.units.lowercase().contains(needle))
    }
}

internal data class SampleStatistics(
    val minimum: Double,
    val maximum: Double,
    val average: Double,
    val median: Double,
    val standardDeviation: Double,
    val percentile05: Double,
    val percentile95: Double,
    val count: Int
)

internal data class SessionMetrics(
    val selectedChannels: Int,
    val liveChannels: Int,
    val receivedPoints: Int,
    val windowLabel: String
)

internal data class AnalysisSummary(
    val channels: Int,
    val samples: Int,
    val durationLabel: String,
    val coverageLabel: String
)

internal fun analysisSummary(
    selected: List<LoggerChannel>,
    history: Map<String, List<LiveDataSample>>
): AnalysisSummary {
    val received = selected.mapNotNull { channel ->
        history[channel.parameterId]?.takeIf { it.isNotEmpty() }
    }
    val timestamps = received.flatten().map { it.timestampMillis }
    val duration = if (timestamps.size < 2) null
        else (timestamps.maxOrNull()!! - timestamps.minOrNull()!!) / 1000.0
    return AnalysisSummary(
        received.size,
        received.sumOf { it.size },
        duration?.let { "%.1f s".format(it) } ?: "—",
        if (selected.isEmpty()) "—" else
            "${received.size} / ${selected.size}"
    )
}

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
    stats: SampleStatistics?,
    showPeak: Boolean = false
): String {
    val current = if (showPeak) {
        stats?.maximum?.formatValue()?.let { "peak $it" } ?: "no peak value"
    } else sample?.displayValue ?: "no current value"
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
        percentile(values, .05), percentile(values, .95), values.size
    )
}

internal fun percentile(sortedValues: List<Double>, percentile: Double): Double {
    require(sortedValues.isNotEmpty())
    val position = (sortedValues.lastIndex * percentile.coerceIn(0.0, 1.0))
    val lower = position.toInt()
    val upper = kotlin.math.ceil(position).toInt()
    if (lower == upper) return sortedValues[lower]
    val fraction = position - lower
    return sortedValues[lower] +
        (sortedValues[upper] - sortedValues[lower]) * fraction
}

private fun Double.formatValue(): String {
    val magnitude = kotlin.math.abs(this)
    return when {
        magnitude >= 1000 -> "%.0f".format(this)
        magnitude >= 100 -> "%.1f".format(this)
        else -> "%.2f".format(this)
    }
}

private fun Double.formatScaleTick(): String = when {
    this == toLong().toDouble() -> toLong().toString()
    kotlin.math.abs(this) < 10.0 -> "%.1f".format(this)
    else -> "%.1f".format(this).trimEnd('0').trimEnd('.')
}

private fun stateLabel(state: LoggerSessionState): String = when (state) {
    LoggerSessionState.STOPPED -> "Disconnected"
    LoggerSessionState.CONNECTING -> "Connecting"
    LoggerSessionState.RECONNECTING -> "Reconnecting"
    LoggerSessionState.LIVE_ECU -> "Live ECU"
    LoggerSessionState.LIVE_EXTERNAL -> "Live external"
    LoggerSessionState.RECORDING -> "Recording"
}

private fun workspaceColors(dark: Boolean, steamOs: Boolean = false): Colors =
    if (steamOs) {
    darkColors(
        primary = Color(0xFF66C0F4),
        primaryVariant = Color(0xFF2A475E),
        secondary = Color(0xFF5BBE84),
        background = Color(0xFF171A21),
        surface = Color(0xFF1B2838),
        error = Color(0xFFE85A6A),
        onPrimary = Color(0xFF101820),
        onSecondary = Color.White,
        onBackground = Color(0xFFEAF4FA),
        onSurface = Color(0xFFEAF4FA),
        onError = Color.White
    )
} else if (dark) {
    darkColors(
        primary = brandRed,
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
        primary = brandRed,
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
    if (EventQueue.isDispatchThread()) action()
    else EventQueue.invokeLater { action() }
}
