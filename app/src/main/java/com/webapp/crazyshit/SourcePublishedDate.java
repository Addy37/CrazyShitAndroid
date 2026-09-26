package com.webapp.crazyshit;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SourcePublishedDate {
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private static final Pattern NUMERIC = Pattern.compile(
            "\\b(\\d{1,2})/(\\d{1,2})/(\\d{2}|\\d{4})\\b"
    );
    private static final Pattern NAMED = Pattern.compile(
            "(?i)(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)?\\s*" +
            "(january|february|march|april|may|june|july|august|september|october|november|december)\\s+" +
            "(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?"
    );

    private SourcePublishedDate() {
    }

    static long parse(String value, long nowMillis) {
        if (value == null) return 0L;
        String text = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return 0L;

        String lower = text.toLowerCase(Locale.US);
        if (lower.contains("today's crazy shit") || lower.contains("today’s crazy shit")
                || lower.equals("today")) {
            return startOfDay(nowMillis);
        }

        Matcher numeric = NUMERIC.matcher(text);
        if (numeric.find()) {
            int month = parseInt(numeric.group(1));
            int day = parseInt(numeric.group(2));
            int year = parseInt(numeric.group(3));
            if (year < 100) year += year >= 70 ? 1900 : 2000;
            return dateMillis(year, month, day);
        }

        Matcher named = NAMED.matcher(text);
        if (!named.find()) return 0L;
        int month = month(named.group(1));
        int day = parseInt(named.group(2));
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        int year = named.group(3) == null
                ? now.get(Calendar.YEAR)
                : parseInt(named.group(3));
        long parsed = dateMillis(year, month, day);
        if (named.group(3) == null && parsed > nowMillis + DAY_MS) {
            parsed = dateMillis(year - 1, month, day);
        }
        return parsed;
    }

    static boolean isWithinLastDays(long publishedAtMillis, long nowMillis, int days) {
        if (publishedAtMillis <= 0L || days <= 0) return false;
        long cutoff = nowMillis - (days * DAY_MS);
        return publishedAtMillis >= cutoff && publishedAtMillis <= nowMillis + DAY_MS;
    }

    private static long startOfDay(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long dateMillis(int year, int month, int day) {
        if (year < 1970 || month < 1 || month > 12 || day < 1 || day > 31) return 0L;
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.setLenient(false);
        try {
            calendar.set(year, month - 1, day, 0, 0, 0);
            return calendar.getTimeInMillis();
        } catch (IllegalArgumentException invalid) {
            return 0L;
        }
    }

    private static int month(String value) {
        if (value == null) return 0;
        switch (value.toLowerCase(Locale.US)) {
            case "january": return 1;
            case "february": return 2;
            case "march": return 3;
            case "april": return 4;
            case "may": return 5;
            case "june": return 6;
            case "july": return 7;
            case "august": return 8;
            case "september": return 9;
            case "october": return 10;
            case "november": return 11;
            case "december": return 12;
            default: return 0;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }
}
