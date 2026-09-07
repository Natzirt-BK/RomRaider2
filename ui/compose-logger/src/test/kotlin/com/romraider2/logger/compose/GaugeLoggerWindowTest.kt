/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.romraider.logger.api.LiveDataSample
import com.romraider.logger.api.LoggerChannel
import com.romraider.logger.api.LoggerChannelKind
import com.romraider.logger.api.LoggerChannelService
import com.romraider.logger.api.LoggerGaugeDisplay
import com.romraider.logger.api.LoggerGaugeTheme
import com.romraider.logger.api.LoggerLiveDataBus
import com.romraider.logger.api.LoggerSessionService
import com.romraider.logger.api.LoggerSessionState
import com.romraider.logger.api.LoggerWorkspacePreferences
import com.romraider.logger.api.LoggerWorkspaceView
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext
import com.romraider.ui.DesktopDisplayAwake
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real Compose Window/WindowState, synthetic recording, no runtime or adapters. */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.runtime.tooling.ComposeToolingApi::class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RR2_COMPOSE_WINDOW_SMOKE", matches = "1")
class GaugeLoggerWindowTest {
    @Test fun nativeWindowLifecyclePreservesRecordingAndRestoresSetup() {
        val bus = LoggerLiveDataBus.getInstance()
        bus.clearSamples(); bus.loggingData()
        val commands = AtomicInteger()
        val held = AtomicInteger()
        val awake = DesktopDisplayAwake {
            assertEquals(1, held.incrementAndGet())
            object : DesktopDisplayAwake.Lease {
                override fun close() { assertEquals(0, held.decrementAndGet()) }
            }
        }
        val session = LoggerSessionService(bus, { commands.incrementAndGet() }, { commands.incrementAndGet() },
            { commands.incrementAndGet() }, { commands.incrementAndGet() }, { throw it })
        val channels = LoggerChannelService({ _, _ -> commands.incrementAndGet() }, { throw it })
        channels.replaceChannels(listOf(LoggerChannel("fixture", "Synthetic RPM", "rpm", LoggerChannelKind.PARAMETER, true)))
        val preferences = LoggerWorkspacePreferences(LoggerWorkspaceView.OVERVIEW, true) { _, _ -> }
        preferences.setGaugeDisplay(LoggerGaugeDisplay().withChannel(0, "fixture").withCount(1))
        preferences.setGaugeTheme(LoggerGaugeTheme.EVOLUTION_NIGHT)
        val context = LoggerWorkspaceContext(bus, session, channels, preferences, true)
        val windowRef = AtomicReference<ComposeWindow>()
        val stateRef = AtomicReference<WindowState>()
        val presentationRef = AtomicReference<GaugeWindowPresentation>()
        val exitRef = AtomicReference<() -> Unit>()
        val failure = AtomicReference<Throwable>()
        val app = thread(name = "Synthetic Compose logger application", isDaemon = true) {
            try {
                application(exitProcessOnExit = false) {
                    val state = rememberFittedWindowState(1000, 800)
                    SideEffect { stateRef.set(state); exitRef.set { exitApplication() } }
                    GaugeLoggerWindow("Synthetic Compose recording · no adapter", state,
                        onClose = { exitApplication() }, awakeFactory = { awake }) { display, status ->
                        SideEffect { windowRef.set(window); presentationRef.set(display) }
                        if (!display.fullScreen) MenuBar { Menu("Fixture") { Item("Close", onClick = { exitApplication() }) } }
                        LoggerWorkspace(context, onGaugeFullScreen = display::requestFullScreen,
                            gaugeFullScreenExitRevision = display.exitRevision, gaugeAwakeStatus = status)
                    }
                }
            } catch (error: Throwable) { failure.set(error) }
        }
        fun await(label: String, timeout: Long = 10_000, predicate: () -> Boolean) {
            val deadline = System.nanoTime() + timeout * 1_000_000
            while (!predicate()) {
                failure.get()?.let { throw it }
                check(System.nanoTime() < deadline) { "Timed out: $label" }
                Thread.sleep(40)
            }
        }
        try {
            await("native window") { windowRef.get() != null && edt { windowRef.get().isShowing } }
            val window = windowRef.get()
            val state = stateRef.get()
            fun has(text: String) = edt { nodes(window).any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
            } }
            fun click(text: String) = edt {
                val node = nodes(window).first { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true &&
                        node.config.getOrNull(SemanticsActions.OnClick)?.action != null
                }
                assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            }
            val robot = Robot()
            fun capture(suffix: String) {
                val prefix = System.getenv("RR2_COMPOSE_GAUGE_CAPTURE") ?: return
                System.setProperty("awt.robot.screenshotMethod", "x11")
                robot.delay(300)
                javax.imageio.ImageIO.write(robot.createScreenCapture(edt { window.graphicsConfiguration.bounds }), "png",
                    java.io.File("$prefix-$suffix.png"))
            }
            fun tap() {
                val bounds = edt { window.bounds }
                robot.mouseMove(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.delay(80)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            }
            fun enter() {
                await("setup button") { has("Full screen") }
                click("Full screen")
                await("native full screen") { edt { window.placement == WindowPlacement.Fullscreen && window.isFocused } }
                await("awake lease") { held.get() == 1 }
                assertFalse(has("Exit full screen"))
                assertTrue(edt { window.jMenuBar == null })
                try {
                    // AWT retains decoration insets in Frame.bounds on X11 even
                    // when the native full-screen client has no decorations.
                    // Verify the actual visible client area, not those cached insets.
                    await("display-fitted full screen") { edt {
                        java.awt.Rectangle(window.contentPane.locationOnScreen, window.contentPane.size) == window.graphicsConfiguration.bounds
                    } }
                } catch (error: Throwable) {
                    System.err.println(edt { "Fullscreen window=${window.bounds} display=${window.graphicsConfiguration.bounds} insets=${window.insets} root=${window.rootPane.bounds} content=${window.contentPane.bounds} contentOnScreen=${window.contentPane.locationOnScreen} menu=${window.jMenuBar}" })
                    capture("geometry-failure")
                    throw error
                }
            }
            await("gauge tab") { has("Gauges only") }
            click("Gauges only")
            assertEquals(0, held.get())
            val originalBounds = edt { window.bounds }
            enter()
            val sample = LiveDataSample("fixture", "Synthetic RPM", 3456.0, "3456", "rpm",
                System.currentTimeMillis(), channels.channels.first().conversionIdentity)
            bus.publish(sample)
            await("live reading after full screen") { edt { nodes(window).any { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { "3456" in it } == true
            } } }
            capture("fullscreen")
            tap(); await("tap menu") { has("Exit full screen") }
            capture("menu")
            Thread.sleep(3000); tap(); Thread.sleep(2500)
            assertTrue(has("Exit full screen"), "Tap must reset five-second menu timeout")
            await("menu timeout", 4000) { !has("Exit full screen") }
            val probe = edt { JFrame("Synthetic focus probe").apply {
                setBounds(20, 20, 150, 120); isVisible = true; toFront(); requestFocus()
            } }
            try {
                await("focus loss releases awake") { edt { probe.isFocused } && held.get() == 0 }
            } finally { edt { probe.dispose(); window.toFront(); window.requestFocus() } }
            await("focus regain") { edt { window.isFocused } && held.get() == 1 }
            tap(); await("exit menu") { has("Exit full screen") }; click("Exit full screen")
            try {
                await("restored native bounds") { edt { window.placement == WindowPlacement.Floating && window.bounds == originalBounds } }
            } catch (error: Throwable) {
                System.err.println(edt { "Restore expected=$originalBounds actual=${window.bounds} placement=${window.placement} state=${state.placement} size=${state.size} position=${state.position}" })
                throw error
            }
            await("exit releases awake") { held.get() == 0 }
            // Repeat rapid transitions: asynchronous native state notifications
            // must not make a later entry forget the maximized setup placement.
            repeat(5) { transition ->
                edt { state.placement = WindowPlacement.Maximized }
                await("maximized native window") { edt { window.placement == WindowPlacement.Maximized } }
                val beforeEntry = edt { "native=${window.placement} state=${state.placement}" }
                enter()
                val savedPlacement = edt {
                    GaugeWindowPresentation::class.java.getDeclaredField("previousPlacement").apply {
                        isAccessible = true
                    }.get(presentationRef.get())
                }
                robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE)
                try {
                    await("Escape restores maximized setup") { has("Full screen") && edt { window.placement == WindowPlacement.Maximized } }
                } catch (error: Throwable) {
                    val setupVisible = has("Full screen")
                    System.err.println(edt { "Escape transition=$transition beforeEntry=[$beforeEntry] saved=$savedPlacement setup=$setupVisible placement=${window.placement} state=${state.placement} bounds=${window.bounds} awake=${awake.status} focusOwner=${window.focusOwner}" })
                    throw error
                }
                await("Escape releases awake") { held.get() == 0 }
            }
            enter()
            // Native placement change bypasses the workspace callback, like a WM exit.
            edt { window.placement = WindowPlacement.Floating }
            try {
                // The WM may restore the pre-full-screen maximized state. The
                // workspace must mirror the actual non-full-screen result.
                await("native exit synchronizes setup") { has("Full screen") && edt {
                    window.placement != WindowPlacement.Fullscreen && state.placement == window.placement
                } }
            } catch (error: Throwable) {
                val setupVisible = has("Full screen")
                System.err.println(edt { "External exit setup=$setupVisible native=${window.placement} state=${state.placement} bounds=${window.bounds} awake=${awake.status}" })
                throw error
            }
            await("native exit releases awake") { held.get() == 0 }
            enter()
            edt { state.isMinimized = true }
            await("minimize releases awake") { edt { window.isMinimized } && held.get() == 0 }
            edt { state.isMinimized = false; window.toFront(); window.requestFocus() }
            await("restore native window") { edt { !window.isMinimized && window.isFocused } }
            if (edt { state.placement == WindowPlacement.Fullscreen }) await("restore full-screen awake") { held.get() == 1 }
            else { await("restore setup") { has("Full screen") }; assertEquals(0, held.get()); enter() }
            assertEquals(LoggerSessionState.RECORDING, session.state)
            assertEquals(0, commands.get(), "Presentation issued logger commands")
            assertSame(sample, bus.latestSamples.single())
            edt { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) }
            await("window close") { !app.isAlive }
            await("close releases awake") { held.get() == 0 && awake.status == DesktopDisplayAwake.Status.OFF }
            failure.get()?.let { throw it }
        } finally {
            exitRef.get()?.let { exit -> edt { exit() } }
            app.join(5000)
            awake.close(); session.close(); bus.stopped(); bus.clearSamples()
            check(!app.isAlive) { "Synthetic Compose application did not terminate" }
        }
    }

    private fun nodes(window: ComposeWindow): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return window.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }
    private fun <T> edt(action: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return result!!.getOrThrow()
    }
}
