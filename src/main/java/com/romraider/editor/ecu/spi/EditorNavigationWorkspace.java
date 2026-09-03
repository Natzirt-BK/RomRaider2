/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu.spi;

import javax.swing.JComponent;

/** Lifecycle and command boundary for the editor navigation surface. */
public interface EditorNavigationWorkspace extends AutoCloseable {
    JComponent getComponent();
    void refresh();
    void refreshChangedMaps();
    void focusSearch();
    void goBack();
    void goForward();
    void close();
}
