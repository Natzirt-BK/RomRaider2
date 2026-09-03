/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposePanel
import com.romraider.editor.document.EditorDocumentSession
import com.romraider.editor.ecu.spi.EditorNavigationWorkspace
import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceContext
import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceProvider
import java.awt.Dimension
import java.awt.EventQueue
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Compatibility bridge for the optional legacy Swing editor shell. */
class ComposeEditorNavigationWorkspaceProvider :
    EditorNavigationWorkspaceProvider {
    override fun getName(): String = "Compose Desktop Editor Navigation"

    override fun createWorkspace(
        context: EditorNavigationWorkspaceContext
    ): EditorNavigationWorkspace {
        if (SwingUtilities.isEventDispatchThread()) {
            return ComposeEditorNavigationWorkspace(context)
        }
        var workspace: EditorNavigationWorkspace? = null
        SwingUtilities.invokeAndWait {
            workspace = ComposeEditorNavigationWorkspace(context)
        }
        return checkNotNull(workspace)
    }
}

private class ComposeEditorNavigationWorkspace(
    private val context: EditorNavigationWorkspaceContext
) : EditorNavigationWorkspace {
    private var refreshRequest by mutableIntStateOf(0)
    private var focusRequest by mutableIntStateOf(0)
    private val listener = EditorDocumentSession.Listener { refresh() }
    private val panel = ComposePanel().apply {
        preferredSize = Dimension(270, 620)
        minimumSize = Dimension(190, 320)
        setContent {
            EditorNavigationSurface(context, refreshRequest, focusRequest)
        }
    }

    init {
        context.addSessionListener(listener)
    }

    override fun getComponent(): JComponent = panel
    override fun refresh() = onUi { refreshRequest++ }
    override fun refreshChangedMaps() = refresh()
    override fun focusSearch() = onUi { focusRequest++ }

    override fun goBack() {
        context.goBack()
        refresh()
    }

    override fun goForward() {
        context.goForward()
        refresh()
    }

    override fun close() {
        context.removeSessionListener(listener)
    }

    private fun onUi(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action()
        else EventQueue.invokeLater(action)
    }
}
