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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Scanner;
import java.util.Vector;

import javax.swing.*;

import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.ui.BrandImages;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ModernIconFactory.Action;
import com.romraider.ui.TouchTargetService;
import com.romraider.util.CustomizationFiles;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;
import com.romraider.xml.ConversionLayer.ConversionLayer;
import com.romraider.xml.ConversionLayer.ConversionLayerFactory;

public class DefinitionManager extends AbstractFrame implements ActionListener {

    private static final long serialVersionUID = -3920843496218196737L;
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            DefinitionManager.class.getName());
    private static final Logger LOGGER = Logger.getLogger(
            DefinitionManager.class);
    public static int MOVE_UP = 0;
    public static int MOVE_DOWN = 1;
    private final Properties props = loadSequences();

    Vector<String> fileNames;
    private final Vector<String> visibleFileNames = new Vector<String>();

    public DefinitionManager() {
        initComponents();
        BrandImages.apply(this);
        initSettings();

        definitionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        definitionList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateSelectionState();
        });
        searchField.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                updateListModel();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                updateListModel();
            }
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                updateListModel();
            }
        });

        btnCancel.addActionListener(this);
        btnSave.addActionListener(this);
        btnAddDefinition.addActionListener(this);
        btnRemoveDefinition.addActionListener(this);
        btnMoveUp.addActionListener(this);
        btnMoveDown.addActionListener(this);
        btnApply.addActionListener(this);
        btnUndo.addActionListener(this);
        btnMoveTop.addActionListener(this);
        btnMoveBottom.addActionListener(this);
        btnOpenFolder.addActionListener(this);
        updateSelectionState();
    }

    private void initSettings() {
        // add definitions to list
        Vector<File> definitionFiles = SettingsManager.getSettings().getEcuDefinitionFiles();
        fileNames = new Vector<String>();

        for (int i = 0; i < definitionFiles.size(); i++) {
            fileNames.add(definitionFiles.get(i).getAbsolutePath());
        }

        updateListModel();
    }

    private void initComponents() {
        jScrollPane1 = new javax.swing.JScrollPane();
        definitionList = new javax.swing.JList<String>();
        defLabel = new javax.swing.JLabel();
        btnMoveUp = new javax.swing.JButton();
        btnMoveDown = new javax.swing.JButton();
        btnAddDefinition = new javax.swing.JButton();
        btnRemoveDefinition = new javax.swing.JButton();
        btnSave = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();
        btnApply = new javax.swing.JButton();
        btnUndo = new javax.swing.JButton();
        btnMoveTop = new javax.swing.JButton("⇈ Top");
        btnMoveBottom = new javax.swing.JButton("⇊ Bottom");
        btnOpenFolder = new javax.swing.JButton("Open Definitions Folder");
        searchField = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Definition File Manager");
        setJMenuBar(new JMenuBar());
        setMinimumSize(new Dimension(760, 520));
        jScrollPane1.setViewportView(definitionList);
        definitionList.setName("DEFINITION PRIORITY LIST");
        definitionList.setFixedCellHeight(48);
        definitionList.setCellRenderer(new DefinitionPathRenderer());

        defLabel.setText("Definition File Priority");
        defLabel.setFont(defLabel.getFont().deriveFont(Font.BOLD, 18f));

        btnMoveUp.setText("↑ " + rb.getString("MOVEUP"));

        btnMoveDown.setText("↓ " + rb.getString("MOVEDOWN"));

        btnAddDefinition.setText(rb.getString("ADD"));
        btnAddDefinition.setIcon(ModernIconFactory.icon(Action.OPEN));

        btnRemoveDefinition.setText(rb.getString("REMOVE"));
        btnRemoveDefinition.setIcon(ModernIconFactory.icon(Action.CLOSE));

        btnSave.setText(rb.getString("SAVE"));
        btnSave.setIcon(ModernIconFactory.icon(Action.SAVE));

        btnCancel.setText(rb.getString("CANCEL"));

        btnApply.setText(rb.getString("APPLY"));

        btnUndo.setText(rb.getString("UNDO"));
        btnUndo.setIcon(ModernIconFactory.icon(Action.UNDO));
        btnMoveTop.setToolTipText("Move selected definition to highest priority");
        btnMoveBottom.setToolTipText("Move selected definition to lowest priority");
        btnOpenFolder.setIcon(ModernIconFactory.icon(Action.CATEGORY));
        searchField.setName("DEFINITION SEARCH");
        searchField.setToolTipText("Filter definitions by name or path");

        JPanel heading = new JPanel(new BorderLayout(12, 0));
        JPanel headingText = new JPanel();
        headingText.setLayout(new BoxLayout(headingText, BoxLayout.Y_AXIS));
        headingText.add(defLabel);
        headingText.add(new JLabel(
                "Definitions are loaded from top to bottom; higher files override lower files."));
        heading.add(headingText, BorderLayout.CENTER);
        heading.add(btnOpenFolder, BorderLayout.EAST);

        JPanel filter = new JPanel(new BorderLayout(8, 0));
        filter.add(new JLabel("Search"), BorderLayout.WEST);
        filter.add(searchField, BorderLayout.CENTER);

        JPanel moveActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        moveActions.add(btnMoveUp);
        moveActions.add(btnMoveDown);
        moveActions.add(btnMoveTop);
        moveActions.add(btnMoveBottom);
        JPanel fileActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        fileActions.add(btnAddDefinition);
        fileActions.add(btnRemoveDefinition);
        JPanel listActions = new JPanel(new BorderLayout());
        listActions.add(moveActions, BorderLayout.WEST);
        listActions.add(fileActions, BorderLayout.EAST);

        JPanel listPanel = new JPanel(new BorderLayout(0, 8));
        listPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        listPanel.add(filter, BorderLayout.NORTH);
        listPanel.add(jScrollPane1, BorderLayout.CENTER);
        listPanel.add(listActions, BorderLayout.SOUTH);

        JPanel details = createDetailsPanel();
        JSplitPane content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                listPanel, details);
        content.setName("DEFINITION MANAGER SPLIT");
        content.setResizeWeight(0.72);
        content.setContinuousLayout(true);
        content.setDividerLocation(680);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        footer.add(btnUndo, BorderLayout.WEST);
        JPanel commit = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        commit.add(btnCancel);
        commit.add(btnApply);
        commit.add(btnSave);
        footer.add(commit, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 0, 14));
        root.add(heading, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        TouchTargetService.apply(root,
                SettingsManager.getSettings().getDisplayMode());
        setPreferredSize(new Dimension(1040, 650));
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setName("DEFINITION DETAILS");
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Details"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        addDetail(panel, constraints, "Name", detailName);
        addDetail(panel, constraints, "Type", detailType);
        addDetail(panel, constraints, "Path", detailPath);
        addDetail(panel, constraints, "Status", detailStatus);
        addDetail(panel, constraints, "Priority", detailPriority);
        addDetail(panel, constraints, "Last modified", detailModified);
        constraints.weighty = 1;
        panel.add(Box.createVerticalGlue(), constraints);
        panel.setMinimumSize(new Dimension(250, 260));
        return panel;
    }

    private static void addDetail(JPanel panel, GridBagConstraints constraints,
            String title, JLabel value) {
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        panel.add(heading, constraints);
        constraints.gridy++;
        value.setText("—");
        value.setVerticalAlignment(SwingConstants.TOP);
        panel.add(value, constraints);
        constraints.gridy++;
        panel.add(Box.createVerticalStrut(12), constraints);
        constraints.gridy++;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnCancel) {
            dispose();

        } else if (e.getSource() == btnSave) {
            saveSettings();
            dispose();

        } else if (e.getSource() == btnApply) {
            saveSettings();

        } else if (e.getSource() == btnMoveUp) {
            moveSelection(MOVE_UP);

        } else if (e.getSource() == btnMoveDown) {
            moveSelection(MOVE_DOWN);

        } else if (e.getSource() == btnAddDefinition) {
            addFile();

        } else if (e.getSource() == btnRemoveDefinition) {
            removeSelection();

        } else if (e.getSource() == btnUndo) {
            initSettings();

        } else if (e.getSource() == btnMoveTop) {
            moveSelectionToEdge(true);

        } else if (e.getSource() == btnMoveBottom) {
            moveSelectionToEdge(false);

        } else if (e.getSource() == btnOpenFolder) {
            openDefinitionsFolder();

        }

    }

    public void saveSettings() {
        Vector<File> output = new Vector<File>();

        // create file vector
        for (int i = 0; i < fileNames.size(); i++) {
            output.add(new File(fileNames.get(i)));
        }

        // save
        SettingsManager.getSettings().setEcuDefinitionFiles(output);
    }

    public void addFile() {
        final Settings settings = SettingsManager.getSettings();
        final JFileChooser fc = new IntegratedFileChooser(
                settings.getLastDefinitionDir());
        fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new DefinitionFilter());

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : fc.getSelectedFiles()) {
                boolean alreadyAdded = false;

                //Check if it already exists in the list
                for (String path : fileNames) {
                    if (path.equalsIgnoreCase(f.getAbsolutePath())) {
                        alreadyAdded = true;
                        break;
                    }
                }

                if (!alreadyAdded) {
                    //If its a file that needs to be converted sometimes a warning
                    //should be displayed to the user
                    if (ConversionLayerFactory.requiresConversionLayer(f)) {
                        ConversionLayer layer = ConversionLayerFactory.getConversionLayerForFile(f);

                        if (layer.getDefinitionPickerInfo() != null) {
                            JOptionPane.showMessageDialog(null, layer.getDefinitionPickerInfo(),
                                    rb.getString("CONVERSIONTITLE"), JOptionPane.WARNING_MESSAGE);
                        }
                    }
                }
                else {
                    continue;   // selected file(s) for loop
                }

                // Try to determine if the selected file is valid, refuse to
                // add invalid types.
                // File types and search sequences are loaded from a properties file.
                if (props.size() > 0) {
                    String fileType = "RomRaider";
                    boolean breakSearch = false;
                    try {
                        final Scanner scan = new Scanner(f);
                        // Scan the file looking for invalid string sequences,
                        // the value of a properties file key.
                        while(scan.hasNext()) {
                            breakSearch = false;
                            final String line = scan.nextLine().toLowerCase().toString();
                            for (Object key : props.keySet()) {
                                if (line.contains(props.getProperty((String) key))) {
                                    fileType = (String) key;
                                    breakSearch = true;
                                    break;  // for loop
                                }
                            }
                            if (breakSearch) break; // while loop
                        }
                        scan.close();

                    } catch (FileNotFoundException e) {
                        // Since the user selected it, it should be found.
                        e.printStackTrace();
                    }
                    if (!fileType.equalsIgnoreCase("RomRaider")) {
                        JOptionPane.showMessageDialog(this, MessageFormat.format(
                                rb.getString("INVALIDMSG"), fileType, f.getName()),
                                rb.getString("INVALIDFILE"),
                                JOptionPane.WARNING_MESSAGE);
                        continue;
                    }
                }
                fileNames.add(f.getAbsolutePath());

                settings.setLastDefinitionDir(f.getParentFile());
            }

            updateListModel();
        }
    }

    public void moveSelection(int direction) {
        String fileName = definitionList.getSelectedValue();
        if (fileName == null) return;
        int selectedIndex = fileNames.indexOf(fileName);

        if (direction == MOVE_UP && selectedIndex > 0) {
            fileNames.remove(selectedIndex);
            fileNames.add(--selectedIndex, fileName);

        } else if (direction == MOVE_DOWN
                && selectedIndex < fileNames.size() - 1) {
            fileNames.remove(selectedIndex);
            fileNames.add(++selectedIndex, fileName);

        }
        updateListModel();
        definitionList.setSelectedValue(fileName, true);
    }

    private void moveSelectionToEdge(boolean top) {
        String fileName = definitionList.getSelectedValue();
        if (fileName == null) return;
        fileNames.remove(fileName);
        fileNames.add(top ? 0 : fileNames.size(), fileName);
        updateListModel();
        definitionList.setSelectedValue(fileName, true);
    }

    public void removeSelection() {
        String fileName = definitionList.getSelectedValue();
        if (fileName == null) return;
        fileNames.remove(fileName);
        updateListModel();

    }

    public void updateListModel() {
        String selected = definitionList == null
                ? null : definitionList.getSelectedValue();
        String query = searchField == null ? ""
                : searchField.getText().trim().toLowerCase();
        visibleFileNames.clear();
        for (String path : fileNames) {
            File file = new File(path);
            if (query.isEmpty()
                    || path.toLowerCase().contains(query)
                    || file.getName().toLowerCase().contains(query)) {
                visibleFileNames.add(path);
            }
        }
        definitionList.setListData(visibleFileNames);
        if (selected != null && visibleFileNames.contains(selected)) {
            definitionList.setSelectedValue(selected, true);
        } else if (!visibleFileNames.isEmpty()) {
            definitionList.setSelectedIndex(0);
        } else {
            updateSelectionState();
        }
    }

    private void updateSelectionState() {
        String path = definitionList.getSelectedValue();
        boolean selected = path != null;
        int priority = selected ? fileNames.indexOf(path) : -1;
        btnMoveUp.setEnabled(selected && priority > 0);
        btnMoveTop.setEnabled(selected && priority > 0);
        btnMoveDown.setEnabled(selected && priority < fileNames.size() - 1);
        btnMoveBottom.setEnabled(selected && priority < fileNames.size() - 1);
        btnRemoveDefinition.setEnabled(selected);
        if (!selected) {
            detailName.setText("—");
            detailType.setText("—");
            detailPath.setText("—");
            detailStatus.setText("—");
            detailPriority.setText("—");
            detailModified.setText("—");
            return;
        }
        File file = new File(path);
        detailName.setText(file.getName());
        detailType.setText(file.isDirectory() ? "Definition folder"
                : "XML definition file");
        detailPath.setText("<html>" + escape(path) + "</html>");
        detailStatus.setText(file.exists() ? "Available" : "File not found");
        detailPriority.setText((priority + 1) + " of " + fileNames.size());
        detailModified.setText(file.exists()
                ? new SimpleDateFormat("MMM d, yyyy h:mm a").format(
                        new Date(file.lastModified())) : "—");
    }

    private void openDefinitionsFolder() {
        String path = definitionList.getSelectedValue();
        File folder = path == null
                ? SettingsManager.getSettings().getLastDefinitionDir()
                : new File(path).getParentFile();
        if (folder == null) folder = new File(System.getProperty("user.home"));
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException("Opening folders is not supported by this desktop");
            }
            Desktop.getDesktop().open(folder);
        } catch (IOException | SecurityException exception) {
            IntegratedOptionDialog.show(this,
                    "Unable to open " + folder + ":\n" + exception.getMessage(),
                    "Open Definitions Folder", JOptionPane.WARNING_MESSAGE,
                    new Object[] {"OK"}, "OK");
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private final class DefinitionPathRenderer extends JPanel
            implements ListCellRenderer<String> {
        private static final long serialVersionUID = 1L;
        private final JLabel name = new JLabel();
        private final JLabel path = new JLabel();
        private final JLabel priority = new JLabel();

        private DefinitionPathRenderer() {
            super(new BorderLayout(10, 2));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            text.add(name);
            text.add(path);
            add(priority, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        }

        public Component getListCellRendererComponent(JList<? extends String> list,
                String value, int index, boolean selected, boolean focus) {
            File file = new File(value);
            priority.setText(String.valueOf(fileNames.indexOf(value) + 1));
            name.setText(file.getName());
            path.setText(value);
            Color background = selected ? list.getSelectionBackground()
                    : list.getBackground();
            Color foreground = selected ? list.getSelectionForeground()
                    : list.getForeground();
            setBackground(background);
            priority.setForeground(foreground);
            name.setForeground(foreground);
            path.setForeground(foreground);
            return this;
        }
    }

    private javax.swing.JButton btnAddDefinition;
    private javax.swing.JButton btnApply;
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnMoveDown;
    private javax.swing.JButton btnMoveUp;
    private javax.swing.JButton btnRemoveDefinition;
    private javax.swing.JButton btnSave;
    private javax.swing.JButton btnUndo;
    private javax.swing.JButton btnMoveTop;
    private javax.swing.JButton btnMoveBottom;
    private javax.swing.JButton btnOpenFolder;
    private javax.swing.JLabel defLabel;
    private javax.swing.JList<String> definitionList;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField searchField;
    private final javax.swing.JLabel detailName = new javax.swing.JLabel();
    private final javax.swing.JLabel detailType = new javax.swing.JLabel();
    private final javax.swing.JLabel detailPath = new javax.swing.JLabel();
    private final javax.swing.JLabel detailStatus = new javax.swing.JLabel();
    private final javax.swing.JLabel detailPriority = new javax.swing.JLabel();
    private final javax.swing.JLabel detailModified = new javax.swing.JLabel();

    /**
     * Load String search sequences from a user customized properties file.
     * The file will populate a search list if it is present.
     * String search Sequences in the file are in type=sequence sets.
     * @exception    FileNotFoundException if the directory or file is not present
     * @exception    IOException if there's some kind of IO error
     */
    private Properties loadSequences() {
        final Properties sequences = new Properties();
        try (FileInputStream propFile = CustomizationFiles.open(
                "nameSequences.properties")) {
            sequences.load(propFile);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Definition name classifier is unavailable; "
                    + "continuing without file-type heuristics: "
                    + e.getLocalizedMessage());
        } catch (IOException e) {
            LOGGER.warn("Definition name classifier could not be read; "
                    + "continuing without file-type heuristics.", e);
        }
        return sequences;
    }
}
