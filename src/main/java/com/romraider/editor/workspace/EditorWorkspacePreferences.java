/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EditorWorkspacePreferences implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_RECENT_TABLES = 30;

    private final Set<TableLocation> favorites = new LinkedHashSet<TableLocation>();
    private final LinkedList<TableLocation> recent = new LinkedList<TableLocation>();
    private final Map<String, LinkedHashSet<String>> openTables =
            new LinkedHashMap<String, LinkedHashSet<String>>();
    private final Map<String, String> activeTables =
            new LinkedHashMap<String, String>();
    private final Map<TableLocation, String> tableNotes =
            new LinkedHashMap<TableLocation, String>();

    public synchronized boolean toggleFavorite(TableLocation location) {
        if (favorites.remove(location)) return false;
        favorites.add(location);
        return true;
    }

    public synchronized void addFavorite(TableLocation location) {
        favorites.add(location);
    }

    public synchronized boolean removeFavorite(TableLocation location) {
        return location != null && favorites.remove(location);
    }

    public synchronized boolean isFavorite(TableLocation location) {
        return favorites.contains(location);
    }

    public synchronized List<TableLocation> getFavorites() {
        return Collections.unmodifiableList(new ArrayList<TableLocation>(favorites));
    }

    public synchronized void recordRecent(TableLocation location) {
        recent.remove(location);
        recent.addFirst(location);
        while (recent.size() > MAX_RECENT_TABLES) recent.removeLast();
    }

    public synchronized void appendRecent(TableLocation location) {
        if (recent.contains(location)) return;
        if (recent.size() < MAX_RECENT_TABLES) recent.addLast(location);
    }

    public synchronized List<TableLocation> getRecent() {
        return Collections.unmodifiableList(new ArrayList<TableLocation>(recent));
    }

    public synchronized void markOpen(TableLocation location) {
        LinkedHashSet<String> names = openTables.get(location.getRomId());
        if (names == null) {
            names = new LinkedHashSet<String>();
            openTables.put(location.getRomId(), names);
        }
        names.add(location.getTableName());
    }

    public synchronized void markClosed(TableLocation location) {
        LinkedHashSet<String> names = openTables.get(location.getRomId());
        if (names == null) return;
        names.remove(location.getTableName());
        if (names.isEmpty()) openTables.remove(location.getRomId());
        if (location.getTableName().equals(activeTables.get(location.getRomId()))) {
            activeTables.remove(location.getRomId());
        }
    }

    public synchronized void markActive(TableLocation location) {
        markOpen(location);
        activeTables.put(location.getRomId(), location.getTableName());
    }

    public synchronized String getActiveTable(String romId) {
        return activeTables.get(romId);
    }

    public synchronized void replaceOpenTables(String romId, List<String> tableNames) {
        if (romId == null || romId.trim().isEmpty()) return;
        LinkedHashSet<String> ordered = new LinkedHashSet<String>();
        if (tableNames != null) {
            for (String tableName : tableNames) {
                if (tableName != null && !tableName.trim().isEmpty()) {
                    ordered.add(tableName.trim());
                }
            }
        }
        if (ordered.isEmpty()) {
            openTables.remove(romId);
            activeTables.remove(romId);
        } else {
            openTables.put(romId, ordered);
            String active = activeTables.get(romId);
            if (active != null && !ordered.contains(active)) {
                activeTables.remove(romId);
            }
        }
    }

    public synchronized List<String> getOpenTables(String romId) {
        LinkedHashSet<String> names = openTables.get(romId);
        if (names == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<String>(names));
    }

    public synchronized Map<String, List<String>> getAllOpenTables() {
        Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : openTables.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public synchronized void setTableNote(TableLocation location, String note) {
        String value = note == null ? "" : note;
        if (value.trim().isEmpty()) tableNotes.remove(location);
        else tableNotes.put(location, value);
    }

    public synchronized String getTableNote(TableLocation location) {
        String note = tableNotes.get(location);
        return note == null ? "" : note;
    }

    public synchronized Map<TableLocation, String> getAllTableNotes() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<TableLocation, String>(tableNotes));
    }
}
