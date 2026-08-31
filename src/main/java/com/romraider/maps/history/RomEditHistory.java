/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.maps.history;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.romraider.maps.DataCell;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.maps.UserLevelException;

/** UI-independent, bounded undo/redo history grouped by open ROM. */
public final class RomEditHistory {
    private static final int MAX_BATCHES = 250;
    private static final RomEditHistory INSTANCE = new RomEditHistory();

    private final Map<Rom, HistoryState> states =
            new WeakHashMap<Rom, HistoryState>();
    private final List<EditHistoryListener> listeners =
            new CopyOnWriteArrayList<EditHistoryListener>();
    private final ThreadLocal<PendingTransaction> transaction =
            new ThreadLocal<PendingTransaction>();
    private final ThreadLocal<Boolean> replaying = new ThreadLocal<Boolean>();

    public static RomEditHistory getInstance() { return INSTANCE; }

    public EditTransaction begin(Table table, String description) {
        return begin(table == null ? null : table.getRom(), description);
    }

    public EditTransaction begin(Rom rom, String description) {
        PendingTransaction pending = transaction.get();
        if (pending == null) {
            pending = new PendingTransaction(new EditBatch(rom, description));
            transaction.set(pending);
        } else {
            pending.depth++;
        }
        return new EditTransaction(this);
    }

    public void recordChange(Rom rom, DataCell cell, double oldValue,
            double newValue) {
        if (rom == null || cell == null || isReplaying()
                || Double.compare(oldValue, newValue) == 0) return;
        PendingTransaction pending = transaction.get();
        if (pending != null && pending.batch.getRom() == rom) {
            pending.batch.add(cell, oldValue, newValue);
            return;
        }
        EditBatch single = new EditBatch(rom, "Edit "
                + (cell.getTable() == null ? "calibration"
                        : cell.getTable().getName()));
        single.add(cell, oldValue, newValue);
        commit(single);
    }

    void endTransaction() {
        PendingTransaction pending = transaction.get();
        if (pending == null) return;
        pending.depth--;
        if (pending.depth > 0) return;
        transaction.remove();
        commit(pending.batch);
    }

    public synchronized boolean canUndo(Rom rom) {
        HistoryState state = states.get(rom);
        return state != null && !state.undo.isEmpty();
    }

    public synchronized boolean canRedo(Rom rom) {
        HistoryState state = states.get(rom);
        return state != null && !state.redo.isEmpty();
    }

    public synchronized String nextUndoDescription(Rom rom) {
        HistoryState state = states.get(rom);
        return state == null || state.undo.isEmpty() ? "" :
                state.undo.peekLast().getDescription();
    }

    public synchronized String nextRedoDescription(Rom rom) {
        HistoryState state = states.get(rom);
        return state == null || state.redo.isEmpty() ? "" :
                state.redo.peekLast().getDescription();
    }

    public synchronized int undoDepth(Rom rom) {
        HistoryState state = states.get(rom);
        return state == null ? 0 : state.undo.size();
    }

    /** Newest-first immutable view for history UIs and tooling. */
    public synchronized List<EditHistoryEntry> undoHistory(Rom rom) {
        HistoryState state = states.get(rom);
        return state == null ? Collections.<EditHistoryEntry>emptyList()
                : summaries(state.undo);
    }

    /** Newest-first immutable view of operations currently available to redo. */
    public synchronized List<EditHistoryEntry> redoHistory(Rom rom) {
        HistoryState state = states.get(rom);
        return state == null ? Collections.<EditHistoryEntry>emptyList()
                : summaries(state.redo);
    }

    public void undo(Rom rom) throws UserLevelException {
        EditBatch batch;
        synchronized (this) {
            HistoryState state = states.get(rom);
            if (state == null || state.undo.isEmpty()) return;
            batch = state.undo.removeLast();
        }
        try {
            replay(batch, false);
        } catch (UserLevelException exception) {
            synchronized (this) { state(rom).undo.addLast(batch); }
            notifyListeners(rom);
            throw exception;
        }
        synchronized (this) { state(rom).redo.addLast(batch); }
        notifyListeners(rom);
    }

    public void redo(Rom rom) throws UserLevelException {
        EditBatch batch;
        synchronized (this) {
            HistoryState state = states.get(rom);
            if (state == null || state.redo.isEmpty()) return;
            batch = state.redo.removeLast();
        }
        try {
            replay(batch, true);
        } catch (UserLevelException exception) {
            synchronized (this) { state(rom).redo.addLast(batch); }
            notifyListeners(rom);
            throw exception;
        }
        synchronized (this) { state(rom).undo.addLast(batch); }
        notifyListeners(rom);
    }

    public synchronized void clear(Rom rom) {
        if (rom == null) return;
        states.remove(rom);
        notifyListeners(rom);
    }

    public void addListener(EditHistoryListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(EditHistoryListener listener) {
        listeners.remove(listener);
    }

    private void replay(EditBatch batch, boolean redo)
            throws UserLevelException {
        replaying.set(Boolean.TRUE);
        try {
            if (redo) batch.redo(); else batch.undo();
        } finally {
            replaying.remove();
        }
    }

    private boolean isReplaying() {
        return Boolean.TRUE.equals(replaying.get());
    }

    private void commit(EditBatch batch) {
        if (batch == null || batch.getRom() == null || batch.isEmpty()) return;
        synchronized (this) {
            HistoryState state = state(batch.getRom());
            state.undo.addLast(batch);
            state.redo.clear();
            while (state.undo.size() > MAX_BATCHES) state.undo.removeFirst();
        }
        notifyListeners(batch.getRom());
    }

    private HistoryState state(Rom rom) {
        HistoryState state = states.get(rom);
        if (state == null) {
            state = new HistoryState();
            states.put(rom, state);
        }
        return state;
    }

    private void notifyListeners(Rom rom) {
        for (EditHistoryListener listener :
                new ArrayList<EditHistoryListener>(listeners)) {
            listener.historyChanged(rom);
        }
    }

    private static List<EditHistoryEntry> summaries(Deque<EditBatch> batches) {
        List<EditHistoryEntry> entries = new ArrayList<EditHistoryEntry>();
        Iterator<EditBatch> newestFirst = batches.descendingIterator();
        while (newestFirst.hasNext()) {
            entries.add(newestFirst.next().summary());
        }
        return Collections.unmodifiableList(entries);
    }

    private static final class PendingTransaction {
        private final EditBatch batch;
        private int depth = 1;

        private PendingTransaction(EditBatch batch) { this.batch = batch; }
    }

    private static final class HistoryState {
        private final Deque<EditBatch> undo = new ArrayDeque<EditBatch>();
        private final Deque<EditBatch> redo = new ArrayDeque<EditBatch>();
    }
}
