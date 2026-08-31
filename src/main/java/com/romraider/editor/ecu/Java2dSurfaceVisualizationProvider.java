/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.ecu;

import javax.swing.JComponent;

import com.romraider.maps.Table;
import com.romraider.maps.Table3D;

/** Dependency-free surface renderer used while external engines are evaluated. */
public final class Java2dSurfaceVisualizationProvider
        implements MapVisualizationProvider {
    public String getName() {
        return "Java2D surface";
    }

    public boolean supports(Table table) {
        return table instanceof Table3D;
    }

    public JComponent createVisualization(Table table) {
        return supports(table) ? new Java2dSurfacePanel((Table3D) table) : null;
    }

    public void dispose(JComponent visualization) {
        if (visualization instanceof Java2dSurfacePanel) {
            ((Java2dSurfacePanel) visualization).disposeSurface();
        }
    }
}
