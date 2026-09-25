package com.webapp.crazyshit;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.database.StandaloneDatabaseProvider;

import java.io.File;

/** Small, separate cache for the first bytes of adjacent gallery videos. */
@UnstableApi
final class GalleryVideoCache {
    private static final long MAX_BYTES = 64L * 1024L * 1024L;
    private static final long PRELOAD_NEAR_BYTES = 1024L * 1024L;
    private static final long PRELOAD_SECOND_BYTES = 512L * 1024L;
    private static final long PRELOAD_FAR_BYTES = 256L * 1024L;
    private static SimpleCache cache;
    private static StandaloneDatabaseProvider database;

    private GalleryVideoCache() { }

    private static synchronized SimpleCache get(Context context) {
        if (cache == null) {
            Context app = context.getApplicationContext();
            database = new StandaloneDatabaseProvider(app);
            cache = new SimpleCache(new File(app.getCacheDir(), "onlyfap_gallery_media"),
                    new LeastRecentlyUsedCacheEvictor(MAX_BYTES), database);
        }
        return cache;
    }

    static CacheDataSource.Factory factory(Context context, DefaultHttpDataSource.Factory upstream) {
        return new CacheDataSource.Factory()
                .setCache(get(context))
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    static long preloadBytesForDistance(int distance) {
        return distance <= 1
                ? PRELOAD_NEAR_BYTES
                : distance == 2 ? PRELOAD_SECOND_BYTES : PRELOAD_FAR_BYTES;
    }

    static void warm(Context context, DefaultHttpDataSource.Factory upstream, String url) {
        warm(context, upstream, url, 3);
    }

    static void warm(
            Context context,
            DefaultHttpDataSource.Factory upstream,
            String url,
            int distance
    ) {
        if (url == null || url.isEmpty() || url.contains(".m3u8") || url.contains(".mpd")) return;
        long bytes = preloadBytesForDistance(distance);
        try {
            CacheDataSource source = factory(context, upstream).createDataSource();
            DataSpec spec = new DataSpec.Builder().setUri(Uri.parse(url))
                    .setLength(bytes).build();
            new CacheWriter(source, spec, null, null).cache();
        } catch (Exception ignored) {
            // A failed warm-up must never block normal playback.
        }
    }
}
