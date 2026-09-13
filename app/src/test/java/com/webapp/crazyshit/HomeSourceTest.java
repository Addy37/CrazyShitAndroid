package com.webapp.crazyshit;

import android.app.Application;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class HomeSourceTest {
    private NativeContentItem item(String url) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Sample video", url, "", "", "", "");
    }
    private NativeContentItem section(String title) {
        return new NativeContentItem(NativeContentItem.KIND_SECTION, title,
                "section:" + title.toLowerCase(Locale.US).replace(' ', '-'), "", "", "", "");
    }
    @Test public void fastSourcePublishesBeforeSlowSourceAndSurvivesFailure() throws Exception {
        CountDownLatch published = new CountDownLatch(1), releaseSlow = new CountDownLatch(1);
        HomeSourceRepository repo = new HomeSourceRepository((context, source, page) -> {
            if (source == 1) { releaseSlow.await(); throw new IOException("offline"); }
            if (source == 2) return Collections.singletonList(item("https://efukt.com/one"));
            published.await();
            return Collections.singletonList(item("https://efukt.com/one"));
        }, 2000);
        ExecutorService host = Executors.newSingleThreadExecutor();
        try {
            Future<List<NativeContentItem>> result = host.submit(() -> repo.fetch(null, 0, 1, values -> published.countDown()));
            assertTrue("A fast feed must publish while the slow source is blocked", published.await(1, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            releaseSlow.countDown();
            assertEquals(1, result.get(2, TimeUnit.SECONDS).size());
        } finally { releaseSlow.countDown(); host.shutdownNow(); }
    }
    @Test public void directCrazyShitSourceKeepsSectionHeaders() throws Exception {
        HomeSourceRepository repo = new HomeSourceRepository((context, source, page) -> Arrays.asList(
                section("TODAY'S CRAZY SHIT"),
                item("https://crazyshit.com/video/one")
        ), 1000);
        List<NativeContentItem> result = repo.fetch(null, 1, 1);
        assertEquals(2, result.size());
        assertTrue(result.get(0).isSection());
        assertEquals("TODAY'S CRAZY SHIT", result.get(0).title);
    }
    @Test public void emptyFirstPageIsRetryableButEmptyLaterPageEndsPagination() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HomeSourceRepository repo = new HomeSourceRepository((context, source, page) -> {
            calls.incrementAndGet(); return Collections.emptyList();
        }, 1000);
        try { repo.fetch(null, 3, 1); fail("Empty first page must report an unavailable source"); }
        catch (IOException expected) { }
        assertTrue(repo.fetch(null, 3, 2).isEmpty());
        assertEquals(2, calls.get());
    }
    @Test public void sourceTimeoutCancelsWork() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        HomeSourceRepository repo = new HomeSourceRepository((context, source, page) -> {
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException stop) { cancelled.countDown(); throw stop; }
            return Collections.emptyList();
        }, 100);
        try { repo.fetch(null, 1, 1); fail("Expected bounded timeout"); }
        catch (IOException expected) { }
        assertTrue(cancelled.await(1, TimeUnit.SECONDS));
    }
    @Test public void popularFeedUsesPublicPaginationAndKeepsPostsWithoutThumbnails() {
        assertEquals("https://fapello.com/popular_videos/week/", FapelloRepository.popularVideosUrl(1));
        assertEquals("https://fapello.com/popular_videos/week/page-2/", FapelloRepository.popularVideosUrl(2));
        String html = "<a href='/sample-creator/12/'>Video</a>"
                + "<a href='/sample-creator/12/'><img src='/thumb.jpg'></a>"
                + "<a href='/sample-creator/13/'>Another video</a><a href='/hot/'>Hot</a>";
        List<NativeContentItem> items = new FapelloRepository().parsePopularVideos(
                Jsoup.parse(html, FapelloRepository.BASE), FapelloRepository.BASE);
        assertEquals(2, items.size());
        assertEquals("https://fapello.com/thumb.jpg", items.get(0).imageUrl);
        assertEquals("", items.get(1).imageUrl);
    }

    @Test public void creatorListingsUseObservedPublicPaginationRoutes() throws Exception {
        assertEquals("https://fapello.com/", FapelloRepository.listingUrl("new", 1));
        assertEquals("https://fapello.com/page-2/", FapelloRepository.listingUrl("new", 2));
        assertEquals("https://fapello.com/hot/", FapelloRepository.listingUrl("hot", 1));
        assertEquals("https://fapello.com/hot-2/", FapelloRepository.listingUrl("hot", 2));
        assertEquals("https://fapello.com/popular-3/", FapelloRepository.listingUrl("popular", 3));
    }
    @Test public void weeklyVideoRoutesArePlayableAndDoNotDuplicateCanonicalPostLinks() {
        String url = "https://fapello.com/video/week/31913741/";
        assertTrue(FapelloRepository.isPostUrl(url));
        assertFalse(FapelloRepository.isPostUrl("https://other.example/video/week/31913741/"));
        String html = "<a href='" + url + "'><img data-src='/weekly.jpg'></a>"
                + "<a href='" + url + "'>Video</a><a href='/sample-creator/15/'>Creator post</a>";
        List<NativeContentItem> items = new FapelloRepository().parsePopularVideos(
                Jsoup.parse(html, FapelloRepository.BASE), FapelloRepository.BASE);
        assertEquals(1, items.size());
        assertEquals(url, items.get(0).url);
        assertEquals("https://fapello.com/weekly.jpg", items.get(0).imageUrl);
    }

    @SuppressWarnings("unchecked")
    @Test public void homeFeedSkipsSizingTileAndUsesRealThumbnail() throws Exception {
        String page = "https://crazyshit.com/";
        String media = "https://crazyshit.com/cnt/medias/220038-fighting-fridays-457";
        String thumbnail = "https://media.crazyshit.com/thumbs/2026/09/b91275e4.jpg";
        String html = "<div class='container_box'>"
                + "<div class='row heading'><h3 class='title'>today's crazy shit</h3></div>"
                + "<div class='row tiles'><div class='tile'>"
                + "<a href='" + media + "' title='FIGHTING FRIDAYS #457' class='thumb'>"
                + "<img src='https://static.crazyshit.com/static/images/blank-tile.png' class='size-helper'>"
                + "<div class='image-container'><img src='" + thumbnail
                + "' alt='FIGHTING FRIDAYS #457' class='image-thumb'></div></a>"
                + "<div class='meta'><h3 class='title'><a href='" + media
                + "'>FIGHTING FRIDAYS #457</a></h3>"
                + "<div class='stat views'><span>25,900</span></div>"
                + "<div class='stat comments'><span>17</span></div></div>"
                + "</div></div></div>";

        CrazyShitRepository repository = new CrazyShitRepository();
        Method parse = CrazyShitRepository.class.getDeclaredMethod(
                "parseHomeFeed", org.jsoup.nodes.Document.class, int.class);
        parse.setAccessible(true);
        List<NativeContentItem> items = (List<NativeContentItem>) parse.invoke(
                repository, Jsoup.parse(html, page), 1);

        assertEquals(2, items.size());
        assertTrue(items.get(0).isSection());
        assertEquals(media, items.get(1).url);
        assertEquals(thumbnail, items.get(1).imageUrl);
    }

}
