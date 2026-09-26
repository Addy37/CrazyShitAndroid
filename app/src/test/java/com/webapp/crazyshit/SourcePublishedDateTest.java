package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class SourcePublishedDateTest {
    @Test
    public void parsesCurrentSourceDateFormats() {
        long now = millis(2026, 9, 25, 12);

        assertEquals(
                millis(2026, 9, 24, 0),
                SourcePublishedDate.parse("09/24/26", now)
        );
        assertEquals(
                millis(2026, 9, 24, 0),
                SourcePublishedDate.parse("Thursday September 24, 2026", now)
        );
        assertEquals(
                millis(2026, 9, 23, 0),
                SourcePublishedDate.parse("Wednesday September 23", now)
        );
        assertEquals(
                millis(2026, 9, 25, 0),
                SourcePublishedDate.parse("today's crazy shit", now)
        );
    }

    @Test
    public void rollingWindowRejectsOlderItems() {
        long now = millis(2026, 9, 25, 12);
        assertTrue(SourcePublishedDate.isWithinLastDays(
                millis(2026, 9, 19, 0),
                now,
                7
        ));
        assertFalse(SourcePublishedDate.isWithinLastDays(
                millis(2026, 9, 17, 0),
                now,
                7
        ));
        assertFalse(SourcePublishedDate.isWithinLastDays(0L, now, 7));
    }

    private long millis(int year, int month, int day, int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, hour, 0, 0);
        return calendar.getTimeInMillis();
    }
}
