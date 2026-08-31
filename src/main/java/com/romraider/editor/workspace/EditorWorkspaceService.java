/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.util.SettingsManager;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.romraider.search.SearchEntry;
import com.romraider.search.SearchKind;
import com.romraider.search.UnifiedSearchIndex;
import com.romraider.swing.TableTreeNode;

public final class EditorWorkspaceService {
    private static final EditorWorkspaceService INSTANCE = new EditorWorkspaceService();
    private final EditorNavigationHistory navigation = new EditorNavigationHistory();

    private EditorWorkspaceService() {
    }

    public static EditorWorkspaceService getInstance() {
        return INSTANCE;
    }

    public void tableOpened(Rom rom, Table table) {
        TableLocation location = location(rom, table);
        preferences().recordRecent(location);
        preferences().markOpen(location);
        preferences().markActive(location);
        navigation.visit(location);
    }

    public void tableActivated(Rom rom, Table table) {
        preferences().markActive(location(rom, table));
    }

    public void tableClosed(Rom rom, Table table) {
        preferences().markClosed(location(rom, table));
    }

    public void tableOrderChanged(String romId, List<String> tableNames) {
        preferences().replaceOpenTables(romId, tableNames);
    }

    public void indexRom(Rom rom) {
        String romId = romIdentity(rom);
        List<SearchEntry> entries = new ArrayList<SearchEntry>();
        for (TableTreeNode node : rom.getTableNodes().values()) {
            Table table = node.getTable();
            entries.add(new SearchEntry(SearchKind.TABLE, romId,
                    table.getName(), table.getName(), table.getCategory(),
                    table.getDescription(), Arrays.asList(rom.getFileName(), romId)));
        }
        UnifiedSearchIndex.getInstance().replaceSource(searchSource(romId), entries);
    }

    public void removeRomFromIndex(Rom rom) {
        UnifiedSearchIndex.getInstance().removeSource(searchSource(romIdentity(rom)));
    }

    /** Changed maps in open-ROM order and per-ROM impact order. */
    public Map<TableLocation, Integer> changedTables(
            Iterable<? extends Rom> roms) {
        if (roms == null) return Collections.emptyMap();
        Map<TableLocation, Integer> changed =
                new LinkedHashMap<TableLocation, Integer>();
        for (Rom rom : roms) {
            if (rom == null) continue;
            for (TableChangeSummary summary : RomChangeSummary.summarize(rom)) {
                changed.put(new TableLocation(romIdentity(rom),
                        summary.getTableName()), summary.getChangedCells());
            }
        }
        return Collections.unmodifiableMap(changed);
    }

    public String getTableNote(Rom rom, Table table) {
        return preferences().getTableNote(location(rom, table));
    }

    public void setTableNote(Rom rom, Table table, String note) {
        preferences().setTableNote(location(rom, table), note);
    }

    public boolean toggleFavorite(Rom rom, Table table) {
        return preferences().toggleFavorite(location(rom, table));
    }

    public boolean isFavorite(Rom rom, Table table) {
        return preferences().isFavorite(location(rom, table));
    }

    public boolean removeFavorite(TableLocation location) {
        return preferences().removeFavorite(location);
    }

    public EditorWorkspacePreferences preferences() {
        return SettingsManager.getSettings().getEditorWorkspacePreferences();
    }

    public EditorNavigationHistory navigation() {
        return navigation;
    }

    public static String romIdentity(Rom rom) {
        if (rom == null) throw new IllegalArgumentException("ROM is required");
        String identity = rom.getRomIDString();
        if (identity == null || identity.trim().isEmpty()) identity = rom.getFileName();
        if (identity == null || identity.trim().isEmpty()) identity = "unknown-rom";
        return identity;
    }

    private static TableLocation location(Rom rom, Table table) {
        if (table == null) throw new IllegalArgumentException("Table is required");
        return new TableLocation(romIdentity(rom), table.getName());
    }

    private static String searchSource(String romId) {
        return "rom:" + romId;
    }
}
