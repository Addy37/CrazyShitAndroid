package com.webapp.crazyshit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationCoordinatorFormattingTest {
    @Test
    public void consolidatesProviderAlertsIntoOnlyFapAndShitTok() {
        List<NotificationCoordinator.SourceAlert> alerts = Arrays.asList(
                alert("fapello", "Fapello", media(
                        "OnlyFap video #32318226",
                        "https://fapello.com/video/emily-rinaudo/32318226/"
                )),
                alert("bunkr", "Bunkr", series(
                        "Emily Rinaudo | EmjayyPlays",
                        "https://bunkr.example/a/emily"
                )),
                alert("crazyshit", "CrazyShit", media(
                        "Home clip",
                        "https://example.com/home"
                )),
                alert("efukt", "EFukt", media(
                        "EFukt clip",
                        "https://example.com/efukt"
                )),
                alert("kaotic", "Kaotic", media(
                        "Kaotic clip",
                        "https://example.com/kaotic"
                ))
        );

        List<NotificationCoordinator.ExperienceAlert> grouped =
                NotificationCoordinator.consolidateAlerts(alerts);

        assertEquals(2, grouped.size());
        assertEquals("onlyfap", grouped.get(0).key);
        assertEquals("OnlyFap", grouped.get(0).label);
        assertEquals(2, grouped.get(0).items.size());
        assertEquals("shittok", grouped.get(1).key);
        assertEquals("ShitTok", grouped.get(1).label);
        assertEquals(3, grouped.get(1).items.size());
    }

    @Test
    public void onlyFapLinesGroupFapelloVideosByCreatorWithoutRawIds() {
        NotificationCoordinator.SourceAlert fapello = new NotificationCoordinator.SourceAlert(
                "fapello",
                "Fapello",
                Arrays.asList(
                        media(
                                "OnlyFap video #32318226",
                                "https://fapello.com/video/emily-rinaudo/32318226/"
                        ),
                        media(
                                "OnlyFap video #31303166",
                                "https://fapello.com/video/emily-rinaudo/31303166/"
                        ),
                        media(
                                "OnlyFap video #32266421",
                                "https://fapello.com/video/grace-bartlow/32266421/"
                        )
                )
        );

        NotificationCoordinator.ExperienceAlert onlyFap =
                NotificationCoordinator.consolidateAlerts(
                        Arrays.asList(fapello)
                ).get(0);
        List<String> lines = NotificationCoordinator.notificationLines(onlyFap);

        assertEquals(2, lines.size());
        assertEquals("Emily Rinaudo · 2 new videos", lines.get(0));
        assertEquals("Grace Bartlow · 1 new video", lines.get(1));
        for (String line : lines) {
            assertFalse(line.contains("#32318226"));
            assertFalse(line.contains("#31303166"));
            assertFalse(line.contains("#32266421"));
            assertFalse(line.contains("Fapello"));
        }
    }

    @Test
    public void onlyFapLinesUseReadableBunkrCreatorNames() {
        NotificationCoordinator.SourceAlert bunkr = new NotificationCoordinator.SourceAlert(
                "bunkr",
                "Bunkr",
                Arrays.asList(
                        series("Emily Rinaudo | EmjayyPlays | EmilyRinaudo", "https://bunkr.example/a/1"),
                        series("hay_nyash", "https://bunkr.example/a/2"),
                        series("Jillian Beyor - MJV", "https://bunkr.example/a/3"),
                        series("Vietcos's leaked - Miss Lam", "https://bunkr.example/a/4")
                )
        );

        NotificationCoordinator.ExperienceAlert onlyFap =
                NotificationCoordinator.consolidateAlerts(
                        Arrays.asList(bunkr)
                ).get(0);
        List<String> lines = NotificationCoordinator.notificationLines(onlyFap);

        assertTrue(lines.contains("Emily Rinaudo · new content"));
        assertTrue(lines.contains("hay nyash · new content"));
        assertTrue(lines.contains("Jillian Beyor · new content"));
        assertTrue(lines.contains("Miss Lam · new content"));
        for (String line : lines) assertFalse(line.contains("Bunkr"));
    }

    @Test
    public void genericFapelloIdsAreNeverShownWhenCreatorCannotBeRecovered() {
        NotificationCoordinator.ExperienceItem item = new NotificationCoordinator.ExperienceItem(
                "fapello",
                media(
                        "OnlyFap video #47578493",
                        "https://fapello.com/video/47578493/"
                )
        );

        assertEquals("", NotificationCoordinator.onlyFapCreatorName(item));

        NotificationCoordinator.ExperienceAlert onlyFap =
                new NotificationCoordinator.ExperienceAlert("onlyfap", "OnlyFap");
        onlyFap.items.add(item);
        List<String> lines = NotificationCoordinator.notificationLines(onlyFap);
        assertTrue(lines.isEmpty());
    }

    private static NotificationCoordinator.SourceAlert alert(
            String key,
            String label,
            NativeContentItem item
    ) {
        ArrayList<NativeContentItem> items = new ArrayList<>();
        items.add(item);
        return new NotificationCoordinator.SourceAlert(key, label, items);
    }

    private static NativeContentItem media(String title, String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title,
                url,
                "",
                "",
                "",
                ""
        );
    }

    private static NativeContentItem series(String title, String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_SERIES,
                title,
                url,
                "",
                "",
                "",
                ""
        );
    }
}
