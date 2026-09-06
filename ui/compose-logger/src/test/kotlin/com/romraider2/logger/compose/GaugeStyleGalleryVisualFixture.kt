/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.ui.awt.ComposePanel
import com.romraider.logger.api.LoggerGaugeTheme
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.Window
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/** Isolated native gallery capture. No logger, adapter, preferences or vehicle access. */
fun main(args: Array<String>) {
    require(args.size in 1..2) { "Output PNG path and optional display mode required" }
    val failure = AtomicReference<Throwable?>()
    Thread.setDefaultUncaughtExceptionHandler { _, error -> failure.set(error) }
    val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    SwingUtilities.invokeAndWait {
        val frame = JFrame("RR2 Gauge Gallery · Synthetic Fixture")
        frame.setSize(minOf(bounds.width, 1000), minOf(bounds.height, 700))
        frame.setLocationRelativeTo(null)
        frame.contentPane.add(ComposePanel().apply {
            setContent {
                MaterialTheme(colors = darkColors()) {
                    if (args.getOrNull(1) == "display") androidx.compose.material.Surface {
                        GaugeDisplayVisualContent()
                    }
                    else GaugeStyleGallery("Default gauge style", LoggerGaugeTheme.STI_NIGHT, false, {}, {})
                }
            }
        })
        frame.isVisible = true
    }
    Thread.sleep(4000)
    var result = 0
    try {
        failure.get()?.let { throw it }
        val visible = Window.getWindows().filter { it.isShowing }
        // Compose may host modal content in the frame's scene instead of a second AWT window.
        check(visible.isNotEmpty()) { "Gallery fixture has no visible window" }
        val dialog = visible.last()
        check(dialog.width > 200 && dialog.height > 200) { "Gallery has no usable bounds" }
        check(ImageIO.write(Robot().createScreenCapture(dialog.bounds), "png", File(args[0])))
        println("PASS: native Compose gallery captured at ${dialog.width}×${dialog.height}")
    } catch (error: Throwable) {
        error.printStackTrace()
        result = 1
    } finally {
        SwingUtilities.invokeAndWait { Window.getWindows().forEach { it.dispose() } }
    }
    exitProcess(result)
}
