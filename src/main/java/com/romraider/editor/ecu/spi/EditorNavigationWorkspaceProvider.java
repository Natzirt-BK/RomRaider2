/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

/** Optional replacement editor navigation loaded by the desktop host. */
public interface EditorNavigationWorkspaceProvider {
    String getName();
    EditorNavigationWorkspace createWorkspace(
            EditorNavigationWorkspaceContext context);
}
