package com.webapp.crazyshit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class BunkrLiveProbeTest {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    @Test
    public void liveSearchAlbumThumbnailAndVideoPipeline() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        BunkrRepository repository = new BunkrRepository();

        List<NativeContentItem> albums = repository.searchAlbums(context, "Omegle", 1);
        assertFalse("Live Bunkr search returned no albums", albums.isEmpty());
        NativeContentItem album = albums.get(0);
        assertTrue("Search result was not a Bunkr album", BunkrRepository.isAlbumUrl(album.url));

        List<NativeContentItem> files = repository.fetchAlbum(context, album.url, 1);
        assertFalse("Live Bunkr album returned no files", files.isEmpty());
        NativeContentItem video = null;
        for (NativeContentItem item : files) {
            if (item != null && item.isVideo()) {
                video = item;
                break;
            }
        }
        assertNotNull("Sample album did not contain a video", video);
        assertFalse("Video thumbnail URL was blank", video.imageUrl.trim().isEmpty());

        HttpURLConnection thumbnail = openRange(video.imageUrl, video.url);
        int thumbnailStatus = thumbnail.getResponseCode();
        assertTrue("Thumbnail request failed with " + thumbnailStatus,
                thumbnailStatus == 200 || thumbnailStatus == 206);
        assertTrue("Thumbnail MIME type was not an image",
                value(thumbnail.getContentType()).toLowerCase().startsWith("image/"));
        thumbnail.disconnect();

        CrazyShitRepository.StreamInfo stream = repository.resolvePlayable(context, video.url);
        assertFalse("Resolved media URL was blank", stream.mediaUrl.trim().isEmpty());
        HttpURLConnection media = openRange(stream.mediaUrl, stream.requestReferer);
        int mediaStatus = media.getResponseCode();
        assertTrue("Range request failed with " + mediaStatus,
                mediaStatus == 200 || mediaStatus == 206);
        assertTrue("Resolved MIME type was not video",
                value(media.getContentType()).toLowerCase().startsWith("video/"));

        System.out.println("BUNKR_LIVE_RESULT=" +
                " albums=" + albums.size() +
                " files=" + files.size() +
                " albumHost=" + host(album.url) +
                " thumbnailHost=" + host(video.imageUrl) +
                " mediaHost=" + host(stream.mediaUrl) +
                " mediaStatus=" + mediaStatus);
        media.disconnect();
    }

    private HttpURLConnection openRange(String rawUrl, String referer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Range", "bytes=0-1");
        if (referer != null && !referer.trim().isEmpty()) {
            connection.setRequestProperty("Referer", referer);
        }
        return connection;
    }

    private String host(String value) {
        try {
            return new URI(value).getHost();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
