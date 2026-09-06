/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.romraider.portable.gauge.GaugeFaceRenderer

@Composable
internal fun InstrumentGauge(style: GaugeFaceRenderer.Style,
    reading: GaugeFaceRenderer.Reading, modifier: Modifier = Modifier,
    presentation: GaugeFaceRenderer.Presentation = GaugeFaceRenderer.Presentation.CARD) {
    val measurer = rememberTextMeasurer()
    val motion = remember { com.romraider.portable.gauge.GaugeMotion() }
    var indicator by remember { mutableStateOf(reading.value) }
    val animated = style.usesNeedleMotion() &&
        !java.lang.Boolean.getBoolean("romraider2.gauge.reduceMotion")
    LaunchedEffect(style, reading.value) {
        if (!animated || !reading.value.isFinite()) {
            motion.update(reading.value, System.nanoTime()); indicator = reading.value
        } else {
            motion.update(reading.value, System.nanoTime())
            do { withFrameNanos { now -> indicator = motion.valueAt(now) } }
            while (motion.isAnimating(System.nanoTime()))
        }
    }
    Canvas(modifier) {
        val factor = minOf(size.width / 320f, size.height / 250f)
        if (factor <= 0f) return@Canvas
        withTransform({
            translate((size.width - 320 * factor) / 2, (size.height - 250 * factor) / 2)
            scale(factor, factor, Offset.Zero)
        }) {
            val scope = this
            GaugeFaceRenderer.draw(object : GaugeFaceRenderer.Surface {
                override fun rect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int) {
                    scope.drawRoundRect(Color(color), Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()), CornerRadius(r.toFloat()))
                }
                override fun circle(x: Double, y: Double, r: Double, color: Int) {
                    scope.drawCircle(Color(color), r.toFloat(), Offset(x.toFloat(), y.toFloat()))
                }
                override fun line(x1: Double, y1: Double, x2: Double, y2: Double, width: Double, color: Int) {
                    scope.drawLine(Color(color), Offset(x1.toFloat(), y1.toFloat()), Offset(x2.toFloat(), y2.toFloat()), width.toFloat())
                }
                override fun text(text: String, x: Double, baseline: Double, size: Double,
                    color: Int, align: Int, maxWidth: Double, mono: Boolean) {
                    drawLabel(text, x, baseline, size, color, align, maxWidth, mono)
                }
                override fun radialCircle(x: Double, y: Double, radius: Double, center: Int, edge: Int) {
                    scope.drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(Color(center), Color(edge)), Offset(x.toFloat(), y.toFloat()), radius.toFloat()),
                        radius.toFloat(), Offset(x.toFloat(), y.toFloat()))
                }
                override fun path(commands: DoubleArray, color: Int) {
                    val path = androidx.compose.ui.graphics.Path()
                    var i = 0
                    while (i < commands.size) {
                        when (commands[i++].toInt()) {
                            0 -> path.moveTo(commands[i++].toFloat(), commands[i++].toFloat())
                            1 -> path.lineTo(commands[i++].toFloat(), commands[i++].toFloat())
                            2 -> path.cubicTo(commands[i++].toFloat(), commands[i++].toFloat(), commands[i++].toFloat(),
                                commands[i++].toFloat(), commands[i++].toFloat(), commands[i++].toFloat())
                            3 -> path.close()
                            else -> error("Unknown vector command")
                        }
                    }
                    scope.drawPath(path, Color(color))
                }
                private fun drawLabel(text: String, x: Double, baseline: Double, size: Double,
                    color: Int, align: Int, maxWidth: Double, mono: Boolean) {
                    val layout = measurer.measure(text, style = TextStyle(color = Color(color),
                        fontSize = with(scope) { size.toFloat().toSp() },
                        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)))
                    val left = x.toFloat() - when { align < 0 -> 0f; align > 0 -> layout.size.width.toFloat(); else -> layout.size.width / 2f }
                    scope.drawText(layout, topLeft = Offset(left, baseline.toFloat() - layout.firstBaseline))
                }
            }, style, if (animated) reading.withIndicator(indicator) else reading, presentation)
        }
    }
}
