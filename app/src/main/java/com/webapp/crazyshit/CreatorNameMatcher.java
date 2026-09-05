package com.webapp.crazyshit;

import java.text.Normalizer;
import java.util.Locale;

/** Shared matching rules for typed names, cached creators and favorite sorting. */
final class CreatorNameMatcher {
    private CreatorNameMatcher() { }

    static String normalized(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim()
                .replaceAll("\\s+", " ");
    }

    static int rank(String name, String query) {
        String candidate = normalized(name);
        String needle = normalized(query);
        if (needle.isEmpty()) return 0;
        if (candidate.equals(needle)) return 0;
        String compactName = candidate.replace(" ", "");
        String compactQuery = needle.replace(" ", "");
        if (compactName.equals(compactQuery)) return 0;
        if (candidate.startsWith(needle) || compactName.startsWith(compactQuery)) return 1;
        if (candidate.contains(" " + needle)) return 2;
        if (candidate.contains(needle) || compactName.contains(compactQuery)) return 3;
        return Integer.MAX_VALUE;
    }
}
