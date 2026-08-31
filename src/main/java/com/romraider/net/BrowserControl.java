/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2022 RomRaider.com
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

package com.romraider.net;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import org.apache.log4j.Logger;

public class BrowserControl {
    private static final Logger LOGGER = Logger.getLogger(BrowserControl.class);

    private BrowserControl() {
        throw new UnsupportedOperationException();
    }

    public static void displayURL(String url) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new UnsupportedOperationException("Desktop integration is unavailable");
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("Desktop browsing is unavailable");
            }
            desktop.browse(new URI(url));
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Failed to display URL through desktop integration; using the platform launcher.", e);
            displayUrlWithPlatformLauncher(url);
        }
    }

    private static void displayUrlWithPlatformLauncher(String url) {
        boolean windows = isWindowsPlatform();
        ProcessBuilder command = windows
                ? new ProcessBuilder(WIN_PATH, WIN_FLAG, url)
                : new ProcessBuilder(UNIX_PATH, url);
        try {
            command.start();
        }
        catch (IOException x) {
            LOGGER.error("Could not invoke the platform browser launcher", x);
        }
    }

    public static boolean isWindowsPlatform() {
        String os = System.getProperty("os.name");
        if (os != null && os.startsWith(WIN_ID)) {
            return true;
        } else {
            return false;
        }
    }

    private static final String WIN_ID = "Windows";
    private static final String WIN_PATH = "rundll32";
    private static final String WIN_FLAG = "url.dll,FileProtocolHandler";
    private static final String UNIX_PATH = "xdg-open";
}
