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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ResourceBundle;
import java.util.Vector;

import javax.naming.NameNotFoundException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.editor.ecu.ECUEditorManager;
import com.romraider.maps.Scale;
import com.romraider.maps.Table;
import com.romraider.maps.history.EditTransaction;
import com.romraider.maps.history.RomEditHistory;
import com.romraider.maps.Table1D;
import com.romraider.maps.Table1DView;
import com.romraider.maps.Table3D;
import com.romraider.maps.TableSelectionSummary;
import com.romraider.maps.TableView;
import com.romraider.maps.UserLevelException;
import com.romraider.ui.ModernIconFactory;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.TouchTargetService;
import com.romraider.ui.UiThemeService;
import com.romraider.util.NumberUtil;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

public class TableToolBar extends JToolBar implements MouseListener, ItemListener, ActionListener {

    private static final long serialVersionUID = 8697645329367637930L;
    private static final Logger LOGGER = Logger.getLogger(TableToolBar.class);
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            TableToolBar.class.getName());
    private final JButton incrementFine = new JButton();
    private final JButton decrementFine = new JButton();
    private final JButton incrementCoarse = new JButton();
    private final JButton decrementCoarse = new JButton();
    private final JButton colorCells = new JButton();
    private final JButton refreshCompare = new JButton();
    private final JButton revertSelected = new JButton("Revert",
            ModernIconFactory.icon(ModernIconFactory.Action.UNDO));
    private final JButton moreActions = new JButton("More ▾");
    private final JPopupMenu moreActionsPopup = new JPopupMenu();
    private final JLabel selectionStatus = new JLabel("No cells selected");

    private final JButton setValue = new JButton("Set");
    private final JButton multiply = new JButton("Multiply");

    private final ECUEditorNumberField incrementByFine = new ECUEditorNumberField();
    private final ECUEditorNumberField incrementByCoarse = new ECUEditorNumberField();
    private final ECUEditorNumberField setValueText = new ECUEditorNumberField();

    private final JComboBox scaleSelection = new JComboBox();

    private final JPanel liveDataPanel = new JPanel();
    private final JCheckBox overlayLog = new JCheckBox("Live trace");
    private final JButton clearOverlay = new JButton("Clear trace");
    private final JLabel liveDataValue = new JLabel();

    private final Border toolbarBorder = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0,
                    UiThemeService.getInstance().color(ThemeToken.RAISED_SURFACE)),
            BorderFactory.createEmptyBorder(3, 6, 3, 6));

    private Table selectedTable = null;
    private Table pendingValueTable;
    private boolean pendingValueEdit;

    public TableToolBar() {
        super(Settings.defaultTableToolBarName);
        this.setFloatable(false);
        setValueText.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { valueTextChanged(); }
            public void removeUpdate(DocumentEvent event) { valueTextChanged(); }
            public void changedUpdate(DocumentEvent event) { valueTextChanged(); }
        });
        this.setRollover(true);
        FlowLayout toolBarLayout = new FlowLayout(FlowLayout.LEFT, 6, 4);
        this.setLayout(toolBarLayout);
        setBackground(UiThemeService.getInstance().color(ThemeToken.SURFACE));

        setBorder(toolbarBorder);

        this.updateIcons();

        selectionStatus.setName("TABLE SELECTION STATUS");
        selectionStatus.setForeground(UiThemeService.getInstance().color(
                ThemeToken.SECONDARY_TEXT));
        selectionStatus.setToolTipText(
                "Select one or more calibration cells to edit their values");
        revertSelected.setName("REVERT SELECTED CELLS");
        revertSelected.setToolTipText(
                "Restore selected changed cells to their saved ROM values");
        revertSelected.getAccessibleContext().setAccessibleName(
                "Revert selected changed cells");
        revertSelected.addActionListener(this);
        this.add(group(null, selectionStatus, revertSelected));
        this.add(group("Fine", decrementFine, incrementByFine, incrementFine));
        this.add(group("Value", setValueText, setValue, multiply));

        colorCells.setEnabled(false);
        refreshCompare.setEnabled(false);
        configureMoreActions();
        this.add(group(null, moreActions));

        incrementByFine.setAlignmentX(JTextArea.CENTER_ALIGNMENT);
        incrementByFine.setAlignmentY(JTextArea.CENTER_ALIGNMENT);
        incrementByFine.setColumns(4);
        incrementByCoarse.setAlignmentX(JTextArea.CENTER_ALIGNMENT);
        incrementByCoarse.setAlignmentY(JTextArea.CENTER_ALIGNMENT);
        incrementByCoarse.setColumns(4);
        setValueText.setAlignmentX(JTextArea.CENTER_ALIGNMENT);
        setValueText.setAlignmentY(JTextArea.CENTER_ALIGNMENT);
        setValueText.setColumns(4);

        incrementFine.setToolTipText(rb.getString("INCFTT"));
        decrementFine.setToolTipText(rb.getString("DECFTT"));
        incrementCoarse.setToolTipText(rb.getString("INCCTT"));
        decrementCoarse.setToolTipText(rb.getString("DECCTT"));
        setValue.setToolTipText(rb.getString("SETABSTT"));
        setValueText.setToolTipText(rb.getString("SETABSTT"));
        incrementByFine.setToolTipText(rb.getString("FINEVALUETT"));
        incrementByCoarse.setToolTipText(rb.getString("COURSEVALUETT"));
        multiply.setToolTipText(rb.getString("MULTVALUETT"));
        overlayLog.setToolTipText(rb.getString("OVERLAYLOGTT"));
        clearOverlay.setToolTipText(rb.getString("CLEAROVERLAYTT"));
        colorCells.setToolTipText(rb.getString("CTCTT"));
        refreshCompare.setToolTipText(rb.getString("RTCTT"));
        incrementFine.getAccessibleContext().setAccessibleName(
                "Increase by fine step");
        decrementFine.getAccessibleContext().setAccessibleName(
                "Decrease by fine step");
        incrementCoarse.getAccessibleContext().setAccessibleName(
                "Increase by coarse step");
        decrementCoarse.getAccessibleContext().setAccessibleName(
                "Decrease by coarse step");
        incrementByFine.getAccessibleContext().setAccessibleName("Fine step");
        incrementByCoarse.getAccessibleContext().setAccessibleName("Coarse step");
        setValueText.getAccessibleContext().setAccessibleName("New cell value");

        incrementFine.addMouseListener(this);
        decrementFine.addMouseListener(this);
        incrementCoarse.addMouseListener(this);
        decrementCoarse.addMouseListener(this);
        setValue.addMouseListener(this);
        multiply.addMouseListener(this);
        scaleSelection.addItemListener(this);
        overlayLog.addItemListener(this);
        clearOverlay.addActionListener(this);
        colorCells.addMouseListener(this);
        refreshCompare.addMouseListener(this);

        // key binding actions
        Action enterAction = new AbstractAction() {
            private static final long serialVersionUID = -6008026264821746092L;

            @Override
            public void actionPerformed(ActionEvent e) {
                TableFrame frame = ECUEditorManager.getECUEditor().getActiveTableFrame();
                if(frame == null) return;
                frame.getTableView().requestFocusInWindow();

                try {
                    setValue(frame.getTable());
                } catch (UserLevelException e1) {
                    e1.printStackTrace();
                }
            }
        };

        // set input mapping
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);

        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);

        im.put(enter, "enterAction");
        getActionMap().put(im.get(enter), enterAction);

        liveDataPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        liveDataPanel.add(overlayLog);
        liveDataPanel.add(clearOverlay);
        //liveDataPanel.add(liveDataValue);
        overlayLog.setEnabled(false);
        clearOverlay.setEnabled(false);

        incrementFine.getInputMap().put(enter, "enterAction");
        decrementFine.getInputMap().put(enter, "enterAction");
        incrementCoarse.getInputMap().put(enter, "enterAction");
        decrementCoarse.getInputMap().put(enter, "enterAction");
        incrementByFine.getInputMap().put(enter, "enterAction");
        incrementByCoarse.getInputMap().put(enter, "enterAction");
        setValueText.getInputMap().put(enter, "enterAction");
        setValue.getInputMap().put(enter, "enterAction");

        this.setEnabled(true);
        // Keep the central calibration canvas resizable beside the inspector.
        // Lower-frequency controls live in an explicit overflow instead of
        // silently clipping beyond the visible toolbar edge.
        setMinimumSize(new Dimension(0, getPreferredSize().height));
        toggleTableToolBar(null);
    }

    private void configureMoreActions() {
        moreActions.setName("TABLE TOOLBAR MORE ACTIONS");
        moreActions.setToolTipText(
                "Coarse step, color, scale, compare, and live-trace controls");
        moreActions.getAccessibleContext().setAccessibleName(
                "More table controls");
        moreActionsPopup.setName("TABLE TOOLBAR MORE POPUP");
        moreActionsPopup.add(group("Coarse", decrementCoarse,
                incrementByCoarse, incrementCoarse));
        moreActionsPopup.addSeparator();
        moreActionsPopup.add(group("Table", colorCells, refreshCompare));
        moreActionsPopup.addSeparator();
        moreActionsPopup.add(group("Scale", scaleSelection));
        moreActionsPopup.addSeparator();
        moreActionsPopup.add(group("Logging", liveDataPanel));
        moreActions.putClientProperty("TABLE_TOOLBAR_MORE_POPUP",
                moreActionsPopup);
        moreActions.addActionListener(event -> {
            TouchTargetService.apply(moreActionsPopup,
                    SettingsManager.getSettings().getDisplayMode());
            moreActionsPopup.show(moreActions, 0, moreActions.getHeight());
        });
    }

    public void updateIcons() {
        incrementFine.setText("+");
        decrementFine.setText("−");
        incrementCoarse.setText("+");
        decrementCoarse.setText("−");
        colorCells.setText("Color");
        colorCells.setIcon(ModernIconFactory.icon(
                ModernIconFactory.Action.COLOR));
        refreshCompare.setText("Refresh Diff");
        refreshCompare.setIcon(ModernIconFactory.icon(
                ModernIconFactory.Action.REFRESH));
    }

    private static JPanel group(String label, Component... controls) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        if (label != null) {
            JLabel groupLabel = new JLabel(label);
            groupLabel.setForeground(UiThemeService.getInstance().color(
                    ThemeToken.SECONDARY_TEXT));
            panel.add(groupLabel);
        }
        for (Component control : controls) panel.add(control);
        return panel;
    }

    @Override
    public void setBorder(Border border) {
        if(SettingsManager.getSettings().isShowTableToolbarBorder()) {
            super.setBorder(toolbarBorder);
        } else {
            super.setBorder(BorderFactory.createEmptyBorder());
        }
    }

    private void saveFineCourseValuesInTable(Table t) {
        if(t == null || t.getCurrentScale() == null) return;

        double incCoarse = 0;
        double incFine = 0;

        try {
            //Commit the value which was typed (if field still has focus)
            incrementByCoarse.commitEdit();
            incrementByFine.commitEdit();

            incCoarse = Double.parseDouble(String.valueOf(incrementByCoarse.getValue()));
            incFine = Double.parseDouble(String.valueOf(incrementByFine.getValue()));
        }
        //Current value in the inc/dec field are not valid
        catch(ParseException e) {
            return;
        }
        //Should not happen since ParseException would happen before that
        catch(NumberFormatException e) {
            return;
        }

        //Save current inc/dec values in table before we switch
        if(incCoarse!=0 && incFine != 0) {
            t.updateIncrementDecrementValues(incFine,incCoarse);
        }
    }

    public void updateTableToolBar(Table selectedTable) {
        
    	// If the table is a 1D table, we might select an axis
    	// but we want to change the scales of the entire table
        if(selectedTable instanceof Table1D)
        	{
        		Table t = ((Table1D)selectedTable).getAxisParent();
        		// Table will not have a parent if its a standalone 1D table
        		if(t != null)
        		{
        			selectedTable = t;
        		}
       }

        if(selectedTable == null  && this.selectedTable == null) {
            // Skip if the table is the same to avoid multiple updates
            return;
        } else if(selectedTable == null || this.selectedTable == null) {
            // Update the toolbar.
        } else if(this.selectedTable.equals(selectedTable)) {
            // Selection can change while the active table stays the same.
            updateSelectionStatus(selectedTable);
            toggleTableToolBar(selectedTable);
            return;
        }


        //Save the current inc/dec values in the table
        saveFineCourseValuesInTable(this.selectedTable);
        this.selectedTable = selectedTable;
        updateSelectionStatus(selectedTable);

        setBorder(toolbarBorder);

        if(null == selectedTable)
        {
            // disable the toolbar.
            toggleTableToolBar(selectedTable);
            return;
        }

        updateToolbarIncrementDecrementValues();
        
        if(selectedTable.getTableView() != null)
        	this.overlayLog.setSelected(selectedTable.getTableView().getOverlayLog());
        setScales(selectedTable.getScales());

        if(null == selectedTable.getCurrentScale())
        {
            this.scaleSelection.setSelectedItem("Default");
        } else {
            this.scaleSelection.setSelectedItem(selectedTable.getCurrentScale().getCategory());
        }

        toggleTableToolBar(selectedTable);
    }

    private void updateToolbarIncrementDecrementValues() {
        if(null == selectedTable) {
            return;
        }

        double fineIncrement = 0;
        double coarseIncrement = 0;

        try {
            // enable the toolbar.
            fineIncrement = Math.abs(selectedTable.getCurrentScale().getFineIncrement());
            coarseIncrement = Math.abs(selectedTable.getCurrentScale().getCoarseIncrement());
        } catch (Exception ex) {
            // scaling units haven't been added yet -- no problem
        }

        incrementByFine.setValue(fineIncrement);
        incrementByCoarse.setValue(coarseIncrement);
    }

    private void toggleTableToolBar(Table currentTable) {
        boolean enabled = currentTable != null;
        TableSelectionSummary selection = TableSelectionSummary.of(currentTable);
        boolean editableSelection = enabled && selection.hasSelection();
        boolean changedSelection = enabled && selection.hasChangedSelection();

        setVisible(enabled);

        incrementFine.setEnabled(editableSelection);
        decrementFine.setEnabled(editableSelection);
        incrementCoarse.setEnabled(editableSelection);
        decrementCoarse.setEnabled(editableSelection);

        setValue.setEnabled(editableSelection);
        multiply.setEnabled(editableSelection);

        incrementByFine.setEnabled(editableSelection);
        incrementByCoarse.setEnabled(editableSelection);
        setValueText.setEnabled(editableSelection);
        revertSelected.setEnabled(changedSelection);

        scaleSelection.setEnabled(enabled);
        moreActions.setEnabled(enabled);

        liveDataValue.setEnabled(enabled);

        colorCells.setEnabled(enabled);
        refreshCompare.setEnabled(enabled);

        if(null != currentTable && null != currentTable.getCompareTable() && enabled) {
            refreshCompare.setEnabled(true);
        } else {
            refreshCompare.setEnabled(false);
        }

        if (null != currentTable && currentTable.isLiveDataSupported() && enabled) {
            overlayLog.setEnabled(true);
            clearOverlay.setEnabled(true);
        }
        else{
            overlayLog.setEnabled(false);
            clearOverlay.setEnabled(false);
        }

        if(null != currentTable && currentTable.isStaticDataTable()) {
            if(enabled) {
                scaleSelection.setEnabled(true);
            } else {
                scaleSelection.setEnabled(false);
            }

            // Disable everything that does not apply to static value tables.
            colorCells.setEnabled(false);
            refreshCompare.setEnabled(false);

            incrementFine.setEnabled(false);
            decrementFine.setEnabled(false);
            incrementCoarse.setEnabled(false);
            decrementCoarse.setEnabled(false);
            incrementByFine.setEnabled(false);
            incrementByCoarse.setEnabled(false);
            setValue.setEnabled(false);
            setValueText.setEnabled(false);
            multiply.setEnabled(false);
        }


        repaint();
    }

    private void updateSelectionStatus(Table table) {
        TableSelectionSummary summary = TableSelectionSummary.of(table);
        if (!summary.hasSelection()) {
            selectionStatus.setText("No cells selected");
            selectionStatus.setToolTipText(
                    "Select one or more calibration cells to edit their values");
            revertSelected.setEnabled(false);
            return;
        }

        String count = summary.getSelectedCells() + (summary.getSelectedCells() == 1
                ? " cell" : " cells");
        String range = formatSelectionValue(table, summary.getMinimum());
        if (Double.compare(summary.getMinimum(), summary.getMaximum()) != 0) {
            range += " – " + formatSelectionValue(table, summary.getMaximum());
        }
        selectionStatus.setText(count + "  •  " + range);
        int changed = summary.getChangedCells();
        revertSelected.setEnabled(changed > 0
                && table != null && !table.isStaticDataTable());
        selectionStatus.setToolTipText("Selected calibration value range: "
                + range + (changed > 0 ? " • " + changed + " changed" : ""));
        revertSelected.setToolTipText(changed > 0
                ? "Revert " + changed + (changed == 1
                        ? " selected changed cell" : " selected changed cells")
                : "Selected cells already match their saved ROM values");
    }

    private static String formatSelectionValue(Table table, double value) {
        try {
            Scale scale = table == null ? null : table.getCurrentScale();
            String pattern = scale == null ? "0.###" : scale.getFormat();
            String unit = scale == null || scale.getUnit() == null
                    || "0x".equals(scale.getUnit()) ? "" : " " + scale.getUnit();
            return new DecimalFormat(pattern).format(value) + unit;
        } catch (RuntimeException ignored) {
            return new DecimalFormat("0.###").format(value);
        }
    }

    public void setScales(Vector<Scale> scales) {

        // remove item listener to avoid null pointer exception when populating
        scaleSelection.removeItemListener(this);
        scaleSelection.removeAllItems();

        for (Scale scale : scales) {
            scaleSelection.addItem(scale.getCategory());
        }

        // and put it back
        scaleSelection.addItemListener(this);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Table curTable = getSelectedTable();
        if(null == curTable)
        {
            return;
        }
        try {
            if (e.getSource() == incrementCoarse) {
                incrementCoarse(curTable);
            } else if (e.getSource() == decrementCoarse) {
                decrementCoarse(curTable);
            } else if (e.getSource() == incrementFine) {
                incrementFine(curTable);
            } else if (e.getSource() == decrementFine) {
                decrementFine(curTable);
            } else if (e.getSource() == multiply) {
                multiply(curTable);
            } else if (e.getSource() == setValue) {
                setValue(curTable);
            } else if (e.getSource() == colorCells) {
                colorCells(curTable);
            } else if (e.getSource() == refreshCompare) {
                refreshCompare(curTable);
            }
        }
        catch(UserLevelException ex) {
            TableView.showInvalidUserLevelPopup(ex);
        }
    }

    public void setValue(Table currentTable) throws UserLevelException {
        try (EditTransaction edit = RomEditHistory.getInstance().begin(
                currentTable, "Set selected values")) {
            currentTable.setRealValue(setValueText.getText());
        }
        updateSelectionStatus(currentTable);
        pendingValueEdit = false;
        pendingValueTable = null;
    }

    public void multiply() throws UserLevelException {
        Table curTable = getSelectedTable();
        if(null == curTable) {
            return;
        }
        multiply(curTable);
    }

    public void multiply(Table currentTable) throws UserLevelException {
        try (EditTransaction edit = RomEditHistory.getInstance().begin(
                currentTable, "Multiply selected values")) {
            try {
                currentTable.multiply(NumberUtil.doubleValue(setValueText.getText()));
            } catch(ParseException nex) {
                LOGGER.error(this.getClass().getName() + ".multiply(" + currentTable + ") " + nex);
            }
        }
        updateSelectionStatus(currentTable);
        pendingValueEdit = false;
        pendingValueTable = null;
    }

    public void incrementFine() throws NumberFormatException, UserLevelException {
        Table curTable = getSelectedTable();
        if(null == curTable) {
            return;
        }
        incrementFine(curTable);
    }

    public void incrementFine(Table currentTable) throws NumberFormatException, UserLevelException {
        increment(currentTable,
                Double.parseDouble(String.valueOf(incrementByFine.getValue())),
                "Increase selected values");
    }

    public void decrementFine() throws NumberFormatException, UserLevelException {
        Table curTable = getSelectedTable();
        if(null == curTable) {
            return;
        }
        decrementFine(curTable);
    }

    public void decrementFine(Table currentTable) throws NumberFormatException, UserLevelException {
        increment(currentTable,
                0 - Double.parseDouble(String.valueOf(incrementByFine.getValue())),
                "Decrease selected values");
    }

    public void incrementCoarse() throws NumberFormatException, UserLevelException {
        Table curTable = getSelectedTable();
        if(null == curTable) {
            return;
        }
        incrementCoarse(curTable);
    }

    public void incrementCoarse(Table currentTable) throws NumberFormatException, UserLevelException {
        increment(currentTable,
                Double.parseDouble(String.valueOf(incrementByCoarse.getValue())),
                "Increase selected values");
    }

    public void decrementCoarse() throws NumberFormatException, UserLevelException {
        Table curTable = getSelectedTable();
        if(null == curTable) {
            return;
        }
        decrementCoarse(curTable);
    }

    public void decrementCoarse(Table currentTable) throws NumberFormatException, UserLevelException {
        increment(currentTable,
                0 - Double.parseDouble(String.valueOf(incrementByCoarse.getValue())),
                "Decrease selected values");
    }

    private void increment(Table currentTable, double amount, String description)
            throws UserLevelException {
        try (EditTransaction edit = RomEditHistory.getInstance().begin(
                currentTable, description)) {
            currentTable.increment(amount);
        }
        updateSelectionStatus(currentTable);
    }

    /** Reveals the Java 21-safe surface integrated with the active map tab. */
    public void enable3d(Table currentTable) {
        if (currentTable == null ||
                currentTable.getType() != Table.TableType.TABLE_3D) return;
        ECUEditorManager.getECUEditor().showIntegratedVisualization(
                ECUEditorManager.getECUEditor().getActiveTableFrame());
    }

    public void colorCells(Table currentTable) {
        currentTable.colorCells();
    }

    public void refreshCompare(Table currentTable) {
        currentTable.populateCompareValues(currentTable.getCompareTable());
    }

    public void setCoarseValue(double input) {
        incrementByCoarse.setText(String.valueOf(input));
        try {
            incrementByCoarse.commitEdit();
        } catch (ParseException ex) {
        }
    }

    public void setFineValue(double input) {
        incrementByFine.setText(String.valueOf(input));
        try {
            incrementByFine.commitEdit();
        } catch (ParseException ex) {
        }
    }

    public void focusSetValue(char input) {
        setValueText.requestFocus();
        setValueText.setText(String.valueOf(input));
    }

    private void valueTextChanged() {
        if (selectedTable == null) return;
        pendingValueTable = selectedTable;
        pendingValueEdit = true;
    }

    /** Commits text entered directly from a selected map cell before shutdown. */
    public void commitPendingValueEdit() throws UserLevelException {
        if (!pendingValueEdit || pendingValueTable == null) return;
        setValue(pendingValueTable);
    }

    String getSetValueTextForTesting() {
        return setValueText.getText();
    }

    boolean hasPendingValueEditForTesting() {
        return pendingValueEdit;
    }

    String getSelectionStatusForTesting() {
        return selectionStatus.getText();
    }

    boolean isSetValueEnabledForTesting() {
        return setValue.isEnabled();
    }

    boolean isRevertSelectedEnabledForTesting() {
        return revertSelected.isEnabled();
    }

    public void setInputMap(InputMap im) {
        incrementFine.setInputMap(WHEN_FOCUSED, im);
        decrementFine.setInputMap(WHEN_FOCUSED, im);
        incrementCoarse.setInputMap(WHEN_FOCUSED, im);
        decrementCoarse.setInputMap(WHEN_FOCUSED, im);
        setValue.setInputMap(WHEN_FOCUSED, im);
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        Table curTable = getSelectedTable();
        if(null == curTable) {
            return;
        }

        if (e.getSource() == scaleSelection) {
            // scale changed
            try {
                curTable.setScaleByCategory((String)scaleSelection.getSelectedItem());
                updateToolbarIncrementDecrementValues();
            } catch (NameNotFoundException e1) {
                e1.printStackTrace();
            }
        } else if (e.getSource() == overlayLog) {
            // enable/disable log overlay and live data display
            curTable.getTableView().setOverlayLog(overlayLog.isSelected());
        }
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        Table curTable = getSelectedTable();
        if(null == curTable) {
            return;
        }

        if (e.getSource() == clearOverlay) {
            // clear log overlay
            curTable.getTableView().clearLiveDataTrace();
        } else if (e.getSource() == revertSelected) {
            try (EditTransaction edit = RomEditHistory.getInstance().begin(
                    curTable, "Revert selected values")) {
                curTable.getTableView().undoSelected();
                updateSelectionStatus(curTable);
                toggleTableToolBar(curTable);
            } catch (UserLevelException exception) {
                TableView.showInvalidUserLevelPopup(exception);
            }
        }
    }

    public void setLiveDataValue(String value) {
        liveDataValue.setText(value);
    }


    public void selectStateChange(int x, int z, boolean value) {
        Table curTable = getSelectedTable();
        if(null == curTable) {
            return;
        }

        if(curTable.getType() == Table.TableType.TABLE_3D) {
            if (value) {
                Table3D table3d = (Table3D) curTable;
                table3d.selectCellAtWithoutClear(x, table3d.getSizeY() - z - 1);
            } else {
                Table3D table3d = (Table3D) curTable;
                table3d.deSelectCellAt(x, table3d.getSizeY() - z - 1);
            }
        }
    }

    public Table getSelectedTable() {
        return this.selectedTable;
    }
}
