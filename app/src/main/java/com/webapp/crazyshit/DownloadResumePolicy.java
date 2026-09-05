package com.webapp.crazyshit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reject changed resources and incorrect byte ranges before appending saved bytes. */
final class DownloadResumePolicy {
    private static final Pattern RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)", Pattern.CASE_INSENSITIVE);
    private DownloadResumePolicy() { }

    static boolean isMediaResponse(String contentType) {
        if (contentType == null) return true;
        String type = contentType.toLowerCase(java.util.Locale.ROOT);
        return !type.contains("text/html") && !type.contains("application/xhtml")
                && !type.contains("application/json") && !type.contains("mpegurl")
                && !type.contains("dash+xml");
    }

    static boolean sameResource(String previousValidator, String validator, long previousSize, long size) {
        return previousValidator != null && !previousValidator.isEmpty()
                && !previousValidator.startsWith("W/") && previousValidator.equals(validator)
                && previousSize > 0 && previousSize == size;
    }

    static boolean validRange(int status, String range, long start, long end, long total) {
        if (status != 206 || range == null || start < 0 || end < start || total <= end) return false;
        Matcher match = RANGE.matcher(range.trim());
        if (!match.matches()) return false;
        try {
            return Long.parseLong(match.group(1)) == start && Long.parseLong(match.group(2)) == end
                    && Long.parseLong(match.group(3)) == total;
        } catch (NumberFormatException invalid) { return false; }
    }
}
