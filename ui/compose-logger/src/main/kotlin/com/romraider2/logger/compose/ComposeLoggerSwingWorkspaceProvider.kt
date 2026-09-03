/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.ui.awt.ComposePanel
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceProvider
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Legacy bridge used only when the explicitly selected Swing shell hosts Compose. */
class ComposeLoggerWorkspaceProvider : LoggerWorkspaceProvider {
    override fun getName(): String = "Compose Desktop Logger"

    override fun createWorkspace(context: LoggerWorkspaceContext): JComponent {
        if (SwingUtilities.isEventDispatchThread()) return panel(context)
        var workspace: JComponent? = null
        SwingUtilities.invokeAndWait { workspace = panel(context) }
        return checkNotNull(workspace)
    }

    private fun panel(context: LoggerWorkspaceContext): JComponent =
        ComposePanel().apply {
            preferredSize = Dimension(1000, 650)
            minimumSize = Dimension(640, 500)
            setContent { LoggerWorkspace(context) }
        }
}
