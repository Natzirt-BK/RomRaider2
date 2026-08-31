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

package com.romraider.swing;

import static com.romraider.Version.PRODUCT_NAME;

import com.romraider.diagnostics.PrivacySafeDiagnostics;
import com.romraider.net.URL;
import com.romraider.util.ResourceUtil;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.text.MessageFormat;
import java.util.ResourceBundle;

public class DebugPanel extends JPanel {

    private static final long serialVersionUID = -7159385694793030962L;
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            DebugPanel.class.getName());

    public DebugPanel(Exception ex, String url) {
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new GridLayout(5, 1));
        top.add(new JLabel(MessageFormat.format(
                rb.getString("LABEL1"),
                PRODUCT_NAME)));
        top.add(new JLabel(rb.getString("LABEL2")));
        top.add(new JLabel(rb.getString("LABEL3")));
        top.add(new URL(url));
        top.add(new JLabel(rb.getString("LABEL4")));
        add(top, BorderLayout.NORTH);

        final String report = PrivacySafeDiagnostics.buildReport(ex);
        JTextArea output = new JTextArea(report);
        add(new JScrollPane(output), BorderLayout.CENTER);
        output.setAutoscrolls(true);
        output.setRows(14);
        output.setColumns(72);
        output.setEditable(false);
        output.setCaretPosition(0);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        JButton save = new JButton(rb.getString("SAVE"));
        save.addActionListener(event -> saveReport(report));
        actions.add(save);
        JButton copy = new JButton(rb.getString("COPY"));
        copy.addActionListener(event -> Toolkit.getDefaultToolkit()
                .getSystemClipboard().setContents(new StringSelection(report), null));
        actions.add(copy);
        add(actions, BorderLayout.SOUTH);
    }

    private void saveReport(String report) {
        JFileChooser chooser = new IntegratedFileChooser();
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        chooser.setSelectedFile(new java.io.File(
                "RomRaider2-diagnostic-" + timestamp + ".txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path destination = chooser.getSelectedFile().toPath();
        try {
            Files.write(destination, report.getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(this,
                    MessageFormat.format(rb.getString("SAVED"), destination),
                    rb.getString("SAVE"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException failure) {
            JOptionPane.showMessageDialog(this, rb.getString("SAVEFAILED"),
                    rb.getString("SAVE"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
