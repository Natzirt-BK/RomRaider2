/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
    reading: GaugeFaceRenderer.Reading, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
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
                    val layout = measurer.measure(text, style = TextStyle(color = Color(color),
                        fontSize = with(scope) { size.toFloat().toSp() },
                        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)))
                    val left = x.toFloat() - when { align < 0 -> 0f; align > 0 -> layout.size.width.toFloat(); else -> layout.size.width / 2f }
                    scope.drawText(layout, topLeft = Offset(left, baseline.toFloat() - layout.firstBaseline))
                }
            }, style, reading)
        }
    }
}
