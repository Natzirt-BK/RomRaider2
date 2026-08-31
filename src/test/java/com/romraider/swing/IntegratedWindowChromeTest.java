/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import static org.junit.Assert.assertEquals;

import java.awt.Dimension;
import java.awt.Rectangle;

import org.junit.Test;

public class IntegratedWindowChromeTest {
    @Test
    public void lowerLeftCornerMovesLeftEdgeAndBottomIndependently() {
        Rectangle resized = IntegratedWindowChrome.resizeFromBottomCorner(
                new Rectangle(100, 80, 900, 600), -70, 45,
                new Dimension(640, 480), true);

        assertEquals(new Rectangle(30, 80, 970, 645), resized);
    }

    @Test
    public void lowerLeftCornerKeepsRightEdgeFixedAtMinimumWidth() {
        Rectangle resized = IntegratedWindowChrome.resizeFromBottomCorner(
                new Rectangle(100, 80, 900, 600), 500, -300,
                new Dimension(640, 480), true);

        assertEquals(new Rectangle(360, 80, 640, 480), resized);
    }
}
