/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.romraider.maps.Rom;

/** Identity-safe weak association between ROMs and compatibility tree nodes. */
public final class SwingRomTreeRegistry {
    private static final List<Entry> NODES = new ArrayList<Entry>();

    private SwingRomTreeRegistry() { }

    public static synchronized SwingRomTreeNode nodeFor(Rom rom) {
        if (rom == null) return null;
        Iterator<Entry> entries = NODES.iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            Rom candidate = entry.rom.get();
            SwingRomTreeNode node = entry.node.get();
            if (candidate == null || node == null) {
                entries.remove();
            } else if (candidate == rom) {
                return node;
            }
        }
        SwingRomTreeNode node = new SwingRomTreeNode(rom);
        NODES.add(new Entry(rom, node));
        return node;
    }

    public static synchronized void forget(Rom rom) {
        Iterator<Entry> entries = NODES.iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            Rom candidate = entry.rom.get();
            if (candidate == null || candidate == rom
                    || entry.node.get() == null) {
                entries.remove();
            }
        }
    }

    private static final class Entry {
        private final WeakReference<Rom> rom;
        private final WeakReference<SwingRomTreeNode> node;

        private Entry(Rom rom, SwingRomTreeNode node) {
            this.rom = new WeakReference<Rom>(rom);
            this.node = new WeakReference<SwingRomTreeNode>(node);
        }
    }
}
