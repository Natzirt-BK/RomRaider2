/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed as isKeyShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed as isPointerShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.romraider.editor.calibration.CalibrationCellSnapshot
import com.romraider.editor.calibration.CalibrationCellEdit
import com.romraider.editor.calibration.CalibrationCellCoordinate
import com.romraider.editor.calibration.CalibrationAdjustment
import com.romraider.editor.calibration.CalibrationAxis
import com.romraider.editor.calibration.CalibrationEditController
import com.romraider.editor.calibration.CalibrationEditListener
import com.romraider.editor.calibration.CalibrationGridSnapshot
import com.romraider.editor.calibration.CalibrationInterpolation
import com.romraider.editor.ecu.spi.CalibrationWorkspaceContext
import com.romraider.editor.workspace.EditorWorkspaceService
import com.romraider.editor.workspace.LiveTunePlanProjectionService
import com.romraider.editor.workspace.RomChangeSummary
import com.romraider.logger.api.LiveDataSample
import com.romraider.logger.api.LoggerLiveDataBus
import com.romraider.logger.api.LoggerLiveDataListener
import com.romraider.logger.api.LoggerSessionState
import com.romraider.maps.history.RomEditHistory
import com.romraider.platform.PlatformContext
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.math.BigDecimal
import java.math.RoundingMode

private val calibrationGraphite = Color(0xFF10151B)
private val calibrationSurface = Color(0xFF171E26)
private val calibrationRaised = Color(0xFF202934)
private val calibrationText = Color(0xFFE4E8ED)
private val calibrationSecondary = Color(0xFFA9B1BA)
private val calibrationSteel = Color(0xFF56AEA6)
private val calibrationRed = Color(0xFFD92632)

private enum class CalibrationViewMode { TABLE, SPLIT, THREE_D }
private enum class InspectorTab { CELL, INFO, LIVE, NOTES, CHANGES, TUNE }
private data class AxisEditRequest(
    val axis: CalibrationAxis,
    val index: Int,
    val value: String
)

@Composable
internal fun CalibrationWorkspace(context: CalibrationWorkspaceContext) {
    val dark = rememberApplicationDarkTheme()
    val colors = if (dark) darkColors(
        primary = calibrationSteel,
        background = calibrationGraphite,
        surface = calibrationSurface,
        onPrimary = Color.White,
        onBackground = calibrationText,
        onSurface = calibrationText
    ) else lightColors(
        primary = Color(0xFF007F78),
        background = Color(0xFFF2F5F8),
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = Color(0xFF1C2228),
        onSurface = Color(0xFF1C2228)
    )
    MaterialTheme(colors = colors) {
        var snapshot by remember(context) {
            mutableStateOf(context.editController?.snapshot ?: context.snapshot)
        }
        val controller = context.editController
        var status by remember { mutableStateOf<String?>(null) }
        var viewMode by remember(context.snapshot.tableName) {
            mutableStateOf(CalibrationViewMode.TABLE)
        }
        var axisEditRequest by remember(context.snapshot.tableName) {
            mutableStateOf<AxisEditRequest?>(null)
        }
        val onAxisEdit: ((CalibrationAxis, Int) -> Unit)? =
            if (controller == null) null else { axis, index ->
                axisEditRequest = axisRequest(snapshot, axis, index)
            }
        DisposableEffect(controller) {
            if (controller == null) return@DisposableEffect onDispose { }
            val listener = CalibrationEditListener { updated ->
                EventQueue.invokeLater { snapshot = updated }
            }
            controller.addListener(listener)
            onDispose { controller.removeListener(listener) }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                PreviewHeader(snapshot, dark, controller, viewMode,
                    onViewMode = { viewMode = it },
                    onUndo = {
                        status = historyAction(controller, false) { snapshot = it }
                    },
                    onRedo = {
                        status = historyAction(controller, true) { snapshot = it }
                    })
                Spacer(Modifier.height(12.dp))
                var selected by remember(snapshot.tableName) {
                    mutableStateOf(snapshot.cells.indexOfFirst { it.isChanged }
                        .takeIf { it >= 0 } ?: 0)
                }
                var selectionAnchor by remember(snapshot.tableName) {
                    mutableStateOf(selected)
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth < 780.dp) {
                        Column(Modifier.fillMaxSize()) {
                            CalibrationMainView(snapshot, selected,
                                selectionAnchor,
                                { index, extend ->
                                    if (!extend) selectionAnchor = index
                                    selected = index
                                }, dark,
                                onCopy = {
                                    status = copySelectionValues(snapshot,
                                        selectionAnchor, selected)
                                },
                                onPaste = {
                                    status = pasteCellValue(controller, snapshot,
                                        selected) { snapshot = it }
                                },
                                onUndo = {
                                    status = historyAction(controller, false) {
                                        snapshot = it
                                    }
                                },
                                onRedo = {
                                    status = historyAction(controller, true) {
                                        snapshot = it
                                    }
                                },
                                onAxisEdit = onAxisEdit,
                                viewMode, Modifier.weight(1f))
                            Spacer(Modifier.height(10.dp))
                            SelectionDetails(snapshot, selected,
                                selectionAnchor, dark, true,
                                controller, status, { status = it },
                                { snapshot = it },
                                Modifier.fillMaxWidth().height(234.dp))
                        }
                    } else {
                        Row(Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CalibrationMainView(snapshot, selected,
                                selectionAnchor,
                                { index, extend ->
                                    if (!extend) selectionAnchor = index
                                    selected = index
                                }, dark,
                                onCopy = {
                                    status = copySelectionValues(snapshot,
                                        selectionAnchor, selected)
                                },
                                onPaste = {
                                    status = pasteCellValue(controller, snapshot,
                                        selected) { snapshot = it }
                                },
                                onUndo = {
                                    status = historyAction(controller, false) {
                                        snapshot = it
                                    }
                                },
                                onRedo = {
                                    status = historyAction(controller, true) {
                                        snapshot = it
                                    }
                                },
                                onAxisEdit = onAxisEdit,
                                viewMode, Modifier.weight(1f))
                            CalibrationInspector(context, snapshot, selected,
                                selectionAnchor, dark, controller, status,
                                { status = it }, { snapshot = it },
                                Modifier.width(304.dp).fillMaxHeight())
                        }
                    }
                }
            }
        }
        axisEditRequest?.let { request ->
            AxisValueEditor(request, snapshot, controller,
                onDismiss = { axisEditRequest = null },
                onApplied = { result, message ->
                    snapshot = result
                    status = message
                    axisEditRequest = null
                })
        }
    }
}

@Composable
private fun PreviewHeader(
    snapshot: CalibrationGridSnapshot,
    dark: Boolean,
    controller: CalibrationEditController?,
    viewMode: CalibrationViewMode,
    onViewMode: (CalibrationViewMode) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val title: @Composable () -> Unit = {
            Column {
                Text(snapshot.tableName.ifBlank { "Calibration map" },
                    fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    "${snapshot.tableType.replace('_', ' ')}  •  " +
                        "${snapshot.columns} × ${snapshot.rows}" +
                        snapshot.unit.takeIf { it.isNotBlank() }
                            ?.let { "  •  $it" }.orEmpty(),
                    color = secondary(dark), fontSize = 12.sp
                )
            }
        }
        val chips: @Composable () -> Unit = {
            Row {
                if (snapshot.rows > 1 && snapshot.columns > 1) {
                    CalibrationViewMode.values().forEachIndexed { index, mode ->
                        ViewModeAction(mode, viewMode == mode) {
                            onViewMode(mode)
                        }
                        if (index < CalibrationViewMode.values().lastIndex) {
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (snapshot.changedValueCount > 0) {
                    StatusChip("${snapshot.changedValueCount} CHANGED",
                        calibrationRed, Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                StatusChip(if (controller == null) "READ-ONLY PREVIEW"
                    else "EDITABLE GRID", raised(dark),
                    if (controller == null) secondary(dark) else calibrationSteel)
                if (controller != null) {
                    Spacer(Modifier.width(8.dp))
                    CompactAction("Undo", controller.canUndo(), onUndo)
                    Spacer(Modifier.width(6.dp))
                    CompactAction("Redo", controller.canRedo(), onRedo)
                }
            }
        }
        if (maxWidth < 760.dp) {
            Column {
                title()
                Spacer(Modifier.height(7.dp))
                chips()
            }
        } else {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { title() }
                chips()
            }
        }
    }
}

@Composable
private fun ViewModeAction(
    mode: CalibrationViewMode,
    selected: Boolean,
    action: () -> Unit
) {
    val label = when (mode) {
        CalibrationViewMode.TABLE -> "Table"
        CalibrationViewMode.SPLIT -> "Split"
        CalibrationViewMode.THREE_D -> "3D"
    }
    Text(label, color = if (selected) Color.White
        else MaterialTheme.colors.onSurface.copy(.72f),
        fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(if (selected) calibrationSteel
            else MaterialTheme.colors.surface, RoundedCornerShape(5.dp))
            .border(1.dp, if (selected) calibrationSteel
                else MaterialTheme.colors.onSurface.copy(.13f),
                RoundedCornerShape(5.dp))
            .clickable(onClick = action)
            .padding(horizontal = 9.dp, vertical = 7.dp))
}

@Composable
private fun CalibrationMainView(
    snapshot: CalibrationGridSnapshot,
    selected: Int,
    selectionAnchor: Int,
    onSelected: (Int, Boolean) -> Unit,
    dark: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAxisEdit: ((CalibrationAxis, Int) -> Unit)?,
    viewMode: CalibrationViewMode,
    modifier: Modifier
) {
    when (viewMode) {
        CalibrationViewMode.TABLE -> GridCard(snapshot, selected,
            selectionAnchor, onSelected, dark, onCopy, onPaste, onUndo,
            onRedo, onAxisEdit, modifier)
        CalibrationViewMode.SPLIT -> Column(modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GridCard(snapshot, selected, selectionAnchor, onSelected, dark,
                onCopy, onPaste, onUndo, onRedo, onAxisEdit,
                Modifier.weight(.58f))
            CalibrationSurfacePlot(snapshot, selected, onSelected, dark,
                Modifier.weight(.42f))
        }
        CalibrationViewMode.THREE_D -> CalibrationSurfacePlot(snapshot,
            selected, onSelected, dark, modifier)
    }
}

@Composable
private fun CompactAction(text: String, enabled: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = enabled,
        modifier = Modifier.height(30.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp, vertical = 0.dp)) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusChip(text: String, background: Color, foreground: Color) {
    Text(text, color = foreground, fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(background, RoundedCornerShape(5.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp))
}

@Composable
private fun GridCard(
    snapshot: CalibrationGridSnapshot,
    selected: Int,
    selectionAnchor: Int,
    onSelected: (Int, Boolean) -> Unit,
    dark: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAxisEdit: ((CalibrationAxis, Int) -> Unit)?,
    modifier: Modifier
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    BoxWithConstraints(modifier.focusRequester(focusRequester).focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (event.isCtrlPressed) {
                when (event.key) {
                    Key.C -> onCopy()
                    Key.V -> onPaste()
                    Key.Z -> onUndo()
                    Key.Y -> onRedo()
                    Key.A -> {
                        onSelected(0, false)
                        onSelected((snapshot.cells.size - 1).coerceAtLeast(0),
                            true)
                    }
                    else -> return@onPreviewKeyEvent false
                }
                return@onPreviewKeyEvent true
            }
            val moved = when (event.key) {
                Key.DirectionLeft -> moveSelection(snapshot, selected, 0, -1)
                Key.DirectionRight -> moveSelection(snapshot, selected, 0, 1)
                Key.DirectionUp -> moveSelection(snapshot, selected, -1, 0)
                Key.DirectionDown -> moveSelection(snapshot, selected, 1, 0)
                Key.Escape -> {
                    onSelected(selected, false)
                    return@onPreviewKeyEvent true
                }
                else -> return@onPreviewKeyEvent false
            }
            onSelected(moved, event.isKeyShiftPressed)
            true
        }
        .background(surface(dark), RoundedCornerShape(7.dp))
        .border(1.dp, raised(dark), RoundedCornerShape(7.dp))
        .padding(10.dp)) {
        val axisWidth = 64.dp
        val axisHeight = 36.dp
        val xAxisTitle = axisTitle(snapshot.columnAxisName,
            snapshot.columnAxisUnit)
        val yAxisTitle = axisTitle(snapshot.rowAxisName, snapshot.rowAxisUnit)
        val axisTitleHeight = if (xAxisTitle.isBlank() && yAxisTitle.isBlank())
            0.dp else 28.dp
        val availableWidth = (maxWidth - axisWidth - 24.dp)
            .coerceAtLeast(0.dp)
        val availableHeight = (maxHeight - axisHeight - axisTitleHeight - 24.dp)
            .coerceAtLeast(0.dp)
        val cellWidth = (availableWidth /
            snapshot.columns.coerceAtLeast(1).toFloat()).coerceIn(34.dp, 86.dp)
        val cellHeight = (availableHeight /
            snapshot.rows.coerceAtLeast(1).toFloat()).coerceIn(24.dp, 48.dp)
        if (axisTitleHeight > 0.dp) {
            Row(Modifier.fillMaxWidth().height(axisTitleHeight)
                .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (yAxisTitle.isBlank()) "" else "Y · $yAxisTitle",
                    color = calibrationSteel, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(axisWidth + 70.dp))
                Text(if (xAxisTitle.isBlank()) "" else "X · $xAxisTitle",
                    color = calibrationSteel, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxSize().padding(top = axisTitleHeight,
            end = 12.dp, bottom = 12.dp)) {
            Column(Modifier.horizontalScroll(horizontal).verticalScroll(vertical)) {
                Row {
                    AxisCell("", 0, dark, true, axisWidth, cellWidth,
                        axisHeight)
                    snapshot.columnLabels.forEachIndexed { index, label ->
                        AxisCell(label, snapshot.columnFractionDigits, dark,
                            false, axisWidth, cellWidth, axisHeight,
                            if (snapshot.columnAxisName.isBlank()
                                || onAxisEdit == null) null else {
                                { onAxisEdit(CalibrationAxis.COLUMN, index) }
                            })
                    }
                }
                repeat(snapshot.rows) { row ->
                    Row {
                        AxisCell(snapshot.rowLabels.getOrElse(row) { "$row" },
                            snapshot.rowFractionDigits, dark, true, axisWidth,
                            cellWidth, cellHeight,
                            if (snapshot.rowAxisName.isBlank()
                                || onAxisEdit == null) null else {
                                { onAxisEdit(CalibrationAxis.ROW, row) }
                            })
                        repeat(snapshot.columns) { column ->
                            val index = row * snapshot.columns + column
                            CalibrationCell(snapshot, snapshot.cellAt(row, column),
                                index == selected,
                                isCellSelected(snapshot, selectionAnchor,
                                    selected, index), { extend ->
                                    onSelected(index, extend)
                                    focusRequester.requestFocus()
                                }, dark, cellWidth, cellHeight)
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(vertical),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                .padding(top = axisTitleHeight, bottom = 12.dp))
        HorizontalScrollbar(rememberScrollbarAdapter(horizontal),
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(end = 12.dp))
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun CalibrationSurfacePlot(
    snapshot: CalibrationGridSnapshot,
    selected: Int,
    onSelected: (Int, Boolean) -> Unit,
    dark: Boolean,
    modifier: Modifier
) {
    val values = snapshot.cells.map { it.rawValue }
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: minimum
    val span = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
    val xAxis = axisTitle(snapshot.columnAxisName, snapshot.columnAxisUnit)
    val yAxis = axisTitle(snapshot.rowAxisName, snapshot.rowAxisUnit)
    var yaw by remember(snapshot.tableName) { mutableStateOf(-15f) }
    var pitch by remember(snapshot.tableName) { mutableStateOf(.78f) }
    var zoom by remember(snapshot.tableName) { mutableStateOf(1f) }
    var plotSize by remember { mutableStateOf(IntSize.Zero) }
    var extendSelection by remember { mutableStateOf(false) }
    var hovered by remember(snapshot.tableName) { mutableStateOf<Int?>(null) }

    fun resetView() {
        yaw = -15f
        pitch = .78f
        zoom = 1f
    }

    fun nearestCell(position: Offset): Int? = nearestSurfaceCell(
        snapshot, position, Size(plotSize.width.toFloat(),
            plotSize.height.toFloat()), minimum, span, yaw, pitch, zoom)

    Box(modifier.background(surface(dark), RoundedCornerShape(7.dp))
        .border(1.dp, raised(dark), RoundedCornerShape(7.dp))
        .padding(12.dp)
        .semantics {
            contentDescription = "3D surface for ${snapshot.tableName}"
        }) {
        Canvas(Modifier.fillMaxSize().padding(top = 30.dp, bottom = 28.dp)
            .onSizeChanged { plotSize = it }
            .onPointerEvent(PointerEventType.Press) { event ->
                extendSelection = event.keyboardModifiers.isPointerShiftPressed
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                hovered = event.changes.firstOrNull()?.position?.let(::nearestCell)
            }
            .onPointerEvent(PointerEventType.Exit) { hovered = null }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) {
                    zoom = (zoom * if (delta < 0f) 1.1f else .9f)
                        .coerceIn(.55f, 2.1f)
                    event.changes.forEach { it.consume() }
                }
            }
            .pointerInput(snapshot, plotSize, yaw, pitch, zoom) {
                detectTapGestures(
                    onDoubleTap = { resetView() },
                    onTap = { position ->
                        nearestCell(position)?.let {
                            onSelected(it, extendSelection)
                        }
                        extendSelection = false
                    }
                )
            }
            .pointerInput(snapshot.tableName) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    yaw = (yaw + dragAmount.x * .45f) % 360f
                    pitch = (pitch + dragAmount.y * .006f)
                        .coerceIn(.28f, 1.25f)
                }
            }) {
            if (snapshot.rows < 2 || snapshot.columns < 2) return@Canvas
            fun point(row: Int, column: Int): Offset {
                val value = snapshot.cellAt(row, column).rawValue
                val height = ((value - minimum) / span).toFloat()
                return surfacePoint(row, column, snapshot.rows,
                    snapshot.columns, height, size, yaw, pitch, zoom)
            }
            for (row in snapshot.rows - 2 downTo 0) {
                for (column in 0 until snapshot.columns - 1) {
                    val first = point(row, column)
                    val second = point(row, column + 1)
                    val third = point(row + 1, column + 1)
                    val fourth = point(row + 1, column)
                    val fraction = listOf(
                        snapshot.cellAt(row, column),
                        snapshot.cellAt(row, column + 1),
                        snapshot.cellAt(row + 1, column + 1),
                        snapshot.cellAt(row + 1, column)
                    ).map { heatFraction(snapshot, it) }.average().toFloat()
                    val fill = surfaceHeatColor(fraction)
                    val face = Path().apply {
                        moveTo(first.x, first.y)
                        lineTo(second.x, second.y)
                        lineTo(third.x, third.y)
                        lineTo(fourth.x, fourth.y)
                        close()
                    }
                    drawPath(face, fill.copy(alpha = .90f))
                    drawPath(face,
                        Color.White.copy(alpha = if (dark) .14f else .28f),
                        style = Stroke(width = 1f))
                }
            }
            val frontLeft = point(snapshot.rows - 1, 0)
            val frontRight = point(snapshot.rows - 1, snapshot.columns - 1)
            val rearRight = point(0, snapshot.columns - 1)
            drawLine(calibrationSteel.copy(.72f), frontLeft, frontRight, 2.2f)
            drawLine(calibrationSteel.copy(.72f), frontRight, rearRight, 2.2f)
            snapshot.cells.getOrNull(selected)?.let { cell ->
                val point = point(cell.row, cell.column)
                drawCircle(Color.White.copy(.92f), 7.5f, point)
                drawCircle(calibrationSteel, 4.5f, point)
            }
            hovered?.takeIf { it != selected }?.let { index ->
                snapshot.cells.getOrNull(index)?.let { cell ->
                    drawCircle(Color.White.copy(.82f), 5.5f,
                        point(cell.row, cell.column), style = Stroke(1.5f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("3D SURFACE", color = calibrationSteel, fontSize = 10.sp,
                fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hoverCell = hovered?.let(snapshot.cells::getOrNull)
                Text((hoverCell?.let {
                    "R${it.row + 1} C${it.column + 1} · " +
                        calibrationDisplayText(it.displayValue,
                            snapshot.valueFractionDigits)
                } ?: "${formatRaw(minimum)}–${formatRaw(maximum)}") +
                    snapshot.unit.takeIf { it.isNotBlank() }
                        ?.let { "  $it" }.orEmpty(),
                    color = secondary(dark), fontSize = 10.sp)
                Spacer(Modifier.width(12.dp))
                Text("RESET VIEW", color = calibrationSteel,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { resetView() }
                        .padding(horizontal = 5.dp, vertical = 3.dp))
            }
        }
        Text("Drag to rotate  •  Scroll to zoom  •  Click a point to select",
            color = secondary(dark), fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 17.dp))
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (yAxis.isBlank()) "Y axis" else "Y · $yAxis",
                color = secondary(dark), fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(14.dp))
            Text(if (xAxis.isBlank()) "X axis" else "X · $xAxis",
                color = secondary(dark), fontSize = 10.sp,
                textAlign = TextAlign.End, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }
    }
}

internal fun surfacePoint(
    row: Int,
    column: Int,
    rows: Int,
    columns: Int,
    heightFraction: Float,
    size: Size,
    yawDegrees: Float,
    pitch: Float,
    zoom: Float
): Offset {
    if (rows < 2 || columns < 2 || size.width <= 0f || size.height <= 0f) {
        return Offset.Zero
    }
    val x = column.toFloat() / (columns - 1f) - .5f
    val y = row.toFloat() / (rows - 1f) - .5f
    val angle = Math.toRadians((45f + yawDegrees).toDouble())
    val horizontal = (x * kotlin.math.cos(angle) -
        y * kotlin.math.sin(angle)).toFloat()
    val depth = (x * kotlin.math.sin(angle) +
        y * kotlin.math.cos(angle)).toFloat()
    return Offset(
        size.width * (.5f + horizontal * .65f * zoom),
        size.height * (.64f + depth * .28f * pitch * zoom -
            heightFraction.coerceIn(0f, 1f) * .50f * zoom)
    )
}

internal fun nearestSurfaceCell(
    snapshot: CalibrationGridSnapshot,
    position: Offset,
    size: Size,
    minimum: Double,
    span: Double,
    yawDegrees: Float,
    pitch: Float,
    zoom: Float
): Int? {
    if (size.width <= 0f || size.height <= 0f || snapshot.cells.isEmpty()) {
        return null
    }
    val nearest = snapshot.cells.mapIndexed { index, cell ->
        val height = ((cell.rawValue - minimum) / span).toFloat()
        val point = surfacePoint(cell.row, cell.column, snapshot.rows,
            snapshot.columns, height, size, yawDegrees, pitch, zoom)
        index to (point - position).getDistance()
    }.minByOrNull { it.second } ?: return null
    val threshold = (30f * zoom.coerceAtLeast(.8f)).coerceAtMost(48f)
    return nearest.first.takeIf { nearest.second <= threshold }
}

private fun axisTitle(name: String, unit: String): String =
    name.trim() + unit.trim().takeIf { it.isNotBlank() }
        ?.let { " ($it)" }.orEmpty()

private fun axisRequest(
    snapshot: CalibrationGridSnapshot,
    axis: CalibrationAxis,
    index: Int
): AxisEditRequest {
    val labels = if (axis == CalibrationAxis.ROW)
        snapshot.rowLabels else snapshot.columnLabels
    return AxisEditRequest(axis, index, labels.getOrElse(index) { "" })
}

@Composable
private fun AxisValueEditor(
    request: AxisEditRequest,
    snapshot: CalibrationGridSnapshot,
    controller: CalibrationEditController?,
    onDismiss: () -> Unit,
    onApplied: (CalibrationGridSnapshot, String) -> Unit
) {
    var value by remember(request) { mutableStateOf(request.value) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    val isRow = request.axis == CalibrationAxis.ROW
    val axisLetter = if (isRow) "Y" else "X"
    val axisName = (if (isRow) snapshot.rowAxisName
        else snapshot.columnAxisName).ifBlank { "$axisLetter axis" }
    val axisUnit = if (isRow) snapshot.rowAxisUnit else snapshot.columnAxisUnit
    val apply = {
        if (controller != null) {
            try {
                val result = controller.setAxisValue(
                    request.axis, request.index, value)
                onApplied(result.snapshot, if (result.isChanged)
                    "Axis value applied to ROM history."
                else "Axis value already matches the ROM.")
            } catch (failure: Exception) {
                error = failure.message
                    ?: "The calibration axis value could not be applied."
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit $axisLetter axis value") },
        text = {
            Column {
                Text("$axisName · position ${request.index + 1}" +
                    axisUnit.takeIf { it.isNotBlank() }
                        ?.let { " · $it" }.orEmpty(),
                    color = MaterialTheme.colors.onSurface.copy(.62f),
                    fontSize = 11.sp)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(value, {
                    value = it
                    error = null
                }, label = { Text("Axis value") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().applyOnEnter(apply))
                error?.let {
                    Spacer(Modifier.height(7.dp))
                    Text(it, color = calibrationRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = apply, enabled = controller != null) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun surfaceHeatColor(fraction: Float): Color {
    val safe = fraction.coerceIn(0f, 1f)
    val low = Color(0xFF536FD8)
    val middle = Color(0xFF43D6AD)
    val high = Color(0xFFF14F58)
    return if (safe <= .5f) lerp(low, middle, safe * 2f)
    else lerp(middle, high, (safe - .5f) * 2f)
}

private fun copySelectionValues(
    snapshot: CalibrationGridSnapshot,
    anchor: Int,
    selected: Int
): String {
    val values = calibrationSelectionText(snapshot, anchor, selected)
    if (values.isBlank()) return "There is no cell value to copy."
    val count = selectedCellIndices(snapshot, anchor, selected).size
    return try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            StringSelection(values), null)
        if (count == 1) "Cell value copied."
        else "$count values copied as a block."
    } catch (failure: Exception) {
        failure.message ?: "The cell value could not be copied."
    }
}

private fun pasteCellValue(
    controller: CalibrationEditController?,
    snapshot: CalibrationGridSnapshot,
    selected: Int,
    onSnapshot: (CalibrationGridSnapshot) -> Unit
): String {
    if (controller == null) return "This grid is read-only."
    if (snapshot.cells.getOrNull(selected) == null) {
        return "There is no selected cell to paste into."
    }
    return try {
        val value = Toolkit.getDefaultToolkit().systemClipboard
            .getData(DataFlavor.stringFlavor)?.toString()?.trim().orEmpty()
        if (value.isBlank()) return "The clipboard does not contain a value."
        val edits = calibrationBlockEdits(snapshot, selected, value)
        val result = controller.setCellValues(edits)
        onSnapshot(result.snapshot)
        if (result.isChanged) {
            "Pasted ${result.requestedCellCount} " +
                if (result.requestedCellCount == 1) "value as one change."
                else "values as one change."
        } else "Pasted values already match."
    } catch (failure: Exception) {
        failure.message ?: "The clipboard value could not be applied."
    }
}

internal fun calibrationBlockEdits(
    snapshot: CalibrationGridSnapshot,
    selected: Int,
    clipboard: String
): List<CalibrationCellEdit> {
    val active = snapshot.cells.getOrNull(selected)
        ?: throw IllegalArgumentException("There is no selected cell to paste into.")
    val rows = clipboard.trim().lines().map { line -> line.split('\t') }
    if (rows.isEmpty() || rows.any { it.isEmpty() }) {
        throw IllegalArgumentException("The clipboard does not contain values.")
    }
    val width = rows.first().size
    if (rows.any { it.size != width }) {
        throw IllegalArgumentException("The pasted rows must have the same width.")
    }
    if (active.row + rows.size > snapshot.rows ||
        active.column + width > snapshot.columns) {
        throw IllegalArgumentException("The pasted values do not fit in this table.")
    }
    return rows.flatMapIndexed { rowOffset, values ->
        values.mapIndexed { columnOffset, value ->
            CalibrationCellEdit(active.row + rowOffset,
                active.column + columnOffset, value.trim())
        }
    }
}

internal fun moveSelection(
    snapshot: CalibrationGridSnapshot,
    current: Int,
    rowDelta: Int,
    columnDelta: Int
): Int {
    if (snapshot.rows <= 0 || snapshot.columns <= 0
        || snapshot.cells.isEmpty()) return 0
    val safe = current.coerceIn(0, snapshot.cells.size - 1)
    val row = (safe / snapshot.columns + rowDelta)
        .coerceIn(0, snapshot.rows - 1)
    val column = (safe % snapshot.columns + columnDelta)
        .coerceIn(0, snapshot.columns - 1)
    return row * snapshot.columns + column
}

internal fun selectedCellIndices(
    snapshot: CalibrationGridSnapshot,
    anchor: Int,
    active: Int
): List<Int> {
    if (snapshot.rows <= 0 || snapshot.columns <= 0 ||
        snapshot.cells.isEmpty()) return emptyList()
    val first = anchor.coerceIn(0, snapshot.cells.size - 1)
    val last = active.coerceIn(0, snapshot.cells.size - 1)
    val firstRow = first / snapshot.columns
    val firstColumn = first % snapshot.columns
    val lastRow = last / snapshot.columns
    val lastColumn = last % snapshot.columns
    val rows = minOf(firstRow, lastRow)..maxOf(firstRow, lastRow)
    val columns = minOf(firstColumn, lastColumn)..maxOf(firstColumn, lastColumn)
    return rows.flatMap { row ->
        columns.map { column -> row * snapshot.columns + column }
    }
}

internal fun calibrationSelectionText(
    snapshot: CalibrationGridSnapshot,
    anchor: Int,
    active: Int
): String {
    val indices = selectedCellIndices(snapshot, anchor, active)
    if (indices.isEmpty()) return ""
    val first = indices.first()
    val last = indices.last()
    val firstRow = first / snapshot.columns
    val lastRow = last / snapshot.columns
    val firstColumn = indices.minOf { it % snapshot.columns }
    val lastColumn = indices.maxOf { it % snapshot.columns }
    return (firstRow..lastRow).joinToString("\n") { row ->
        (firstColumn..lastColumn).joinToString("\t") { column ->
            snapshot.cellAt(row, column).displayValue
        }
    }
}

private fun isCellSelected(snapshot: CalibrationGridSnapshot, anchor: Int,
    active: Int, index: Int): Boolean {
    if (snapshot.cells.isEmpty() || index !in snapshot.cells.indices) return false
    val safeAnchor = anchor.coerceIn(0, snapshot.cells.size - 1)
    val safeActive = active.coerceIn(0, snapshot.cells.size - 1)
    val row = index / snapshot.columns
    val column = index % snapshot.columns
    return row in minOf(safeAnchor / snapshot.columns,
        safeActive / snapshot.columns)..maxOf(safeAnchor / snapshot.columns,
        safeActive / snapshot.columns) &&
        column in minOf(safeAnchor % snapshot.columns,
            safeActive % snapshot.columns)..maxOf(
            safeAnchor % snapshot.columns, safeActive % snapshot.columns)
}

@Composable
private fun AxisCell(
    label: String,
    fractionDigits: Int,
    dark: Boolean,
    corner: Boolean,
    axisWidth: Dp,
    cellWidth: Dp,
    cellHeight: Dp,
    onEdit: (() -> Unit)? = null
) {
    val editModifier = if (onEdit == null) Modifier else Modifier
        .clickable(onClick = onEdit)
        .semantics {
            contentDescription = "Edit calibration axis value $label"
        }
    Box(Modifier.size(if (corner) axisWidth else cellWidth, cellHeight)
        .background(raised(dark)).border(1.dp, surface(dark))
        .then(editModifier),
        contentAlignment = Alignment.Center) {
        Text(calibrationDisplayText(label, fractionDigits),
            color = if (onEdit == null) secondary(dark) else calibrationSteel,
            fontSize = if (cellWidth < 48.dp || cellHeight < 30.dp) 8.sp
                else if (cellWidth < 58.dp || cellHeight < 34.dp) 9.sp
                else 11.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun CalibrationCell(
    snapshot: CalibrationGridSnapshot,
    cell: CalibrationCellSnapshot,
    active: Boolean,
    selected: Boolean,
    onSelected: (Boolean) -> Unit,
    dark: Boolean,
    cellWidth: Dp,
    cellHeight: Dp
) {
    var extendSelection by remember(cell.row, cell.column) {
        mutableStateOf(false)
    }
    val background = heatColor(snapshot, cell)
    val outline = when {
        active || selected -> calibrationSteel
        cell.isChanged -> calibrationRed
        else -> surface(dark)
    }
    val thickness = if (active || cell.isChanged) 2.dp else 1.dp
    Box(Modifier.size(cellWidth, cellHeight)
        .background(background)
        .border(thickness, outline)
        .semantics {
            contentDescription = "Row ${cell.row + 1}, column " +
                "${cell.column + 1}, ${cell.displayValue}" +
                (if (cell.isChanged) ", changed" else "") +
                (if (active) ", active" else if (selected) ", selected" else "")
        }
        .onPointerEvent(PointerEventType.Press) { event ->
            extendSelection = event.keyboardModifiers.isPointerShiftPressed
        }
        .clickable {
            onSelected(extendSelection)
            extendSelection = false
        }, contentAlignment = Alignment.Center) {
        Text(calibrationDisplayText(cell.displayValue,
            snapshot.valueFractionDigits),
            color = readableText(background),
            fontWeight = FontWeight.Bold,
            fontSize = if (cellWidth < 48.dp || cellHeight < 30.dp) 8.sp
                else if (cellWidth < 58.dp || cellHeight < 34.dp) 10.sp
                else 12.sp,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CalibrationInspector(
    context: CalibrationWorkspaceContext,
    snapshot: CalibrationGridSnapshot,
    selected: Int,
    selectionAnchor: Int,
    dark: Boolean,
    controller: CalibrationEditController?,
    status: String?,
    onStatus: (String?) -> Unit,
    onSnapshot: (CalibrationGridSnapshot) -> Unit,
    modifier: Modifier
) {
    var tab by remember(context.table, snapshot.tableName) {
        mutableStateOf(InspectorTab.CELL)
    }
    Column(modifier) {
        Text("INSPECTOR", color = calibrationSteel, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(
                start = 4.dp, bottom = 6.dp))
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            InspectorTab.values().forEach { candidate ->
                Box(Modifier.weight(1f).height(31.dp)
                        .background(if (tab == candidate) calibrationSteel
                            else surface(dark), RoundedCornerShape(5.dp))
                        .clickable { tab = candidate },
                    contentAlignment = Alignment.Center) {
                    Text(candidate.name, fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tab == candidate) Color.White
                            else secondary(dark), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        when (tab) {
            InspectorTab.CELL -> SelectionDetails(snapshot, selected,
                selectionAnchor, dark, false, controller, status, onStatus,
                onSnapshot, Modifier.fillMaxSize())
            InspectorTab.INFO -> CalibrationInfoInspector(context, snapshot,
                dark, Modifier.fillMaxSize())
            InspectorTab.LIVE -> CalibrationLiveInspector(dark,
                Modifier.fillMaxSize())
            InspectorTab.NOTES -> CalibrationNotesInspector(context, dark,
                Modifier.fillMaxSize())
            InspectorTab.CHANGES -> CalibrationChangesInspector(context,
                snapshot, dark, Modifier.fillMaxSize())
            InspectorTab.TUNE -> CalibrationTuneInspector(context, snapshot,
                dark, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun InspectorSurface(
    dark: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier.background(surface(dark), RoundedCornerShape(7.dp))
        .border(1.dp, raised(dark), RoundedCornerShape(7.dp))
        .padding(13.dp).verticalScroll(rememberScrollState())) {
        content()
    }
}

@Composable
private fun CalibrationInfoInspector(
    context: CalibrationWorkspaceContext,
    snapshot: CalibrationGridSnapshot,
    dark: Boolean,
    modifier: Modifier
) = InspectorSurface(dark, modifier) {
    val table = context.table
    val rom = context.rom
    InspectorHeading("TABLE DETAILS")
    InspectorLine("Name", snapshot.tableName, dark)
    InspectorLine("Category", table?.category.orEmpty().ifBlank { "—" }, dark)
    InspectorLine("Address", table?.let {
        "0x%06X".format(it.storageAddress)
    } ?: "—", dark)
    InspectorLine("Type", snapshot.tableType.replace('_', ' '), dark)
    InspectorLine("Dimensions", "${snapshot.columns} × ${snapshot.rows}", dark)
    InspectorLine("Scale", table?.currentScale?.name.orEmpty()
        .ifBlank { snapshot.unit.ifBlank { "Raw value" } }, dark)
    InspectorLine("Range", table?.let {
        "${formatRaw(it.minReal)} – ${formatRaw(it.maxReal)}"
    } ?: "—", dark)
    InspectorLine("Access level", table?.userLevel?.toString() ?: "—", dark)
    table?.description?.takeIf { it.isNotBlank() }?.let { description ->
        Spacer(Modifier.height(7.dp))
        Text(description, color = secondary(dark), fontSize = 10.sp,
            lineHeight = 14.sp)
    }
    Spacer(Modifier.height(16.dp))
    InspectorHeading("ROM INFORMATION")
    InspectorLine("File", rom?.fileName.orEmpty().ifBlank { "—" }, dark)
    InspectorLine("ROM ID", rom?.romIDString.orEmpty().ifBlank { "—" }, dark)
    InspectorLine("Size", rom?.realFileSize?.let { "$it bytes" } ?: "—", dark)
    InspectorLine("Tables", rom?.tableCatalog?.size?.toString() ?: "—", dark)
    Spacer(Modifier.height(16.dp))
    InspectorHeading("WORKSPACE CONTEXT")
    val platform = PlatformContext.getInstance()
    InspectorLine("Platform", platform.platform.displayName, dark)
    InspectorLine("Module", platform.module.displayName, dark)
    InspectorLine("Realtime", if (platform.isRamTuneRuntimeAvailable)
        "Runtime advertised" else "Offline", dark)
}

@Composable
private fun CalibrationLiveInspector(dark: Boolean, modifier: Modifier) {
    val bus = remember { LoggerLiveDataBus.getInstance() }
    var state by remember { mutableStateOf(bus.state) }
    var samples by remember {
        mutableStateOf(bus.latestSamples.associateBy { it.parameterId })
    }
    DisposableEffect(bus) {
        val listener = object : LoggerLiveDataListener {
            override fun sessionStateChanged(next: LoggerSessionState) {
                EventQueue.invokeLater { state = next }
            }
            override fun sampleUpdated(sample: LiveDataSample) {
                EventQueue.invokeLater {
                    samples = samples + (sample.parameterId to sample)
                }
            }
            override fun parameterRemoved(parameterId: String) {
                EventQueue.invokeLater { samples = samples - parameterId }
            }
        }
        bus.addListener(listener)
        onDispose { bus.removeListener(listener) }
    }
    InspectorSurface(dark, modifier) {
        InspectorHeading("LIVE DATA")
        Text("●  ${state.name.replace('_', ' ')}",
            color = if (state == LoggerSessionState.STOPPED) calibrationSecondary
                else calibrationSteel, fontSize = 11.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        if (samples.isEmpty()) {
            Text("No live parameters yet. Open Logger, connect, and select " +
                "channels to mirror their latest values here.",
                color = secondary(dark), fontSize = 11.sp, lineHeight = 15.sp)
        } else {
            samples.values.take(12).forEach { sample ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(sample.name, color = secondary(dark), fontSize = 9.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth()) {
                        Text(sample.displayValue, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        Text(sample.units, color = secondary(dark), fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.Bottom))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationNotesInspector(
    context: CalibrationWorkspaceContext,
    dark: Boolean,
    modifier: Modifier
) {
    val service = remember { EditorWorkspaceService.getInstance() }
    val rom = context.rom
    val table = context.table
    var note by remember(rom, table) {
        mutableStateOf(if (rom != null && table != null)
            service.getTableNote(rom, table) else "")
    }
    var saved by remember(rom, table) { mutableStateOf(false) }
    InspectorSurface(dark, modifier) {
        InspectorHeading("MAP NOTES")
        Text("Keep setup details, test observations, or tuning intent with " +
            "this calibration.", color = secondary(dark), fontSize = 11.sp,
            lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(note, { note = it; saved = false },
            enabled = rom != null && table != null,
            label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()
                .height(220.dp))
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (rom != null && table != null) {
                service.setTableNote(rom, table, note)
                saved = true
            }
        }, enabled = rom != null && table != null,
            modifier = Modifier.fillMaxWidth()) {
            Text(if (saved) "Saved" else "Save note")
        }
    }
}

@Composable
private fun CalibrationChangesInspector(
    context: CalibrationWorkspaceContext,
    snapshot: CalibrationGridSnapshot,
    dark: Boolean,
    modifier: Modifier
) = InspectorSurface(dark, modifier) {
    val rom = context.rom
    val changes = remember(rom, snapshot.changedValueCount) {
        if (rom == null) emptyList() else RomChangeSummary.summarize(rom)
    }
    val history = remember(rom, snapshot.changedValueCount) {
        if (rom == null) emptyList()
        else RomEditHistory.getInstance().undoHistory(rom)
    }
    InspectorHeading("ROM CHANGES")
    Text(if (changes.isEmpty()) "No unsaved calibration changes"
        else "${changes.sumOf { it.changedCells }} changed cells",
        fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(9.dp))
    changes.take(12).forEach { change ->
        InspectorLine(change.tableName, "${change.changedCells}", dark,
            calibrationRed)
    }
    Spacer(Modifier.height(16.dp))
    InspectorHeading("EDIT HISTORY")
    if (history.isEmpty()) Text("No edit history", color = secondary(dark),
        fontSize = 11.sp)
    history.take(12).forEach { entry ->
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(entry.description, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold)
            Text("${entry.changedCells} cells · " +
                entry.tableNames.joinToString(), color = secondary(dark),
                fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CalibrationTuneInspector(
    context: CalibrationWorkspaceContext,
    snapshot: CalibrationGridSnapshot,
    dark: Boolean,
    modifier: Modifier
) = InspectorSurface(dark, modifier) {
    val rom = context.rom
    val table = context.table
    val draft = remember(rom, table, snapshot.changedValueCount) {
        if (rom == null || table == null) null else runCatching {
            LiveTunePlanProjectionService.preview(rom, listOf(table))
        }.getOrNull()
    }
    val platform = PlatformContext.getInstance()
    InspectorHeading("LIVE TUNE PLAN")
    Text("Offline review only. Nothing in this panel can write to an ECU.",
        color = secondary(dark), fontSize = 11.sp, lineHeight = 15.sp)
    Spacer(Modifier.height(12.dp))
    InspectorLine("Changed table", if (snapshot.changedValueCount > 0)
        "Ready to review" else "No changes", dark)
    InspectorLine("Byte ranges", draft?.changes?.size?.toString() ?: "—", dark)
    InspectorLine("Bytes", draft?.totalBytes?.toString() ?: "—", dark)
    Spacer(Modifier.height(14.dp))
    InspectorHeading("PREFLIGHT")
    TuneCheck("Subaru engine ECU",
        platform.platform.displayName == "Subaru" &&
            platform.module.displayName == "Engine ECU", dark)
    TuneCheck("Runtime detected", platform.isRamTuneRuntimeAvailable, dark)
    TuneCheck("Qualified RAM metadata", platform.hasQualifiedRamTuneMetadata(),
        dark)
    TuneCheck("Mapped changed bytes", draft != null, dark)
}

@Composable
private fun InspectorHeading(text: String) {
    Text(text, color = calibrationSteel, fontSize = 9.sp,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun InspectorLine(
    label: String,
    value: String,
    dark: Boolean,
    color: Color = MaterialTheme.colors.onSurface
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = secondary(dark), fontSize = 10.sp,
            modifier = Modifier.weight(1f), maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(value, color = color, fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(.85f))
    }
}

@Composable
private fun TuneCheck(label: String, passed: Boolean, dark: Boolean) {
    Text((if (passed) "●  " else "○  ") + label,
        color = if (passed) calibrationSteel else secondary(dark),
        fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SelectionDetails(
    snapshot: CalibrationGridSnapshot,
    selected: Int,
    selectionAnchor: Int,
    dark: Boolean,
    compact: Boolean,
    controller: CalibrationEditController?,
    status: String?,
    onStatus: (String?) -> Unit,
    onSnapshot: (CalibrationGridSnapshot) -> Unit,
    modifier: Modifier
) {
    val safeIndex = selected.coerceIn(0, (snapshot.cells.size - 1).coerceAtLeast(0))
    val cell = snapshot.cells.getOrNull(safeIndex)
    val selectedCells = selectedCellIndices(snapshot, selectionAnchor, selected)
        .mapNotNull(snapshot.cells::getOrNull)
    Column(modifier.background(surface(dark), RoundedCornerShape(7.dp))
        .border(1.dp, raised(dark), RoundedCornerShape(7.dp))
        .padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            val selectedCount = selectedCellIndices(snapshot,
                selectionAnchor, selected).size
            Text(if (selectedCount > 1) "SELECTION · $selectedCount CELLS"
                else "SELECTION", color = calibrationSteel,
                fontWeight = FontWeight.Bold, fontSize = 11.sp,
                modifier = Modifier.weight(1f))
            if (!status.isNullOrBlank()) {
                Text(status, color = statusColor(status), fontSize = 10.sp,
                    lineHeight = 12.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(if (compact) 2f else 1.4f))
            }
        }
        Text("Shift extends  •  Esc clears range  •  Ctrl+A all  •  Ctrl+C/V block",
            color = secondary(dark), fontSize = 9.sp, lineHeight = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        if (cell == null) {
            Text("No calibration values", color = secondary(dark))
            return@Column
        }
        if (compact) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.width(150.dp)) {
                    SelectedValue(snapshot, cell, dark)
                }
                Column(Modifier.weight(1f)) {
                    DetailLine("Row", calibrationDisplayText(
                        snapshot.rowLabels.getOrElse(cell.row) {
                        (cell.row + 1).toString()
                    }, snapshot.rowFractionDigits), dark)
                    DetailLine("Column", calibrationDisplayText(snapshot.columnLabels
                        .getOrElse(cell.column) {
                            (cell.column + 1).toString()
                        }, snapshot.columnFractionDigits), dark)
                    DetailLine("State",
                        if (cell.isChanged) "Changed" else "Saved", dark,
                        if (cell.isChanged) calibrationRed else calibrationSteel)
                }
            }
            Spacer(Modifier.height(8.dp))
            InterpolationControls(snapshot, selectionAnchor, selected,
                controller, onStatus, onSnapshot, true)
            Spacer(Modifier.height(8.dp))
            EditControls(cell, selectedCells, controller, onStatus, onSnapshot,
                dark, true)
        } else {
            Spacer(Modifier.height(8.dp))
            SelectedValue(snapshot, cell, dark)
            Spacer(Modifier.height(16.dp))
            DetailLine("Row", calibrationDisplayText(
                snapshot.rowLabels.getOrElse(cell.row) {
                (cell.row + 1).toString()
            }, snapshot.rowFractionDigits), dark)
            DetailLine("Column", calibrationDisplayText(
                snapshot.columnLabels.getOrElse(cell.column) {
                (cell.column + 1).toString()
            }, snapshot.columnFractionDigits), dark)
            DetailLine("State", if (cell.isChanged) "Changed" else "Saved", dark,
                if (cell.isChanged) calibrationRed else calibrationSteel)
            if (cell.isChanged) {
                DetailLine("Original raw", formatRaw(cell.originalRawValue), dark)
                DetailLine("Current raw", formatRaw(cell.rawValue), dark)
            }
            Spacer(Modifier.weight(1f))
            InterpolationControls(snapshot, selectionAnchor, selected,
                controller, onStatus, onSnapshot, false)
            Spacer(Modifier.height(8.dp))
            EditControls(cell, selectedCells, controller, onStatus, onSnapshot,
                dark, false)
        }
    }
}

@Composable
private fun InterpolationControls(
    snapshot: CalibrationGridSnapshot,
    anchor: Int,
    selected: Int,
    controller: CalibrationEditController?,
    onStatus: (String?) -> Unit,
    onSnapshot: (CalibrationGridSnapshot) -> Unit,
    compact: Boolean
) {
    if (controller == null) return
    val first = snapshot.cells.getOrNull(anchor) ?: return
    val last = snapshot.cells.getOrNull(selected) ?: return
    val rows = kotlin.math.abs(last.row - first.row) + 1
    val columns = kotlin.math.abs(last.column - first.column) + 1
    if (rows < 3 && columns < 3) return
    val run: (CalibrationInterpolation) -> Unit = { direction ->
        try {
            val result = controller.interpolate(first.row, first.column,
                last.row, last.column, direction)
            onSnapshot(result.snapshot)
            onStatus(if (result.isChanged)
                "Interpolated ${result.changedCellCount} values."
            else "Interpolated values already match.")
        } catch (failure: Exception) {
            onStatus(failure.message ?: "The selection could not be interpolated.")
        }
    }
    if (!compact) {
        Text("INTERPOLATE SELECTION", color = calibrationSteel,
            fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
    }
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = { run(CalibrationInterpolation.HORIZONTAL) },
            enabled = columns >= 3,
            modifier = Modifier.weight(1f).height(34.dp)) {
            Text("Across", fontSize = 9.sp)
        }
        Button(onClick = { run(CalibrationInterpolation.VERTICAL) },
            enabled = rows >= 3,
            modifier = Modifier.weight(1f).height(34.dp)) {
            Text("Down", fontSize = 9.sp)
        }
        if (rows >= 3 && columns >= 3) {
            Button(onClick = { run(CalibrationInterpolation.BOTH) },
                modifier = Modifier.weight(1f).height(34.dp)) {
                Text("Both", fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun EditControls(
    cell: CalibrationCellSnapshot,
    selectedCells: List<CalibrationCellSnapshot>,
    controller: CalibrationEditController?,
    onStatus: (String?) -> Unit,
    onSnapshot: (CalibrationGridSnapshot) -> Unit,
    dark: Boolean,
    compact: Boolean
) {
    var value by remember(cell.row, cell.column, cell.displayValue) {
        mutableStateOf(cell.displayValue)
    }
    val apply = {
        if (controller != null) {
            try {
                if (selectedCells.size <= 1) {
                    val result = controller.setCellValue(
                        cell.row, cell.column, value)
                    onSnapshot(result.snapshot)
                    value = result.currentValue
                    onStatus(if (result.isChanged)
                        "Value applied to ROM history."
                    else "Value already matches the ROM.")
                } else {
                    val result = controller.setCellValues(selectedCells.map {
                        CalibrationCellEdit(it.row, it.column, value)
                    })
                    onSnapshot(result.snapshot)
                    value = result.snapshot.cellAt(
                        cell.row, cell.column).displayValue
                    onStatus(if (result.isChanged)
                        "Value applied to ${result.changedCellCount} cells as one change."
                    else "Selected values already match the ROM.")
                }
            } catch (failure: Exception) {
                onStatus(failure.message ?: "The value could not be applied.")
            }
        }
    }
    val adjust: (CalibrationAdjustment) -> Unit = { adjustment ->
        if (controller != null) {
            try {
                if (selectedCells.size <= 1) {
                    val result = controller.adjustCellValue(
                        cell.row, cell.column, adjustment)
                    onSnapshot(result.snapshot)
                    value = result.currentValue
                    onStatus(if (adjustment.isCoarse)
                        "Coarse adjustment applied."
                    else "Fine adjustment applied.")
                } else {
                    val result = controller.adjustCellValues(
                        selectedCells.map {
                            CalibrationCellCoordinate(it.row, it.column)
                        }, adjustment)
                    onSnapshot(result.snapshot)
                    value = result.snapshot.cellAt(
                        cell.row, cell.column).displayValue
                    onStatus("Adjusted ${result.changedCellCount} selected cells as one change.")
                }
            } catch (failure: Exception) {
                onStatus(failure.message ?: "The value could not be adjusted.")
            }
        }
    }
    val restore = {
        if (controller != null) {
            try {
                if (selectedCells.size <= 1) {
                    val result = controller.restoreCellValue(
                        cell.row, cell.column)
                    onSnapshot(result.snapshot)
                    value = result.currentValue
                    onStatus("Saved value restored.")
                } else {
                    val result = controller.restoreCellValues(
                        selectedCells.map {
                            CalibrationCellCoordinate(it.row, it.column)
                        })
                    onSnapshot(result.snapshot)
                    value = result.snapshot.cellAt(
                        cell.row, cell.column).displayValue
                    onStatus("Restored ${result.changedCellCount} selected cells as one change.")
                }
            } catch (failure: Exception) {
                onStatus(failure.message ?: "The saved value could not be restored.")
            }
        }
    }
    if (controller == null) {
        Text("This fixture is read-only. Open the map from the Editor to edit.",
            color = secondary(dark), fontSize = 11.sp, lineHeight = 15.sp)
        return
    }
    if (compact) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value, { value = it; onStatus(null) },
                label = { Text("New value") }, singleLine = true,
                modifier = Modifier.weight(1f).height(64.dp)
                    .applyOnEnter(apply))
            SmallAdjustAction("F−", "Decrease by the fine increment") {
                adjust(CalibrationAdjustment.FINE_DECREASE)
            }
            SmallAdjustAction("F+", "Increase by the fine increment") {
                adjust(CalibrationAdjustment.FINE_INCREASE)
            }
            SmallAdjustAction("C−", "Decrease by the coarse increment") {
                adjust(CalibrationAdjustment.COARSE_DECREASE)
            }
            SmallAdjustAction("C+", "Increase by the coarse increment") {
                adjust(CalibrationAdjustment.COARSE_INCREASE)
            }
            if (selectedCells.any { it.isChanged }) {
                SmallAdjustAction("↺", "Restore the loaded cell value", restore)
            }
            Button(onClick = apply, modifier = Modifier.width(94.dp)
                .height(44.dp)) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        OutlinedTextField(value, { value = it; onStatus(null) },
            label = { Text("New value") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().applyOnEnter(apply))
        Spacer(Modifier.height(8.dp))
        Button(onClick = apply, modifier = Modifier.fillMaxWidth()) {
            Text("Apply value", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(9.dp))
        Text("FINE INCREMENT", color = secondary(dark), fontSize = 9.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                adjust(CalibrationAdjustment.FINE_DECREASE)
            }, modifier = Modifier.weight(1f).height(36.dp)) {
                Text("− Fine", fontSize = 10.sp)
            }
            Button(onClick = {
                adjust(CalibrationAdjustment.FINE_INCREASE)
            }, modifier = Modifier.weight(1f).height(36.dp)) {
                Text("+ Fine", fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text("COARSE INCREMENT", color = secondary(dark), fontSize = 9.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                adjust(CalibrationAdjustment.COARSE_DECREASE)
            }, modifier = Modifier.weight(1f).height(36.dp)) {
                Text("− Coarse", fontSize = 10.sp)
            }
            Button(onClick = {
                adjust(CalibrationAdjustment.COARSE_INCREASE)
            }, modifier = Modifier.weight(1f).height(36.dp)) {
                Text("+ Coarse", fontSize = 10.sp)
            }
        }
        if (selectedCells.any { it.isChanged }) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = restore, modifier = Modifier.fillMaxWidth()
                .height(36.dp)) {
                Text("Restore saved value", fontSize = 10.sp)
            }
        }
    }
}

private fun statusColor(status: String): Color =
    if (status.startsWith("Value") || status.startsWith("Change")
        || status.startsWith("Axis")
        || status.startsWith("Fine") || status.startsWith("Coarse")
        || status.startsWith("Saved") || status.startsWith("Cell")
        || status.startsWith("Clipboard") || status.startsWith("Pasted")
        || status.startsWith("Interpolated"))
        calibrationSteel
    else calibrationRed

@Composable
private fun SmallAdjustAction(
    text: String,
    description: String,
    action: () -> Unit
) {
    Button(onClick = action, modifier = Modifier.size(44.dp)
        .semantics { contentDescription = description },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

private fun Modifier.applyOnEnter(action: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
            action()
            true
        } else false
    }

private fun historyAction(
    controller: CalibrationEditController?,
    redo: Boolean,
    onSnapshot: (CalibrationGridSnapshot) -> Unit
): String? {
    if (controller == null) return null
    return try {
        val updated = if (redo) controller.redo() else controller.undo()
        onSnapshot(updated)
        if (redo) "Change restored." else "Change undone."
    } catch (failure: Exception) {
        failure.message ?: "History could not be changed."
    }
}

@Composable
private fun SelectedValue(
    snapshot: CalibrationGridSnapshot,
    cell: CalibrationCellSnapshot,
    dark: Boolean
) {
    Text(calibrationDisplayText(cell.displayValue,
        snapshot.valueFractionDigits).ifBlank { "—" },
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp)
    if (snapshot.unit.isNotBlank()) {
        Text(snapshot.unit, color = secondary(dark), fontSize = 12.sp)
    }
}

/**
 * Keeps the calibration surface readable without reducing the precision used
 * for editing, copy/paste, comparison, or ROM writes.
 */
internal fun calibrationDisplayText(value: String, fractionDigits: Int): String {
    val number = runCatching { BigDecimal(value.trim()) }.getOrNull()
        ?: return value
    val digits = fractionDigits.coerceIn(0, 2)
    val rounded = number.setScale(digits, RoundingMode.HALF_UP)
    return if (rounded.compareTo(BigDecimal.ZERO) == 0)
        BigDecimal.ZERO.setScale(digits).toPlainString()
    else rounded.toPlainString()
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    dark: Boolean,
    valueColor: Color = MaterialTheme.colors.onSurface
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = secondary(dark), fontSize = 11.sp,
            modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp, textAlign = TextAlign.End)
    }
}

internal fun heatFraction(
    snapshot: CalibrationGridSnapshot,
    cell: CalibrationCellSnapshot
): Float {
    if (snapshot.cells.isEmpty()) return 0.5f
    val minimum = snapshot.cells.minOf { it.rawValue }
    val maximum = snapshot.cells.maxOf { it.rawValue }
    if (maximum <= minimum) return 0.5f
    return ((cell.rawValue - minimum) / (maximum - minimum))
        .toFloat().coerceIn(0f, 1f)
}

private fun heatColor(
    snapshot: CalibrationGridSnapshot,
    cell: CalibrationCellSnapshot
): Color {
    val fraction = heatFraction(snapshot, cell)
    val low = Color(0xFF8795EE)
    val middle = Color(0xFF74EFC2)
    val high = Color(0xFFFF6B69)
    return if (fraction <= 0.5f) lerp(low, middle, fraction * 2f)
    else lerp(middle, high, (fraction - 0.5f) * 2f)
}

private fun readableText(background: Color): Color =
    if (background.red * 0.299f + background.green * 0.587f +
        background.blue * 0.114f > 0.63f) Color(0xFF10151B) else Color.White

private fun formatRaw(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.4f".format(value).trimEnd('0').trimEnd('.')

private fun surface(dark: Boolean) = if (dark) calibrationSurface else Color.White
private fun raised(dark: Boolean) =
    if (dark) calibrationRaised else Color(0xFFE1E6EC)
private fun secondary(dark: Boolean) =
    if (dark) calibrationSecondary else Color(0xFF5B6773)
