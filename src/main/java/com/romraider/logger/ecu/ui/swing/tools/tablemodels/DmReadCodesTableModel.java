/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2020 RomRaider.com
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

package com.romraider.logger.ecu.ui.swing.tools.tablemodels;

import com.romraider.util.ResourceUtil;

import javax.swing.table.DefaultTableModel;
import java.util.*;

public final class DmReadCodesTableModel extends DefaultTableModel {
    private static final long serialVersionUID = -4229632456594395331L;
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            ReadCodesTableModel.class.getName());
    private List<String> dtcAllList;
    private Set<String> dtcSet;
    private Set<String> dtcMemSet;

    public DmReadCodesTableModel(Set<String> dtcSet, Set<String> dtcMemSet) {
        this.dtcSet = dtcSet;
        this.dtcMemSet = dtcMemSet;
        Set<String> dtcAllSet = new TreeSet<>(dtcSet);
        dtcAllSet.addAll(dtcMemSet);
        this.dtcAllList = new ArrayList<>(dtcAllSet);
    }

    public final int getColumnCount() {
        return 3;
    }
    
    public final String getColumnName(int column) {
        switch (column) {
            case 0:     return rb.getString("DTCNAME");
            case 1:     return rb.getString("TEMPORARY");
            case 2:     return rb.getString("MEMORIZED");
            default:    return "";
        }
    }
    
    public final Object getValueAt(int row, int column) {
        if (dtcAllList != null && dtcAllList.size() > 0) {
            final String result = dtcAllList.get(row);
            switch (column) {
                case 0:
                        return " " + result;
                case 1: 
                        return dtcSet.contains(result);
                case 2: 
                        return dtcMemSet.contains(result);
                default:
                        return null;
            }
        }
        else {
            return null;
        }
    }
    
    public final int getRowCount() {
        return (dtcAllList != null) ? dtcAllList.size() : 0;
    }
    
    public final Class<? extends Object> getColumnClass(int column) {
        if (dtcAllList != null && dtcAllList.size() > 0) {
            return getValueAt(0, column).getClass();
        } else {
            return null;
        }
    }

    public final boolean isCellEditable(int row, int column) {
        return false;
    }
}
