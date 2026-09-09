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
import com.romraider.logger.ecu.DesktopDefinitionInstall;
import com.romraider.logger.ecu.DesktopDefinitionInstall.Candidate;
import com.romraider.logger.ecu.definition.LoggerDefinitionInstaller.Installation;
import com.romraider.swing.IntegratedOptionDialog;
import com.romraider.swing.menubar.action.AbstractAction;

public final class InstallLoggerDefinitionAction extends AbstractAction {
    private boolean working;

    public InstallLoggerDefinitionAction(EcuLogger logger) {
        super(logger);
    }

    public void actionPerformed(ActionEvent actionEvent) {
        if (working) return;
        working = true;
        // Let the Help menu close and repaint before file-system discovery
        // starts inside the platform file chooser.
        SwingUtilities.invokeLater(() -> {
            boolean started = false;
            try {
                started = chooseDefinition();
            } catch (Exception exception) {
                fail(exception);
            } finally {
                if (!started) finish();
            }
        });
    }

    private boolean chooseDefinition() {
        DesktopDefinitionInstall request = logger.beginDefinitionInstall();
        if (request == null) {
            if (logger.isDefinitionInstallOwnerOpen())
                logger.reportMessage("Disconnect the Logger before installing definitions.");
            return false;
        }
        File current = getFile(
                logger.getSettings().getLoggerDefinitionFilePath());
        JFileChooser chooser = getDefinitionFileChooser(current);
        if (chooser.showOpenDialog(logger) != APPROVE_OPTION) return false;
        if (!request.isCurrent()) { obsolete(); return false; }

        File source = chooser.getSelectedFile();
        Object[] options = {rb.getString("LDAINSTALL"),
                rb.getString("LDACANCEL")};
        int answer = IntegratedOptionDialog.show(logger,
                MessageFormat.format(rb.getString("LDAINSTALLPROMPT"),
                        source.getAbsolutePath()),
                rb.getString("LDAINSTALLTITLE"), WARNING_MESSAGE,
                options, options[0]);
        if (answer != 0) return false;
        if (!request.isCurrent()) { obsolete(); return false; }
        setEnabled(false);
        logger.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        logger.reportMessage("Validating Logger definitions…");

        new SwingWorker<Candidate, Void>() {
            @Override
            protected Candidate doInBackground() throws Exception {
                return request.prepare(source.toPath());
            }

            @Override
            protected void done() {
                try {
                    if (!request.isCurrent()) { obsolete(); return; }
                    Candidate candidate = get();
                    Installation installation = request.commit(candidate);
                    if (installation == null) { obsolete(); return; }
                    // Installation is now durable. A later catalog/UI failure must
                    // not roll back files or settings adopted by another action.
                    if (logger.activateDefinitionInstall(request, candidate)) {
                        showSuccess(installation);
                    } else if (request.canActivate(candidate)) {
                        logger.reportMessage("Definition installed; channel loading did not complete. Reload the Logger definitions.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    fail(exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    Exception failure = cause instanceof Exception
                            ? (Exception) cause : exception;
                    fail(failure);
                } catch (Exception exception) {
                    fail(exception);
                } finally {
                    finish();
                }
            }
        }.execute();
        return true;
    }

    private void showSuccess(Installation installation) {
        if (!logger.isDefinitionInstallOwnerOpen()) return;
        logger.reportMessage(MessageFormat.format(
                rb.getString("LDASUCCESS"),
                installation.installedFile().getFileName()));
        Object[] complete = {rb.getString("LDAOK")};
        IntegratedOptionDialog.show(logger,
                MessageFormat.format(rb.getString("LDASUCCESSDIALOG"),
                        installation.version(), installation.installedFile().toString()),
                rb.getString("LDASUCCESSTITLE"), INFORMATION_MESSAGE,
                complete, complete[0]);
    }

    private void fail(Exception exception) {
        if (!logger.isDefinitionInstallOwnerOpen()) return;
        logger.reportError(rb.getString("LDAERROR"), exception);
        Object[] close = {rb.getString("LDAOK")};
        IntegratedOptionDialog.show(logger,
                MessageFormat.format(rb.getString("LDAERRORDIALOG"),
                        safeMessage(exception)),
                rb.getString("LDAERRORTITLE"), ERROR_MESSAGE,
                close, close[0]);
    }

    private void obsolete() {
        if (logger.isDefinitionInstallOwnerOpen())
            logger.reportMessage("Definition installation cancelled because the Logger setup changed. Select the definition again.");
    }

    private void finish() {
        working = false;
        if (!logger.isDefinitionInstallOwnerOpen()) return;
        setEnabled(true);
        logger.setCursor(Cursor.getDefaultCursor());
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName() : message;
    }
}
