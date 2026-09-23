package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class RemoteSourceConfigManagerTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("remote_source_config", Context.MODE_PRIVATE).edit().clear().commit();
        RemoteSourceConfigManager.resetForTests();
    }

    @After public void tearDown() {
        context.getSharedPreferences("remote_source_config", Context.MODE_PRIVATE).edit().clear().commit();
        RemoteSourceConfigManager.resetForTests();
    }

    @Test public void bundledDefaultsStartWithoutCacheOrNetwork() {
        long start = System.nanoTime();
        RemoteSourceConfigManager.initialize(context);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        SourceConfig config = RemoteSourceConfigManager.snapshot();
        assertEquals(5L, config.configVersion);
        assertEquals(FapelloRepository.BASE, config.fapello.baseUrl);
        assertEquals(BunkrRepository.INDEX, config.bunkr.indexUrl);
        assertEquals("bundled", RemoteSourceConfigManager.activeOrigin());
        assertTrue("Local startup should only parse a small bundled file", elapsedMs < 2_000L);

    }

    @Test public void backgroundRefreshDoesNotWaitForNetwork() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        long start = System.nanoTime();
        RemoteSourceConfigManager.refreshInBackgroundForTests(context, (endpoint, key, version) -> {
            try { Thread.sleep(300L); }
            finally { finished.countDown(); }
            return new RemoteSourceConfigManager.FetchResult(true, "");
        });
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue("Scheduling a refresh must not wait for the network", elapsedMs < 200L);
        assertTrue(finished.await(2, TimeUnit.SECONDS));
    }

    @Test public void validNewerConfigChangesFapelloRoutesWithoutRebuild() throws Exception {
        JSONObject config = defaults();
        config.put("configVersion", 6);
        config.put("updatedAt", "2026-09-21T13:30:00Z");
        JSONObject fapello = config.getJSONObject("sources").getJSONObject("fapello");
        fapello.put("baseUrl", "https://mirror.example/");
        fapello.put("refererOverride", "https://referer.example/source/");
        fapello.getJSONObject("routes").put("creatorMedia", "v2/{slug}/batch-{page}/");
        fapello.getJSONObject("selectors").put("mediaLinks", "article.media a[href]");
        RemoteSourceConfigManager.applyRemoteForTests(context, config.toString());

        assertEquals("https://mirror.example/v2/sample/batch-3/",
                FapelloRepository.modelMediaUrl("https://mirror.example/sample/", 3));
        assertEquals("article.media a[href]", RemoteSourceConfigManager.snapshot()
                .fapello.mediaLinksSelector);
        assertEquals("https://referer.example/source/", RemoteSourceConfigManager.snapshot()
                .fapello.refererOverride);
    }

    @Test public void invalidAndUnsupportedConfigsAreRejected() throws Exception {
        JSONObject invalidUrl = defaults();
        invalidUrl.put("configVersion", 3);
        invalidUrl.getJSONObject("sources").getJSONObject("fapello")
                .put("baseUrl", "http://unsafe.example/");
        assertRejected(invalidUrl);

        JSONObject unsupported = defaults();
        unsupported.put("schemaVersion", 99);
        assertRejected(unsupported);

        JSONObject badRegex = defaults();
        badRegex.put("configVersion", 3);
        badRegex.getJSONObject("sources").getJSONObject("fapello")
                .getJSONObject("patterns").put("postPath", "[");
        assertRejected(badRegex);

        JSONObject badReferer = defaults();
        badReferer.put("configVersion", 3);
        badReferer.getJSONObject("sources").getJSONObject("bunkr")
                .put("refererOverride", "javascript:alert(1)");
        assertRejected(badReferer);
    }

    @Test public void corruptedCacheFallsBackToBundledDefaults() {
        context.getSharedPreferences("remote_source_config", Context.MODE_PRIVATE).edit()
                .putString("active_json", "{broken").commit();
        RemoteSourceConfigManager.initialize(context);
        assertEquals(5L, RemoteSourceConfigManager.snapshot().configVersion);
        assertEquals("bundled", RemoteSourceConfigManager.activeOrigin());
    }

    @Test public void corruptedActiveCacheUsesPreviousKnownGood() throws Exception {
        JSONObject previous = defaults();
        previous.put("configVersion", 6);
        previous.put("updatedAt", "2026-09-21T13:20:00Z");
        previous.getJSONObject("sources").getJSONObject("fapello")
                .put("baseUrl", "https://previous.example/");
        context.getSharedPreferences("remote_source_config", Context.MODE_PRIVATE).edit()
                .putString("active_json", "{broken")
                .putString("previous_json", previous.toString()).commit();
        RemoteSourceConfigManager.initialize(context);
        assertEquals(6L, RemoteSourceConfigManager.snapshot().configVersion);
        assertEquals("https://previous.example/", RemoteSourceConfigManager.snapshot().fapello.baseUrl);
        assertEquals("rollback", RemoteSourceConfigManager.activeOrigin());
    }

    @Test public void newerFetchedConfigActivatesAndFailedRefreshKeepsIt() throws Exception {
        JSONObject newer = defaults();
        newer.put("configVersion", 6);
        newer.put("updatedAt", "2026-09-21T13:30:00Z");
        RemoteSourceConfigManager.refreshForTests(context,
                (endpoint, key, currentVersion) ->
                        new RemoteSourceConfigManager.FetchResult(false, newer.toString()));
        assertEquals(6L, RemoteSourceConfigManager.snapshot().configVersion);
        assertEquals("remote", RemoteSourceConfigManager.activeOrigin());

        RemoteSourceConfigManager.refreshForTests(context,
                (endpoint, key, currentVersion) -> { throw new IOException("offline"); });
        assertEquals(6L, RemoteSourceConfigManager.snapshot().configVersion);
        assertEquals("remote", RemoteSourceConfigManager.activeOrigin());
    }

    @Test public void olderVersionCannotReplaceNewerKnownGood() throws Exception {
        JSONObject newer = defaults();
        newer.put("configVersion", 6);
        newer.put("updatedAt", "2026-09-13T13:00:00Z");
        RemoteSourceConfigManager.applyRemoteForTests(context, newer.toString());
        JSONObject older = defaults();
        older.put("configVersion", 5);
        older.put("updatedAt", "2026-09-13T12:00:00Z");
        RemoteSourceConfigManager.applyRemoteForTests(context, older.toString());
        assertEquals(6L, RemoteSourceConfigManager.snapshot().configVersion);
    }

    @Test public void disabledSourceFailsWithoutChangingOtherSources() throws Exception {
        JSONObject config = defaults();
        config.put("configVersion", 6);
        config.put("updatedAt", "2026-09-13T12:00:00Z");
        config.getJSONObject("sources").getJSONObject("fapello").put("enabled", false);
        RemoteSourceConfigManager.applyRemoteForTests(context, config.toString());
        try {
            new FapelloRepository().searchModels(context, "sample", 4);
            fail("Disabled source should fail before making a request");
        } catch (FapelloSourceException expected) {
            assertTrue(expected.getMessage().contains("temporarily unavailable"));
        }
        assertTrue(RemoteSourceConfigManager.snapshot().bunkr.enabled);
    }

    @Test public void bunkrFallbackAndWikiFeetHostsCanChangeRemotely() throws Exception {
        JSONObject config = defaults();
        config.put("configVersion", 6);
        config.put("updatedAt", "2026-09-13T12:00:00Z");
        JSONObject sources = config.getJSONObject("sources");
        sources.getJSONObject("bunkr").put("fallbackOrigins",
                new JSONArray().put("https://bunkr-backup.example"));
        JSONObject wiki = sources.getJSONObject("wikifeet");
        wiki.put("baseUrl", "https://feet.example/");
        wiki.put("pictureHost", "pictures.feet.example");
        wiki.put("thumbnailHost", "thumbs.feet.example");
        RemoteSourceConfigManager.applyRemoteForTests(context, config.toString());

        assertTrue(BunkrRepository.isBunkrUrl("https://bunkr-backup.example/a/sample"));
        assertEquals("https://feet.example/search/sample",
                WikiFeetRepository.searchUrl(WikiFeetRepository.Site.WIKIFEET, "sample"));
        assertEquals("https://pictures.feet.example/Sample-Feet-42.jpg",
                WikiFeetRepository.originalUrl(WikiFeetRepository.Site.WIKIFEET, "Sample", 42));
    }

    @Test public void newSourcesCanChangeRemotely() throws Exception {
        JSONObject config = defaults();
        config.put("configVersion", 6);
        config.put("updatedAt", "2026-09-21T12:00:00Z");
        JSONObject sources = config.getJSONObject("sources");
        sources.getJSONObject("kaotic").put("baseUrl", "https://kaotic-mirror.example/");
        JSONObject onlyHaven = sources.getJSONObject("onlyhaven");
        onlyHaven.getJSONObject("routes")
                .put("creatorSearch", "people?query={query}")
                .put("creatorSearchApi", "api/v2/creators?q={query}&n={limit}&o={offset}");
        onlyHaven.put("imageBaseUrl", "https://images.example/");
        RemoteSourceConfigManager.applyRemoteForTests(context, config.toString());

        assertEquals("https://kaotic-mirror.example/",
                RemoteSourceConfigManager.snapshot().kaotic.baseUrl);
        assertEquals("people?query={query}",
                RemoteSourceConfigManager.snapshot().onlyHaven.creatorSearchRoute);
        assertEquals("api/v2/creators?q={query}&n={limit}&o={offset}",
                RemoteSourceConfigManager.snapshot().onlyHaven.creatorSearchApiRoute);
        assertEquals("https://images.example/",
                RemoteSourceConfigManager.snapshot().onlyHaven.imageBaseUrl);
        assertTrue(RemoteSourceConfigManager.snapshot().theYnc.enabled);
        assertTrue(RemoteSourceConfigManager.snapshot().itemFix.enabled);
    }

    private void assertRejected(JSONObject value) throws Exception {
        try {
            SourceConfig.parseAndValidate(value.toString());
            fail("Expected validation failure");
        } catch (SourceConfig.ValidationException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    private JSONObject defaults() throws Exception {
        try (InputStream input = context.getAssets().open("source_config_defaults.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
        }
    }
}
