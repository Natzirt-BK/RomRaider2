/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.search;

import java.util.Locale;

/** Shared fuzzy scorer used by both map filtering and global search. */
public final class SearchScorer {
    private SearchScorer() {
    }

    public static int score(String title, String context, String description,
            Iterable<String> aliases, String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return 1;
        int best = fieldScore(title, normalizedQuery, 1000);
        best = Math.max(best, fieldScore(context, normalizedQuery, 650));
        best = Math.max(best, fieldScore(description, normalizedQuery, 400));
        if (aliases != null) {
            for (String alias : aliases) {
                best = Math.max(best, fieldScore(alias, normalizedQuery, 800));
            }
        }
        return best;
    }

    private static int fieldScore(String field, String query, int weight) {
        String value = normalize(field);
        if (value.isEmpty()) return 0;
        if (value.equals(query)) return weight + 300;
        if (value.startsWith(query)) return weight + 220 - lengthPenalty(value, query);
        int substring = value.indexOf(query);
        if (substring >= 0) return weight + 150 - substring - lengthPenalty(value, query);
        String[] queryWords = query.split(" ");
        int wordMatches = 0;
        for (String word : queryWords) {
            if (!word.isEmpty() && containsSubsequence(value, word)) wordMatches++;
        }
        if (wordMatches == queryWords.length) {
            return weight + 50 + (wordMatches * 10) - lengthPenalty(value, query);
        }
        return containsSubsequence(value.replace(" ", ""), query.replace(" ", ""))
                ? weight + 20 - lengthPenalty(value, query) : 0;
    }

    private static int lengthPenalty(String value, String query) {
        return Math.min(80, Math.max(0, value.length() - query.length()));
    }

    private static boolean containsSubsequence(String value, String query) {
        int queryIndex = 0;
        for (int i = 0; i < value.length() && queryIndex < query.length(); i++) {
            if (value.charAt(i) == query.charAt(queryIndex)) queryIndex++;
        }
        return queryIndex == query.length();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", " ").trim();
    }
}
