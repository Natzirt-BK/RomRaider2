/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.romraider.logger.api.*
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual native button/text layout, synthetic preferences only; no logger. */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.runtime.tooling.ComposeToolingApi::class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RR2_COMPOSE_WINDOW_SMOKE", matches = "1")
class GaugeButtonLayoutTest {
    @Test fun compactArrangeControlsKeepTheirRoleAndSizeLabels() {
        for (width in listOf(160, 210, 282)) {
            val window = edt { ComposeWindow().apply {
                setSize(400, 300)
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                        MaterialTheme(colors = darkColors()) {
                            Box(Modifier.width(width.dp)) {
                                GaugeTileArrangeControls("Gauge", "Standard", true, true, {}, {}, {}, {})
                            }
                        }
                    }
                }
                isVisible = true
            } }
            try {
                val deadline = System.nanoTime() + 10_000_000_000L
                var results: List<TextLayoutResult>
                do {
                    Thread.sleep(50)
                    results = textLayouts(window)
                } while (results.none { it.layoutInput.text.text == "Standard" } && System.nanoTime() < deadline)
                assertTrue(results.any { it.layoutInput.text.text == "Standard" }, "Arrange controls missing")
                assertLabelsFit(results, "Arrange width=$width fontScale=1.5")
            } finally { edt { window.dispose() } }
        }
    }
    @Test fun setupButtonsRetainCompleteLabelsOnHandheldWindowsAndLargerText() {
        val name = "Manifold Relative Pressure - Corrected Measurement"
        val channels = listOf(LoggerChannel("synthetic-pressure", name, "psi", LoggerChannelKind.PARAMETER, true))
        val preferences = LoggerWorkspacePreferences(LoggerWorkspaceView.DASHBOARD, true) { _, _ -> }
        preferences.setDashboardTile("synthetic-pressure", LoggerDashboardTile(
            LoggerDashboardTileRole.GAUGE, LoggerDashboardTileSize.STANDARD, 0)
            .withGaugeTheme(LoggerGaugeTheme.EVOLUTION_NIGHT))
        val display = LoggerGaugeDisplay().withChannel(0, "synthetic-pressure").withCount(1)
        for ((width, height, fontScale) in listOf(Triple(1280, 800, 1f), Triple(1280, 800, 1.5f),
                Triple(800, 480, 1.5f), Triple(440, 700, 1.5f))) {
            val window = edt { ComposeWindow().apply {
                setSize(width, height)
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                        MaterialTheme(colors = darkColors()) {
                            Surface {
                                GaugeDisplaySetup(channels, display, preferences, LoggerGaugeTheme.STI_NIGHT,
                                    {}, {}, {})
                            }
                        }
                    }
                }
                isVisible = true
            } }
            try {
                val deadline = System.nanoTime() + 10_000_000_000L
                var results: List<TextLayoutResult>
                do {
                    Thread.sleep(50)
                    results = textLayouts(window)
                } while (results.none { it.layoutInput.text.text == "1: $name" } && System.nanoTime() < deadline)
                assertTrue(results.any { it.layoutInput.text.text == "1: $name" }, "Native channel button was not laid out")
                assertTrue(results.any { it.layoutInput.text.text == "Use Logger Channels" }, "Native commands missing")
                assertLabelsFit(results, "$width x $height fontScale=$fontScale")
            } finally { edt { window.dispose() } }
        }
    }
    private fun assertLabelsFit(results: List<TextLayoutResult>, scenario: String) {
        for (result in results) {
            // Paragraph metrics are fractional; native layout rounds to pixels.
            val clipped = result.multiParagraph.didExceedMaxLines
                || result.multiParagraph.height > result.size.height + 1f
                || (0 until result.lineCount).any { result.isLineEllipsized(it)
                    || result.getLineRight(it) - result.getLineLeft(it) > result.size.width + 1f }
            assertFalse(clipped,
                "$scenario clipped '${result.layoutInput.text.text}' at ${result.size}; paragraph=${result.multiParagraph.height}, lines=${result.lineCount}")
        }
    }
    private fun textLayouts(window: ComposeWindow): List<TextLayoutResult> = edt {
        window.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }.flatMap { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts
        }
    }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun <T> edt(action: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return result!!.getOrThrow()
    }
}
