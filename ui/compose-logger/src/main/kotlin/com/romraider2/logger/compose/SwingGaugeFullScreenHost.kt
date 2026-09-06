/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.RenderSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GraphicsDevice
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/** Owns window presentation only; it must never own or restart a logger session. */
@OptIn(ExperimentalComposeUiApi::class)
internal class SwingGaugeFullScreenHost(
    private val onHostExit: () -> Unit
) : JPanel(BorderLayout()) {
    // SwingGraphics keeps invalidation working when this embedded view moves to a
    // different window; the native SkiaSurface transfer can leave a frozen scene.
    val composePanel = ComposePanel(renderSettings = RenderSettings.SwingGraphics())
    private var mountedWindow: JFrame? = null
    private var mountedDevice: GraphicsDevice? = null
    private var previousFullScreenWindow: Window? = null
    // ComposePanel's retained attach path does not restore LocalAwtWindow itself.
    var currentWindow: Window? by mutableStateOf(null)
        private set

    init {
        preferredSize = Dimension(1000, 650)
        minimumSize = Dimension(640, 500)
        background = Color.BLACK
        add(composePanel, BorderLayout.CENTER)
    }

    fun setFullScreen(enabled: Boolean) {
        check(SwingUtilities.isEventDispatchThread())
        if (!enabled) { leaveFullScreen(restorePanel = true); return }
        if (mountedWindow != null) return
        val owner = SwingUtilities.getWindowAncestor(this)
        if (!isShowing || owner == null) { onHostExit(); return }
        val device = owner.graphicsConfiguration.device
        val frame = JFrame(owner.graphicsConfiguration).apply {
            title = "RomRaider2 · Gauges"
            isUndecorated = true
            background = Color.BLACK
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent) { setFullScreen(false) }
                override fun windowClosed(event: WindowEvent) {
                    if (mountedWindow === event.window) leaveFullScreen(restorePanel = true)
                }
            })
        }
        mountedWindow = frame
        mountedDevice = device
        previousFullScreenWindow = device.fullScreenWindow
        currentWindow = frame
        try {
            // Preserve the existing composition (including its listeners, history and
            // view state) while transferring between native windows. Restore automatic
            // disposal immediately afterward so normal application close still cleans up.
            retainingComposition {
                frame.contentPane.add(composePanel, BorderLayout.CENTER)
                device.fullScreenWindow = frame
                frame.validate()
            }
            composePanel.requestFocusInWindow()
        } catch (failure: RuntimeException) {
            leaveFullScreen(restorePanel = true)
            JOptionPane.showMessageDialog(owner,
                "Full screen is unavailable on this display. Gauge setup and logging are unchanged.",
                "Gauge display", JOptionPane.WARNING_MESSAGE)
        }
    }

    private fun leaveFullScreen(restorePanel: Boolean) {
        val frame = mountedWindow ?: return
        val device = mountedDevice
        val previous = previousFullScreenWindow
        mountedWindow = null
        mountedDevice = null
        previousFullScreenWindow = null
        currentWindow = if (restorePanel) SwingUtilities.getWindowAncestor(this) else null
        try {
            retainingComposition {
                if (restorePanel && isDisplayable) {
                    add(composePanel, BorderLayout.CENTER)
                    revalidate()
                    repaint()
                } else composePanel.parent?.remove(composePanel)
            }
            // Do not clear another window's newer full-screen request.
            if (device?.fullScreenWindow === frame) {
                device.fullScreenWindow = previous?.takeIf { it.isDisplayable && it.isVisible }
            }
        } finally {
            frame.dispose()
            if (!composePanel.isDisplayable) composePanel.dispose()
            onHostExit()
        }
        if (restorePanel && isShowing) composePanel.requestFocusInWindow()
    }

    private inline fun retainingComposition(action: () -> Unit) {
        val disposeOnRemove = composePanel.isDisposeOnRemove
        composePanel.isDisposeOnRemove = false
        try { action() } finally { composePanel.isDisposeOnRemove = disposeOnRemove }
    }

    override fun removeNotify() {
        // The embedded workspace or its owner is being removed; no orphan window or
        // Compose subscription may survive. Do not dispatch a logger stop here.
        leaveFullScreen(restorePanel = false)
        currentWindow = null
        super.removeNotify()
    }

    override fun addNotify() {
        super.addNotify()
        currentWindow = SwingUtilities.getWindowAncestor(this)
    }
}
