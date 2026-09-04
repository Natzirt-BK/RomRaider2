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

package com.romraider;

import static com.romraider.Version.BUILDNUMBER;
import static com.romraider.Version.PRODUCT_NAME;
import static com.romraider.Version.SUPPORT_URL;
import static com.romraider.Version.VERSION;
import static com.romraider.util.LogManager.initDebugLogging;
import static com.romraider.EditorLoggerCommunication.*;
import static org.apache.log4j.Logger.getLogger;

import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.romraider.desktop.DesktopApplicationCommands;
import com.romraider.desktop.DesktopApplicationLoader;
import com.romraider.diagnostics.PrivacySafeDiagnostics;
import com.romraider.platform.PlatformCapabilities;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.PlatformRegistry;
import com.romraider.ui.ApplicationThemeService;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

public class ECUExec {
    private static final Logger LOGGER = getLogger(ECUExec.class);
    private static final String START_LOGGER_ARG = "-logger";
    private static final String START_LOGGER_FULLSCREEN_ARG = "-logger.fullscreen";
    private static final String LOGGER_TOUCH_ARG = "-logger.touch";
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            ECUExec.class.getName());

    private ECUExec() {
        throw new UnsupportedOperationException();
    }

    public static void main(String args[]) {
        // init i18n resources
        if (rb == null) return;
        // init debug logging
        initDebugLogging();
        // Record a small, share-safe runtime summary for debugging/support.
        LOGGER.info(PRODUCT_NAME + " " + VERSION + " Build: " + BUILDNUMBER);
        LOGGER.info(MessageFormat.format(rb.getString("SUPPORT"), SUPPORT_URL));
        LOGGER.info(DateFormat.getDateTimeInstance(
                DateFormat.FULL,
                DateFormat.LONG).format(System.currentTimeMillis()));
        LOGGER.info(PrivacySafeDiagnostics.buildRuntimeSummary());

        initializePlatformContext();
        setExecType(args);

        // check if already running
        if (isRunning()) {
        	// The other executable will open us, close this app
        	EditorLoggerCommunication.sendTypeToOtherExec(args);
        } else {
            startExecCommunicationDaemon();
            if (DesktopApplicationLoader.launch(args)) {
                return;
            }
            if (legacySwingRequested()) {
                LegacySwingApplication.launch(args);
            } else {
                LOGGER.error("JavaFX desktop shell is unavailable; "
                        + "use -Dromraider2.desktop.shell=swing only for "
                        + "legacy compatibility testing");
            }
        }
    }

    private static void startExecCommunicationDaemon() {
        Thread listener = new Thread(ECUExec::startExecCommunication,
                "RomRaider single-instance listener");
        listener.setDaemon(true);
        listener.start();
    }

    private static void initializePlatformContext() {
        Settings settings = SettingsManager.getSettings();
        ApplicationThemeService.getInstance().apply(settings.getThemeMode());
        PlatformContext context = PlatformContext.getInstance();
        context.setPlatform(settings.getVehiclePlatform());
        PlatformCapabilities capabilities = PlatformRegistry.get(
                context.getPlatform());
        if (capabilities.supports(settings.getVehicleModule())) {
            context.setModule(settings.getVehicleModule());
        }
    }

    private static void setExecType(String[] args) {
    	Exec_type execType = containsLoggerArg(args) ? Exec_type.LOGGER : Exec_type.EDITOR;
    	EditorLoggerCommunication.setExectable(execType, args);
    }

    static boolean containsLoggerArg(String[] args) {
        for (String arg : args) {
            if (	arg.equalsIgnoreCase(START_LOGGER_ARG) ||
            		arg.equalsIgnoreCase(START_LOGGER_FULLSCREEN_ARG) ||
            		arg.equalsIgnoreCase(LOGGER_TOUCH_ARG)) {
                return true;
            }
        }
        return false;
    }

    private static void startExecCommunication() {
        while (true) {
            try {
                ExecutableInstance instance =
                        EditorLoggerCommunication.waitForOtherExec();

                if (DesktopApplicationCommands.dispatch(
                        instance.currentArgs)) {
                    LOGGER.info("Forwarded request to active desktop shell");
                    continue;
                }
                if (legacySwingRequested()) {
                    LegacySwingApplication.handleForwarded(instance);
                } else {
                    LOGGER.warn("No active desktop window accepted the "
                            + "forwarded request");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static boolean legacySwingRequested() {
        return "swing".equalsIgnoreCase(System.getProperty(
                "romraider2.desktop.shell", "javafx"));
    }
}
