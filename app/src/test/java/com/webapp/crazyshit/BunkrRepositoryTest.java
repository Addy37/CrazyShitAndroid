package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class BunkrRepositoryTest {
    @Test
    public void recognizesCurrentBunkrDomainsWithoutAcceptingLookalikes() {
        assertTrue(BunkrRepository.isBunkrUrl("https://bunkr.fi/a/album"));
        assertTrue(BunkrRepository.isBunkrUrl("https://bunkr.cr/f/file"));
        assertTrue(BunkrRepository.isBunkrUrl("https://bunkr.site/a/album"));
        assertTrue(BunkrRepository.isBunkrUrl("https://bunkr.ph/f/file"));
        assertTrue(BunkrRepository.isBunkrUrl("https://app.bunkr.fi/a/album"));
        assertTrue(BunkrRepository.isBunkrUrl("https://bunkrr.su/f/file"));

        assertFalse(BunkrRepository.isBunkrUrl("https://evilbunkr.com/a/album"));
        assertFalse(BunkrRepository.isBunkrUrl("https://bunkr.fi.example.com/a/album"));
        assertFalse(BunkrRepository.isBunkrUrl("https://balbums.st/a/album"));
    }

    @Test
    public void usesBalbumsAsTheOnlyAlbumIndex() {
        assertEquals("https://balbums.st/", BunkrRepository.INDEX);
        assertTrue(BunkrRepository.isBalbumsUrl("https://balbums.st/?search=test"));
        assertTrue(BunkrRepository.isBalbumsUrl("https://www.balbums.st/"));
        assertFalse(BunkrRepository.isBalbumsUrl("https://bunkr-albums.io/"));
    }

    @Test
    public void currentPageFallbackStartsWithBunkrFi() throws Exception {
        Field origins = BunkrRepository.class.getDeclaredField("PAGE_ORIGINS");
        origins.setAccessible(true);
        String[] values = (String[]) origins.get(null);
        assertEquals("https://bunkr.fi", values[0]);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void parsesCurrentBalbumsAndAdvancedAlbumPayloads() throws Exception {
        BunkrRepository repository = new BunkrRepository();
        Document index = Jsoup.parse(
                "<article><a href='https://bunkr.fi/a/abc123'>" +
                        "View album Sample Creator 12 files → Open</a></article>",
                BunkrRepository.INDEX
        );
        Method parseIndex = BunkrRepository.class.getDeclaredMethod(
                "parseAlbumIndex", Document.class
        );
        parseIndex.setAccessible(true);
        List<NativeContentItem> albums =
                (List<NativeContentItem>) parseIndex.invoke(repository, index);
        assertEquals(1, albums.size());
        assertEquals("Sample Creator", albums.get(0).title);
        assertEquals("https://bunkr.fi/a/abc123", albums.get(0).url);

        Document album = Jsoup.parse(
                "<script>window.albumFiles = [\n" +
                        "{ id: 61210097, name: \"sample.mp4\", " +
                        "original: \"Sample.mp4\", slug: \"hzbCieUFyPcVf\", " +
                        "type: \"video/mp4\", extension: \"Video\", size: 353894212, " +
                        "thumbnail: \"https://ino2.scdn.st/thumbs/sample.png\", " +
                        "cdnEndpoint: \"/sample.mp4\" }\n" +
                        "];</script>",
                "https://bunkr.fi/a/abc123?advanced=1"
        );
        Method parseFiles = BunkrRepository.class.getDeclaredMethod(
                "parseAlbumFiles", Document.class, String.class, LinkedHashMap.class
        );
        parseFiles.setAccessible(true);
        ArrayList<NativeContentItem> files = (ArrayList<NativeContentItem>) parseFiles.invoke(
                repository,
                album,
                "https://bunkr.fi",
                new LinkedHashMap<String, String>()
        );
        assertEquals(1, files.size());
        assertTrue(files.get(0).isVideo());
        assertEquals("https://bunkr.fi/f/hzbCieUFyPcVf", files.get(0).url);
        assertEquals("https://ino2.scdn.st/thumbs/sample.png", files.get(0).imageUrl);
    }
}
