package com.webapp.crazyshit;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public final class RedditSourceRepositoryTest {
    @Test public void aspectRatioRoutesWideToMayhemAndTallToShitTok() {
        assertEquals(RedditSourceRepository.Placement.MAYHEM,
                RedditSourceRepository.placementFor(1920, 1080));
        assertEquals(RedditSourceRepository.Placement.SHITTOK,
                RedditSourceRepository.placementFor(1080, 1920));
        assertNull(RedditSourceRepository.placementFor(1080, 1080));
    }

    @Test public void listingParserKeepsNativeRedditVideoAndAttribution() {
        String json = "{\"data\":{\"after\":\"t3_next\",\"children\":["
                + post("wide1", 1920, 1080, "PublicFreakout", "sampleuser")
                + "," + post("tall1", 1080, 1920, "fightporn", "otheruser")
                + "]}}";

        RedditSourceRepository.ListingResult wide =
                RedditSourceRepository.parseListing(
                        json, RedditSourceRepository.Placement.MAYHEM, 10);
        assertEquals("t3_next", wide.after);
        assertEquals(1, wide.items.size());
        NativeContentItem mayhem = wide.items.get(0);
        assertEquals("https://www.reddit.com/r/PublicFreakout/comments/wide1/sample/", mayhem.url);
        assertEquals("r/PublicFreakout · u/sampleuser · via Reddit", mayhem.uploader);
        assertTrue(RedditSourceRepository.isRedditItem(mayhem));

        RedditSourceRepository.ListingResult tall =
                RedditSourceRepository.parseListing(
                        json, RedditSourceRepository.Placement.SHITTOK, 10);
        assertEquals(1, tall.items.size());
        assertTrue(tall.items.get(0).url.contains("/comments/tall1/"));
    }

    @Test public void squareVideoIsExcludedFromBothFeeds() {
        String json = "{\"data\":{\"children\":["
                + post("square1", 1080, 1080, "CrazyFuckingVideos", "sample")
                + "]}}";
        assertTrue(RedditSourceRepository.parseListing(
                json, RedditSourceRepository.Placement.MAYHEM, 10).items.isEmpty());
        assertTrue(RedditSourceRepository.parseListing(
                json, RedditSourceRepository.Placement.SHITTOK, 10).items.isEmpty());
    }

    private String post(String id, int width, int height, String subreddit, String author) {
        return "{\"data\":{"
                + "\"title\":\"Sample clip " + id + "\","
                + "\"subreddit\":\"" + subreddit + "\","
                + "\"author\":\"" + author + "\","
                + "\"permalink\":\"/r/" + subreddit + "/comments/" + id + "/sample/\","
                + "\"preview\":{\"images\":[{\"source\":{\"url\":\"https://preview.redd.it/" + id + ".jpg\"}}]},"
                + "\"secure_media\":{\"reddit_video\":{"
                + "\"fallback_url\":\"https://v.redd.it/" + id + "/DASH_720.mp4?source=fallback\","
                + "\"hls_url\":\"https://v.redd.it/" + id + "/HLSPlaylist.m3u8\","
                + "\"dash_url\":\"https://v.redd.it/" + id + "/DASHPlaylist.mpd\","
                + "\"is_gif\":false,"
                + "\"width\":" + width + ",\"height\":" + height
                + "}}}}";
    }
}
