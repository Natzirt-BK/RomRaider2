/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2016 RomRaider.com
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

package com.romraider.logger.ecu.ui.handler.table;

import static com.romraider.util.ParamChecker.isNullOrEmpty;
import static java.util.Collections.synchronizedMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import javax.swing.SwingUtilities;

import com.romraider.logger.ecu.comms.query.Response;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.ui.handler.DataUpdateHandler;
import com.romraider.maps.Table;
import com.romraider.maps.Table2D;
import com.romraider.maps.Table3D;
import com.romraider.maps.TableView;
import com.romraider.swing.SwingTableViewRegistry;

public final class TableUpdateHandler implements DataUpdateHandler {
    private static final TableUpdateHandler INSTANCE = new TableUpdateHandler();
    private final Map<String, List<Table>> tableMap = synchronizedMap(new HashMap<String, List<Table>>());
    private final Set<String> unavailableParameters = new HashSet<String>();
    private volatile long registrationRevision;

    private TableUpdateHandler() {
        tableMap.clear();
    }

    @Override
    public void registerData(LoggerData loggerData) {
    }

    @Override
    public synchronized void handleDataUpdate(Response response) {
        if (tableMap.isEmpty()) return;
        List<Map.Entry<Table, String>> updates = new ArrayList<Map.Entry<Table, String>>();
        for (LoggerData data : response.getData()) {
            List<Table> tables = tableMap.get(data.getId());
            if (tables == null) continue;
            double value = response.getDataValue(data);
            if (!Double.isFinite(value)) {
                unavailableParameters.add(data.getId());
            } else {
                unavailableParameters.remove(data.getId());
                String formatted = data.getSelectedConvertor().format(value);
                for (Table table : tables) updates.add(new java.util.AbstractMap.SimpleImmutableEntry<Table, String>(table, formatted));
            }
        }
        List<Table> missing = new ArrayList<Table>();
        for (String id : unavailableParameters) {
            if (tableMap.containsKey(id)) missing.addAll(tableMap.get(id));
        }
        queueOverlayUpdate(updates, missing);
    }

    private void queueOverlayUpdate(List<Map.Entry<Table, String>> updates, List<Table> missing) {
        final long revision = registrationRevision;
        SwingUtilities.invokeLater(() -> {
            if (registrationRevision != revision) return;
            Set<TableView> blocked = Collections.newSetFromMap(new IdentityHashMap<TableView, Boolean>());
            for (Table table : missing) {
                TableView view = SwingTableViewRegistry.find(table);
                if (view != null) blocked.add(root(view));
            }
            for (TableView view : blocked) {
                if (view.getOverlayLog()) {
                    view.clearLiveDataTrace();
                    view.getToolbar().setLiveDataValue("NO VALID DATA");
                    view.repaint();
                }
            }
            for (Map.Entry<Table, String> entry : updates) {
                TableView view = SwingTableViewRegistry.find(entry.getKey());
                if (view != null && view.getOverlayLog() && !blocked.contains(root(view))) {
                    view.highlightLiveData(entry.getValue());
                }
            }
        });
    }

    private static TableView root(TableView view) {
        while (view.getAxisParent() != null) view = view.getAxisParent();
        return view;
    }

    @Override
    public void deregisterData(LoggerData loggerData) {
    }

    @Override
    public synchronized void cleanUp() {
        reset();
    	for(List<Table> t: tableMap.values())t.clear();
    	tableMap.clear();
    }

    @Override
    public synchronized void reset() {
        registrationRevision++;
        unavailableParameters.clear();
        List<Table> tables = new ArrayList<Table>();
        for (List<Table> values : tableMap.values()) tables.addAll(values);
        queueOverlayUpdate(Collections.emptyList(), tables);
    }

    public synchronized void registerTable(Table table) {
        registrationRevision++;
        String logParam = table.getLogParam();
        if (!isNullOrEmpty(logParam)) {
            if (!tableMap.containsKey(logParam)) {
                tableMap.put(logParam, new ArrayList<Table>());
            }
            tableMap.get(logParam).add(table);
        }
        registerAxes(table);
    }

    public synchronized void deregisterTable(Table table) {
    	if(table == null) return;
        registrationRevision++;
    	
        String logParam = table.getLogParam();
        if (tableMap.containsKey(logParam)) {
            List<Table> tables = tableMap.get(logParam);
            tables.remove(table);
            if (tables.isEmpty()) {
                tableMap.remove(logParam);
                unavailableParameters.remove(logParam);
            }
        }
        deregisterAxes(table);
    }

    public static TableUpdateHandler getInstance() {
        return INSTANCE;
    }

    private void registerAxes(Table table) {
        if (table instanceof Table2D) {
            registerTable(((Table2D) table).getAxis());
        }
        if (table instanceof Table3D) {
            registerTable(((Table3D) table).getXAxis());
            registerTable(((Table3D) table).getYAxis());
        }
    }

    private void deregisterAxes(Table table) {
        if (table instanceof Table2D) {
            deregisterTable(((Table2D) table).getAxis());
        }
        if (table instanceof Table3D) {
            deregisterTable(((Table3D) table).getXAxis());
            deregisterTable(((Table3D) table).getYAxis());
        }
    }

}
