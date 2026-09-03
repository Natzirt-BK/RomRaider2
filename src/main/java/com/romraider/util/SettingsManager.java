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

package com.romraider.util;

import static com.romraider.Version.VERSION;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.romraider.Settings;
import com.romraider.activity.ProgressReporter;
import com.romraider.xml.DOMSettingsBuilder;
import com.romraider.xml.DOMSettingsUnmarshaller;

public class SettingsManager {
    private static final Logger LOGGER =
            Logger.getLogger(SettingsManager.class);
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            SettingsManager.class.getName());
    private static final String SETTINGS_FILE = "/settings.xml";
    private static final String SETTINGS_DIR_PROPERTY =
            "romraider2.settings.dir";
    private static final String DEFAULT_SETTINGS_FILE_PROPERTY =
            "romraider2.default.settings.file";
    private static final String CONFIGURED_SETTINGS_DIR =
            System.getProperty(SETTINGS_DIR_PROPERTY);
    private static final String DEFAULT_SETTINGS_DIR = resolveSettingsDirectory(
            CONFIGURED_SETTINGS_DIR,
            System.getProperty("user.home"), System.getProperty("os.name"));
    private static final String START_DIR = System.getProperty("user.dir");
    private static String settingsDir = DEFAULT_SETTINGS_DIR;

    private static Settings settings = null;
    private static boolean testing = false;

    public static Settings getSettings() {
        if(null == settings) {
            settings = load();
        }
        return settings;
    }

    /** Returns the directory that owns the active settings file. */
    public static Path getSettingsDirectory() {
        return new File(settingsDir).toPath().toAbsolutePath().normalize();
    }

    public static void setTesting(boolean b) {
    	testing = b;
    }

    public static boolean getTesting() {
    	return testing;
    }

    static String resolveSettingsDirectory(String configuredDirectory,
            String userHome) {
        return resolveSettingsDirectory(configuredDirectory, userHome, "");
    }

    static String resolveSettingsDirectory(String configuredDirectory,
            String userHome, String osName) {
        if (configuredDirectory != null
                && !configuredDirectory.trim().isEmpty()) {
            return configuredDirectory.trim();
        }
        String platform = osName == null ? ""
                : osName.toLowerCase(Locale.ENGLISH);
        if (platform.contains("mac")) {
            return userHome + "/Library/Application Support/RomRaider2";
        }
        return userHome + "/.RomRaider";
    }

    static String resolveLoadSettingsDirectory(String configuredDirectory,
            String userHome, String startDirectory,
            boolean workingSettingsExists) {
        return resolveLoadSettingsDirectory(configuredDirectory, userHome,
                startDirectory, workingSettingsExists, "");
    }

    static String resolveLoadSettingsDirectory(String configuredDirectory,
            String userHome, String startDirectory,
            boolean workingSettingsExists, String osName) {
        if (configuredDirectory != null
                && !configuredDirectory.trim().isEmpty()) {
            return resolveSettingsDirectory(configuredDirectory, userHome,
                    osName);
        }
        return workingSettingsExists
                ? startDirectory
                : resolveSettingsDirectory(null, userHome, osName);
    }
    
    private static Settings load() {
        Settings loadedSettings;
        try {
            final File workingSettings = new File(START_DIR + SETTINGS_FILE);
            settingsDir = resolveLoadSettingsDirectory(
                    CONFIGURED_SETTINGS_DIR,
                    System.getProperty("user.home"), START_DIR,
                    workingSettings.exists(), System.getProperty("os.name"));
            final File sf = new File(settingsDir + SETTINGS_FILE);
            installPackagedDefaults(sf,
                    System.getProperty(DEFAULT_SETTINGS_FILE_PROPERTY));
            LOGGER.info("Loaded settings from file: " + settingsDir.replace("\\", "/") + SETTINGS_FILE);

            if (sf.length() > 0) {
                try (FileInputStream settingsFileIn = new FileInputStream(sf)) {
                    final InputSource src = new InputSource(settingsFileIn);
                    final DOMSettingsUnmarshaller domUms = new DOMSettingsUnmarshaller();
                    final DocumentBuilderFactory dbf =
                            XmlSecurity.newDocumentBuilderFactory();
                    final DocumentBuilder builder = dbf.newDocumentBuilder();
                    final Document doc = builder.parse(src);
                    loadedSettings = domUms.unmarshallSettings(
                            doc.getDocumentElement());
                }
            }
            else {
                throw new FileNotFoundException("file length is 0");
            }
        } catch (FileNotFoundException e) {
            LOGGER.warn(rb.getString("FNF"), e);
            loadedSettings = new Settings();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return loadedSettings;
    }

    static void installPackagedDefaults(File settingsFile,
            String packagedDefaultPath) throws IOException {
        if (settingsFile == null || settingsFile.length() > 0
                || packagedDefaultPath == null
                || packagedDefaultPath.trim().isEmpty()) return;
        File packagedDefault = new File(packagedDefaultPath.trim());
        if (!packagedDefault.isFile() || packagedDefault.length() == 0) return;
        File parent = settingsFile.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        Files.copy(packagedDefault.toPath(), settingsFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    public static void save(Settings newSettings) {
        save(newSettings, (status, percent) -> { });
    }

    public static void save(Settings newSettings, ProgressReporter progress) {
    	if(testing) return;

        final DOMSettingsBuilder builder = new DOMSettingsBuilder();
        try {
            final File newDir = new File(settingsDir);
            newDir.mkdir();     // Creates directory if it does not exist
            final File sf = new File(settingsDir + SETTINGS_FILE);
            builder.buildSettings(newSettings, sf, progress, VERSION);
            settings = newSettings;
            if (sf.length() == 0)
                throw new RuntimeException("Settings file write failed");
        } catch (Exception e) {
            settings = newSettings;
            throw new RuntimeException(e);
        }
    }
}
