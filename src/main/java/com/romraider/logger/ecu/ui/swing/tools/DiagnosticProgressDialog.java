/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.ui.swing.tools;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

/** Cancel stays visible until the worker has relinquished its connection. */
public final class DiagnosticProgressDialog extends JDialog {
    public DiagnosticProgressDialog(Window owner, String target, Runnable cancel) {
        super(owner, "Read " + target + " Codes", Dialog.ModalityType.DOCUMENT_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JLabel status = new JLabel("Pausing logging and reading trouble codes…");
        JButton cancelButton = new JButton("Cancel Read");
        Runnable requestCancel = () -> {
            if (!cancelButton.isEnabled()) return;
            cancelButton.setEnabled(false);
            status.setText("Cancelling… waiting for the adapter to finish.");
            cancel.run();
        };
        cancelButton.addActionListener(event -> requestCancel.run());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { requestCancel.run(); }
        });
        JPanel contents = new JPanel(new BorderLayout(12, 12));
        contents.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        contents.add(status, BorderLayout.NORTH);
        contents.add(progress, BorderLayout.CENTER);
        contents.add(cancelButton, BorderLayout.SOUTH);
        setContentPane(contents);
        pack();
        setMinimumSize(new java.awt.Dimension(430, getHeight()));
        setSize(getMinimumSize());
        setLocationRelativeTo(owner);
    }
}
