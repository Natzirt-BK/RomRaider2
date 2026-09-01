/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.workspace;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.maps.UserLevelException;
import com.romraider.maps.history.EditTransaction;
import com.romraider.maps.history.RomEditHistory;

/** UI-independent saved-state and reset operations for an open ROM. */
public final class RomChangeService {
    private static final Map<Rom, byte[]> savedBinary =
            new WeakHashMap<Rom, byte[]>();
    private static final Set<Rom> forcedUnsaved = Collections.newSetFromMap(
            new WeakHashMap<Rom, Boolean>());

    private RomChangeService() {
    }

    public static void resetToSaved(Rom rom) throws UserLevelException {
        if (rom == null) return;
        try (EditTransaction edit = RomEditHistory.getInstance().begin(
                rom, "Reset ROM changes")) {
            for (Table table : rom.getTables()) table.undoAll();
        }
    }

    public static void markSaved(Rom rom) {
        if (rom == null) return;
        for (Table table : rom.getTables()) table.setRevertPoint();
        rememberSavedBinary(rom);
    }

    /** Keeps a recovered workspace dirty until it is explicitly saved. */
    public static synchronized void markUnsaved(Rom rom) {
        if (rom != null) forcedUnsaved.add(rom);
    }

    /** Captures the file state after a ROM has finished loading. */
    public static synchronized void rememberSavedBinary(Rom rom) {
        if (rom == null) return;
        byte[] binary = rom.getBinary();
        savedBinary.put(rom, binary == null ? null : binary.clone());
        forcedUnsaved.remove(rom);
    }

    /** Detects every binary change, including data not represented by a table. */
    public static synchronized boolean hasBinaryChanges(Rom rom) {
        if (rom == null || !savedBinary.containsKey(rom)) return false;
        return forcedUnsaved.contains(rom)
                || !Arrays.equals(savedBinary.get(rom), rom.getBinary());
    }

    /** Returns a defensive copy of the last loaded or explicitly saved state. */
    public static synchronized byte[] snapshotSavedBinary(Rom rom) {
        byte[] binary = savedBinary.get(rom);
        return binary == null ? null : binary.clone();
    }

    public static synchronized void forget(Rom rom) {
        if (rom != null) {
            savedBinary.remove(rom);
            forcedUnsaved.remove(rom);
        }
    }
}
