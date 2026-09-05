package com.webapp.crazyshit;

import android.app.Application;
import java.io.IOException;
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
}
