/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import javax.swing.JComponent;

import com.romraider.maps.Table;

/** UI adapter boundary for replaceable, independently licensed map renderers. */
public interface MapVisualizationProvider {
    String getName();
    boolean supports(Table table);
    JComponent createVisualization(Table table);
    void dispose(JComponent visualization);
}
