package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Google Photos-style full-screen viewer for a mixed Bunkr album. */
public final class BunkrGalleryActivity extends Activity {
    public static final String EXTRA_SESSION_ID = "bunkr_gallery_session";
    public static final String EXTRA_TITLE = "bunkr_gallery_title";
    public static final String EXTRA_ALBUM_URL = "bunkr_gallery_album_url";
    public static final String EXTRA_CREATOR_QUERY = "bunkr_gallery_creator_query";
    public static final String EXTRA_FAPELLO_PROFILE_URL = "bunkr_gallery_fapello_profile_url";
    public static final String EXTRA_MEDIA_FILTER = "bunkr_gallery_media_filter";
    public static final String EXTRA_INITIAL_URL = "bunkr_gallery_initial_url";
    public static final String EXTRA_INITIAL_POSITION = "bunkr_gallery_initial_position";
    public static final String FILTER_ALL = "all";
    public static final String FILTER_PICTURES = "pictures";
    public static final String FILTER_VIDEOS = "videos";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final ExecutorService pageIo = Executors.newSingleThreadExecutor();
    private final ExecutorService mediaIo = Executors.newFixedThreadPool(2);
    private final BunkrRepository repository = new BunkrRepository();
    private final BunkrCreatorGalleryRepository creatorGalleryRepository =
            new BunkrCreatorGalleryRepository();

    private String sessionId;
    private String albumTitle;
    private String albumUrl;
    private String creatorQuery;
    private String fapelloProfileUrl;
    private String mediaFilter;
    private String initialUrl;
    private int initialPosition;
    private int currentPage;
    private boolean endReached;
    private boolean loadingMore;
    private boolean fapelloFailureShown;
    private int generation;

    private ViewPager2 pager;
    private BunkrGalleryPagerAdapter adapter;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private TextView countView;
    private TextView downloadAction;
    private TextView itemTitleView;
    private TextView itemMetaView;
    private ProgressBar initialLoading;
    private boolean chromeVisible = true;
    private boolean restoreChromeAfterLandscape;
    private boolean landscapeFullscreen;
    private boolean sensorFullscreen;
    private SensorMediaOrientationListener orientationListener;
    private ExoPlayer player;
    private final PlaybackRecovery playbackRecovery = new PlaybackRecovery();
    private boolean recoveryResumed;
    private int activeVideoPosition = -1;
    private volatile int requestedPhotoPosition = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        orientationListener = new SensorMediaOrientationListener(this, this::onPhysicalOrientation);

        sessionId = value(getIntent().getStringExtra(EXTRA_SESSION_ID));
        albumTitle = value(getIntent().getStringExtra(EXTRA_TITLE));
        albumUrl = value(getIntent().getStringExtra(EXTRA_ALBUM_URL));
        creatorQuery = value(getIntent().getStringExtra(EXTRA_CREATOR_QUERY));
        fapelloProfileUrl = value(getIntent().getStringExtra(EXTRA_FAPELLO_PROFILE_URL));
        mediaFilter = value(getIntent().getStringExtra(EXTRA_MEDIA_FILTER));
        if (!FILTER_PICTURES.equals(mediaFilter) && !FILTER_VIDEOS.equals(mediaFilter)) {
            mediaFilter = FILTER_ALL;
        }
        initialUrl = value(getIntent().getStringExtra(EXTRA_INITIAL_URL));
        initialPosition = Math.max(0, getIntent().getIntExtra(EXTRA_INITIAL_POSITION, 0));
        if (albumTitle.isEmpty()) albumTitle = "OnlyFap gallery";

        if (state != null) {
            sessionId = state.getString("session", sessionId);
            initialUrl = state.getString("current_url", initialUrl);
            initialPosition = state.getInt("current_position", initialPosition);
        }
        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(sessionId);
        if (snapshot == null && !sessionId.isEmpty()) {
            pageIo.execute(() -> {
                BunkrGallerySessionStore.Snapshot restored = BunkrGallerySessionStore.restore(this, sessionId);
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) initializeGallery(restored); });
            });
        } else initializeGallery(snapshot);
    }

    private void initializeGallery(BunkrGallerySessionStore.Snapshot snapshot) {
        if (snapshot == null) {
            sessionId = isCreatorGallery()
                    ? BunkrGallerySessionStore.createCreator(albumTitle, albumUrl, creatorQuery)
                    : BunkrGallerySessionStore.create(albumTitle, albumUrl);
            if (isCreatorGallery()) {
                creatorGalleryRepository.reset(
                        sessionId, creatorQuery, fapelloProfileUrl, albumTitle);
            }
        } else {
            albumTitle = snapshot.title;
            albumUrl = snapshot.albumUrl;
            if (!snapshot.creatorQuery.isEmpty()) creatorQuery = snapshot.creatorQuery;
            currentPage = snapshot.currentPage;
            endReached = snapshot.endReached;
        }

        buildUi();
        applyViewerOrientation(getResources().getConfiguration().orientation);
        if (snapshot == null || snapshot.items.isEmpty()) {
            loadInitialPage();
        } else {
            showSnapshot(snapshot);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        pager = new ViewPager2(this);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setOffscreenPageLimit(1);
        adapter = new BunkrGalleryPagerAdapter(
                this,
                new BunkrGalleryPagerAdapter.Listener() {
                    @Override
                    public void onMediaTap(int position, NativeContentItem item) {
                        BunkrGalleryActivity.this.onMediaTap(position, item);
                    }

                    @Override
                    public void onResolvedImageFailed(int position, NativeContentItem item) {
                        BunkrGallerySessionStore.clearResolvedUrl(sessionId, item.url);
                    }
                }
        );
        pager.setAdapter(adapter);
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(6), dp(5), dp(6), dp(5));
        topBar.setBackgroundColor(Color.argb(220, 10, 10, 12));

        TextView back = action("‹", 32);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        topBar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(54)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(5), 0, dp(5), 0);

        TextView album = new TextView(this);
        album.setText(albumTitle);
        album.setTextColor(Color.WHITE);
        album.setTextSize(17);
        album.setTypeface(null, android.graphics.Typeface.BOLD);
        album.setSingleLine(true);
        album.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(album, new LinearLayout.LayoutParams(-1, -2));

        countView = new TextView(this);
        countView.setTextColor(Color.rgb(190, 190, 198));
        countView.setTextSize(12);
        countView.setSingleLine(true);
        heading.addView(countView, new LinearLayout.LayoutParams(-1, -2));
        topBar.addView(heading, new LinearLayout.LayoutParams(0, -1, 1f));

        downloadAction = action("↓", 24);
        downloadAction.setContentDescription("Download video");
        downloadAction.setOnClickListener(v -> downloadCurrentVideo());
        downloadAction.setVisibility(View.GONE);
        topBar.addView(downloadAction, new LinearLayout.LayoutParams(dp(52), dp(54)));

        TextView share = action("↗", 22);
        share.setContentDescription("Share item");
        share.setOnClickListener(v -> shareCurrent());
        topBar.addView(share, new LinearLayout.LayoutParams(dp(52), dp(54)));

        TextView more = action("⋮", 27);
        more.setContentDescription("More options");
        more.setOnClickListener(this::showMenu);
        topBar.addView(more, new LinearLayout.LayoutParams(dp(52), dp(54)));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, dp(64));
        topParams.gravity = Gravity.TOP;
        root.addView(topBar, topParams);

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(18), dp(10), dp(18), dp(12));
        bottomBar.setBackgroundColor(Color.argb(220, 10, 10, 12));

        itemTitleView = new TextView(this);
        itemTitleView.setTextColor(Color.WHITE);
        itemTitleView.setTextSize(14);
        itemTitleView.setTypeface(null, android.graphics.Typeface.BOLD);
        itemTitleView.setSingleLine(true);
        itemTitleView.setEllipsize(TextUtils.TruncateAt.END);
        bottomBar.addView(itemTitleView, new LinearLayout.LayoutParams(-1, -2));

        itemMetaView = new TextView(this);
        itemMetaView.setTextColor(Color.rgb(185, 185, 194));
        itemMetaView.setTextSize(12);
        itemMetaView.setSingleLine(true);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(3);
        bottomBar.addView(itemMetaView, metaParams);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, dp(70));
        bottomParams.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, bottomParams);

        initialLoading = new ProgressBar(this);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(52), dp(52));
        loadingParams.gravity = Gravity.CENTER;
        root.addView(initialLoading, loadingParams);

        setContentView(root);

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                requestedPhotoPosition = position;
                releasePlayer();
                updateChrome(position);
                resolvePhoto(position);
                if (position >= Math.max(0, adapter.getItemCount() - 5)) loadMore();
            }
        });
    }

    private void showSnapshot(BunkrGallerySessionStore.Snapshot snapshot) {
        initialLoading.setVisibility(View.GONE);
        adapter.replace(filterMedia(snapshot.items), snapshot.resolvedUrls);
        currentPage = snapshot.currentPage;
        endReached = snapshot.endReached;
        if (adapter.getItemCount() == 0) {
            updateChrome(0);
            if (isCreatorGallery() && !endReached) {
                initialLoading.setVisibility(View.VISIBLE);
                loadMore();
            }
            return;
        }
        int start = adapter.indexOfUrl(initialUrl);
        if (start < 0) start = Math.min(initialPosition, Math.max(0, adapter.getItemCount() - 1));
        pager.setCurrentItem(start, false);
        updateChrome(start);
        resolvePhoto(start);
        if (start >= Math.max(0, adapter.getItemCount() - 5)) loadMore();
    }

    private void loadInitialPage() {
        if (albumUrl.isEmpty() && !isCreatorGallery()) {
            initialLoading.setVisibility(View.GONE);
            Toast.makeText(this, "This album could not be opened.", Toast.LENGTH_SHORT).show();
            return;
        }
        int requestGeneration = generation;
        pageIo.execute(() -> {
            try {
                BunkrCreatorGalleryRepository.Batch creatorBatch = isCreatorGallery()
                        ? creatorGalleryRepository.fetchNext(
                                this, sessionId, creatorQuery, fapelloProfileUrl, albumTitle)
                        : null;
                List<NativeContentItem> result = creatorBatch == null
                        ? repository.fetchAlbum(this, albumUrl, 1)
                        : creatorBatch.items;
                boolean completed = creatorBatch == null
                        ? result.isEmpty()
                        : creatorBatch.endReached;
                FapelloSourceException fapelloFailure = creatorBatch == null
                        ? null
                        : creatorBatch.fapelloFailure;
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    initialLoading.setVisibility(View.GONE);
                    currentPage = result.isEmpty() ? 0 : 1;
                    endReached = completed;
                    if (!isCreatorGallery()) BunkrGallerySessionStore.replace(
                            sessionId,
                            result,
                            currentPage,
                            endReached
                    );
                    BunkrGallerySessionStore.Snapshot fresh =
                            BunkrGallerySessionStore.snapshot(sessionId);
                    if (fresh != null && !fresh.items.isEmpty()) showSnapshot(fresh);
                    if (fapelloFailure != null) showFapelloFailure(fapelloFailure);
                    else if (fresh == null || fresh.items.isEmpty()) Toast.makeText(
                            this,
                            isCreatorGallery()
                                    ? "No matching pictures or videos loaded. Try again."
                                    : "No supported pictures or videos were found.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    initialLoading.setVisibility(View.GONE);
                    FapelloSourceException failure = fapelloFailure(error);
                    Toast.makeText(
                            this,
                            failure != null
                                    ? failure.userMessage()
                                    : isCreatorGallery()
                                    ? "Couldn't build this creator gallery. Try again."
                                    : "Couldn't load this album.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void loadMore() {
        if (loadingMore || endReached ||
                (albumUrl.isEmpty() && !isCreatorGallery()) ||
                (adapter.getItemCount() == 0 && !isCreatorGallery())) return;
        loadingMore = true;
        int requestPage = Math.max(1, currentPage + 1);
        int requestGeneration = generation;
        pageIo.execute(() -> {
            try {
                BunkrCreatorGalleryRepository.Batch creatorBatch = isCreatorGallery()
                        ? creatorGalleryRepository.fetchNext(
                                this, sessionId, creatorQuery, fapelloProfileUrl, albumTitle)
                        : null;
                List<NativeContentItem> result = creatorBatch == null
                        ? repository.fetchAlbum(this, albumUrl, requestPage)
                        : creatorBatch.items;
                List<NativeContentItem> visibleResult = filterMedia(result);
                boolean completed = creatorBatch != null && creatorBatch.endReached;
                FapelloSourceException fapelloFailure = creatorBatch == null
                        ? null
                        : creatorBatch.fapelloFailure;
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    loadingMore = false;
                    initialLoading.setVisibility(View.GONE);
                    BunkrGallerySessionStore.Snapshot currentCreator = isCreatorGallery()
                            ? BunkrGallerySessionStore.snapshot(sessionId) : null;
                    int added = adapter.append(currentCreator == null ? visibleResult : filterMedia(currentCreator.items));
                    if (currentCreator != null) {
                        currentPage = currentCreator.currentPage;
                        endReached = currentCreator.endReached;
                    } else if (isCreatorGallery()) endReached = completed;
                    else if (result.isEmpty() || added == 0) endReached = true;
                    else currentPage = requestPage;
                    if (currentCreator == null && isCreatorGallery() && added > 0) currentPage = requestPage;
                    if (!isCreatorGallery()) BunkrGallerySessionStore.append(
                            sessionId,
                            result,
                            currentPage,
                            endReached
                    );
                    BunkrGallerySessionStore.persist(this, sessionId);
                    updateChrome(pager.getCurrentItem());
                    if (fapelloFailure != null) showFapelloFailure(fapelloFailure);
                    if (isCreatorGallery() && added == 0 && !endReached) {
                        initialLoading.setVisibility(View.VISIBLE);
                        pager.post(this::loadMore);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    loadingMore = false;
                    initialLoading.setVisibility(View.GONE);
                    if (adapter.getItemCount() == 0) {
                        Toast.makeText(
                                this,
                                "Couldn't load more matching media.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            }
        });
    }

    private void showFapelloFailure(FapelloSourceException failure) {
        if (failure == null || fapelloFailureShown) return;
        fapelloFailureShown = true;
        Toast.makeText(this, failure.userMessage(), Toast.LENGTH_LONG).show();
    }

    private FapelloSourceException fapelloFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof FapelloSourceException) {
                return (FapelloSourceException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private void onMediaTap(int position, NativeContentItem item) {
        if (item == null) return;
        if (adapter.isFailed(position)) {
            adapter.setFailed(position, false);
            if (item.isVideo()) playVideo(position, item);
            else resolvePhoto(position);
            return;
        }
        if (item.isVideo()) playVideo(position, item);
        else toggleChrome();
    }

    private void resolvePhoto(int position) {
        NativeContentItem item = adapter.itemAt(position);
        if (item == null || !item.isImage() || adapter.isLoading(position) ||
                !adapter.resolvedUrl(position).isEmpty()) return;
        if (isOnlyHavenDirectImage(item)) {
            adapter.setResolvedUrl(position, item.url);
            BunkrGallerySessionStore.setResolvedUrl(sessionId, item.url, item.url);
            return;
        }
        adapter.setLoading(position, true);
        int requestGeneration = generation;
        mediaIo.execute(() -> {
            if (requestedPhotoPosition != position) {
                runOnUiThread(() -> {
                    if (requestGeneration == generation && !isFinishing()) {
                        adapter.setLoading(position, false);
                    }
                });
                return;
            }
            try {
                CrazyShitRepository.StreamInfo resolved = PlayableSourceRouter.resolve(
                        this,
                        item.url
                );
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    adapter.setResolvedUrl(position, resolved.mediaUrl);
                    BunkrGallerySessionStore.setResolvedUrl(
                            sessionId,
                            item.url,
                            resolved.mediaUrl
                    );
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    adapter.setLoading(position, false);
                    adapter.setFailed(position, true);
                });
            }
        });
    }

    private void playVideo(int position, NativeContentItem item) {
        if (activeVideoPosition == position && player != null) {
            if (player.isPlaying()) player.pause(); else player.play();
            return;
        }
        playbackRecovery.reset();
        releasePlayer();
        String cached = adapter.resolvedUrl(position);
        if (!cached.isEmpty()) {
            startPlayer(position, item, cached, item.url);
            return;
        }
        if (isOnlyHavenDirectVideo(item)) {
            startPlayer(position, item, item.url, value(item.uploader));
            return;
        }

        adapter.setLoading(position, true);
        int requestGeneration = generation;
        mediaIo.execute(() -> {
            try {
                CrazyShitRepository.StreamInfo resolved = PlayableSourceRouter.resolve(
                        this,
                        item.url
                );
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing() ||
                            pager.getCurrentItem() != position) return;
                    adapter.setResolvedUrl(position, resolved.mediaUrl);
                    BunkrGallerySessionStore.setResolvedUrl(
                            sessionId,
                            item.url,
                            resolved.mediaUrl
                    );
                    startPlayer(
                            position,
                            item,
                            resolved.mediaUrl,
                            value(resolved.requestReferer)
                    );
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    adapter.setLoading(position, false);
                    adapter.setFailed(position, true);
                    Toast.makeText(this, "Couldn't play this video.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startPlayer(
            int position,
            NativeContentItem item,
            String mediaUrl,
            String requestReferer
    ) {
        if (mediaUrl == null || mediaUrl.isEmpty()) return;
        releasePlayer();

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT);
        Map<String, String> headers = new LinkedHashMap<>();
        String referer = requestReferer.isEmpty() ? item.url : requestReferer;
        if (!referer.isEmpty()) {
            headers.put("Referer", referer);
            try {
                Uri parsed = Uri.parse(referer);
                if (parsed.getScheme() != null && parsed.getHost() != null) {
                    headers.put("Origin", parsed.getScheme() + "://" + parsed.getHost());
                }
            } catch (Exception ignored) {
            }
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(mediaUrl);
            if ((cookies == null || cookies.isEmpty()) && !item.url.isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(item.url);
            }
            if (cookies != null && !cookies.isEmpty()) headers.put("Cookie", cookies);
        } catch (Exception ignored) {
        }
        if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);

        DefaultMediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
        activeVideoPosition = position;
        adapter.activateVideo(position, player);

        MediaItem.Builder media = new MediaItem.Builder().setUri(mediaUrl);
        String lower = mediaUrl.toLowerCase();
        if (lower.contains(".m3u8")) media.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.contains(".mpd")) media.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(media.build());
        playbackRecovery.bind(player, item.url);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    RatingFeedbackPrompt.recordSuccessfulPlayback(
                            BunkrGalleryActivity.this, mediaUrl);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                BunkrGallerySessionStore.clearResolvedUrl(sessionId, item.url);
                adapter.setResolvedUrl(position, "");
                if (playbackRecovery.recover(BunkrGalleryActivity.this, error, recovered -> {
                    if (pager.getCurrentItem() != position) return;
                    adapter.setResolvedUrl(position, recovered.stream.mediaUrl);
                    startPlayer(position, item, recovered.stream.mediaUrl, value(recovered.stream.requestReferer));
                    player.seekTo(recovered.position);
                    player.setPlayWhenReady(recovered.playWhenReady && recoveryResumed);
                }, () -> {
                    RatingFeedbackPrompt.recordPlaybackError(BunkrGalleryActivity.this);
                    releasePlayer(); adapter.setFailed(position, true);
                    Toast.makeText(BunkrGalleryActivity.this, "Couldn't refresh this video. Tap it to retry.", Toast.LENGTH_SHORT).show();
                })) return;
                RatingFeedbackPrompt.recordPlaybackError(BunkrGalleryActivity.this);
                releasePlayer();
                adapter.setFailed(position, true);
                Toast.makeText(BunkrGalleryActivity.this, "Couldn't continue this video. Tap it to retry.", Toast.LENGTH_SHORT).show();
            }
        });
        player.setPlayWhenReady(true);
        player.prepare();
        setChromeVisible(false);
    }

    private void releasePlayer() {
        playbackRecovery.cancel();
        if (player != null) {
            player.release();
            player = null;
        }
        activeVideoPosition = -1;
        if (adapter != null) adapter.clearActiveVideo();
    }

    private void updateChrome(int position) {
        NativeContentItem item = adapter.itemAt(position);
        int total = adapter.getItemCount();
        countView.setText(total == 0 ? "" : (position + 1) + " of " + total);
        if (item == null) {
            downloadAction.setVisibility(View.GONE);
            itemTitleView.setText("");
            itemMetaView.setText("");
            return;
        }
        downloadAction.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        itemTitleView.setText(item.title);
        ArrayList<String> meta = new ArrayList<>();
        meta.add(item.isVideo() ? "Video" : "Photo");
        if (item.views != null && !item.views.isEmpty()) meta.add(item.views);
        itemMetaView.setText(TextUtils.join("  •  ", meta));
    }

    private void toggleChrome() {
        setChromeVisible(!chromeVisible);
    }

    private void setChromeVisible(boolean visible) {
        chromeVisible = visible;
        topBar.animate().cancel();
        bottomBar.animate().cancel();
        if (visible) {
            topBar.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
            topBar.animate().alpha(1f).setDuration(140L).start();
            bottomBar.animate().alpha(1f).setDuration(140L).start();
        } else {
            topBar.animate().alpha(0f).setDuration(140L).withEndAction(() -> {
                if (!chromeVisible) topBar.setVisibility(View.INVISIBLE);
            }).start();
            bottomBar.animate().alpha(0f).setDuration(140L).withEndAction(() -> {
                if (!chromeVisible) bottomBar.setVisibility(View.INVISIBLE);
            }).start();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyViewerOrientation(newConfig.orientation);
    }

    private void onPhysicalOrientation(SensorMediaOrientationListener.Position position) {
        if (position == SensorMediaOrientationListener.Position.LANDSCAPE) {
            sensorFullscreen = true;
            PhoneOrientationPolicy.enterSensorFullscreen(this);
        } else if (sensorFullscreen) {
            sensorFullscreen = false;
            PhoneOrientationPolicy.exitFullscreenVideo(this);
        }
    }

    private void applyViewerOrientation(int orientation) {
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (topBar == null || bottomBar == null) {
            setSystemBars(landscape);
            return;
        }
        if (landscape && !landscapeFullscreen) {
            landscapeFullscreen = true;
            restoreChromeAfterLandscape = chromeVisible;
            if (chromeVisible) setChromeVisible(false);
        } else if (!landscape && landscapeFullscreen) {
            landscapeFullscreen = false;
            if (restoreChromeAfterLandscape) setChromeVisible(true);
            restoreChromeAfterLandscape = false;
        }
        setSystemBars(landscape);
    }

    private void setSystemBars(boolean fullscreen) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            int types = WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars();
            if (fullscreen) {
                controller.hide(types);
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            } else {
                controller.show(types);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(fullscreen
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void showMenu(View anchor) {
        NativeContentItem current = adapter.itemAt(pager.getCurrentItem());
        PopupMenu menu = new PopupMenu(this, anchor);
        if (current != null && current.isVideo()) {
            menu.getMenu().add(Menu.NONE, 3, 0, "Download video");
        }
        menu.getMenu().add(Menu.NONE, 1, 1, "Open item page");
        menu.getMenu().add(
                Menu.NONE,
                2,
                2,
                isCreatorGallery() ? "Open creator search page" : "Open album page"
        );
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 3) {
                downloadCurrentVideo();
                return true;
            }
            if (item.getItemId() == 1) {
                if (current != null) openPage(current.url);
                return true;
            }
            if (item.getItemId() == 2) {
                openPage(albumUrl);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void downloadCurrentVideo() {
        NativeContentItem item = adapter.itemAt(pager.getCurrentItem());
        if (item == null || !item.isVideo()) {
            Toast.makeText(this, "This item is not a video.", Toast.LENGTH_SHORT).show();
            return;
        }
        VideoDownloadStore.downloadPage(this, item);
    }

    private void shareCurrent() {
        NativeContentItem item = adapter.itemAt(pager.getCurrentItem());
        if (item == null || item.url.isEmpty()) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, item.url);
        share.putExtra(Intent.EXTRA_SUBJECT, item.title);
        startActivity(Intent.createChooser(share, "Share"));
    }

    private void openPage(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, url);
        startActivity(intent);
    }

    private boolean isCreatorGallery() {
        return creatorQuery != null && !creatorQuery.isEmpty();
    }

    private ArrayList<NativeContentItem> filterMedia(List<NativeContentItem> items) {
        ArrayList<NativeContentItem> filtered = new ArrayList<>();
        if (items == null) return filtered;
        for (NativeContentItem item : items) {
            if (item == null) continue;
            if (FILTER_PICTURES.equals(mediaFilter) && !item.isImage()) continue;
            if (FILTER_VIDEOS.equals(mediaFilter) && !item.isVideo()) continue;
            filtered.add(item);
        }
        return filtered;
    }

    @Override
    protected void onResume() {
        super.onResume();
        recoveryResumed = true;
        if (orientationListener != null) orientationListener.enable();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("session", sessionId);
        if (pager != null && adapter != null) {
            int index = pager.getCurrentItem();
            NativeContentItem item = adapter.itemAt(index);
            state.putInt("current_position", index);
            state.putString("current_url", item == null ? initialUrl : item.url);
        }
        BunkrGallerySessionStore.persist(this, sessionId);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        recoveryResumed = false;
        if (orientationListener != null) orientationListener.disable();
        BunkrGallerySessionStore.persist(this, sessionId);
        releasePlayer();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        generation++;
        if (orientationListener != null) orientationListener.disable();
        releasePlayer();
        pageIo.shutdownNow();
        mediaIo.shutdownNow();
        super.onDestroy();
    }

    private TextView action(String label, int size) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private boolean isOnlyHavenDirectImage(NativeContentItem item) {
        return item != null && item.isImage() &&
                OnlyHavenRepository.isOnlyHavenUrl(item.url) &&
                OnlyHavenRepository.isDirectImageUrl(item.url);
    }

    private boolean isOnlyHavenDirectVideo(NativeContentItem item) {
        if (item == null || !item.isVideo()) return false;
        boolean onlyHaven = OnlyHavenRepository.isOnlyHavenUrl(item.uploader) ||
                value(item.description).toLowerCase(java.util.Locale.US).contains("onlyhaven");
        if (!onlyHaven) return false;
        String lower = value(item.url).toLowerCase(java.util.Locale.US);
        return lower.matches(".*\\.(?:mp4|m3u8|mpd|webm|m4v)(?:\\?.*)?$");
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
