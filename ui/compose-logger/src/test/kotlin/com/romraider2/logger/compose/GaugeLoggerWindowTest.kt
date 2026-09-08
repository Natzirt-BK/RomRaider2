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
    @Test fun floatingRestorationDoesNotOverrideANewMaximizeAction() {
        val window = edt { ComposeWindow().apply { setSize(800, 600); isVisible = true } }
        val state = WindowState()
        val presentation = GaugeWindowPresentation(state)
        try {
            edt {
                presentation.attach(window)
                presentation.requestFullScreen(true)
                window.placement = WindowPlacement.Fullscreen
                presentation.requestFullScreen(false)
            }
            edt { }
            edt { state.placement = WindowPlacement.Maximized; window.placement = WindowPlacement.Maximized }
            Thread.sleep(700)
            assertEquals(WindowPlacement.Maximized, edt { state.placement })
            assertEquals(WindowPlacement.Maximized, edt { window.placement })
        } finally { edt { presentation.close(); window.dispose() } }
    }

    @Test fun floatingRestoreUsesVisibleGeometryInsteadOfDelayedModelCoordinates() {
        val window = edt { ComposeWindow().apply { setBounds(140, 112, 800, 600); isVisible = true } }
        val state = WindowState(position = androidx.compose.ui.window.WindowPosition(
            androidx.compose.ui.unit.Dp(0f), androidx.compose.ui.unit.Dp(0f)),
            size = androidx.compose.ui.unit.DpSize(androidx.compose.ui.unit.Dp(320f), androidx.compose.ui.unit.Dp(240f)))
        val presentation = GaugeWindowPresentation(state)
        try {
            Thread.sleep(200)
            edt { window.setLocation(140, 112) }
            val positioned = System.nanoTime() + 3_000_000_000L
            while (edt { window.x != 140 || window.y != 112 }) {
                check(System.nanoTime() < positioned) { "Fixture could not position the native window" }
                Thread.sleep(40)
            }
            val expected = edt { window.bounds }
            edt {
                presentation.attach(window)
                presentation.requestFullScreen(true)
                window.placement = WindowPlacement.Fullscreen
                presentation.requestFullScreen(false)
            }
            val deadline = System.nanoTime() + 4_000_000_000L
            while (edt { window.placement != WindowPlacement.Floating || window.bounds != expected }) {
                check(System.nanoTime() < deadline) { "Expected native floating bounds $expected, got ${edt { window.bounds }}" }
                Thread.sleep(40)
            }
            // A late full-screen ConfigureNotify can overwrite the first restore.
            Thread.sleep(120)
            edt {
                window.setLocation(0, 0)
                state.position = androidx.compose.ui.window.WindowPosition(androidx.compose.ui.unit.Dp(0f), androidx.compose.ui.unit.Dp(0f))
            }
            val settled = System.nanoTime() + 3_000_000_000L
            while (edt { window.bounds != expected }) {
                check(System.nanoTime() < settled) { "Late native notification lost floating geometry: ${edt { window.bounds }}" }
                Thread.sleep(40)
            }
            Thread.sleep(800)
            edt { window.setLocation(40, 40) }
            Thread.sleep(200)
            assertEquals(java.awt.Point(40, 40), edt { window.location }, "Restoration must not enforce position after settling")
        } finally { edt { presentation.close(); window.dispose() } }
    }

    @Test fun fullScreenExitRepairsDelayedFloatingNotification() {
        val window = edt { ComposeWindow().apply { setSize(800, 600); isVisible = true } }
        val state = WindowState(placement = WindowPlacement.Maximized)
        val presentation = GaugeWindowPresentation(state)
        try {
            edt { window.placement = WindowPlacement.Maximized }
            fun awaitPlacement(expected: WindowPlacement) {
                val deadline = System.nanoTime() + 5_000_000_000L
                while (edt { window.placement != expected || state.placement != expected }) {
                    check(System.nanoTime() < deadline) { "Expected restored $expected, native=${edt { window.placement }} model=${edt { state.placement }}" }
                    Thread.sleep(40)
                }
            }
            awaitPlacement(WindowPlacement.Maximized)
            edt {
                presentation.attach(window)
                presentation.requestFullScreen(true)
                window.placement = WindowPlacement.Fullscreen
                presentation.requestFullScreen(false)
            }
            // Emulate the delayed WM/model normal-state notification seen in CI,
            // after the existing invokeLater restoration has already run.
            edt { }
            Thread.sleep(120)
            edt {
                window.placement = WindowPlacement.Floating
                state.placement = WindowPlacement.Floating
                presentation.nativePlacementChanged()
            }
            awaitPlacement(WindowPlacement.Maximized)
            // Restoration must settle; a later deliberate unmaximize is allowed.
            Thread.sleep(800)
            edt {
                window.placement = WindowPlacement.Floating
                state.placement = WindowPlacement.Floating
                presentation.nativePlacementChanged()
            }
            Thread.sleep(300)
            awaitPlacement(WindowPlacement.Floating)
        } finally { edt { presentation.close(); window.dispose() } }
    }

    @Test fun fullScreenEntryCapturesNativePlacementBeforeDelayedModelNotification() {
        val window = edt { ComposeWindow().apply {
            setSize(800, 600)
            isVisible = true
        } }
        try {
            for (actual in listOf(WindowPlacement.Maximized, WindowPlacement.Floating)) {
                edt { window.placement = actual }
                val deadline = System.nanoTime() + 10_000_000_000L
                while (edt { window.placement != actual }) {
                    check(System.nanoTime() < deadline) { "Native placement did not become $actual" }
                    Thread.sleep(40)
                }
                edt {
                    // Window-manager changes can precede Compose's state listener.
                    // Deliberately hold the model at the preceding placement.
                    val stale = if (actual == WindowPlacement.Maximized)
                        WindowPlacement.Floating else WindowPlacement.Maximized
                    val state = WindowState(placement = stale)
                    val presentation = GaugeWindowPresentation(state)
                    presentation.attach(window)
                    presentation.requestFullScreen(true)
                    assertEquals(WindowPlacement.Fullscreen, state.placement)
                    presentation.requestFullScreen(false)
                    assertEquals(actual, state.placement,
                        "Return mode must match the native window, not a delayed model notification")
                    presentation.close()
                }
                // Drain the owner's queued restoration before the next case.
                edt { }
            }
        } finally { edt { window.dispose() } }
    }

    @Test fun pendingRestoreIsCancelledByReentryOrClose() {
        for (reenter in listOf(true, false)) {
            val window = edt { ComposeWindow().apply { setSize(800, 600); isVisible = true } }
            val state = WindowState(placement = WindowPlacement.Maximized)
            val presentation = GaugeWindowPresentation(state)
            try {
                edt { window.placement = WindowPlacement.Maximized }
                val deadline = System.nanoTime() + 5_000_000_000L
                while (edt { window.placement != WindowPlacement.Maximized }) {
                    check(System.nanoTime() < deadline) { "Native maximize timed out" }
                    Thread.sleep(40)
                }
                edt {
                    presentation.attach(window)
                    presentation.requestFullScreen(true)
                    window.placement = WindowPlacement.Fullscreen
                    presentation.requestFullScreen(false)
                }
                edt { }
                edt {
                    if (reenter) presentation.requestFullScreen(true)
                    else {
                        presentation.close()
                        state.placement = WindowPlacement.Floating
                    }
                }
                Thread.sleep(300)
                assertEquals(if (reenter) WindowPlacement.Fullscreen else WindowPlacement.Floating,
                    edt { state.placement }, "Old restoration must not overwrite a new transition or closed owner")
            } finally { edt { presentation.close(); window.dispose() } }
        }
    }

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
            await("Dashboard navigation") { has("Dashboard") || has("Dashboard  Ctrl+4") }
            assertFalse(has("Gauges only"), "Desktop must not restore the duplicate gauge tab")
            assertFalse(has("Open gauge display"), "Gauge entry belongs to Dashboard")
            click(if (has("Dashboard")) "Dashboard" else "Dashboard  Ctrl+4")
            await("Dashboard gauge entry") { has("Open gauge display") }
            click("Open gauge display")
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
