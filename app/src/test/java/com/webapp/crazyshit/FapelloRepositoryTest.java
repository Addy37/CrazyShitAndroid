package com.webapp.crazyshit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class FapelloRepositoryTest {
    private final FapelloRepository repository = new FapelloRepository();

    @Test public void searchParsingReturnsCanonicalDeduplicatedCreators() throws Exception {
        List<FapelloRepository.Model> models = repository.parseSearchResponse(fixture("search.json"));
        assertEquals(2, models.size());
        assertEquals("Sample Creator", models.get(0).name);
        assertEquals("https://fapello.com/sample-creator/", models.get(0).url);
        assertEquals("https://cdn.fapello.com/content/sample/avatar_300px.webp", models.get(0).imageUrl);
        assertEquals("Second Sample", models.get(1).name);
        assertEquals("https://fapello.com/second-sample/", models.get(1).url);
    }

    @Test public void creatorParsingExtractsImagesVideosLazyUrlsAndPagination() throws Exception {
        FapelloRepository.Model model = model();
        String endpoint = FapelloRepository.modelProfilePageUrl(model.url, 1);
        FapelloRepository.MediaPage page = repository.parseModelMedia(
                document("creator-page.html", endpoint), model, 1, endpoint);
        assertEquals(2, page.items.size());
        assertTrue(page.hasNext);
        assertEquals("https://fapello.com/sample-creator/page-2/", page.nextPageUrl);

        NativeContentItem image = page.items.get(0);
        assertTrue(image.isImage());
        assertEquals("https://fapello.com/sample-creator/1001/", image.url);
        assertEquals("https://cdn.fapello.com/content/sample/photo_300px.webp", image.imageUrl);

        NativeContentItem video = page.items.get(1);
        assertTrue(video.isVideo());
        assertEquals("https://fapello.com/video/new/1002/", video.url);
        assertEquals("https://fapello.com/content/sample/poster.webp", video.imageUrl);
    }

    @Test public void currentAjaxCardsPreservePostsPreviewsAndVideoType() throws Exception {
        String endpoint = FapelloRepository.modelMediaUrl(model().url, 1);
        FapelloRepository.MediaPage page = repository.parseModelMedia(
                document("creator-ajax.html", endpoint), model(), 1, endpoint);
        assertEquals(2, page.items.size());
        assertTrue(page.items.get(0).isImage());
        assertEquals("https://fapello.com/sample-creator/1815/", page.items.get(0).url);
        assertEquals(
                "https://fapello.com/content/s/a/sample-creator/100000/sample-creator_1815_300px.jpg",
                page.items.get(0).imageUrl
        );
        assertTrue(page.items.get(1).isVideo());
        assertEquals("https://fapello.com/video/week/1814/", page.items.get(1).url);
    }

    @Test public void fullImageResolutionPrefersOriginalSizedUrl() throws Exception {
        CrazyShitRepository.StreamInfo stream = repository.parsePlayable(
                document("post-image.html", "https://fapello.com/sample-creator/1001/"),
                "https://fapello.com/sample-creator/1001/"
        );
        assertEquals("https://cdn.fapello.com/content/sample/photo.jpg", stream.mediaUrl);
    }

    @Test public void videoResolutionUsesPlayableSourceInsteadOfPoster() throws Exception {
        CrazyShitRepository.StreamInfo stream = repository.parsePlayable(
                document("post-video.html", "https://fapello.com/video/new/1002/"),
                "https://fapello.com/video/new/1002/"
        );
        assertEquals("https://video.fapello.com/content/sample/clip.mp4", stream.mediaUrl);
        assertFalse(stream.mediaUrl.contains("poster"));
    }

    @Test public void relativeAndProtocolRelativeUrlsResolveAgainstTheCurrentPage() {
        assertEquals(
                "https://fapello.com/sample-creator/page-2/",
                FapelloRepository.normalizeUrl("page-2/", "https://fapello.com/sample-creator/")
        );
        assertEquals(
                "https://cdn.fapello.com/content/sample/photo.webp",
                FapelloRepository.normalizeUrl("//cdn.fapello.com/content/sample/photo.webp", FapelloRepository.BASE)
        );
        assertEquals("", FapelloRepository.normalizeUrl("javascript:alert(1)", FapelloRepository.BASE));
    }

    @Test public void creatorPaginationUsesCurrentAjaxAndPublicRoutes() throws Exception {
        assertEquals(
                "https://fapello.com/ajax/model/sample-creator/page-1/",
                FapelloRepository.modelMediaUrl("https://fapello.com/sample-creator/", 1)
        );
        assertEquals(
                "https://fapello.com/ajax/model/sample-creator/page-3/",
                FapelloRepository.modelMediaUrl("https://fapello.com/sample-creator/", 3)
        );
        assertEquals(
                "https://fapello.com/sample-creator/",
                FapelloRepository.modelProfilePageUrl("https://fapello.com/sample-creator/", 1)
        );
        assertEquals(
                "https://fapello.com/sample-creator/page-3/",
                FapelloRepository.modelProfilePageUrl("https://fapello.com/sample-creator/", 3)
        );
    }

    @Test public void ajaxPaginationContinuesOnlyForFullPages() throws Exception {
        StringBuilder full = new StringBuilder();
        for (int id = 1; id <= 30; id++) {
            full.append("<a href='/sample-creator/").append(id).append("/'>")
                    .append("<img src='/content/sample-creator/photo_")
                    .append(id).append("_300px.jpg'></a>");
        }
        String firstUrl = FapelloRepository.modelMediaUrl(model().url, 1);
        FapelloRepository.MediaPage first = repository.parseModelMedia(
                Jsoup.parse(full.toString(), firstUrl), model(), 1, firstUrl);
        assertEquals(30, first.items.size());
        assertTrue(first.hasNext);
        assertEquals(FapelloRepository.modelMediaUrl(model().url, 2), first.nextPageUrl);

        String lastUrl = FapelloRepository.modelMediaUrl(model().url, 2);
        FapelloRepository.MediaPage last = repository.parseModelMedia(
                Jsoup.parse(full.substring(0, full.indexOf("</a>") + 4), lastUrl),
                model(), 2, lastUrl);
        assertEquals(1, last.items.size());
        assertFalse(last.hasNext);
    }

    @Test public void emptyCreatorIsDifferentFromParserFailure() throws Exception {
        FapelloRepository.MediaPage empty = repository.parseModelMedia(
                document("empty-creator.html", model().url), model(), 1, model().url);
        assertTrue(empty.items.isEmpty());
        assertFalse(empty.hasNext);

        FapelloRepository.MediaPage malformed = repository.parseModelMedia(
                document("malformed.html", model().url), model(), 1, model().url);
        assertTrue(malformed.items.isEmpty());
        assertFalse(malformed.hasNext);
        repository.validateParsedModelPage(
                document("empty-creator.html", model().url), empty, 1);
        try {
            repository.validateParsedModelPage(
                    document("malformed.html", model().url), malformed, 1);
            fail("Expected parser failure");
        } catch (FapelloSourceException error) {
            assertEquals(FapelloSourceException.Reason.PARSER, error.reason);
        }
    }

    @Test public void malformedSearchResponseHasTypedFailure() throws Exception {
        try {
            repository.parseSearchResponse(fixture("malformed.html"));
            fail("Expected malformed response");
        } catch (FapelloSourceException error) {
            assertEquals(FapelloSourceException.Reason.MALFORMED, error.reason);
        }
    }

    @Test public void cloudflareAndHttpFailuresAreClassified() throws Exception {
        try {
            FapelloRepository.validateResponse(
                    403, "text/html", "cloudflare", "sample-ray", fixture("blocked.html"), false);
            fail("Expected blocked response");
        } catch (FapelloSourceException error) {
            assertEquals(FapelloSourceException.Reason.BLOCKED, error.reason);
        }

        assertReason(429, FapelloSourceException.Reason.RATE_LIMITED);
        assertReason(404, FapelloSourceException.Reason.NOT_FOUND);
        assertReason(500, FapelloSourceException.Reason.HTTP);
    }

    @Test public void duplicateMediaCardsCollapseToOneEntry() throws Exception {
        FapelloRepository.MediaPage page = repository.parseModelMedia(
                document("creator-page.html", model().url), model(), 1, model().url);
        long images = page.items.stream()
                .filter(item -> item.url.contains("/sample-creator/1001"))
                .count();
        assertEquals(1L, images);
    }

    private void assertReason(int status, FapelloSourceException.Reason reason) throws Exception {
        try {
            FapelloRepository.validateResponse(status, "text/html", "origin", "", "response", false);
            fail("Expected HTTP failure");
        } catch (FapelloSourceException error) {
            assertEquals(reason, error.reason);
        }
    }

    private FapelloRepository.Model model() {
        return new FapelloRepository.Model(
                "Sample Creator", "https://fapello.com/sample-creator/", "");
    }

    private Document document(String name, String base) throws Exception {
        return Jsoup.parse(fixture(name), base);
    }

    private String fixture(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/fapello/" + name)) {
            if (input == null) throw new IOException("Missing fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
