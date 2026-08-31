/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.search;

public final class TableSearchResult implements Comparable<TableSearchResult> {
    private final TableSearchEntry entry;
    private final int score;

    TableSearchResult(TableSearchEntry entry, int score) {
        this.entry = entry;
        this.score = score;
    }

    public TableSearchEntry getEntry() { return entry; }
    public int getScore() { return score; }

    public int compareTo(TableSearchResult other) {
        int scoreOrder = Integer.compare(other.score, score);
        if (scoreOrder != 0) return scoreOrder;
        int nameOrder = entry.getName().compareToIgnoreCase(other.entry.getName());
        if (nameOrder != 0) return nameOrder;
        return entry.getRomId().compareToIgnoreCase(other.entry.getRomId());
    }
}
