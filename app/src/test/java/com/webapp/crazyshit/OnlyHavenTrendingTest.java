package com.webapp.crazyshit;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class OnlyHavenTrendingTest {
    @Test
    public void creatorApiKeepsPopularityOrderAndContentCounts() throws Exception {
        SourceConfig.OnlyHaven config = new SourceConfig.OnlyHaven(
                true,
                "https://cum.st/",
                Collections.emptyList(),
                "https://e1.cum.st/media/",
                "https://img.cum.st/",
                "test",
                Collections.emptyMap(),
                "",
                1000,
                0,
                "creators?search={query}",
                "api/v1/creators?q={query}&n={limit}&o={offset}",
                "creators/{service}/{id}?page={page}",
                "api/v1/{service}/user/{id}/posts?o={offset}&n={limit}",
                "a[href*=/creators/]",
                "a[href]",
                "video[src]",
                "img[src]",
                Pattern.compile("(?i)^https?://(?:www\\.)?cum\\.st/creators/([a-z0-9_-]+)/([a-z0-9_-]+)(?:[/?#].*)?$"),
                Pattern.compile("(?i)https?://[^\\s\\\"'<>]+")
        );

        String json = "{\"creators\":["
                + "{\"service\":\"onlyfans\",\"id\":\"first\",\"name\":\"First\",\"avatarThumbhash\":\"abc123hash\",\"postCount\":42,\"dmCount\":3},"
                + "{\"service\":\"fansly\",\"id\":\"second\",\"name\":\"Second\",\"post_count\":7,\"dm_count\":0}"
                + "]}";

        List<OnlyHavenRepository.Creator> creators =
                new OnlyHavenRepository().parseCreatorSearchJson(json, config, "", 50);

        assertEquals(2, creators.size());
        assertEquals("First", creators.get(0).name);
        assertEquals(42, creators.get(0).postCount);
        assertEquals(3, creators.get(0).dmCount);
        assertEquals(
                "https://img.cum.st/thumbnail/abc123hash/preview.webp",
                creators.get(0).imageUrl
        );
        assertEquals("Second", creators.get(1).name);
        assertEquals(7, creators.get(1).postCount);
    }

    @Test
    public void onlyFapTopModeIsPresentedAsTrending() {
        assertEquals("Trending", FapzoneCreatorRepository.titleFor(
                FapzoneCreatorRepository.MODE_TOP_50
        ));
        assertEquals("LIVE", FapzoneCreatorRepository.badgeFor(
                FapzoneCreatorRepository.MODE_TOP_50
        ));
    }
    @Test
    public void trendingArtworkFallsBackToGalleryPreview() {
        java.util.ArrayList<NativeContentItem> media = new java.util.ArrayList<>();
        media.add(new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Clip",
                "https://e1.cum.st/media/video.mp4",
                "https://img.cum.st/thumbnail/abc/preview.webp",
                "",
                "https://cum.st/creators/onlyfans/example",
                "",
                "onlyfans · OnlyHaven"
        ));

        assertEquals(
                "https://img.cum.st/thumbnail/abc/preview.webp",
                FapzoneCreatorRepository.chooseGalleryPreview(media)
        );
    }

}
