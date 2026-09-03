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

package com.romraider.logger.ecu.ui.paramlist;

import com.romraider.logger.ecu.EcuLogger;
import com.romraider.logger.ecu.definition.EcuData;
import com.romraider.logger.ecu.definition.ExternalData;
import com.romraider.ui.ModernTableStyle;
import com.romraider.ui.ThemeToken;

import static com.romraider.util.ParamChecker.isNullOrEmpty;

import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.regex.Pattern;

public final class ParameterListTable extends JTable {
    private static final long serialVersionUID = -8489190548281346227L;
    private UnitsComboBoxEditor comboBoxEditor = new UnitsComboBoxEditor();
    private UnitsComboBoxRenderer comboBoxRenderer = new UnitsComboBoxRenderer();
    private final ParameterListTableModel tableModel;

    public ParameterListTable(ParameterListTableModel tableModel) {
        super(tableModel);
        this.tableModel = tableModel;
        ModernTableStyle.applyLayout(this);
        TableRowSorter<ParameterListTableModel> sorter =
                new TableRowSorter<ParameterListTableModel>(tableModel);
        sorter.setSortable(0, false);
        sorter.setSortable(1, true);
        sorter.setSortable(2, false);
        setRowSorter(sorter);
        getColumnModel().getColumn(1).setCellRenderer(
                new ModernTableStyle.TokenRenderer(ThemeToken.LIVE_TRACE));
        if (EcuLogger.isTouchEnabled() == true)
        {
            this.setRowHeight(40);
            
            Font font = new Font("Tahoma", Font.PLAIN, 16);
            this.setFont(font);
        }
    }
    
    public TableCellRenderer getCellRenderer(int row, int col) {
        return displayComboBox(row, col) ? comboBoxRenderer : super.getCellRenderer(row, col);
    }

    public TableCellEditor getCellEditor(int row, int col) {
        return displayComboBox(row, col) ? comboBoxEditor : super.getCellEditor(row, col);
    }

    public String getToolTipText(MouseEvent mouseEvent) {
        List<ParameterRow> parameterRows = tableModel.getParameterRows();
        int viewRow = rowAtPoint(mouseEvent.getPoint());
        if (!isNullOrEmpty(parameterRows) && viewRow >= 0) {
            int modelRow = convertRowIndexToModel(viewRow);
            if (modelRow < 0 || modelRow >= parameterRows.size()) {
                return super.getToolTipText(mouseEvent);
            }
            ParameterRow parameterRow = parameterRows.get(modelRow);
            if (parameterRow != null) {
                String description = parameterRow.getLoggerData().getDescription();
                if (!isNullOrEmpty(description)) {
                    return description;
                }
            }
        }
        return super.getToolTipText(mouseEvent);
    }

    private boolean displayComboBox(int row, int col) {
        Object value = getValueAt(row, col);
        if (EcuData.class.isAssignableFrom(value.getClass())) {
            EcuData ecuData = (EcuData) value;
            if (ecuData.getConvertors().length > 1)
                return true;
        }
        if (ExternalData.class.isAssignableFrom(value.getClass())) {
            ExternalData externalData = (ExternalData) value;
            if (externalData.getConvertors().length > 1)
                return true;
        }
        return false;
    }

    public void setFilterText(String filterText) {
        @SuppressWarnings("unchecked")
        TableRowSorter<ParameterListTableModel> sorter =
                (TableRowSorter<ParameterListTableModel>) getRowSorter();
        String query = filterText == null ? "" : filterText.trim();
        sorter.setRowFilter(query.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(query), 1, 2));
    }
}
