/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.search;

public final class SearchResult implements Comparable<SearchResult> {
    private final SearchEntry entry;
    private final int score;

    SearchResult(SearchEntry entry, int score) {
        this.entry = entry;
        this.score = score;
    }

    public SearchEntry getEntry() { return entry; }
    public int getScore() { return score; }

    public int compareTo(SearchResult other) {
        int scoreOrder = Integer.compare(other.score, score);
        if (scoreOrder != 0) return scoreOrder;
        int titleOrder = entry.getTitle().compareToIgnoreCase(
                other.entry.getTitle());
        if (titleOrder != 0) return titleOrder;
        int kindOrder = entry.getKind().compareTo(other.entry.getKind());
        if (kindOrder != 0) return kindOrder;
        return entry.getTargetId().compareToIgnoreCase(
                other.entry.getTargetId());
    }
}
