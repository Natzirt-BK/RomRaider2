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

package com.romraider.logger.ecu.ui.swing.tools;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import com.romraider.logger.ecu.EcuLogger;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.readcodes.DiagnosticReportCsv;
import com.romraider.logger.ecu.ui.swing.tools.tablemodels.DmReadCodesTableModel;
import com.romraider.logger.ecu.ui.swing.tools.tablemodels.ReadCodesTableModel;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

public final class ReadCodesResultsPanel extends JPanel {
    private static final long serialVersionUID = -3180488605471088911L;
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            ReadCodesResultsPanel.class.getName());
    private final JPanel resultsPanel = new JPanel();
    private static final String DT_FORMAT = "%1$tY%1$tm%1$td-%1$tH%1$tM%1$tS-%1$tL";

    private ReadCodesResultsPanel(ArrayList<EcuQuery> dtcSet) {
        super(new GridLayout(1,0));

        final ReadCodesTableModel dtcModel = new ReadCodesTableModel();
        dtcModel.setDtcList(dtcSet);
        final JTable table = new JTable(dtcModel);

        TableColumn column = null;
        for (int i = 0; i < table.getColumnCount(); i++) {
            column = table.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(360);
            } else {
                column.setPreferredWidth(80);
            }
        }
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(0);
        table.setColumnSelectionAllowed(false);
        table.setRowSelectionAllowed(true);
        table.getTableHeader().setReorderingAllowed(false);

        resultsPanel.setLayout(new BorderLayout());
        final JTableHeader th = table.getTableHeader();
        final Font thFont = th.getFont();
        final Font thBoldFont = new Font(
                thFont.getFamily(),
                Font.BOLD,
                thFont.getSize());
        th.setFont(thBoldFont);
        resultsPanel.add(th, BorderLayout.PAGE_START);
        resultsPanel.add(table, BorderLayout.CENTER);
        add(resultsPanel);
    }

    private ReadCodesResultsPanel(Set<String> dtcSet, Set<String> dtcMemSet) {
        super(new GridLayout(1,0));

        final DmReadCodesTableModel dtcModel = new DmReadCodesTableModel(dtcSet, dtcMemSet);
        final JTable table = new JTable(dtcModel);

        TableColumn column = null;
        for (int i = 0; i < table.getColumnCount(); i++) {
            column = table.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(360);
            } else {
                column.setPreferredWidth(80);
            }
        }
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(0);
        table.setColumnSelectionAllowed(false);
        table.setRowSelectionAllowed(true);
        table.getTableHeader().setReorderingAllowed(false);

        resultsPanel.setLayout(new BorderLayout());
        final JTableHeader th = table.getTableHeader();
        final Font thFont = th.getFont();
        final Font thBoldFont = new Font(
                thFont.getFamily(),
                Font.BOLD,
                thFont.getSize());
        th.setFont(thBoldFont);
        resultsPanel.add(th, BorderLayout.PAGE_START);
        resultsPanel.add(table, BorderLayout.CENTER);
        add(resultsPanel);
    }

    public final static void displayResultsPane(
            EcuLogger logger,
            ArrayList<EcuQuery> dtcSet,
            Set<String> dmCurrentCodes, Set<String> dmMemCodes) {
        final JDialog frame = new JDialog(logger, rb.getString("READRESULTS"), true);
        frame.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        final JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
        mainPanel.setOpaque(true);
        final JPanel tables = new JPanel();
        tables.setLayout(new BoxLayout(tables, BoxLayout.PAGE_AXIS));
        ReadCodesResultsPanel resultsPane = null;
        ReadCodesResultsPanel dmResultsPane = null;
        if (dtcSet.size() > 0) {
            resultsPane =
                    new ReadCodesResultsPanel(dtcSet);
            tables.add(resultsPane);
        }
        if ((dmCurrentCodes != null && dmCurrentCodes.size() > 0) || (dmMemCodes != null && dmMemCodes.size() > 0)) {
            if (dtcSet.size() > 0) {
                JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
                tables.add(sep);
            }
            JLabel dmLabel = new JLabel("DimeMod errors:", SwingConstants.CENTER);
            tables.add(dmLabel);
            dmResultsPane =
                    new ReadCodesResultsPanel(dmCurrentCodes == null ? Set.of() : dmCurrentCodes,
                            dmMemCodes == null ? Set.of() : dmMemCodes);
            tables.add(dmResultsPane);
        }
        mainPanel.add(tables);
        String csv = DiagnosticReportCsv.format(dtcSet, dmCurrentCodes, dmMemCodes,
                rb.getString("TABLEHEADER"), rb.getString("TRUE"), rb.getString("FALSE"), System.lineSeparator());
        mainPanel.add(createSaveResultsPanel(csv, tables));
        frame.setContentPane(mainPanel);
        final Point loggerLocation = logger.getLocation();
        final Point dialogLocation = new Point();
        dialogLocation.setLocation(
                loggerLocation.getX() + 30,
                loggerLocation.getY() + 90);
        frame.setLocation(dialogLocation);
        frame.setIconImage(logger.getIconImage());
        frame.pack();
        frame.setVisible(true);
    }

    public final static void displayResultsPane(
            EcuLogger logger,
            ArrayList<EcuQuery> dtcSet) {
        displayResultsPane(logger, dtcSet, null, null);
    }

    private static final JPanel createSaveResultsPanel(final String csv, JPanel pane) {

        final JPanel basePanel = new JPanel(new BorderLayout());
        basePanel.setBorder(BorderFactory.createTitledBorder(
                rb.getString("SAVETITLE")));

        final JLabel comment = new JLabel();
        comment.setText(rb.getString("COPYTIP"));

        final JPanel controlPanel = new JPanel();
        final JButton toFile = new JButton(rb.getString("SAVETOFILE"));
        toFile.setToolTipText(rb.getString("SAVETOFILETT"));
        toFile.setMnemonic(KeyEvent.VK_F);
        toFile.addActionListener(new ActionListener() {
            @Override
            public final void actionPerformed(ActionEvent actionEvent) {
                saveTableText(csv);
            }
        });
        final JButton toImage = new JButton(rb.getString("SAVETOIMAGE"));
        toImage.setToolTipText(rb.getString("SAVETOIMAGETT"));
        toImage.setMnemonic(KeyEvent.VK_I);
        toImage.addActionListener(new ActionListener() {
            @Override
            public final void actionPerformed(ActionEvent actionEvent) {
                saveTableImage(pane);
            }
        });
        controlPanel.add(toFile);
        controlPanel.add(toImage);
        basePanel.add(comment, BorderLayout.CENTER);
        basePanel.add(controlPanel, BorderLayout.SOUTH);
        return basePanel;
    }

    private static final void saveTableText(String csv) {
        final String nowStr = String.format(DT_FORMAT, System.currentTimeMillis());
        final String fileName = String.format("%s%sromraiderDTC_%s.csv",
                SettingsManager.getSettings().getLoggerOutputDirPath(),
                File.separator,
                nowStr);
        try {
            final File csvFile = new File(fileName);
            DiagnosticReportCsv.writeNew(csvFile.toPath(), csv);
            showMessageDialog(
                    null,
                    MessageFormat.format(
                            rb.getString("TABLESAVED"), fileName),
                    rb.getString("SUCCESS"),
                    INFORMATION_MESSAGE);
        }
        catch (Exception e) {
            showMessageDialog(
                    null,
                    MessageFormat.format(
                            rb.getString("TABLEFAILED"), fileName),
                    rb.getString("FAILED"),
                    ERROR_MESSAGE);
        }
    }

    private final static void saveTableImage(JPanel resultsPanel) {
        final BufferedImage resultsImage = new BufferedImage(
                resultsPanel.getWidth(),
                resultsPanel.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resultsImage.createGraphics();
        try { resultsPanel.paint(graphics); }
        finally { graphics.dispose(); }
        final String nowStr = String.format(DT_FORMAT, System.currentTimeMillis());
        final String fileName = String.format("%s%sromraiderDTC_%s.png",
                SettingsManager.getSettings().getLoggerOutputDirPath(),
                File.separator,
                nowStr);
        try {
            final File imageFile = new File(fileName);
            try (var output = java.nio.file.Files.newOutputStream(imageFile.toPath(),
                    java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
                if (!ImageIO.write(resultsImage, "png", output)) throw new java.io.IOException("PNG writer unavailable");
            }
            showMessageDialog(
                    null,
                    MessageFormat.format(
                            rb.getString("IMAGESAVED"), fileName),
                    rb.getString("SUCCESS"),
                    INFORMATION_MESSAGE);
        }
        catch (Exception e) {
            showMessageDialog(
                    null,
                    MessageFormat.format(
                            rb.getString("IMAGEFAILED"), fileName),
                    rb.getString("FAILED"),
                    ERROR_MESSAGE);
        }
    }
}
