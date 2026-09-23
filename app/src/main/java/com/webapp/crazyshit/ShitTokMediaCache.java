package com.webapp.crazyshit;

import android.content.Context;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.database.StandaloneDatabaseProvider;

import java.io.File;

/**
 * Process-wide disk cache for ShitTok playback.
 *
 * Keeps recently played and preloaded media bytes available across holder/player recreation while
 * allowing Android to reclaim the cache directory when device storage is under pressure.
 */
@UnstableApi
final class ShitTokMediaCache {
    static final long MAX_CACHE_BYTES = 200L * 1024L * 1024L;

    private static SimpleCache cache;
    private static StandaloneDatabaseProvider databaseProvider;

    private ShitTokMediaCache() {
    }

    static synchronized SimpleCache get(Context context) {
        if (cache == null) {
            Context app = context.getApplicationContext();
            Context safeContext = app != null ? app : context;
            databaseProvider = new StandaloneDatabaseProvider(safeContext);
            File cacheDir = new File(safeContext.getCacheDir(), "shittok_media");
            cache = new SimpleCache(
                    cacheDir,
                    new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                    databaseProvider
            );
        }
        return cache;
    }

    static DataSource.Factory wrap(Context context, DataSource.Factory upstream) {
        return new CacheDataSource.Factory()
                .setCache(get(context))
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }
}
