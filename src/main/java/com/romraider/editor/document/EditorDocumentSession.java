/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.romraider.editor.workspace.RomChangeService;
import com.romraider.editor.workspace.RomChangeSummary;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.maps.UserLevelException;
import com.romraider.maps.history.EditHistoryListener;
import com.romraider.maps.history.RomEditHistory;

/**
 * UI-neutral source of truth for open ROMs, table documents and edit state.
 * Swing and Compose adapt this same session during the migration.
 */
public final class EditorDocumentSession implements AutoCloseable {
    public interface Listener {
        void sessionChanged(EditorDocumentSnapshot snapshot);
    }

    private final Map<Rom, State> documents =
            new LinkedHashMap<Rom, State>();
    private final CopyOnWriteArrayList<Listener> listeners =
            new CopyOnWriteArrayList<Listener>();
    private final EditHistoryListener historyListener = rom -> changed(rom);
    private Rom activeRom;
    private long revision;
    private boolean closed;

    public EditorDocumentSession() {
        RomEditHistory.getInstance().addListener(historyListener);
    }

    public void openRom(Rom rom) {
        if (rom == null) throw new IllegalArgumentException("ROM is required");
        synchronized (this) {
            requireOpen();
            if (!documents.containsKey(rom)) {
                documents.put(rom, new State());
                revision++;
            }
            if (activeRom == null) activeRom = rom;
        }
        notifyListeners();
    }

    public void closeRom(Rom rom) {
        if (rom == null) return;
        boolean changed;
        synchronized (this) {
            requireOpen();
            changed = documents.remove(rom) != null;
            if (activeRom == rom) {
                activeRom = documents.isEmpty()
                        ? null : documents.keySet().iterator().next();
            }
            if (changed) revision++;
        }
        if (changed) notifyListeners();
    }

    public void activateRom(Rom rom) {
        if (rom == null) {
            synchronized (this) {
                requireOpen();
                if (activeRom == null) return;
                activeRom = null;
                revision++;
            }
            notifyListeners();
            return;
        }
        synchronized (this) {
            requireOpen();
            requireDocument(rom);
            if (activeRom == rom) return;
            activeRom = rom;
            revision++;
        }
        notifyListeners();
    }

    public void openTable(Rom rom, Table table) {
        validateTable(rom, table);
        synchronized (this) {
            requireOpen();
            State state = requireDocument(rom);
            boolean added = state.openTables.add(table);
            boolean activated = activeRom != rom || state.activeTable != table;
            activeRom = rom;
            state.activeTable = table;
            if (added || activated) revision++;
            else return;
        }
        notifyListeners();
    }

    public void activateTable(Rom rom, Table table) {
        validateTable(rom, table);
        synchronized (this) {
            requireOpen();
            State state = requireDocument(rom);
            if (!state.openTables.contains(table)) {
                throw new IllegalStateException("Table is not open");
            }
            if (activeRom == rom && state.activeTable == table) return;
            activeRom = rom;
            state.activeTable = table;
            revision++;
        }
        notifyListeners();
    }

    public void closeTable(Rom rom, Table table) {
        if (rom == null || table == null) return;
        boolean changed;
        synchronized (this) {
            requireOpen();
            State state = requireDocument(rom);
            changed = state.openTables.remove(table);
            if (state.activeTable == table) {
                state.activeTable = state.openTables.isEmpty() ? null
                        : last(state.openTables);
            }
            if (changed) revision++;
        }
        if (changed) notifyListeners();
    }

    public void undo() throws UserLevelException {
        Rom rom;
        synchronized (this) { rom = activeRom; }
        if (rom != null) RomEditHistory.getInstance().undo(rom);
    }

    public void redo() throws UserLevelException {
        Rom rom;
        synchronized (this) { rom = activeRom; }
        if (rom != null) RomEditHistory.getInstance().redo(rom);
    }

    public synchronized EditorDocumentSnapshot snapshot() {
        List<EditorDocument> snapshots = new ArrayList<EditorDocument>();
        RomEditHistory history = RomEditHistory.getInstance();
        for (Map.Entry<Rom, State> entry : documents.entrySet()) {
            Rom rom = entry.getKey();
            State state = entry.getValue();
            snapshots.add(new EditorDocument(rom,
                    new ArrayList<Table>(state.openTables), state.activeTable,
                    RomChangeSummary.countChangedCells(rom),
                    RomChangeService.hasBinaryChanges(rom),
                    history.canUndo(rom), history.canRedo(rom)));
        }
        State active = documents.get(activeRom);
        return new EditorDocumentSnapshot(snapshots, activeRom,
                active == null ? null : active.activeTable, revision);
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Publish a save's new name/baseline even when no edit-history event fires. */
    public void refresh(Rom rom) {
        changed(rom);
    }

    private void changed(Rom rom) {
        synchronized (this) {
            if (closed || !documents.containsKey(rom)) return;
            revision++;
        }
        notifyListeners();
    }

    private void notifyListeners() {
        EditorDocumentSnapshot next = snapshot();
        for (Listener listener : listeners) listener.sessionChanged(next);
    }

    private State requireDocument(Rom rom) {
        State state = documents.get(rom);
        if (state == null) throw new IllegalStateException("ROM is not open");
        return state;
    }

    private static void validateTable(Rom rom, Table table) {
        if (rom == null || table == null) {
            throw new IllegalArgumentException("ROM and table are required");
        }
        if (table.getRom() != rom || !rom.getTableCatalog().contains(table)) {
            throw new IllegalArgumentException("Table does not belong to ROM");
        }
    }

    private static Table last(LinkedHashSet<Table> tables) {
        Table last = null;
        for (Table table : tables) last = table;
        return last;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Session is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        documents.clear();
        activeRom = null;
        listeners.clear();
        RomEditHistory.getInstance().removeListener(historyListener);
    }

    private static final class State {
        private final LinkedHashSet<Table> openTables =
                new LinkedHashSet<Table>();
        private Table activeTable;
    }
}
