package com.webapp.crazyshit;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class WikiFeetRepositoryTest {
    private static String profile(String name, long... ids) {
        StringBuilder gallery = new StringBuilder();
        for (long id : ids) {
            if (gallery.length() > 0) gallery.append(',');
            gallery.append("{\"pid\":").append(id)
                    .append(",\"pw\":1200,\"ph\":1800,\"removed\":0}");
        }
        return "<script>let ignored={\"brace\":\"}\"}; tdata = {\"cid\":42," +
                "\"cname\":\"" + name + "\",\"gallery\":[" + gallery + "]};" +
                " tbody = [];</script>";
    }

    @Test public void parsesPredictiveCreatorLinksAndCounts() {
        String html = "<div id='searchresults'>" +
                "<a href='/Scarlett_Johansson'><div>Scarlett Johansson</div><small>1784 photos</small></a>" +
                "<a href='https://other.example/nope'><div>Nope</div></a></div>";
        List<WikiFeetRepository.Creator> creators = WikiFeetRepository.parseSearch(
                html, WikiFeetRepository.Site.WIKIFEET, "https://wikifeet.com/", 8);
        assertEquals(1, creators.size());
        assertEquals("Scarlett Johansson", creators.get(0).name);
        assertEquals(1784, creators.get(0).photoCount);
        assertEquals("https://wikifeet.com/Scarlett_Johansson", creators.get(0).url);
    }

    @Test public void profileThumbnailAndPagedOriginalsUseTheSelectedSource() throws Exception {
        String html = profile("Abella Danger", 1001, 1002, 1003);
        WikiFeetRepository.Creator creator = WikiFeetRepository.parseProfile(
                html, WikiFeetRepository.Site.WIKIFEET_X,
                "https://wikifeetx.com/Abella_Danger");
        assertEquals(3, creator.photoCount);
        assertEquals("https://thumbs.wikifeet.com/1003.jpg", creator.imageUrl);

        List<NativeContentItem> firstPage = WikiFeetRepository.parseMedia(html, creator, 1, 2);
        assertEquals(2, firstPage.size());
        assertEquals("https://pics.wikifeet.com/Abella-Danger-Feet-1003.jpg", firstPage.get(0).url);
        List<NativeContentItem> page = WikiFeetRepository.parseMedia(html, creator, 2, 2);
        assertEquals(1, page.size());
        assertEquals("https://pics.wikifeet.com/Abella-Danger-Feet-1001.jpg", page.get(0).url);
        assertEquals("https://thumbs.wikifeet.com/1001.jpg", page.get(0).imageUrl);
        assertEquals("https://wikifeetx.com/Abella_Danger", page.get(0).uploader);
    }

    @Test public void profileDataScannerHandlesQuotedBraces() throws Exception {
        String html = "before tdata = {\"cid\":7,\"cname\":\"A } B\",\"gallery\":[]}; after";
        assertEquals("A } B", WikiFeetRepository.profileData(html).getString("cname"));
    }

    @Test public void rejectsLookalikeHostsAndNonImageRoutes() {
        assertEquals("https://wikifeet.com/search/scarlett%20johansson",
                WikiFeetRepository.searchUrl(
                        WikiFeetRepository.Site.WIKIFEET, "scarlett johansson"));
        assertFalse(WikiFeetRepository.isWikiFeetUrl("https://wikifeet.com.evil.example/A"));
        assertFalse(WikiFeetRepository.isOriginalImageUrl("https://wikifeet.com/A"));
        assertTrue(WikiFeetRepository.isOriginalImageUrl(
                "https://pics.wikifeet.com/A-Feet-1.jpg"));
    }
}
