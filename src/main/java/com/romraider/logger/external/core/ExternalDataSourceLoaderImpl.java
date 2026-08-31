/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2015 RomRaider.com
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

package com.romraider.logger.external.core;

import com.romraider.logger.ecu.definition.plugin.PluginFilenameFilter;
import com.romraider.logger.ecu.exception.ConfigurationException;
import com.romraider.logger.ecu.exception.PluginNotInstalledException;

import static com.romraider.util.ParamChecker.isNullOrEmpty;
import static com.romraider.util.Platform.isPlatform;
import org.apache.log4j.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class ExternalDataSourceLoaderImpl implements ExternalDataSourceLoader {
    private static final Logger LOGGER = Logger.getLogger(ExternalDataSourceLoaderImpl.class);
    private static final String PLUGIN_DIRECTORY_PROPERTY = "romraider2.plugins.dir";
    private List<ExternalDataSource> externalDataSources = new ArrayList<ExternalDataSource>();

    public void loadExternalDataSources(Map<String, String> loggerPluginPorts) {
        try {
            File pluginsDir = new File(System.getProperty(
                    PLUGIN_DIRECTORY_PROPERTY, "./plugins"));
            if (pluginsDir.exists() && pluginsDir.isDirectory()) {
                File[] pluginPropertyFiles = pluginsDir.listFiles(new PluginFilenameFilter());
                for (File pluginPropertyFile : pluginPropertyFiles) {
                    Properties pluginProps = new Properties();
                    FileInputStream inputStream = new FileInputStream(pluginPropertyFile);
                    try {
                        pluginProps.load(inputStream);
                        if (!supportsCurrentPlatform(pluginProps)) {
                            LOGGER.info("Skipping external datasource for this operating system: "
                                    + pluginPropertyFile.getName());
                            continue;
                        }
                        String datasourceClassName = pluginProps.getProperty("datasource.class");
                        if (!isNullOrEmpty(datasourceClassName)) {
                            try {
                                Class<?> dataSourceClass = getClass().getClassLoader().loadClass(datasourceClassName);
                                if (dataSourceClass != null && ExternalDataSource.class.isAssignableFrom(dataSourceClass)) {
                                    ExternalDataSource dataSource = dataSource(dataSourceClass, loggerPluginPorts, pluginProps);
                                    ExternalDataSource managedDataSource = new GenericDataSourceManager(dataSource);
                                    externalDataSources.add(managedDataSource);
                                    LOGGER.info("Plugin loaded: " + dataSource.getName() + " v" + dataSource.getVersion());
                                }
                            }
                            catch (PluginNotInstalledException e) {
                                LOGGER.warn(e.getMessage());
                            }
                            catch (Throwable t) {
                                LOGGER.error("Error loading external datasource: " + datasourceClassName + ", specified in: "
                                        + pluginPropertyFile.getAbsolutePath(), t);
                            }
                        }
                    } finally {
                        inputStream.close();
                    }
                }
            }
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
    }

    static boolean supportsCurrentPlatform(Properties properties) {
        String platforms = properties.getProperty("datasource.os", "").trim();
        if (platforms.length() == 0) return true;
        for (String platform : platforms.split(",")) {
            if (platform.trim().length() > 0 && isPlatform(platform.trim())) return true;
        }
        return false;
    }

    private ExternalDataSource dataSource(
            Class<?> dataSourceClass,
            Map<String, String> loggerPluginPorts,
            Properties pluginProps) throws Exception {

        ExternalDataSource dataSource = (ExternalDataSource) dataSourceClass
                .getDeclaredConstructor().newInstance();
        if (loggerPluginPorts != null) {
            String port = loggerPluginPorts.get(dataSource.getId());
            if (port != null && port.trim().length() > 0) dataSource.setPort(port);
        }
        dataSource.setProperties(pluginProps);
        return dataSource;
    }

    public List<ExternalDataSource> getExternalDataSources() {
        return externalDataSources;
    }
}
