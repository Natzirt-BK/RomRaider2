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

package com.romraider.logger.ecu.comms.readcodes;

import static com.romraider.logger.ecu.comms.io.connection.LoggerConnectionFactory.getConnection;
import static com.romraider.util.ParamChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.*;

import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.definition.Module;
import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.logger.ecu.EcuLogger;
import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.definition.EcuSwitch;
import com.romraider.logger.ecu.ui.MessageListener;
import com.romraider.logger.ecu.ui.swing.tools.ReadCodesResultsPanel;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

public final class ReadCodesManagerImpl implements ReadCodesManager {
    private static final Logger LOGGER =
            Logger.getLogger(ReadCodesManagerImpl.class);
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            ReadCodesManagerImpl.class.getName());
    private final MessageListener messageListener;
    private final EcuLogger logger;
    private final List<EcuSwitch> dtcodes;
    private final int ecuInitLength;

    public ReadCodesManagerImpl(EcuLogger logger,
            List<EcuSwitch> dtcodes,
            int ecuInitLength) {
        checkNotNull(logger, dtcodes);
        this.logger = logger;
        this.messageListener = logger;
        this.dtcodes = new ArrayList<>(dtcodes);
        this.ecuInitLength = ecuInitLength;
    }

    @Override
    public final int readCodes() {
        final Settings settings = SettingsManager.getSettings();
        final Module module = settings.getDestinationTarget();
        final String target = module.getName().toUpperCase(Locale.ROOT);
        try {
            final DtcReadPlan plan = DtcReadPlan.prepare(dtcodes, ecuInitLength);
            final DmRuntimeReadRequest dmRequest = logger.captureDmRuntimeRead();
            dmRequest.requireCurrent();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("DTC read cancelled");
            final LoggerConnection connection = getConnection(
                    settings.getLoggerProtocol(),
                    settings.getLoggerPort(),
                    settings.getLoggerConnectionProperties());
            try {
                // Identify before using any cached dynamic address. A cache miss
                // stays a standard-code read, never a discovery request.
                final DmInit dmRuntime = dmRequest.read(connection, module);
                messageListener.reportMessage(MessageFormat.format(
                        rb.getString("READCODES"), target));
                final ArrayList<EcuQuery> dtcSet = plan.read(connection, module, dmRequest::requireCurrent);

                dmRequest.requireCurrent();
                final int[] dmCodes = dmRuntime == null ? new int[0] : dmRuntime.getRuntimeCurrentErrors();
                final int[] dmMemCodes = dmRuntime == null ? new int[0] : dmRuntime.getRuntimeMemErrors();
                messageListener.reportMessage(MessageFormat.format(rb.getString("COMPLETE"), target));

                if (dtcSet.isEmpty() && (dmCodes.length == 0 || dmCodes[0] == 0)
                        && (dmMemCodes.length == 0 || dmMemCodes[0] == 0)) {
                    LOGGER.info("Success reading " + target +
                            " DTC codes, none set");
                    return -1;
                }
                else {
                    if (dmRuntime != null) {
                        Set<String> dmCodesStr = dmRuntime.decodeDmCurrentErrors();
                        Set<String> dmMemCodesStr = dmRuntime.decodeDmMemorizedErrors();
                        ReadCodesResultsPanel.displayResultsPane(logger, dtcSet, dmCodesStr, dmMemCodesStr);
                    } else {
                        ReadCodesResultsPanel.displayResultsPane(logger, dtcSet);
                    }
                }
                return 1;
            }
            finally {
                connection.close();
            }
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            messageListener.reportMessage(MessageFormat.format(
                    rb.getString("FAILED"), target));
            LOGGER.error("Error reading " + target + " DTC codes", e);

            return 0;
        }
    }

}
