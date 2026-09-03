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

package com.romraider.maps;

import static com.romraider.util.ColorScaler.getScaledColor;
import static com.romraider.util.ParamChecker.isNullOrEmpty;
import static javax.swing.BorderFactory.createLineBorder;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.Serializable;
import java.text.DecimalFormat;

import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;

import com.romraider.Settings;
import com.romraider.editor.ecu.ECUEditorManager;
import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;
import com.romraider.util.JEPUtil;
import com.romraider.util.SettingsManager;

public class DataCellView extends JLabel implements MouseListener, Serializable {
	private static final long serialVersionUID = 1L;
	static final Font DEFAULT_FONT = new Font("Arial", Font.BOLD, 12);
    static final String ST_DELIMITER = "\t\n\r\f";
    static final DecimalFormat FORMATTER = new DecimalFormat();
    static final String PERCENT_FORMAT = "#,##0.0%";
    static final String TT_FORMAT = "#,##0.##########";
    static final String TT_PERCENT_FORMAT = "#,##0.0#########%";
    static final String REPLACE_TEXT = "\u0020|\u00a0";
    
    static int UNSELECT_MASK1 = MouseEvent.BUTTON1_DOWN_MASK + MouseEvent.CTRL_DOWN_MASK + MouseEvent.ALT_DOWN_MASK;
    static int UNSELECT_MASK2 = MouseEvent.BUTTON3_DOWN_MASK + MouseEvent.CTRL_DOWN_MASK + MouseEvent.ALT_DOWN_MASK;
        
    private DataCell dataCell; //Data Source
    private TableView tableView = null;
    
    private int x = 0;
    private int y = 0;
    

    private boolean highlighted = false;
    private boolean traced = false;
    private boolean tracedStale = false;
 
    public DataCellView(DataCell cell, TableView view) {
        this.dataCell = cell;
        this.tableView = view;
        this.setHorizontalAlignment(CENTER);
        this.setVerticalAlignment(CENTER);
        this.setFont(DEFAULT_FONT);
        this.setOpaque(true);
        this.setVisible(true);
        this.addMouseListener(this);
        
        this.y = cell.getIndexInTable();
        this.setPreferredSize(getSettings().getCellSize());      
    }
    
    public DataCellView(DataCell cell, TableView view, int x, int y) {
    	this(cell, view);
    	
        this.x = x;
        this.y = y;
    }
        
    public boolean equals (DataCellView v) {
    	return v.dataCell.equals(dataCell);
    }
    
    public DataCell getDataCell() {
    	return dataCell;
    }
    
    private static Settings getSettings() {
        return SettingsManager.getSettings();
    }
    
    public void drawCell() {
        if(tableView == null || tableView.isHidden()) {
            // Table will be null in the static case.
            return;
        }

        tableView.updatePresetPanel();
        //this.invalidate();
        setFont(getSettings().getTableFont());
        setText(getCellText());
        setToolTipText(getCellToolTip());
        setBackground(getCellBackgroundColor());
        setForeground(getCellTextColor());
        setBorder(getCellBorder());
        //this.validate();   
        //super.repaint();
    }

    private Color getCellBackgroundColor() {
        Color backgroundColor = null == tableView.getTable().getCompareTable()
                ? getBinColor() : getCompareColor();

        // Preserve the value heatmap while showing interaction state. Older
        // builds replaced selected cells with a flat grey/blue block, hiding
        // the most useful visual information in a multi-cell selection.
        if (highlighted) {
            return blend(backgroundColor, UiThemeService.getInstance().color(
                    ThemeToken.LIVE_TRACE), 0.34);
        }
        if (dataCell.isSelected()) {
            return blend(backgroundColor, UiThemeService.getInstance().color(
                    ThemeToken.SELECTION), 0.24);
        }
        return backgroundColor;
    }
    
    public Color getCompareColor() {
    	Table t = tableView.getTable();
    	
        if (usesThemedAxisSurface()) {
            return UiThemeService.getInstance().color(ThemeToken.RAISED_SURFACE);
        }

        double compareScale;
        if (0.0 == dataCell.getCompareValue()) {
            return Settings.UNCHANGED_VALUE_COLOR;
        }else if(t.getMinCompare() == t.getMaxCompare()) {
            return getSettings().getMaxColor();
        } else {
            compareScale = (dataCell.getCompareValue() - t.getMinCompare()) / (t.getMaxCompare() - t.getMinCompare());
        }
        return getScaledColor(compareScale);
    }

    public Color getBinColor() {
    	Table t = tableView.getTable();
    	
        if (usesThemedAxisSurface()) {
            return UiThemeService.getInstance().color(ThemeToken.RAISED_SURFACE);
        }

        if (dataCell.getMaxAllowedBin() < dataCell.getBinValue()) {
            return getSettings().getWarningColor();
        } else if (dataCell.getMinAllowedBin() > dataCell.getBinValue()) {
            return getSettings().getWarningColor();
        } else {
            // limits not set, scale based on table values
            double colorScale;
            if (t.getMaxBin() - t.getMinBin() == 0.0) {
                // if all values are the same, color will be middle value
                colorScale = .5;
            } else {
                colorScale = (dataCell.getRealValue() - t.getMinReal()) / (t.getMaxReal() - t.getMinReal());
            }

            return getScaledColor(colorScale);
        }
    }
      
    @Override
    public void mouseEntered(MouseEvent e) {
        if (UNSELECT_MASK1 == (e.getModifiersEx() & UNSELECT_MASK1)) {
            clearCell();
        } else if (UNSELECT_MASK2 == (e.getModifiersEx() & UNSELECT_MASK2)) {
            clearCell();
        } else {
        	tableView.highlight(x, y);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!e.isControlDown()) {
        	dataCell.getTable().clearSelection();
        }

        if (e.isControlDown() && e.isAltDown()) {
            clearCell();
        } else {
        	tableView.startHighlight(x, y);
        }
        requestFocus();
        ECUEditorManager.getECUEditor().getTableToolBar().updateTableToolBar(dataCell.getTable());
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    	tableView.stopHighlight();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
    
    private Color getCellTextColor() {
        Color textColor;

        if(traced) {
            if(!dataCell.getLiveValue().isEmpty()) {
                if(tableView.getTable() instanceof Table1D) {
                    textColor = Settings.scaleTextColor;
                } else {
                    textColor = Settings.liveDataTraceTextColor;
                }
            } else {
                textColor = Settings.scaleTextColor;
            }
        } else if (highlighted) {
            textColor = Settings.highlightTextColor;
        } else if (dataCell.isSelected()) {
            textColor = Settings.selectTextColor;
        } else if (usesThemedAxisSurface()) {
            textColor = UiThemeService.getInstance().color(ThemeToken.PRIMARY_TEXT);
        } else {
            textColor = UiThemeService.contrastText(getCellBackgroundColor());
        }

        return textColor;
    }

    private Border getCellBorder() {
        if(traced) {
            Color traceColor = tracedStale ? getSettings().getliveValueColor()
                    : getSettings().getCurLiveValueColor();
            return createLineBorder(traceColor, 2);
        }
        if (highlighted) {
            return createLineBorder(UiThemeService.getInstance().color(
                    ThemeToken.LIVE_TRACE), 2);
        }
        if (dataCell.isSelected()) {
            return createLineBorder(UiThemeService.getInstance().color(
                    ThemeToken.SELECTION), 2);
        }

        int changeDirection = getChangeDirection();
        if (changeDirection > 0) {
            return createLineBorder(getSettings().getIncreaseBorder(), 2);
        }
        if (changeDirection < 0) {
            return createLineBorder(getSettings().getDecreaseBorder(), 2);
        }

        Border border = UIManager.getBorder("RomRaider2.tableCellBorder");
        if (border != null) return border;
        Color gridColor = UIManager.getColor("controlShadow");
        return createLineBorder(
                gridColor == null ? Color.DARK_GRAY : gridColor, 1);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        int direction = getChangeDirection();
        if (direction == 0 || getWidth() < 8 || getHeight() < 8) return;

        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(direction > 0 ? getSettings().getIncreaseBorder()
                    : getSettings().getDecreaseBorder());
            int right = getWidth() - 2;
            Polygon marker = new Polygon(
                    new int[] {right - 7, right, right},
                    new int[] {1, 1, 8}, 3);
            g2.fillPolygon(marker);
        } finally {
            g2.dispose();
        }
    }

    private int getChangeDirection() {
        double baseline = null == tableView.getTable().getCompareTable()
                ? dataCell.getOriginalValue() : dataCell.getCompareToValue();
        return compareChange(baseline, dataCell.getBinValue());
    }

    static int compareChange(double baseline, double current) {
        return Double.compare(current, baseline);
    }

    static Color blend(Color base, Color overlay, double overlayWeight) {
        if (base == null) return overlay;
        if (overlay == null) return base;
        double weight = Math.max(0.0, Math.min(1.0, overlayWeight));
        double baseWeight = 1.0 - weight;
        return new Color(
                (int) Math.round(base.getRed() * baseWeight
                        + overlay.getRed() * weight),
                (int) Math.round(base.getGreen() * baseWeight
                        + overlay.getGreen() * weight),
                (int) Math.round(base.getBlue() * baseWeight
                        + overlay.getBlue() * weight));
    }

    private boolean usesThemedAxisSurface() {
        return tableView instanceof Table1DView
                && ((Table1DView) tableView).isAxis()
                && !getSettings().isColorAxis();
    }

    public String getCellText() {        
    	if(tableView.getTable().isStaticDataTable()) {
            return getStaticText();
        }
               
        FORMATTER.applyPattern(tableView.getTable().getCurrentScale().getFormat());
        String displayString = "";

        if (null == tableView.getTable().getCompareTable()) {
            displayString = FORMATTER.format(dataCell.getRealValue());
        } else if (tableView.getCompareDisplay() == Settings.CompareDisplay.ABSOLUTE) {
            displayString = FORMATTER.format(dataCell.getRealCompareValue());
        } else if (tableView.getCompareDisplay() == Settings.CompareDisplay.PERCENT) {
            FORMATTER.applyPattern(PERCENT_FORMAT);
            if (dataCell.getCompareValue() == 0.0) {
                displayString = FORMATTER.format(0.0);
            } else {
                displayString = FORMATTER.format(dataCell.getRealCompareChangeValue());
            }
        }

        if(traced) {
            if(!(tableView.getTable() instanceof Table1D)) {
                displayString = getLiveValueString(displayString);
            }
        }
        return displayString;
    }

    private String getCellToolTip() {
        if(tableView.getTable().isStaticDataTable()) {
            return getStaticText();
        }
        String ttString = null;
        FORMATTER.applyPattern(TT_FORMAT);
        if (null == tableView.getTable().getCompareTable()) {
            ttString = FORMATTER.format(dataCell.getRealValue());
        } else if (tableView.getCompareDisplay() == Settings.CompareDisplay.ABSOLUTE) {
            ttString = FORMATTER.format(dataCell.getRealCompareValue());
        } else if (tableView.getCompareDisplay() == Settings.CompareDisplay.PERCENT) {
            FORMATTER.applyPattern(TT_PERCENT_FORMAT);
            if (dataCell.getCompareValue() == 0.0) {
                ttString = FORMATTER.format(0.0);
            } else {
                ttString = FORMATTER.format(dataCell.getRealCompareChangeValue());
            }
        }
        if(traced) {
            if(!(tableView.getTable() instanceof Table1D)) {
                ttString = getLiveValueString(ttString);
            }
        }
        int direction = getChangeDirection();
        if (direction != 0) {
            double baseline = null == tableView.getTable().getCompareTable()
                    ? dataCell.getOriginalValue()
                    : dataCell.getCompareToValue();
            double currentReal = realValue(dataCell.getBinValue());
            double baselineReal = realValue(baseline);
            String baselineLabel = null == tableView.getTable().getCompareTable()
                    ? "Original" : "Compared with";
            ttString = "<html><b>" + html(ttString) + "</b><br>Current: "
                    + html(formatTooltipValue(currentReal)) + "<br>"
                    + baselineLabel + ": "
                    + html(formatTooltipValue(baselineReal)) + "<br>Change: "
                    + (direction > 0 ? "+" : "")
                    + html(formatTooltipValue(currentReal - baselineReal))
                    + "</html>";
        }
        return ttString;
    }

    private double realValue(double rawValue) {
        if (tableView.getTable().getCurrentScale() == null) return rawValue;
        return JEPUtil.evaluate(tableView.getTable().getCurrentScale()
                .getExpression(), rawValue);
    }

    private String formatTooltipValue(double value) {
        FORMATTER.applyPattern(TT_FORMAT);
        String formatted = FORMATTER.format(value);
        String unit = tableView.getTable().getCurrentScale() == null
                ? null : tableView.getTable().getCurrentScale().getUnit();
        return unit == null || unit.trim().isEmpty() || "0x".equals(unit)
                ? formatted : formatted + " " + unit.trim();
    }

    private static String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
    
    
    private void clearCell() {
        if(isHighlighted()) {
            setHighlighted(false);
        }
        if(dataCell.isSelected()) {
        	dataCell.setSelected(false);
        }
    }
    
    public boolean isSelected() {
    	return dataCell.isSelected();
    }
    
    @Override
    public String toString() {
        return getCellText();
    }


    public void setHighlighted(boolean highlighted) {
        if(!tableView.getTable().isStaticDataTable() && this.highlighted != highlighted) {
            this.highlighted = highlighted;
            drawCell();
        }
    }

    public boolean isHighlighted() {
        return highlighted;
    }
    
    public void setLiveDataTrace(boolean trace) {
        if(traced != trace) {
            traced = trace;
            drawCell();
        }
    }

    public void setPreviousLiveDataTrace(boolean trace) {
        if(tracedStale != trace) {
            tracedStale = trace;
            drawCell();
        }
    }
    
    private String getLiveValueString(String currentValue) {
        return currentValue + (isNullOrEmpty(dataCell.getLiveValue()) ? Settings.BLANK : (':' + dataCell.getLiveValue()));
    }
    
    public String getStaticText() {
        String displayString = null;
        try {
            FORMATTER.applyPattern(tableView.getTable().getCurrentScale().getFormat());
            double staticDouble = Double.parseDouble(dataCell.getStaticText());
            displayString = FORMATTER.format(JEPUtil.evaluate(tableView.getTable().getCurrentScale().getExpression(), staticDouble));
        } catch (Exception ex) {
            displayString = dataCell.getStaticText();
        }
        return displayString;
    }

    public void setY(int y) {
        this.y = y;
    }
    
}
