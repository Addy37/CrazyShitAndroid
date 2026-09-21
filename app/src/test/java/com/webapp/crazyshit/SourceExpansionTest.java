package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;

import org.jsoup.Jsoup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class SourceExpansionTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("remote_source_config", Context.MODE_PRIVATE)
                .edit().clear().commit();
        RemoteSourceConfigManager.resetForTests();
        RemoteSourceConfigManager.initialize(context);
    }

    @After public void tearDown() {
        context.getSharedPreferences("remote_source_config", Context.MODE_PRIVATE)
                .edit().clear().commit();
        RemoteSourceConfigManager.resetForTests();
    }

    @Test public void itemFixDefaultsUseNewestListingAndRealPagination() {
        SourceConfig.WebVideo config = RemoteSourceConfigManager.snapshot().itemFix;
        assertEquals("list?order_by=newest_added", config.feedFirstRoute);
        assertEquals("list?order_by=newest_added&page={page}", config.feedPageRoute);
    }

    @Test public void kaoticParserKeepsDateSectionHeaders() {
        String html = "<main>"
                + "<h2>Sunday September 21st, 2026</h2>"
                + "<article><a href='/video/sample-one' title='Sample one'>"
                + "<img src='/thumb1.jpg'></a></article>"
                + "<h2>Saturday September 20th, 2026</h2>"
                + "<article><a href='/video/sample-two' title='Sample two'>"
                + "<img src='/thumb2.jpg'></a></article>"
                + "</main>";
        List<NativeContentItem> items = new WebVideoSourceRepository().parseFeed(
                Jsoup.parse(html, "https://kaotic.com/"),
                WebVideoSourceRepository.Source.KAOTIC,
                1
        );
        assertEquals(4, items.size());
        assertTrue(items.get(0).isSection());
        assertEquals("Sunday September 21st, 2026", items.get(0).title);
        assertEquals("https://kaotic.com/video/sample-one", items.get(1).url);
        assertTrue(items.get(2).isSection());
        assertEquals("Saturday September 20th, 2026", items.get(2).title);
    }

    @Test public void onlyHavenCreatorSearchApiFindsCanonicalCreator() throws Exception {
        OnlyHavenRepository repository = new OnlyHavenRepository();
        SourceConfig.OnlyHaven config = RemoteSourceConfigManager.snapshot().onlyHaven;
        assertEquals("api/v1/creators?q={query}&n={limit}&o={offset}",
                config.creatorSearchApiRoute);
        String json = "{\"creators\":[{"
                + "\"id\":\"30340311\","
                + "\"name\":\"samplecreator\","
                + "\"displayName\":\"Sample Creator\","
                + "\"service\":\"onlyfans\"}]}";

        List<OnlyHavenRepository.Creator> creators =
                repository.parseCreatorSearchJson(json, config, "Sample Creator", 4);

        assertEquals(1, creators.size());
        assertEquals("onlyfans", creators.get(0).service);
        assertEquals("30340311", creators.get(0).id);
        assertEquals("Sample Creator", creators.get(0).name);
        assertEquals("https://cum.st/creators/onlyfans/30340311", creators.get(0).url);
    }

    @Test public void onlyHavenApiBuildsOriginalVariantMediaUrls() throws Exception {
        OnlyHavenRepository repository = new OnlyHavenRepository();
        OnlyHavenRepository.Creator creator = new OnlyHavenRepository.Creator(
                "onlyfans", "12345", "Sample Creator",
                "https://cum.st/creators/onlyfans/12345", ""
        );
        SourceConfig.OnlyHaven config = RemoteSourceConfigManager.snapshot().onlyHaven;
        String videoKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String imageKey = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        String json = "{\"total\":2,\"posts\":["
                + "{\"title\":\"Video post\",\"attachments\":["
                + "{\"storageKey\":\"" + videoKey + "\","
                + "\"mimeType\":\"video/mp4\","
                + "\"variants\":[{\"name\":\"720p.mp4\"},{\"name\":\"original.mp4\"}]}]},"
                + "{\"title\":\"Image post\",\"file\":"
                + "{\"storageKey\":\"" + imageKey + "\","
                + "\"mimeType\":\"image/jpeg\","
                + "\"variants\":[{\"name\":\"original.jpg\"}]}}]}";

        List<NativeContentItem> items =
                repository.parseCreatorMediaJson(json, config, creator, 10);

        assertEquals(2, items.size());
        assertEquals(NativeContentItem.KIND_MEDIA, items.get(0).kind);
        assertEquals("https://e1.cum.st/media/" + videoKey + "/original.mp4", items.get(0).url);
        assertEquals(NativeContentItem.KIND_IMAGE, items.get(1).kind);
        assertEquals("https://e1.cum.st/media/" + imageKey + "/original.jpg", items.get(1).url);
    }

    @Test public void theYncParserDeduplicatesLinksForOneVideoAndKeepsRealTitle() {
        String html = "<article>"
                + "<a href='/video/12345/comments'>59</a>"
                + "<a href='/video/12345/sample-title?ref=home#player' title='Sample title'>"
                + "<img src='/sample.jpg'></a>"
                + "</article>";
        List<NativeContentItem> items = new WebVideoSourceRepository().parseFeed(
                Jsoup.parse(html, "https://theync.com/"),
                WebVideoSourceRepository.Source.THEYNC,
                1
        );
        assertEquals(1, items.size());
        assertEquals("Sample title", items.get(0).title);
        assertEquals("https://theync.com/video/12345/comments", items.get(0).url);
    }
}
