package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Optional maintainer smoke test. Normal test runs never contact Fapello. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class FapelloLiveSmokeTest {
    @Test public void liveSearchCreatorPaginationImagesAndVideos() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("FAPELLO_LIVE_TEST")));
        Context context = RuntimeEnvironment.getApplication();
        FapelloRepository repository = new FapelloRepository();

        List<FapelloRepository.Model> models =
                repository.searchConfirmedModels(context, "sonya blaze", 8);
        assertFalse("Live Fapello search returned no creators", models.isEmpty());
        FapelloRepository.Model model = models.stream()
                .filter(value -> value.url.contains("sonya-blaze-1"))
                .findFirst()
                .orElse(models.get(0));
        assertTrue(FapelloRepository.isModelUrl(model.url));

        FapelloRepository.MediaPage first = repository.fetchModelMediaPage(context, model, 1);
        assertFalse("Live Fapello creator page returned no media", first.items.isEmpty());
        assertTrue("Live Fapello creator page did not expose page 2", first.hasNext);
        FapelloRepository.MediaPage second = repository.fetchModelMediaPage(context, model, 2);
        assertFalse("Live Fapello creator page 2 returned no media", second.items.isEmpty());

        Set<String> firstUrls = new HashSet<>();
        for (NativeContentItem item : first.items) firstUrls.add(item.url);
        assertTrue("Live Fapello page 2 repeated page 1",
                second.items.stream().anyMatch(item -> !firstUrls.contains(item.url)));

        NativeContentItem image = first.items.stream()
                .filter(NativeContentItem::isImage)
                .findFirst()
                .orElse(null);
        assertNotNull("Live Fapello creator page had no image", image);
        CrazyShitRepository.StreamInfo imageStream =
                repository.resolvePlayable(context, image.url);
        assertTrue(imageStream.mediaUrl.toLowerCase(Locale.US)
                .matches(".*\\.(?:jpe?g|png|webp|avif|gif)(?:[?#].*)?$"));

        NativeContentItem video = null;
        for (int page = 1; page <= 12 && video == null; page++) {
            FapelloRepository.MediaPage media = page == 1
                    ? first
                    : page == 2 ? second : repository.fetchModelMediaPage(context, model, page);
            video = media.items.stream().filter(NativeContentItem::isVideo)
                    .findFirst().orElse(null);
            if (!media.hasNext) break;
        }
        if (video == null) {
            List<NativeContentItem> popularVideos = repository.fetchPopularVideos(context, 1);
            assertFalse("Live Fapello video feed returned no videos", popularVideos.isEmpty());
            video = popularVideos.get(0);
        }
        CrazyShitRepository.StreamInfo videoStream =
                repository.resolvePlayable(context, video.url);
        assertTrue("Live Fapello video did not resolve to playable media",
                videoStream.mediaUrl.toLowerCase(Locale.US)
                        .matches(".*\\.(?:mp4|webm|m4v|mov|m3u8|mpd)(?:[?#].*)?$"));
    }
}
