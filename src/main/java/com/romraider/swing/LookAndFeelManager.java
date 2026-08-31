/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2012 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.swing;

import static com.romraider.Version.PRODUCT_NAME;
import static com.romraider.util.Platform.MAC_OS_X;
import static com.romraider.util.Platform.isPlatform;
import static javax.swing.UIManager.getCrossPlatformLookAndFeelClassName;
import static javax.swing.UIManager.getSystemLookAndFeelClassName;
import static javax.swing.UIManager.setLookAndFeel;

import javax.swing.JDialog;
import javax.swing.JFrame;

import org.apache.log4j.Logger;

import com.romraider.ui.UiDisplayService;

public final class LookAndFeelManager {
    private static final Logger LOGGER = Logger.getLogger(LookAndFeelManager.class);

    private LookAndFeelManager() {
        throw new UnsupportedOperationException();
    }

    public static void initLookAndFeel() {
        try {
            if (isPlatform(MAC_OS_X)) {
                System.setProperty("apple.awt.rendering", "true");
                System.setProperty("apple.awt.brushMetalLook", "true");
                System.setProperty("apple.laf.useScreenMenuBar", "true");
                System.setProperty("apple.awt.window.position.forceSafeCreation", "true");
                System.setProperty("com.apple.mrj.application.apple.menu.about.name", PRODUCT_NAME);
            }

            setLookAndFeel(getLookAndFeel());

            // RomRaider2 frames opt into their integrated chrome individually.
            // Leave ordinary dialogs to the window manager on Windows/Linux;
            // Metal's client decorations produce the legacy stippled title bars
            // and close buttons seen in otherwise themed message dialogs.
            boolean useLookAndFeelDecorations = isPlatform(MAC_OS_X);
            JFrame.setDefaultLookAndFeelDecorated(useLookAndFeelDecorations);
            JDialog.setDefaultLookAndFeelDecorated(useLookAndFeelDecorations);
            UiDisplayService.getInstance().applyFromSettings();

        } catch (Exception ex) {
            LOGGER.error("Error loading system look and feel.", ex);
        }
    }

    private static String getLookAndFeel() {
//        if (true) return "com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel";
        // RomRaider2 owns its palette and window chrome.  Native Windows and
        // Linux delegates can ignore those colors (most visibly in menus,
        // tabs, combo boxes, and file choosers), so use the predictable
        // cross-platform delegates everywhere except macOS, where the screen
        // menu bar and platform integration require the system look and feel.
        if (!isPlatform(MAC_OS_X)) return getCrossPlatformLookAndFeelClassName();
        return getSystemLookAndFeelClassName();
    }
}
