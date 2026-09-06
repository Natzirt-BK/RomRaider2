/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import com.romraider.portable.gauge.GaugeDashboardLayout;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/** One to six borderless faces, fitted without a scroll container. */
final class FxMountedGaugePane extends Pane {
    FxMountedGaugePane() { setMinSize(0, 0); }
    @Override protected void layoutChildren() {
        var tiles = GaugeDashboardLayout.fit((int) Math.max(0, getWidth()),
                (int) Math.max(0, getHeight()), getChildren().size(), 320.0 / 250, 2);
        for (int i = 0; i < tiles.size(); i++) {
            var tile = tiles.get(i);
            var child = getChildren().get(i);
            if (child instanceof Region region) { region.setMinSize(0, 0); region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); }
            child.resizeRelocate(tile.left, tile.top, tile.width, tile.height);
        }
    }
}
