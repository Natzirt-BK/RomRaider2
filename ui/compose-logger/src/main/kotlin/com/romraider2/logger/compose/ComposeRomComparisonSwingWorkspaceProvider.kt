/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.ui.awt.ComposePanel
import com.romraider.editor.ecu.spi.RomComparisonWorkspaceContext
import com.romraider.editor.ecu.spi.RomComparisonWorkspaceProvider
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Compatibility bridge for the optional legacy Swing editor shell. */
class ComposeRomComparisonWorkspaceProvider : RomComparisonWorkspaceProvider {
    override fun getName(): String = "Compose Desktop ROM Comparison"

    override fun createWorkspace(context: RomComparisonWorkspaceContext): JComponent {
        if (SwingUtilities.isEventDispatchThread()) return panel(context)
        var workspace: JComponent? = null
        SwingUtilities.invokeAndWait { workspace = panel(context) }
        return checkNotNull(workspace)
    }

    private fun panel(context: RomComparisonWorkspaceContext) = ComposePanel().apply {
        preferredSize = Dimension(900, 560)
        minimumSize = Dimension(440, 320)
        setContent { RomComparisonWorkspace(context) }
    }
}
