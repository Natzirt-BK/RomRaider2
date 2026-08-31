/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2019 RomRaider.com
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

package com.romraider.util;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import java.util.ResourceBundle;

public final class ResourceUtil {

    public ResourceBundle getBundle(final String bundle) {
        final File file = new File("i18n");
        Exception externalFailure = null;
        if (file.isDirectory()) {
            try (URLClassLoader loader = new URLClassLoader(
                    new URL[] {file.toURI().toURL()})) {
                return ResourceBundle.getBundle(
                        bundle, Locale.getDefault(), loader);
            } catch (Exception e) {
                externalFailure = e;
            }
        }
        try {
            return ResourceBundle.getBundle(bundle, Locale.getDefault(),
                    ResourceUtil.class.getClassLoader());
        } catch (Exception e) {
            if (externalFailure != null) e.addSuppressed(externalFailure);
            showMessageDialog(null,
                    e.getLocalizedMessage(),
                    "Error",
                    ERROR_MESSAGE);
        }
        return null;
    }
}
