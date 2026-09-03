/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2014 RomRaider.com
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

package com.romraider.logger.ecu.ui.swing.menubar.action;

import static com.romraider.logger.ecu.ui.swing.menubar.util.FileHelper.getDefinitionFileChooser;
import static com.romraider.logger.ecu.ui.swing.menubar.util.FileHelper.getFile;
import static javax.swing.JFileChooser.APPROVE_OPTION;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.WARNING_MESSAGE;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.MessageFormat;
import java.util.concurrent.ExecutionException;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.romraider.logger.ecu.EcuLogger;
import com.romraider.logger.ecu.definition.EcuDataLoader;
import com.romraider.logger.ecu.definition.EcuDataLoaderImpl;
import com.romraider.logger.ecu.definition.LoggerDefinitionInstaller;
import com.romraider.logger.ecu.definition.LoggerDefinitionInstaller.Installation;
import com.romraider.swing.IntegratedOptionDialog;
import com.romraider.swing.menubar.action.AbstractAction;
import com.romraider.util.SettingsManager;

public final class InstallLoggerDefinitionAction extends AbstractAction {
    private boolean working;

    public InstallLoggerDefinitionAction(EcuLogger logger) {
        super(logger);
    }

    public void actionPerformed(ActionEvent actionEvent) {
        if (working) return;
        // Let the Help menu close and repaint before file-system discovery
        // starts inside the platform file chooser.
        SwingUtilities.invokeLater(this::chooseDefinition);
    }

    private void chooseDefinition() {
        if (working) return;
        File current = getFile(
                logger.getSettings().getLoggerDefinitionFilePath());
        JFileChooser chooser = getDefinitionFileChooser(current);
        if (chooser.showOpenDialog(logger) != APPROVE_OPTION) return;

        File source = chooser.getSelectedFile();
        Object[] options = {rb.getString("LDAINSTALL"),
                rb.getString("LDACANCEL")};
        int answer = IntegratedOptionDialog.show(logger,
                MessageFormat.format(rb.getString("LDAINSTALLPROMPT"),
                        source.getAbsolutePath()),
                rb.getString("LDAINSTALLTITLE"), WARNING_MESSAGE,
                options, options[0]);
        if (answer != 0) return;

        String previousPath = logger.getSettings()
                .getLoggerDefinitionFilePath();
        String previousProtocol = logger.getSettings().getLoggerProtocol();
        String previousTransport = logger.getSettings().getTransportProtocol();
        working = true;
        setEnabled(false);
        logger.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        logger.reportMessage("Installing and validating Logger definitions…");

        new SwingWorker<PreparedDefinition, Void>() {
            private Installation installation;

            @Override
            protected PreparedDefinition doInBackground() throws Exception {
                installation = new LoggerDefinitionInstaller().install(
                        source.toPath(), SettingsManager.getSettingsDirectory());
                String installedPath = installation.installedFile()
                        .toAbsolutePath().toString();
                EcuDataLoader loaded = new EcuDataLoaderImpl();
                loaded.loadConfigFromXml(installedPath,
                        logger.getSettings().getLoggerProtocol(),
                        logger.getSettings().getFileLoggingControllerSwitchId(),
                        null);
                return new PreparedDefinition(installation, installedPath,
                        loaded);
            }

            @Override
            protected void done() {
                try {
                    activate(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    fail(exception, installation, previousPath,
                            previousProtocol, previousTransport);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    Exception failure = cause instanceof Exception
                            ? (Exception) cause : exception;
                    fail(failure, installation, previousPath,
                            previousProtocol, previousTransport);
                } catch (Exception exception) {
                    fail(exception, installation, previousPath,
                            previousProtocol, previousTransport);
                } finally {
                    working = false;
                    setEnabled(true);
                    logger.setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }

    private void activate(PreparedDefinition prepared) throws Exception {
        String installedPath = prepared.installedPath;
        logger.getSettings().setLoggerDefinitionFilePath(installedPath);
        SettingsManager.save(logger.getSettings());
        logger.loadLoggerParams(prepared.loaded);
        logger.reportMessage(MessageFormat.format(
                rb.getString("LDASUCCESS"),
                prepared.installation.installedFile().getFileName()));
        Object[] complete = {rb.getString("LDAOK")};
        IntegratedOptionDialog.show(logger,
                MessageFormat.format(rb.getString("LDASUCCESSDIALOG"),
                        prepared.installation.version(), installedPath),
                rb.getString("LDASUCCESSTITLE"), INFORMATION_MESSAGE,
                complete, complete[0]);
    }

    private void fail(Exception exception, Installation installation,
            String previousPath, String previousProtocol,
            String previousTransport) {
        logger.getSettings().setLoggerDefinitionFilePath(previousPath);
        logger.getSettings().setLoggerProtocol(previousProtocol);
        logger.getSettings().setTransportProtocol(previousTransport);
        if (installation != null) {
            try {
                installation.rollback();
            } catch (Exception rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
        }
        try {
            SettingsManager.save(logger.getSettings());
        } catch (Exception rollbackFailure) {
            exception.addSuppressed(rollbackFailure);
        }
        logger.reportError(rb.getString("LDAERROR"), exception);
        Object[] close = {rb.getString("LDAOK")};
        IntegratedOptionDialog.show(logger,
                MessageFormat.format(rb.getString("LDAERRORDIALOG"),
                        safeMessage(exception)),
                rb.getString("LDAERRORTITLE"), ERROR_MESSAGE,
                close, close[0]);
    }

    private static final class PreparedDefinition {
        private final Installation installation;
        private final String installedPath;
        private final EcuDataLoader loaded;

        private PreparedDefinition(Installation installation,
                String installedPath, EcuDataLoader loaded) {
            this.installation = installation;
            this.installedPath = installedPath;
            this.loaded = loaded;
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName() : message;
    }
}
