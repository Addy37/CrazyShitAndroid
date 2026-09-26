package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class WeeklyShowsFeedTest {
    @Test
    public void keepsOnlyRecentPlayableItemsAndSortsNewestFirst() {
        long now = millis(2026, 9, 25, 12);
        NativeContentItem crazy = item(
                "Crazy",
                "https://crazyshit.com/cnt/medias/100",
                millis(2026, 9, 25, 0)
        );
        NativeContentItem efukt = item(
                "EFukt",
                "https://efukt.com/example/123_clip.html",
                millis(2026, 9, 24, 0)
        );
        NativeContentItem kaotic = item(
                "Kaotic",
                "https://kaotic.com/video/example",
                millis(2026, 9, 23, 0)
        );
        NativeContentItem old = item(
                "Old",
                "https://crazyshit.com/cnt/medias/99",
                millis(2026, 9, 10, 0)
        );
        NativeContentItem section = new NativeContentItem(
                NativeContentItem.KIND_SECTION,
                "Thursday September 24",
                "section:test",
                "",
                "",
                "",
                ""
        );

        List<NativeContentItem> result = WeeklyShowsFeed.build(
                Arrays.asList(old, kaotic, section, crazy, efukt, crazy),
                now
        );

        assertEquals(3, result.size());
        assertEquals(crazy.url, result.get(0).url);
        assertEquals(efukt.url, result.get(1).url);
        assertEquals(kaotic.url, result.get(2).url);
        assertEquals("CRAZYSHIT", WeeklyShowsFeed.sourceLabel(crazy));
        assertEquals("EFUKT", WeeklyShowsFeed.sourceLabel(efukt));
        assertEquals("KAOTIC", WeeklyShowsFeed.sourceLabel(kaotic));
    }

    private NativeContentItem item(String title, String url, long publishedAt) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title,
                url,
                "https://img.example/" + title + ".jpg",
                "",
                "",
                "",
                "",
                "",
                publishedAt
        );
    }

    private long millis(int year, int month, int day, int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, hour, 0, 0);
        return calendar.getTimeInMillis();
    }
}
