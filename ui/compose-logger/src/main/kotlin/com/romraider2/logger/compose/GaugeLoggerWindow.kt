/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.romraider.ui.AwtDisplayAwakeBinding
import com.romraider.ui.DesktopDisplayAwake
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Presentation state only: no logger runtime, transport or session commands. */
internal class GaugeWindowPresentation(private val state: WindowState) {
    var fullScreen by mutableStateOf(false)
        private set
    var exitRevision by mutableIntStateOf(0)
        private set
    private var previousPlacement = state.placement
    private var previousPosition = state.position
    private var previousSize = state.size
    private var nativeWindow: ComposeWindow? = null
    private var restoreTimer: Timer? = null
    private var transitionRevision = 0

    fun attach(window: ComposeWindow) { nativeWindow = window }

    fun requestFullScreen(enabled: Boolean) {
        if (enabled == fullScreen) return
        cancelRestoration()
        val revision = transitionRevision
        if (enabled) {
            // Native maximize/restore notifications can lag behind a user tap.
            // Save the visible window's placement, not its preceding model state.
            previousPlacement = nativeWindow?.takeIf { it.isShowing }?.placement
                ?: state.placement
            previousPosition = state.position
            previousSize = state.size
            state.placement = WindowPlacement.Fullscreen
        } else {
            // Compose's Maximized setter does not itself clear native full screen.
            nativeWindow?.let { if (it.isDisplayable && it.placement == WindowPlacement.Fullscreen) it.placement = WindowPlacement.Floating }
            state.placement = previousPlacement
            // AWT posts the intermediate Floating state while leaving exclusive
            // full screen. Apply the requested mode after those native callbacks.
            SwingUtilities.invokeLater {
                val window = nativeWindow
                if (revision == transitionRevision && !fullScreen && window != null && window.isDisplayable) {
                    if (previousPlacement == WindowPlacement.Floating) restoreGeometry()
                    window.placement = previousPlacement
                    state.placement = previousPlacement
                    if (previousPlacement == WindowPlacement.Maximized) settleMaximizedRestore(window)
                }
            }
        }
        fullScreen = enabled
    }

    private fun settleMaximizedRestore(window: ComposeWindow) {
        // X11 normal-state callbacks can arrive after invokeLater has already
        // re-applied Maximized. Briefly reconcile both native and Compose state
        // until they agree continuously, with a hard limit for unsupported WMs.
        // This is transition-only: never keep enforcing a user's window mode.
        val deadline = System.nanoTime() + 2_000_000_000L
        var stableSince = System.nanoTime()
        restoreTimer = Timer(50) {
            val now = System.nanoTime()
            if (fullScreen || !window.isDisplayable || now >= deadline) {
                cancelRestoration()
            } else if (window.placement != WindowPlacement.Maximized || state.placement != WindowPlacement.Maximized) {
                stableSince = now
                if (window.placement != WindowPlacement.Maximized) window.placement = WindowPlacement.Maximized
                state.placement = WindowPlacement.Maximized
            } else if (now - stableSince >= 500_000_000L) {
                cancelRestoration()
            }
        }.apply { start() }
    }

    private fun cancelRestoration() {
        transitionRevision++
        restoreTimer?.stop()
        restoreTimer = null
    }

    fun close() { cancelRestoration(); nativeWindow = null }

    fun nativePlacementChanged() {
        if (fullScreen && state.placement != WindowPlacement.Fullscreen) {
            fullScreen = false
            if (previousPlacement == WindowPlacement.Floating) restoreGeometry()
            exitRevision++
        }
    }

    private fun restoreGeometry() {
        state.position = previousPosition
        state.size = previousSize
        nativeWindow?.let { window ->
            if (window.isDisplayable && state.placement == WindowPlacement.Floating && previousPosition.isSpecified) {
                window.setLocation(previousPosition.x.value.toInt(), previousPosition.y.value.toInt())
                window.setSize(previousSize.width.value.toInt(), previousSize.height.value.toInt())
            }
        }
    }
}

/** Shared by the production Compose-owned logger and its synthetic native test. */
@Composable
internal fun GaugeLoggerWindow(
    title: String,
    state: WindowState,
    onClose: () -> Unit,
    icon: Painter? = null,
    awakeFactory: () -> DesktopDisplayAwake = { DesktopDisplayAwake() },
    content: @Composable FrameWindowScope.(GaugeWindowPresentation, DesktopDisplayAwake.Status) -> Unit
) {
    val presentation = remember(state) { GaugeWindowPresentation(state) }
    val awake = remember { awakeFactory() }
    val status = rememberDisplayAwakeStatus(awake)
    DisposableEffect(awake) { onDispose { awake.close() } }
    LaunchedEffect(state.placement) { presentation.nativePlacementChanged() }
    Window(onCloseRequest = onClose, title = title, icon = icon, state = state) {
        val binding = remember(window, awake) {
            presentation.attach(window)
            AwtDisplayAwakeBinding(window, awake) {
                presentation.fullScreen && state.placement == WindowPlacement.Fullscreen &&
                    window.isDisplayable && window.placement == WindowPlacement.Fullscreen
            }
        }
        LaunchedEffect(window, presentation.fullScreen) {
            var observedFullScreen = false
            while (isActive && presentation.fullScreen && window.isDisplayable) {
                val actualPlacement = window.placement
                if (actualPlacement == WindowPlacement.Fullscreen) observedFullScreen = true
                else if (observedFullScreen) {
                    // Mirror native placement even between resize notifications.
                    state.placement = actualPlacement
                    presentation.nativePlacementChanged()
                }
                binding.update()
                delay(250)
            }
        }
        LaunchedEffect(presentation.fullScreen, state.placement) {
            // Removing the native menu must also relayout/repaint the root pane;
            // otherwise a retained menu strip can remain above the gauge canvas.
            window.rootPane.revalidate()
            window.rootPane.repaint()
            SwingUtilities.invokeLater {
                if (window.isDisplayable) {
                    if (presentation.fullScreen && window.placement == WindowPlacement.Fullscreen)
                        window.bounds = window.graphicsConfiguration.bounds
                    window.invalidate()
                    window.validate()
                    window.repaint()
                }
            }
            binding.update()
        }
        DisposableEffect(binding) { onDispose { presentation.close(); binding.close() } }
        content(presentation, status)
    }
}
