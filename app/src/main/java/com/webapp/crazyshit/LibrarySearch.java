package com.webapp.crazyshit;

import java.text.Normalizer;
import java.util.Locale;

/** Shared local search matching for Library media destinations. */
final class LibrarySearch {
    private LibrarySearch() {
    }

    static boolean matches(String query, String... values) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return true;

        StringBuilder haystack = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                String normalized = normalize(value);
                if (normalized.isEmpty()) continue;
                if (haystack.length() > 0) haystack.append(' ');
                haystack.append(normalized);
            }
        }

        if (haystack.length() == 0) return false;
        String searchable = haystack.toString();
        for (String token : normalizedQuery.split(" ")) {
            if (token.isEmpty()) continue;
            if (!searchable.contains(token)) return false;
        }
        return true;
    }

    static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }
}
