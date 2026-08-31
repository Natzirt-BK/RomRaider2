/* RomRaider2 - GPL 2.0 or later. */
package com.romraider.ui;

import java.awt.Image;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFrame;

/** Approved RomRaider2 brand images shared by every top-level window. */
public final class BrandImages {
    private static final String ICON_ROOT =
            "/com/romraider2/ui/assets/icons/app/romraider2-app-";
    private static final int[] WINDOW_ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final List<Image> WINDOW_ICONS = loadWindowIcons();

    private BrandImages() {
    }

    public static ImageIcon icon(int size) {
        URL resource = BrandImages.class.getResource(
                ICON_ROOT + size + ".png");
        if (resource == null) {
            throw new IllegalStateException(
                    "Missing RomRaider2 application icon at size " + size);
        }
        return new ImageIcon(resource);
    }

    public static List<Image> windowIcons() {
        return WINDOW_ICONS;
    }

    public static void apply(JFrame frame) {
        frame.setIconImages(WINDOW_ICONS);
    }

    private static List<Image> loadWindowIcons() {
        List<Image> images = new ArrayList<Image>();
        for (int size : WINDOW_ICON_SIZES) images.add(icon(size).getImage());
        return Collections.unmodifiableList(images);
    }
}
