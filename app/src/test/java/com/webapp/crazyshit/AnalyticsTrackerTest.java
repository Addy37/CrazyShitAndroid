package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;

public final class AnalyticsTrackerTest {
    @Test
    public void reportingDate_usesEasternMidnightDuringDaylightTime() {
        assertEquals(
                LocalDate.of(2026, 9, 18),
                AnalyticsTracker.reportingDate(Instant.parse("2026-09-19T03:59:59Z"))
        );
        assertEquals(
                LocalDate.of(2026, 9, 19),
                AnalyticsTracker.reportingDate(Instant.parse("2026-09-19T04:00:00Z"))
        );
    }

    @Test
    public void reportingDate_usesEasternMidnightDuringStandardTime() {
        assertEquals(
                LocalDate.of(2026, 1, 14),
                AnalyticsTracker.reportingDate(Instant.parse("2026-01-15T04:59:59Z"))
        );
        assertEquals(
                LocalDate.of(2026, 1, 15),
                AnalyticsTracker.reportingDate(Instant.parse("2026-01-15T05:00:00Z"))
        );
    }

    @Test
    public void reportingWeekAndMonth_useReportingCalendar() {
        LocalDate date = LocalDate.of(2026, 9, 18);
        assertEquals(LocalDate.of(2026, 9, 14), AnalyticsTracker.reportingWeek(date));
        assertEquals(LocalDate.of(2026, 9, 1), AnalyticsTracker.reportingMonth(date));
    }
}
