/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceProvider
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Legacy bridge used only when the explicitly selected Swing shell hosts Compose. */
class ComposeLoggerWorkspaceProvider internal constructor(
    private val displayAwakeFactory: () -> com.romraider.ui.DesktopDisplayAwake
) : LoggerWorkspaceProvider {
    constructor() : this({ com.romraider.ui.DesktopDisplayAwake() })
    override fun getName(): String = "Compose Desktop Logger"

    override fun createWorkspace(context: LoggerWorkspaceContext): JComponent {
        if (SwingUtilities.isEventDispatchThread()) return panel(context)
        var workspace: JComponent? = null
        SwingUtilities.invokeAndWait { workspace = panel(context) }
        return checkNotNull(workspace)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun panel(context: LoggerWorkspaceContext): JComponent {
        val exitRevision = mutableIntStateOf(0)
        val host = SwingGaugeFullScreenHost(displayAwakeFactory()) { exitRevision.intValue++ }
        host.composePanel.setContent {
            CompositionLocalProvider(LocalAwtWindow provides host.currentWindow) {
                LoggerWorkspace(context, onGaugeFullScreen = { enabled ->
                    // Never detach a ComposePanel inside its own composition/frame callback.
                    SwingUtilities.invokeLater { host.setFullScreen(enabled) }
                }, gaugeFullScreenExitRevision = exitRevision.intValue,
                    gaugeAwakeStatus = rememberDisplayAwakeStatus(host.displayAwake))
            }
        }
        return host
    }
}
