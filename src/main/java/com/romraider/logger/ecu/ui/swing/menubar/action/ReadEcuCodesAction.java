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

package com.romraider.logger.ecu.ui.swing.menubar.action;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.OK_OPTION;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.YES_NO_OPTION;
import static javax.swing.JOptionPane.showConfirmDialog;
import static javax.swing.JOptionPane.showMessageDialog;

import java.awt.event.ActionEvent;
import java.text.MessageFormat;

import com.romraider.logger.ecu.EcuLogger;
import com.romraider.swing.menubar.action.AbstractAction;
import com.romraider.logger.ecu.DesktopDiagnosticTask;
import com.romraider.logger.ecu.comms.readcodes.DiagnosticReadRequest;
import com.romraider.logger.ecu.ui.swing.tools.DiagnosticProgressDialog;
import com.romraider.logger.ecu.ui.swing.tools.ReadCodesResultsPanel;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;

public final class ReadEcuCodesAction extends AbstractAction {
    private boolean working;
    public ReadEcuCodesAction(EcuLogger logger) {
        super(logger);
    }

    public final void actionPerformed(ActionEvent actionEvent) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> actionPerformed(actionEvent));
            return;
        }
        if (working || logger.isDiagnosticBusy() || !logger.isDiagnosticOwnerOpen()) return;
        if (logger.getDtcodesEmpty()) {
            showMessageDialog(logger,
                    rb.getString("RECADEFERROR"),
                    rb.getString("RECADEFERRORTITLE"), ERROR_MESSAGE);
        }
        else if (!logger.isEcuInit()) {
            showMessageDialog(logger,MessageFormat.format(
                            rb.getString("RECANOINIT"), logger.getTarget()),
                    rb.getString("RECANOINITTITLE"), ERROR_MESSAGE);
        }
        else {
            working = true;
            setEnabled(false);
            DiagnosticProgressDialog progress = null;
            AtomicReference<DesktopDiagnosticTask<?>> task = new AtomicReference<>();
            try {
                DiagnosticReadRequest request = logger.prepareDiagnosticRead();
                boolean logging = logger.isLogging();
                JCheckBox resume = new JCheckBox("Resume logging after a successful read", logging);
                resume.setEnabled(logging);
                int confirmed = showConfirmDialog(logger, new Object[] {
                        "Read trouble codes from " + request.target() + "?",
                        "Logging pauses for this read. Cancelling or a failed read will not restart it.", resume},
                        "Read Trouble Codes", YES_NO_OPTION, WARNING_MESSAGE);
                request.requireCurrent();
                if (confirmed != OK_OPTION) { finish(); return; }
                boolean resumeAfterSuccess = resume.isSelected();
                progress = new DiagnosticProgressDialog(logger, request.target(), () -> {
                    if (task.get() != null) task.get().cancel();
                });
                DiagnosticProgressDialog dialog = progress;
                task.set(logger.beginDiagnosticRead(request, result -> {
                    dialog.dispose();
                    finish();
                    if (!logger.isDiagnosticOwnerOpen() || result.stale()) return;
                    if (result.cancelled()) {
                        logger.reportMessage("Diagnostic read cancelled. Logging was not restarted.");
                        return;
                    }
                    if (!result.succeeded()) {
                        logger.reportError("Unable to read trouble codes. Logging was not restarted.",
                                new Exception("Diagnostic read failed", result.failure()));
                        return;
                    }
                    try {
                        request.requireCurrent();
                        showResult(result.result());
                        request.requireCurrent();
                        if (resumeAfterSuccess && logger.isDiagnosticOwnerOpen() && !logger.isLogging()) logger.startLogging();
                    } catch (IllegalStateException stale) {
                        // A result dialog pumps events; never restart a changed/closed owner.
                    }
                }));
                dialog.setVisible(true);
            } catch (Exception failure) {
                if (task.get() != null) task.get().cancel();
                if (progress != null) progress.dispose();
                finish();
                if (logger.isDiagnosticOwnerOpen()) logger.reportError("Unable to start the diagnostic read.", failure);
            }
        }
    }

    private void finish() {
        working = false;
        if (logger.isDiagnosticOwnerOpen()) setEnabled(true);
    }

    private void showResult(DiagnosticReadRequest.Result result) {
        if (result.isEmpty()) showMessageDialog(logger,
                "No active or memorized codes found in the available entries for this logger definition.",
                "Read Complete", INFORMATION_MESSAGE);
        else ReadCodesResultsPanel.displayResultsPane(logger, new ArrayList<>(result.codes()),
                result.currentDimeCodes(), result.memorizedDimeCodes());
    }
}
