/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.ui.awt.ComposePanel
import com.romraider.editor.ecu.spi.CalibrationWorkspaceContext
import com.romraider.editor.ecu.spi.CalibrationWorkspaceProvider
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Compatibility bridge for the optional legacy Swing editor shell. */
class ComposeCalibrationWorkspaceProvider : CalibrationWorkspaceProvider {
    override fun getName(): String = "Compose Desktop Calibration Workspace"

    override fun createWorkspace(context: CalibrationWorkspaceContext): JComponent {
        if (SwingUtilities.isEventDispatchThread()) return panel(context)
        var workspace: JComponent? = null
        SwingUtilities.invokeAndWait { workspace = panel(context) }
        return checkNotNull(workspace)
    }

    private fun panel(context: CalibrationWorkspaceContext) = ComposePanel().apply {
        preferredSize = Dimension(900, 560)
        minimumSize = Dimension(400, 280)
        setContent { CalibrationWorkspace(context) }
    }
}
