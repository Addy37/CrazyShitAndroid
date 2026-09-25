package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PlaybackHistoryStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void showsContinueWatchingOnlyIncludesShowsVideos() {
        PlaybackHistoryStore.record(
                context,
                "Show clip",
                "https://crazyshit.com/video/show-clip/",
                "https://cdn.example.com/show-clip.jpg",
                45_000L,
                120_000L,
                false,
                true
        );
        PlaybackHistoryStore.record(
                context,
                "Home clip",
                "https://crazyshit.com/video/home-clip/",
                "https://cdn.example.com/home-clip.jpg",
                50_000L,
                120_000L,
                false,
                false
        );

        List<PlaybackHistoryStore.Item> shows =
                PlaybackHistoryStore.continueWatchingShows(context);

        assertEquals(1, shows.size());
        assertEquals("Show clip", shows.get(0).title);
        assertEquals("https://cdn.example.com/show-clip.jpg", shows.get(0).posterUrl);
        assertTrue(shows.get(0).fromShows);
        assertEquals(37, shows.get(0).progressPercent());
    }

    @Test
    public void laterPlaybackKeepsKnownShowsClassificationAndPoster() {
        String pageUrl = "https://crazyshit.com/video/show-clip/";
        PlaybackHistoryStore.record(
                context,
                "Show clip",
                pageUrl,
                "https://cdn.example.com/show-clip.jpg",
                45_000L,
                120_000L,
                false,
                true
        );

        PlaybackHistoryStore.record(
                context,
                "Show clip",
                pageUrl,
                60_000L,
                120_000L,
                false
        );

        List<PlaybackHistoryStore.Item> shows =
                PlaybackHistoryStore.continueWatchingShows(context);

        assertEquals(1, shows.size());
        assertTrue(shows.get(0).fromShows);
        assertEquals("https://cdn.example.com/show-clip.jpg", shows.get(0).posterUrl);
        assertEquals(50, shows.get(0).progressPercent());
    }

    @Test
    public void completedShowsVideoDropsOutOfContinueWatching() {
        PlaybackHistoryStore.record(
                context,
                "Finished clip",
                "https://crazyshit.com/video/finished/",
                "",
                119_000L,
                120_000L,
                false,
                true
        );

        assertTrue(PlaybackHistoryStore.continueWatchingShows(context).isEmpty());
        PlaybackHistoryStore.Item item = PlaybackHistoryStore.load(context).get(0);
        assertTrue(item.complete);
        assertFalse(item.posterUrl == null);
    }
}
