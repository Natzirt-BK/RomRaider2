/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.romraider.logger.api.LoggerChannel
import com.romraider.logger.api.LoggerChannelKind
import com.romraider.logger.api.LoggerChannelService
import com.romraider.logger.api.LoggerGaugeDisplay
import com.romraider.logger.api.LoggerLiveDataBus
import com.romraider.logger.api.LoggerSessionService
import com.romraider.logger.api.LoggerSessionState
import com.romraider.logger.api.LoggerWorkspacePreferences
import com.romraider.logger.api.LoggerWorkspaceView
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse

/** Native UI checks use a synthetic bus only; no runtime or adapter is constructed. */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.runtime.tooling.ComposeToolingApi::class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RR2_COMPOSE_WINDOW_SMOKE", matches = "1")
class SwingGaugeFullScreenHostTest {
    @Test fun transferRetainsCompositionAndOwnerCloseDisposesItExactlyOnce() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Native Swing/Compose test requires a display")
        val mounts = AtomicInteger()
        val disposals = AtomicInteger()
        val displayRevision = androidx.compose.runtime.mutableIntStateOf(0)
        lateinit var owner: JFrame
        lateinit var host: SwingGaugeFullScreenHost
        edt {
            host = SwingGaugeFullScreenHost {}
            host.composePanel.setContent {
                DisposableEffect(Unit) { mounts.incrementAndGet(); onDispose { disposals.incrementAndGet() } }
                Text("Retained composition ${displayRevision.intValue}")
            }
            owner = JFrame("Synthetic full-screen host").apply {
                contentPane.add(host); setBounds(40, 50, 800, 600); isVisible = true
            }
        }
        try {
            await { mounts.get() == 1 }
            val originalBounds = edt { owner.bounds }
            repeat(3) { index ->
                lateinit var full: JFrame
                edt {
                    host.setFullScreen(true)
                    full = owner.graphicsConfiguration.device.fullScreenWindow as JFrame
                    assertTrue(full.isUndecorated)
                    assertSame(full, SwingUtilities.getWindowAncestor(host.composePanel))
                    displayRevision.intValue = index * 2 + 1
                }
                // Window-manager placement is asynchronous; require the exact
                // final bounds rather than asserting during the transition.
                await { edt { owner.graphicsConfiguration.bounds == full.bounds } }
                await { hasText(host, "Retained composition ${index * 2 + 1}") }
                edt {
                    host.setFullScreen(false)
                    assertSame(host, host.composePanel.parent)
                    assertTrue(host.composePanel.isDisposeOnRemove)
                    assertEquals(originalBounds, owner.bounds)
                    displayRevision.intValue = index * 2 + 2
                }
                await { hasText(host, "Retained composition ${index * 2 + 2}") }
            }
            edt { host.setFullScreen(true); owner.dispose() }
            await { disposals.get() == 1 }
            assertEquals(1, mounts.get(), "Full-screen transfer recreated the composition")
            edt { assertTrue(owner.graphicsConfiguration.device.fullScreenWindow == null) }
        } finally { edt { owner.dispose() } }
    }

    @Test fun productionWorkspaceTapTimeoutExitAndWindowClosePreserveRecording() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Native Swing/Compose test requires a display")
        val bus = LoggerLiveDataBus.getInstance()
        bus.clearSamples()
        bus.loggingData()
        val commands = AtomicInteger()
        val heldAwake = AtomicInteger()
        val denyAwake = java.util.concurrent.atomic.AtomicBoolean()
        val awake = com.romraider.ui.DesktopDisplayAwake {
            if (denyAwake.get()) throw IllegalStateException("Synthetic desktop denial")
            assertEquals(1, heldAwake.incrementAndGet())
            object : com.romraider.ui.DesktopDisplayAwake.Lease {
                override fun close() { assertEquals(0, heldAwake.decrementAndGet()) }
            }
        }
        val session = LoggerSessionService(bus, { commands.incrementAndGet() }, { commands.incrementAndGet() },
            { commands.incrementAndGet() }, { commands.incrementAndGet() }, { throw it })
        val channels = LoggerChannelService({ _, _ -> commands.incrementAndGet() }, { throw it })
        channels.replaceChannels(listOf(LoggerChannel("fixture", "Synthetic RPM", "rpm", LoggerChannelKind.PARAMETER, true)))
        val preferences = LoggerWorkspacePreferences(LoggerWorkspaceView.OVERVIEW, true) { _, _ -> }
        preferences.setGaugeDisplay(LoggerGaugeDisplay().withChannel(0, "fixture").withCount(1))
        preferences.setGaugeTheme(com.romraider.logger.api.LoggerGaugeTheme.STI_NIGHT)
        lateinit var owner: JFrame
        lateinit var host: SwingGaugeFullScreenHost
        edt {
            host = ComposeLoggerWorkspaceProvider { awake }.createWorkspace(
                LoggerWorkspaceContext(bus, session, channels, preferences, true)) as SwingGaugeFullScreenHost
            owner = JFrame("Synthetic recording · no adapter").apply {
                contentPane.add(host); setBounds(30, 40, 1000, 800); isVisible = true
            }
        }
        try {
            await { hasText(host, "Gauges only") }
            click(host, "Gauges only")
            await { hasText(host, "Full screen") }
            assertEquals(0, heldAwake.get(), "Gauge setup must not keep the display awake")
            click(host, "Full screen")
            await { edt { owner.graphicsConfiguration.device.fullScreenWindow != null } }
            await { !hasText(host, "Full screen") }
            await { edt { SwingUtilities.getWindowAncestor(host.composePanel).isFocused } }
            await { heldAwake.get() == 1 }
            assertFalse(hasText(host, "Exit full screen"))
            // Readings continue arriving through the existing bus after the transfer.
            val sample = com.romraider.logger.api.LiveDataSample("fixture", "Synthetic RPM", 2345.0, "2345", "rpm",
                System.currentTimeMillis(), channels.channels.first().conversionIdentity)
            bus.publish(sample)
            await { edt { nodes(host).any { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { "2345" in it } == true
            } } }
            val robot = Robot()
            val capturePrefix = System.getenv("RR2_SWING_GAUGE_CAPTURE")
            fun capture(suffix: String) {
                if (capturePrefix == null) return
                System.setProperty("awt.robot.screenshotMethod", "x11")
                val bounds = edt { SwingUtilities.getWindowAncestor(host.composePanel).bounds }
                javax.imageio.ImageIO.write(robot.createScreenCapture(bounds), "png", java.io.File("$capturePrefix-$suffix.png"))
                println("Native capture: $bounds; panel: ${edt { host.composePanel.bounds }}")
            }
            robot.delay(500)
            capture("fullscreen")
            fun tap() {
                val point = edt {
                    host.composePanel.locationOnScreen.apply {
                        translate(host.composePanel.width / 2, host.composePanel.height / 2)
                    }
                }
                robot.mouseMove(point.x, point.y)
                robot.delay(100)
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.delay(100)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            }
            tap()
            robot.delay(500)
            capture("tapped")
            await { hasText(host, "Exit full screen") }
            Thread.sleep(3000)
            tap()
            Thread.sleep(2500)
            assertTrue(hasText(host, "Exit full screen"), "A second tap must reset the timeout")
            await(4000) { !hasText(host, "Exit full screen") }
            assertEquals(LoggerSessionState.RECORDING, session.state)
            // Changing native window focus clears the menu; refocusing must not reopen it.
            tap()
            await { hasText(host, "Exit full screen") }
            val probe = edt { JFrame("Synthetic focus probe").apply {
                setBounds(10, 10, 120, 100); isVisible = true; toFront(); requestFocus()
            } }
            try {
                await { edt { probe.isFocused } }
                await { heldAwake.get() == 0 }
                await { !hasText(host, "Exit full screen") }
            } finally { edt { probe.dispose(); SwingUtilities.getWindowAncestor(host.composePanel).requestFocus() } }
            await { edt { SwingUtilities.getWindowAncestor(host.composePanel).isFocused } }
            await { heldAwake.get() == 1 }
            assertFalse(hasText(host, "Exit full screen"))
            tap()
            await { hasText(host, "Exit full screen") }
            click(host, "Exit full screen")
            await { hasText(host, "Full screen") }
            await { edt { host.composePanel.parent === host } }
            await { heldAwake.get() == 0 }
            // Native close (e.g. Alt-F4) exits the display, not the host logger.
            click(host, "Full screen")
            await { edt { owner.graphicsConfiguration.device.fullScreenWindow != null } }
            edt {
                val full = owner.graphicsConfiguration.device.fullScreenWindow
                full.dispatchEvent(WindowEvent(full, WindowEvent.WINDOW_CLOSING))
            }
            await { hasText(host, "Full screen") }
            await { edt { host.composePanel.parent === host } }
            click(host, "Full screen")
            await { edt { SwingUtilities.getWindowAncestor(host.composePanel) !== owner &&
                SwingUtilities.getWindowAncestor(host.composePanel).isFocused } }
            robot.keyPress(KeyEvent.VK_ESCAPE)
            robot.keyRelease(KeyEvent.VK_ESCAPE)
            await { hasText(host, "Full screen") }
            await { edt { host.composePanel.parent === host } }
            assertEquals(LoggerSessionState.RECORDING, session.state)
            assertEquals(0, commands.get(), "Changing presentation issued a logger command")
            assertEquals(listOf("fixture"), preferences.gaugeDisplay.visibleChannels)
            assertSame(sample, bus.latestSamples.single())
            denyAwake.set(true)
            click(host, "Full screen")
            await { awake.status == com.romraider.ui.DesktopDisplayAwake.Status.UNAVAILABLE }
            await { hasText(host, "Screen awake unavailable") }
            assertFalse(hasText(host, "Exit full screen"))
            assertEquals(0, heldAwake.get())
            capture("unavailable")
            assertEquals(LoggerSessionState.RECORDING, session.state)
            assertEquals(0, commands.get(), "A denied awake request affected recording")
        } finally {
            edt { owner.dispose() }
            awake.close()
            await { heldAwake.get() == 0 && awake.status == com.romraider.ui.DesktopDisplayAwake.Status.OFF }
            session.close()
            bus.stopped()
            bus.clearSamples()
        }
    }

    private fun nodes(host: SwingGaugeFullScreenHost): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return host.composePanel.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }

    private fun hasText(host: SwingGaugeFullScreenHost, text: String): Boolean = edt {
        nodes(host).any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
    }

    private fun click(host: SwingGaugeFullScreenHost, text: String) = edt {
        val node = nodes(host).first { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true &&
                node.config.getOrNull(SemanticsActions.OnClick)?.action != null
        }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }

    private fun await(timeout: Long = 10_000, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + timeout * 1_000_000
        while (!predicate()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for native gauge UI" }
            Thread.sleep(40)
        }
    }

    private fun <T> edt(action: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return result!!.getOrThrow()
    }
}
