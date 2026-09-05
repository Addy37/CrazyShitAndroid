package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class VideoDetailActivity extends Activity {
    public static final String EXTRA_VIEWS = "views";
    public static final String EXTRA_UPLOADER = "uploader";
    public static final String EXTRA_COMMENTS = "comments";
    public static final String EXTRA_REOPEN_DETAIL = "reopen_detail";
    public static final String EXTRA_RELATED_FEED_URL = "related_feed_url";
    public static final String EXTRA_SOURCE = "content_source";
    public static final String EXTRA_MEDIA_REFERER = "media_referer";

    private static final String SITE = "https://crazyshit.com/";
    private static final int CONTROL_TIMEOUT_MS = 2600;
    private static final int RELATED_HISTORY_LIMIT = 24;
    private static final int RELATED_THUMBNAIL_WORKERS = 4;
    private static final String THUMB_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final EfuktRepository efuktRepository = new EfuktRepository();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final Map<String, ImageView> relatedImages = new LinkedHashMap<>();
    private final Map<String, String> resolvedRelatedThumbnails = new LinkedHashMap<>();
    private final Set<String> requestedRelatedThumbnails = new HashSet<>();
    private final ArrayDeque<VideoHistoryEntry> relatedHistory = new ArrayDeque<>();

    private FrameLayout root;
    private LinearLayout shell;
    private SwipeMinimizeFrameLayout playerContainer;
    private PlayerView playerView;
    private ScrollView detailsScroll;
    private LinearLayout detailsColumn;
    private LinearLayout relatedContainer;
    private TextView titleView;
    private TextView metaView;
    private TextView playerTitleView;
    private ImageButton portraitFullscreenButton;
    private ProgressBar loading;
    private ExoPlayer player;
    private RenderedThumbnailResolver[] thumbnailResolvers;
    private OnBackInvokedCallback backCallback;
    private FrameLayout relatedBackPreviewLayer;
    private ImageView relatedBackPreviewImage;
    private VideoHistoryEntry relatedBackPreviewEntry;
    private float relatedBackDirection = 1f;

    private String mediaUrl;
    private String pageUrl;
    private String title;
    private String views;
    private String uploader;
    private String comments;
    private String userAgent;
    private String cookies;
    private String relatedFeedUrl;
    private String source;
    private String mediaReferer;
    private final PlaybackRecovery playbackRecovery = new PlaybackRecovery();
    private boolean recoveryResumed;
    private long requestedStartPosition;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private boolean failureShown;
    private boolean minimizing;
    private boolean entrancePlayed;
    private boolean portraitVideo;
    private boolean portraitFullscreen;
    private int thumbnailResolverCursor;
    private int relatedLoadGeneration;
    private int relatedPlayGeneration;

    private static final class VideoHistoryEntry {
        final String mediaUrl;
        final String pageUrl;
        final String title;
        final String views;
        final String uploader;
        final String comments;
        final String userAgent;
        final String cookies;
        final String mediaReferer;
        final long positionMs;
        final Bitmap previewBitmap;

        VideoHistoryEntry(
                String mediaUrl,
                String pageUrl,
                String title,
                String views,
                String uploader,
                String comments,
                String userAgent,
                String cookies,
                String mediaReferer,
                long positionMs,
                Bitmap previewBitmap
        ) {
            this.mediaUrl = mediaUrl;
            this.pageUrl = pageUrl;
            this.title = title;
            this.views = views;
            this.uploader = uploader;
            this.comments = comments;
            this.userAgent = userAgent;
            this.cookies = cookies;
            this.mediaReferer = mediaReferer;
            this.positionMs = positionMs;
            this.previewBitmap = previewBitmap;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        }

        mediaUrl = getIntent().getStringExtra(PlayerActivity.EXTRA_MEDIA_URL);
        pageUrl = getIntent().getStringExtra(PlayerActivity.EXTRA_PAGE_URL);
        title = clean(getIntent().getStringExtra(PlayerActivity.EXTRA_TITLE));
        views = clean(getIntent().getStringExtra(EXTRA_VIEWS));
        uploader = clean(getIntent().getStringExtra(EXTRA_UPLOADER));
        comments = clean(getIntent().getStringExtra(EXTRA_COMMENTS));
        userAgent = clean(getIntent().getStringExtra(PlayerActivity.EXTRA_USER_AGENT));
        cookies = clean(getIntent().getStringExtra(PlayerActivity.EXTRA_COOKIES));
        relatedFeedUrl = clean(getIntent().getStringExtra(EXTRA_RELATED_FEED_URL));
        source = clean(getIntent().getStringExtra(EXTRA_SOURCE));
        mediaReferer = clean(getIntent().getStringExtra(EXTRA_MEDIA_REFERER));
        requestedStartPosition = getIntent().getLongExtra(PlayerActivity.EXTRA_START_POSITION, -1L);

        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            finish();
            return;
        }
        if (title.isEmpty()) title = "Video";
        if (pageUrl == null) pageUrl = "";
        if (mediaReferer.isEmpty()) mediaReferer = pageUrl;

        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        buildUi();
        buildPlayer(requestedStartPosition);
        thumbnailResolvers = new RenderedThumbnailResolver[RELATED_THUMBNAIL_WORKERS];
        for (int i = 0; i < thumbnailResolvers.length; i++) {
            thumbnailResolvers[i] = new RenderedThumbnailResolver(this, this::onThumbnailResolved);
        }
        configureBackHandling();
        applyOrientation(getResources().getConfiguration().orientation);
        loadRelated();
        root.post(this::playEntranceOnce);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(oledEnabled() ? Color.BLACK : Color.rgb(13, 13, 15));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            if (portraitFullscreen ||
                    getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                view.setPadding(0, 0, 0, 0);
                return insets;
            }
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        playerContainer = new SwipeMinimizeFrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setPivotY(0f);
        playerContainer.setListener(new SwipeMinimizeFrameLayout.Listener() {
            @Override
            public void onDrag(float distancePx, float progress) {
                if (minimizing) return;
                playerView.hideController();
                playerContainer.setPivotX(playerContainer.getWidth() / 2f);

                float scale = 1f - (0.08f * progress);
                float shift = Math.min(dp(38), distancePx * 0.22f);
                playerContainer.setScaleX(scale);
                playerContainer.setScaleY(scale);
                playerContainer.setTranslationY(shift);
                playerContainer.setAlpha(1f);

                if (detailsScroll != null) {
                    detailsScroll.setAlpha(1f - (0.42f * progress));
                    detailsScroll.setTranslationY(Math.min(dp(12), distancePx * 0.04f));
                }
            }

            @Override
            public void onRelease(boolean minimize, float distancePx) {
                if (minimizing) return;
                if (minimize) finishSwipeMinimize();
                else restoreFromSwipe();
            }
        });
        shell.addView(playerContainer, new LinearLayout.LayoutParams(-1, portraitPlayerHeight()));

        playerView = (PlayerView) getLayoutInflater().inflate(
                R.layout.view_polished_video_player_texture,
                playerContainer,
                false
        );
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(false);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerShowTimeoutMs(CONTROL_TIMEOUT_MS);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(resizeMode);
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        View playerBack = playerView.findViewById(R.id.player_back);
        playerTitleView = playerView.findViewById(R.id.player_title);
        portraitFullscreenButton = playerView.findViewById(R.id.player_portrait_fullscreen);
        View playerMenu = playerView.findViewById(R.id.player_menu);
        playerTitleView.setText(title);
        playerBack.setOnClickListener(v -> {
            haptic(v);
            handleBack();
        });
        playerMenu.setOnClickListener(v -> {
            haptic(v);
            showPlayerMenu();
        });
        portraitFullscreenButton.setOnClickListener(v -> {
            haptic(v);
            setPortraitFullscreen(!portraitFullscreen);
        });
        updatePortraitFullscreenButton();
        playerView.hideController();

        detailsScroll = new ScrollView(this);
        detailsScroll.setFillViewport(true);
        applyDetailsBackground();
        shell.addView(detailsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        detailsColumn = new LinearLayout(this);
        detailsColumn.setOrientation(LinearLayout.VERTICAL);
        detailsColumn.setPadding(dp(16), dp(15), dp(16), dp(30));
        detailsScroll.addView(detailsColumn, new ScrollView.LayoutParams(-1, -2));

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(21);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setLineSpacing(0f, 1.08f);
        detailsColumn.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        metaView = new TextView(this);
        metaView.setTextColor(Color.rgb(165, 165, 174));
        metaView.setTextSize(12);
        metaView.setPadding(0, dp(6), 0, dp(9));
        detailsColumn.addView(metaView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, 0, 0, dp(3));
        detailsColumn.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        if (supportsComments()) {
            actions.addView(actionButton(comments.isEmpty() ? "💬 Comments" : "💬 " + comments, this::openComments), actionParams());
        }
        actions.addView(actionButton("♡ Later", this::toggleWatchLater), actionParams());
        actions.addView(actionButton("↗ Share", this::sharePage), actionParams());

        TextView relatedTitle = new TextView(this);
        relatedTitle.setText("Related videos");
        relatedTitle.setTextColor(Color.WHITE);
        relatedTitle.setTextSize(18);
        relatedTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        relatedTitle.setPadding(dp(2), dp(18), dp(2), dp(8));
        detailsColumn.addView(relatedTitle, new LinearLayout.LayoutParams(-1, -2));

        relatedContainer = new LinearLayout(this);
        relatedContainer.setOrientation(LinearLayout.VERTICAL);
        detailsColumn.addView(relatedContainer, new LinearLayout.LayoutParams(-1, -2));

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(52));
        lp.gravity = Gravity.CENTER;
        root.addView(loading, lp);

        updateMetadataUi();
        setContentView(root);
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private void applyDetailsBackground() {
        if (detailsScroll == null) return;
        if (!oledEnabled()) {
            detailsScroll.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {Color.rgb(31, 30, 9), Color.rgb(16, 16, 19), Color.rgb(13, 13, 15)}
            ));
            return;
        }
        boolean glow = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("ambient_feed_glow", true);
        if (!glow) {
            detailsScroll.setBackgroundColor(Color.BLACK);
            return;
        }
        detailsScroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.rgb(8, 8, 1), Color.rgb(2, 2, 0), Color.BLACK, Color.BLACK}
        ));
    }

    private boolean oledEnabled() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("oled_black_enabled", true);
    }

    private void playEntranceOnce() {
        if (entrancePlayed || isFinishing()) return;
        entrancePlayed = true;
        if (playerContainer != null) {
            playerContainer.animate().cancel();
            playerContainer.setPivotX(playerContainer.getWidth() * 0.5f);
            playerContainer.setPivotY(0f);
            playerContainer.setScaleX(0.98f);
            playerContainer.setScaleY(0.98f);
            playerContainer.setAlpha(0.72f);
            playerContainer.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        if (detailsScroll != null) {
            detailsScroll.animate().cancel();
            detailsScroll.setAlpha(0.35f);
            detailsScroll.setTranslationY(dp(6));
            detailsScroll.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(25L)
                    .setDuration(190L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void finishSwipeMinimize() {
        minimizing = true;
        updateSwipeEnabled();
        savePlaybackState(false);
        haptic(playerContainer);

        playerContainer.animate().cancel();
        if (detailsScroll != null) detailsScroll.animate().cancel();

        if (detailsScroll != null) {
            detailsScroll.animate()
                    .alpha(0f)
                    .translationY(dp(10))
                    .setDuration(90L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        playerContainer.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .translationY(dp(28))
                .alpha(0f)
                .setDuration(110L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::minimizeToFeed)
                .start();
    }

    private void restoreFromSwipe() {
        playerContainer.animate().cancel();
        playerContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .alpha(1f)
                .setDuration(150L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> playerView.showController())
                .start();
        if (detailsScroll != null) {
            detailsScroll.animate().cancel();
            detailsScroll.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(150L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private TextView actionButton(String text, Runnable action) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.rgb(238, 238, 242));
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setBackground(actionPill());
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });
        return button;
    }

    private GradientDrawable actionPill() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(oledEnabled() ? Color.rgb(8, 8, 10) : Color.rgb(27, 27, 32));
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), oledEnabled() ? Color.rgb(30, 30, 34) : Color.rgb(55, 55, 63));
        return bg;
    }

    private void buildPlayer(long startPosition) {
        portraitVideo = false;
        if (portraitFullscreen) setPortraitFullscreen(false);
        else updatePortraitFullscreenButton();
        releasePlayer();
        failureShown = false;

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
        if (!userAgent.isEmpty()) httpFactory.setUserAgent(userAgent);

        Map<String, String> headers = new LinkedHashMap<>();
        if (!mediaReferer.isEmpty()) {
            headers.put("Referer", mediaReferer);
            try {
                Uri page = Uri.parse(mediaReferer);
                if (page.getScheme() != null && page.getHost() != null) {
                    headers.put("Origin", page.getScheme() + "://" + page.getHost());
                }
            } catch (Exception ignored) {
            }
        }
        if (!cookies.isEmpty()) headers.put("Cookie", cookies);
        if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);

        DefaultMediaSourceFactory sourceFactory =
                new DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(sourceFactory).build();
        playerView.setPlayer(player);

        MediaItem.Builder item = new MediaItem.Builder().setUri(mediaUrl);
        String lower = mediaUrl.toLowerCase();
        if (lower.contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.contains(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(item.build());
        playbackRecovery.bind(player, pageUrl);

        long position = startPosition;
        if (position < 0L && rememberPositionEnabled()) {
            position = getSharedPreferences("player_positions", MODE_PRIVATE)
                    .getLong(positionKey(), 0L);
        }
        if (position > 0L) player.seekTo(position);
        player.setPlayWhenReady(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    String readyPageUrl = pageUrl;
                    playerView.postDelayed(() -> {
                        if (supportsComments() && readyPageUrl.equals(pageUrl)) {
                            NativeCommentsLoader.preload(VideoDetailActivity.this, readyPageUrl);
                        }
                    }, 650L);
                } else if (playbackState == Player.STATE_ENDED) {
                    savePlaybackState(true);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (!recoverPlayback(error)) showPlaybackFailure();
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                float displayWidth = videoSize == null
                        ? 0f
                        : videoSize.width * videoSize.pixelWidthHeightRatio;
                boolean isPortrait = videoSize != null
                        && videoSize.width > 0
                        && videoSize.height > displayWidth;
                if (portraitVideo == isPortrait) return;
                portraitVideo = isPortrait;
                if (!portraitVideo && portraitFullscreen) setPortraitFullscreen(false);
                else updatePortraitFullscreenButton();
            }
        });
        player.prepare();
    }

    private void updateMetadataUi() {
        if (titleView != null) titleView.setText(title);
        if (playerTitleView != null) playerTitleView.setText(title);
        if (metaView != null) {
            ArrayList<String> parts = new ArrayList<>();
            if (!views.isEmpty()) parts.add(views + " views");
            if (!uploader.isEmpty()) parts.add(uploader);
            metaView.setText(TextUtils.join("  •  ", parts));
        }
    }

    private void loadRelated() {
        if (relatedContainer == null) return;
        final int requestGeneration = ++relatedLoadGeneration;
        relatedContainer.removeAllViews();
        TextView loadingText = new TextView(this);
        loadingText.setText("Loading related videos…");
        loadingText.setTextColor(Color.rgb(155, 155, 164));
        loadingText.setTextSize(13);
        loadingText.setPadding(dp(4), dp(10), dp(4), dp(18));
        relatedContainer.addView(loadingText);

        final String excludeUrl = pageUrl;
        io.execute(() -> {
            LinkedHashMap<String, NativeContentItem> merged = new LinkedHashMap<>();
            if (isBunkr()) {
                try {
                    if (!relatedFeedUrl.isEmpty()) {
                        List<NativeContentItem> album = bunkrRepository.fetchAlbum(this, relatedFeedUrl, 1);
                        for (NativeContentItem item : album) {
                            if (item.isVideo() && !item.url.equals(excludeUrl)) {
                                merged.put(item.url, item);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            } else if (isEfukt()) {
                try {
                    String feed = relatedFeedUrl.isEmpty() ? EfuktRepository.SERIES : relatedFeedUrl;
                    List<NativeContentItem> series = efuktRepository.fetchSeriesFeed(this, feed, 1);
                    for (NativeContentItem item : series) {
                        if (!item.url.equals(excludeUrl)) merged.put(item.url, item);
                    }
                } catch (Exception ignored) {
                }
            } else {
                try {
                    List<NativeContentItem> home = repository.fetchFeed(this, CrazyShitRepository.HOME, 1);
                    for (NativeContentItem item : home) {
                        if (!item.url.equals(excludeUrl)) merged.put(item.url, item);
                    }
                } catch (Exception ignored) {
                }
                try {
                    List<NativeContentItem> trending = repository.fetchFeed(this, CrazyShitRepository.TRENDING, 1);
                    for (NativeContentItem item : trending) {
                        if (!item.url.equals(excludeUrl)) merged.putIfAbsent(item.url, item);
                    }
                } catch (Exception ignored) {
                }
            }
            ArrayList<NativeContentItem> result = new ArrayList<>();
            for (NativeContentItem item : merged.values()) {
                result.add(item);
                if (result.size() >= 12) break;
            }
            runOnUiThread(() -> {
                if (isFinishing() || requestGeneration != relatedLoadGeneration) return;
                if (!excludeUrl.equals(pageUrl)) return;
                renderRelated(result);
            });
        });
    }

    void renderContextRelated(List<NativeContentItem> items) {
        if (isFinishing() || items == null || items.isEmpty()) return;
        relatedLoadGeneration++;
        renderRelated(items);
    }

    private void renderRelated(List<NativeContentItem> items) {
        if (relatedContainer == null) return;
        relatedContainer.removeAllViews();
        clearRelatedImageTargets();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No related videos could be loaded right now.");
            empty.setTextColor(Color.rgb(155, 155, 164));
            empty.setTextSize(13);
            empty.setPadding(dp(4), dp(10), dp(4), dp(18));
            relatedContainer.addView(empty);
            return;
        }
        for (NativeContentItem item : items) {
            relatedContainer.addView(buildRelatedCard(item), marginParams(0, dp(8)));
        }
    }

    private View buildRelatedCard(NativeContentItem item) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(oledEnabled() ? Color.rgb(9, 9, 11) : Color.rgb(23, 23, 27));
        card.setStrokeColor(oledEnabled() ? Color.rgb(29, 29, 33) : Color.rgb(49, 49, 57));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            haptic(v);
            playRelated(item);
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new MaterialCardView.LayoutParams(-1, -2));

        FrameLayout visual = new FrameLayout(this);
        row.addView(visual, new LinearLayout.LayoutParams(dp(146), dp(92)));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(18, 18, 21));
        visual.addView(image, new FrameLayout.LayoutParams(-1, -1));
        TextView play = new TextView(this);
        play.setText("▶");
        play.setTextColor(Color.WHITE);
        play.setTextSize(18);
        play.setGravity(Gravity.CENTER);
        play.setBackground(new ColorDrawable(Color.argb(120, 0, 0, 0)));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(38), dp(38));
        pp.gravity = Gravity.CENTER;
        visual.addView(play, pp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), dp(9), dp(10), dp(9));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = new TextView(this);
        name.setText(item.title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(14);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);

        ArrayList<String> info = new ArrayList<>();
        if (!clean(item.views).isEmpty()) info.add(item.views + " views");
        if (!clean(item.uploader).isEmpty()) info.add(item.uploader);
        TextView meta = new TextView(this);
        meta.setText(TextUtils.join("  •  ", info));
        meta.setTextColor(Color.rgb(160, 160, 170));
        meta.setTextSize(11);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(5), 0, 0);
        copy.addView(meta);

        if (!clean(item.comments).isEmpty()) {
            TextView commentCount = new TextView(this);
            commentCount.setText(item.comments + " comments");
            commentCount.setTextColor(UiPalette.PRIMARY);
            commentCount.setTextSize(11);
            commentCount.setPadding(0, dp(5), 0, 0);
            copy.addView(commentCount);
        }

        relatedImages.put(item.url, image);
        String resolvedThumbnail = resolvedRelatedThumbnails.get(item.url);
        if (!clean(resolvedThumbnail).isEmpty()) {
            loadImage(image, resolvedThumbnail, item.url);
        } else if (!clean(item.imageUrl).isEmpty()) {
            loadImage(image, item.imageUrl, item.url);
        }
        requestRelatedThumbnail(item.url);
        return card;
    }

    private void requestRelatedThumbnail(String page) {
        if (page == null || page.isEmpty() || thumbnailResolvers == null || thumbnailResolvers.length == 0) {
            return;
        }
        if (resolvedRelatedThumbnails.containsKey(page)) return;
        if (!requestedRelatedThumbnails.add(page)) return;
        RenderedThumbnailResolver resolver =
                thumbnailResolvers[thumbnailResolverCursor++ % thumbnailResolvers.length];
        resolver.request(page);
    }

    private void onThumbnailResolved(String page, String imageUrl) {
        if (page == null || page.isEmpty()) return;
        if (imageUrl == null || imageUrl.isEmpty()) {
            requestedRelatedThumbnails.remove(page);
            return;
        }
        resolvedRelatedThumbnails.put(page, imageUrl);
        ImageView target = relatedImages.get(page);
        if (target == null) return;
        loadImage(target, imageUrl, page);
    }

    private void clearRelatedImageTargets() {
        for (ImageView image : relatedImages.values()) {
            if (image == null) continue;
            try {
                Glide.with(image).clear(image);
            } catch (Exception ignored) {
            }
        }
        relatedImages.clear();
    }

    private void loadImage(ImageView view, String imageUrl, String referer) {
        Object source = imageUrl.startsWith("file://") ? imageUrl : withHeaders(imageUrl, referer);
        try {
            Glide.with(view)
                    .load(source)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.rgb(18, 18, 21)))
                    .error(new ColorDrawable(Color.rgb(18, 18, 21)))
                    .into(view);
        } catch (Exception ignored) {
        }
    }

    private GlideUrl withHeaders(String imageUrl, String referer) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", THUMB_UA)
                .addHeader("Referer", referer == null || referer.isEmpty() ? SITE : referer)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String c = CookieManager.getInstance().getCookie(imageUrl);
            if ((c == null || c.isEmpty()) && referer != null) c = CookieManager.getInstance().getCookie(referer);
            if (c != null && !c.isEmpty()) headers.addHeader("Cookie", c);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private void playRelated(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        loading.setVisibility(View.VISIBLE);
        final int requestGeneration = ++relatedPlayGeneration;
        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = PlayableSourceRouter.resolve(this, item.url);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            runOnUiThread(() -> {
                if (isFinishing() || requestGeneration != relatedPlayGeneration) return;
                loading.setVisibility(View.GONE);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    openWebsite(item.url);
                    return;
                }
                pushCurrentVideo();
                savePlaybackState(false);
                mediaUrl = resolved.mediaUrl;
                pageUrl = item.url;
                mediaReferer = resolved.requestReferer;
                title = clean(item.title).isEmpty() ? resolved.title : item.title;
                views = clean(item.views);
                uploader = clean(item.uploader);
                comments = clean(item.comments);
                userAgent = defaultUserAgent();
                cookies = cookiesFor(mediaUrl, pageUrl);
                requestedStartPosition = -1L;
                updateMetadataUi();
                buildPlayer(-1L);
                if (detailsScroll != null) detailsScroll.smoothScrollTo(0, 0);
                loadRelated();
            });
        });
    }

    private void pushCurrentVideo() {
        if (mediaUrl == null || mediaUrl.isEmpty()) return;
        long position = player == null ? 0L : Math.max(0L, player.getCurrentPosition());
        relatedHistory.addLast(new VideoHistoryEntry(
                mediaUrl,
                pageUrl,
                title,
                views,
                uploader,
                comments,
                userAgent,
                cookies,
                mediaReferer,
                position,
                captureRelatedBackPreview()
        ));
        while (relatedHistory.size() > RELATED_HISTORY_LIMIT) {
            recycleHistoryPreview(relatedHistory.removeFirst());
        }
    }

    private boolean restorePreviousRelatedVideo() {
        VideoHistoryEntry previous = relatedHistory.pollLast();
        if (previous == null) return false;

        relatedPlayGeneration++;
        savePlaybackState(false);
        loading.setVisibility(View.GONE);
        mediaUrl = previous.mediaUrl;
        pageUrl = previous.pageUrl;
        title = previous.title;
        views = previous.views;
        uploader = previous.uploader;
        comments = previous.comments;
        userAgent = previous.userAgent;
        cookies = previous.cookies;
        mediaReferer = previous.mediaReferer;
        requestedStartPosition = previous.positionMs;
        updateMetadataUi();
        buildPlayer(previous.positionMs);
        if (detailsScroll != null) detailsScroll.smoothScrollTo(0, 0);
        loadRelated();
        recycleHistoryPreview(previous);
        return true;
    }

    private Bitmap captureRelatedBackPreview() {
        TextureView texture = findTextureView(playerView);
        if (texture == null || !texture.isAvailable() || texture.getWidth() <= 0 || texture.getHeight() <= 0) {
            return null;
        }
        int targetWidth = Math.min(texture.getWidth(), 480);
        int targetHeight = Math.max(1, Math.round(texture.getHeight() * (targetWidth / (float) texture.getWidth())));
        Bitmap frame = null;
        try {
            frame = texture.getBitmap(targetWidth, targetHeight);
            if (frame == null) return null;
            Bitmap compact = frame.copy(Bitmap.Config.RGB_565, false);
            if (compact == null) return frame;
            frame.recycle();
            return compact;
        } catch (Throwable ignored) {
            if (frame != null && !frame.isRecycled()) frame.recycle();
            return null;
        }
    }

    private TextureView findTextureView(View candidate) {
        if (candidate instanceof TextureView) return (TextureView) candidate;
        if (!(candidate instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) candidate;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextureView texture = findTextureView(group.getChildAt(i));
            if (texture != null) return texture;
        }
        return null;
    }

    private void recycleHistoryPreview(VideoHistoryEntry entry) {
        if (entry == null || entry.previewBitmap == null || entry.previewBitmap.isRecycled()) return;
        entry.previewBitmap.recycle();
    }

    boolean canPreviewRelatedBack() {
        return !minimizing
                && !portraitFullscreen
                && root != null
                && shell != null
                && getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE
                && !relatedHistory.isEmpty();
    }

    boolean startRelatedBackPreview(boolean fromRight) {
        if (!canPreviewRelatedBack()) return false;
        VideoHistoryEntry previous = relatedHistory.peekLast();
        if (previous == null) return false;

        clearRelatedBackPreviewLayer();
        resetRelatedBackForeground();
        relatedBackPreviewEntry = previous;
        relatedBackDirection = fromRight ? -1f : 1f;

        FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(oledEnabled() ? Color.BLACK : Color.rgb(13, 13, 15));
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        FrameLayout mediaPreview = new FrameLayout(this);
        mediaPreview.setBackgroundColor(Color.BLACK);
        FrameLayout.LayoutParams mediaParams = new FrameLayout.LayoutParams(-1, portraitPlayerHeight());
        mediaParams.topMargin = shell.getPaddingTop();
        preview.addView(mediaPreview, mediaParams);

        Bitmap bitmap = previous.previewBitmap;
        if (bitmap != null && !bitmap.isRecycled()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageBitmap(bitmap);
            mediaPreview.addView(image, new FrameLayout.LayoutParams(-1, -1));
            relatedBackPreviewImage = image;
        }

        LinearLayout destination = new LinearLayout(this);
        destination.setOrientation(LinearLayout.VERTICAL);
        destination.setPadding(dp(16), dp(15), dp(16), dp(30));
        FrameLayout.LayoutParams destinationParams = new FrameLayout.LayoutParams(-1, -1);
        destinationParams.topMargin = shell.getPaddingTop() + portraitPlayerHeight();
        preview.addView(destination, destinationParams);

        TextView destinationTitle = new TextView(this);
        destinationTitle.setText(previous.title == null || previous.title.isEmpty() ? "Previous video" : previous.title);
        destinationTitle.setTextColor(Color.WHITE);
        destinationTitle.setTextSize(21);
        destinationTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        destination.addView(destinationTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView destinationMeta = new TextView(this);
        StringBuilder previewMeta = new StringBuilder();
        if (previous.uploader != null && !previous.uploader.isEmpty()) previewMeta.append(previous.uploader);
        if (previous.views != null && !previous.views.isEmpty()) {
            if (previewMeta.length() > 0) previewMeta.append("  •  ");
            previewMeta.append(previous.views).append(" views");
        }
        destinationMeta.setText(previewMeta);
        destinationMeta.setTextColor(Color.rgb(165, 165, 174));
        destinationMeta.setTextSize(12);
        destinationMeta.setPadding(0, dp(6), 0, dp(18));
        destination.addView(destinationMeta, new LinearLayout.LayoutParams(-1, -2));

        TextView destinationRelated = new TextView(this);
        destinationRelated.setText("Related videos");
        destinationRelated.setTextColor(Color.WHITE);
        destinationRelated.setTextSize(18);
        destinationRelated.setTypeface(null, android.graphics.Typeface.BOLD);
        destination.addView(destinationRelated, new LinearLayout.LayoutParams(-1, -2));

        preview.setAlpha(0.45f);
        preview.setScaleX(0.985f);
        preview.setScaleY(0.985f);
        preview.setTranslationX(-relatedBackDirection * dp(18));
        root.addView(preview, 0, new FrameLayout.LayoutParams(-1, -1));
        relatedBackPreviewLayer = preview;
        return true;
    }

    void updateRelatedBackPreview(float progress, boolean fromRight) {
        if (relatedBackPreviewLayer == null || relatedBackPreviewEntry == null) return;
        float p = Math.max(0f, Math.min(1f, progress));
        float eased = 1f - (1f - p) * (1f - p);
        relatedBackDirection = fromRight ? -1f : 1f;

        shell.animate().cancel();
        shell.setPivotX(fromRight ? shell.getWidth() : 0f);
        shell.setPivotY(shell.getHeight() * 0.5f);
        float travel = Math.max(dp(48), root.getWidth() * 0.24f);
        shell.setTranslationX(relatedBackDirection * travel * eased);
        float foregroundScale = 1f - (0.04f * eased);
        shell.setScaleX(foregroundScale);
        shell.setScaleY(foregroundScale);
        shell.setAlpha(1f - (0.05f * eased));

        relatedBackPreviewLayer.animate().cancel();
        relatedBackPreviewLayer.setAlpha(0.45f + (0.55f * eased));
        float previewScale = 0.985f + (0.015f * eased);
        relatedBackPreviewLayer.setScaleX(previewScale);
        relatedBackPreviewLayer.setScaleY(previewScale);
        relatedBackPreviewLayer.setTranslationX(-relatedBackDirection * dp(18) * (1f - eased));
    }

    void cancelRelatedBackPreview() {
        if (shell != null) {
            shell.animate().cancel();
            shell.animate()
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(150L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        FrameLayout preview = relatedBackPreviewLayer;
        relatedBackPreviewEntry = null;
        if (preview == null) return;
        preview.animate().cancel();
        preview.animate()
                .alpha(0f)
                .translationX(-relatedBackDirection * dp(18))
                .setDuration(130L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (relatedBackPreviewLayer == preview) clearRelatedBackPreviewLayer();
                })
                .start();
    }

    void commitRelatedBackPreview() {
        VideoHistoryEntry expected = relatedBackPreviewEntry;
        if (expected == null || relatedHistory.peekLast() != expected || shell == null || root == null) {
            abortRelatedBackPreview();
            handleBack();
            return;
        }

        relatedBackPreviewEntry = null;
        shell.animate().cancel();
        shell.animate()
                .translationX(relatedBackDirection * Math.max(dp(64), root.getWidth() * 0.34f))
                .scaleX(0.955f)
                .scaleY(0.955f)
                .alpha(0.9f)
                .setDuration(90L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    clearRelatedBackPreviewLayer();
                    boolean restored = restorePreviousRelatedVideo();
                    resetRelatedBackForeground();
                    if (!restored) handleBack();
                })
                .start();
    }

    void abortRelatedBackPreview() {
        relatedBackPreviewEntry = null;
        clearRelatedBackPreviewLayer();
        resetRelatedBackForeground();
    }

    private void clearRelatedBackPreviewLayer() {
        FrameLayout preview = relatedBackPreviewLayer;
        if (preview != null) {
            preview.animate().cancel();
            if (relatedBackPreviewImage != null) relatedBackPreviewImage.setImageDrawable(null);
            if (root != null) root.removeView(preview);
        }
        relatedBackPreviewLayer = null;
        relatedBackPreviewImage = null;
    }

    private void resetRelatedBackForeground() {
        if (shell == null) return;
        shell.animate().cancel();
        shell.setTranslationX(0f);
        shell.setScaleX(1f);
        shell.setScaleY(1f);
        shell.setAlpha(1f);
    }

    private void openComments() {
        if (pageUrl.isEmpty() || !supportsComments()) return;
        new InlineCommentsDialog(
                this,
                pageUrl,
                title,
                comments,
                null
        ).show();
    }

    private void toggleWatchLater() {
        if (pageUrl.isEmpty()) return;
        if (FavoriteStore.contains(this, pageUrl)) {
            FavoriteStore.remove(this, pageUrl);
            Toast.makeText(this, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
        } else {
            FavoriteStore.add(this, title, pageUrl);
            Toast.makeText(this, "Saved to Watch Later.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePage() {
        String shareUrl = pageUrl.isEmpty() ? mediaUrl : pageUrl;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareUrl);
        share.putExtra(Intent.EXTRA_SUBJECT, title);
        startActivity(Intent.createChooser(share, "Share video"));
    }

    private void showPlayerMenu() {
        String saveTitle = FavoriteStore.contains(this, pageUrl)
                ? "Remove from Watch Later"
                : "Save to Watch Later";
        ArrayList<VideoActionSheet.Action> actions = new ArrayList<>();
        if (supportsComments()) {
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_comments,
                    "Comments",
                    "Read and reply without leaving the video",
                    this::openComments
            ));
        }
        actions.add(VideoActionSheet.action(
                R.drawable.ic_action_share,
                "Share",
                "Send the CrazyShit page",
                this::sharePage
        ));
        actions.add(VideoActionSheet.action(
                R.drawable.ic_more_website,
                "Open webpage",
                "View this video on the site",
                () -> openWebsite(pageUrl)
        ));
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            if (portraitVideo) {
                actions.add(VideoActionSheet.action(
                        portraitFullscreen
                                ? R.drawable.ic_action_fullscreen_exit
                                : R.drawable.ic_action_fullscreen,
                        portraitFullscreen ? "Exit portrait fullscreen" : "Portrait fullscreen",
                        portraitFullscreen
                                ? "Return to the video details"
                                : "Fill the screen without rotating",
                        () -> setPortraitFullscreen(!portraitFullscreen)
                ));
            }
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_minimize,
                    "Minimize",
                    "Keep playing while you browse",
                    this::minimizeFromMenu
            ));
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_fullscreen,
                    "Rotate fullscreen",
                    "Turn the player sideways",
                    () -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            ));
        }

        VideoActionSheet.show(
                this,
                title,
                VideoActionSheet.section(
                        "PLAYBACK",
                        VideoActionSheet.action(
                                R.drawable.ic_action_speed,
                                "Playback speed",
                                "Choose from 0.5× to 2×",
                                this::showSpeedMenu
                        ),
                        VideoActionSheet.action(
                                R.drawable.ic_more_view_style,
                                "Fit / Fill / Zoom",
                                "Choose how the video fills the player",
                                this::showResizeMenu
                        )
                ),
                VideoActionSheet.section(
                        "SAVE",
                        VideoActionSheet.action(
                                R.drawable.ic_action_download,
                                "Download",
                                "Save this video for offline playback",
                                this::downloadCurrentVideo
                        ),
                        VideoActionSheet.action(
                                R.drawable.ic_more_library,
                                saveTitle,
                                "Keep this video in your library",
                                this::toggleWatchLater
                        )
                ),
                VideoActionSheet.section(
                        "ACTIONS",
                        actions.toArray(new VideoActionSheet.Action[0])
                )
        );
    }

    private void downloadCurrentVideo() {
        VideoDownloadStore.downloadKnown(
                this,
                title,
                pageUrl,
                "",
                mediaUrl,
                userAgent,
                cookies,
                mediaReferer
        );
    }

    private boolean isEfukt() {
        return NativeFeedBrowserActivity.SOURCE_EFUKT.equals(source) || EfuktRepository.isEfuktUrl(pageUrl);
    }

    private boolean isBunkr() {
        return NativeFeedBrowserActivity.SOURCE_BUNKR.equals(source) || BunkrRepository.isBunkrUrl(pageUrl);
    }

    private boolean supportsComments() {
        return !isEfukt() && !isBunkr();
    }

    private void minimizeFromMenu() {
        minimizing = true;
        minimizeToFeed();
    }

    private void showSpeedMenu() {
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        new AlertDialog.Builder(this)
                .setTitle("Playback speed")
                .setItems(labels, (dialog, which) -> {
                    if (player != null) player.setPlaybackParameters(new PlaybackParameters(speeds[which]));
                })
                .show();
    }

    private void showResizeMenu() {
        String[] labels = {"Fit", "Fill", "Zoom"};
        int[] modes = {
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                AspectRatioFrameLayout.RESIZE_MODE_FILL,
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        };
        new AlertDialog.Builder(this)
                .setTitle("Video size")
                .setItems(labels, (dialog, which) -> {
                    resizeMode = modes[which];
                    playerView.setResizeMode(resizeMode);
                })
                .show();
    }

    private void showPlaybackFailure() {
        if (failureShown || isFinishing()) return;
        failureShown = true;
        new AlertDialog.Builder(this)
                .setTitle("Couldn't play this stream")
                .setMessage("The native player couldn't continue this video. You can open the normal webpage instead.")
                .setNegativeButton("Close", null)
                .setNeutralButton("Retry", (dialog, which) -> recoverPlayback(null))
                .setPositiveButton("Open page", (dialog, which) -> openWebsite(pageUrl))
                .show();
    }

    private void openWebsite(String url) {
        savePlaybackState(false);
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL,
                url == null || url.isEmpty() ? CrazyShitRepository.HOME : url);
        startActivity(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        abortRelatedBackPreview();
        applyOrientation(newConfig.orientation);
    }

    private void applyOrientation(int orientation) {
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) portraitFullscreen = false;
        boolean fullscreen = landscape || portraitFullscreen;
        if (detailsScroll != null) {
            detailsScroll.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
            detailsScroll.setAlpha(1f);
            detailsScroll.setTranslationY(0f);
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) playerContainer.getLayoutParams();
        if (fullscreen) {
            params.height = 0;
            params.weight = 1f;
            setFullscreenUi(true);
        } else {
            params.height = portraitPlayerHeight();
            params.weight = 0f;
            setFullscreenUi(false);
        }
        playerContainer.setLayoutParams(params);
        playerContainer.setScaleX(1f);
        playerContainer.setScaleY(1f);
        playerContainer.setTranslationY(0f);
        playerContainer.setAlpha(1f);
        updatePortraitFullscreenButton();
        updateSwipeEnabled();
        shell.requestApplyInsets();
    }

    private void setPortraitFullscreen(boolean enabled) {
        boolean portraitOrientation = getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
        portraitFullscreen = enabled && portraitVideo && portraitOrientation;
        applyOrientation(getResources().getConfiguration().orientation);
        if (playerView != null) playerView.showController();
    }

    private void updatePortraitFullscreenButton() {
        if (portraitFullscreenButton == null) return;
        boolean portraitOrientation = getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
        portraitFullscreenButton.setVisibility(
                portraitVideo && portraitOrientation ? View.VISIBLE : View.GONE
        );
        portraitFullscreenButton.setImageResource(
                portraitFullscreen
                        ? R.drawable.ic_action_fullscreen_exit
                        : R.drawable.ic_action_fullscreen
        );
        portraitFullscreenButton.setContentDescription(
                portraitFullscreen ? "Exit portrait fullscreen" : "Portrait fullscreen"
        );
    }

    private void updateSwipeEnabled() {
        if (playerContainer == null) return;
        boolean portrait = getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        boolean enabled = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("swipe_down_minimize", true);
        playerContainer.setSwipeEnabled(portrait && enabled && !minimizing && !portraitFullscreen);
    }

    private void setFullscreenUi(boolean enabled) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (enabled) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(enabled
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void configureBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backCallback
            );
        }
    }

    private void handleBack() {
        if (portraitFullscreen) {
            setPortraitFullscreen(false);
            return;
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        if (restorePreviousRelatedVideo()) return;
        if (getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("minimize_on_back", true)) {
            minimizing = true;
            minimizeToFeed();
        } else {
            savePlaybackState(false);
            finish();
            suppressCloseTransition();
        }
    }

    private void minimizeToFeed() {
        if (isFinishing()) return;
        savePlaybackState(false);
        Intent result = new Intent();
        result.putExtra(PlayerActivity.EXTRA_MINIMIZED, true);
        result.putExtra(PlayerActivity.EXTRA_MEDIA_URL, mediaUrl);
        result.putExtra(PlayerActivity.EXTRA_PAGE_URL, pageUrl);
        result.putExtra(PlayerActivity.EXTRA_TITLE, title);
        result.putExtra(PlayerActivity.EXTRA_USER_AGENT, userAgent);
        result.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
        result.putExtra(EXTRA_MEDIA_REFERER, mediaReferer);
        result.putExtra(EXTRA_REOPEN_DETAIL, true);
        result.putExtra(EXTRA_VIEWS, views);
        result.putExtra(EXTRA_UPLOADER, uploader);
        result.putExtra(EXTRA_COMMENTS, comments);
        result.putExtra(EXTRA_RELATED_FEED_URL, relatedFeedUrl);
        result.putExtra(EXTRA_SOURCE, source);
        if (player != null) result.putExtra(PlayerActivity.EXTRA_START_POSITION, player.getCurrentPosition());
        setResult(RESULT_OK, result);
        finish();
        suppressCloseTransition();
    }

    @SuppressWarnings("deprecation")
    private void suppressCloseTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private boolean rememberPositionEnabled() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("remember_video_position", true);
    }

    private String positionKey() {
        String key = pageUrl == null || pageUrl.isEmpty() ? mediaUrl : pageUrl;
        return "position_" + Integer.toHexString((key == null ? "" : key).hashCode());
    }

    private void savePlaybackState(boolean ended) {
        if (player == null) return;
        long position = Math.max(0L, player.getCurrentPosition());
        long duration = player.getDuration();
        if (duration < 0L) duration = 0L;

        PlaybackHistoryStore.record(this, title, pageUrl, position, duration, ended);

        if (!rememberPositionEnabled()) return;
        SharedPreferences prefs = getSharedPreferences("player_positions", MODE_PRIVATE);
        boolean nearlyFinished = duration > 0L && position >= Math.max(0L, duration - 5000L);
        if (ended || nearlyFinished) {
            prefs.edit().remove(positionKey()).apply();
        } else if (position > 3000L) {
            prefs.edit().putLong(positionKey(), position).apply();
        }
    }

    private boolean recoverPlayback(PlaybackException error) {
        if (error == null) failureShown = false;
        return playbackRecovery.recover(this, error, recovered -> {
            mediaUrl = recovered.stream.mediaUrl;
            mediaReferer = recovered.stream.requestReferer;
            try { cookies = clean(CookieManager.getInstance().getCookie(mediaUrl)); } catch (Exception ignored) { }
            buildPlayer(recovered.position);
            player.setPlayWhenReady(recovered.playWhenReady && recoveryResumed);
        }, this::showPlaybackFailure);
    }

    private void releasePlayer() {
        playbackRecovery.cancel();
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private String defaultUserAgent() {
        try {
            return WebSettings.getDefaultUserAgent(this);
        } catch (Exception e) {
            return THUMB_UA;
        }
    }

    private String cookiesFor(String media, String page) {
        try {
            String value = CookieManager.getInstance().getCookie(media);
            if ((value == null || value.isEmpty()) && page != null) {
                value = CookieManager.getInstance().getCookie(page);
            }
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    private LinearLayout.LayoutParams marginParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, top, 0, bottom);
        return params;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int portraitPlayerHeight() {
        int width = getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(190), Math.round(width * 9f / 16f));
    }

    private void haptic(View view) {
        if (view == null) return;
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        recoveryResumed = true;
        updateSwipeEnabled();
        if (detailsScroll != null) applyDetailsBackground();
    }

    @Override
    protected void onStop() {
        savePlaybackState(false);
        if (player != null && !isChangingConfigurations()) player.pause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        abortRelatedBackPreview();
        if (portraitFullscreen ||
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setFullscreenUi(false);
        }
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            } catch (Exception ignored) {
            }
            backCallback = null;
        }
        relatedPlayGeneration++;
        relatedLoadGeneration++;
        savePlaybackState(false);
        releasePlayer();
        clearRelatedImageTargets();
        if (thumbnailResolvers != null) {
            for (RenderedThumbnailResolver resolver : thumbnailResolvers) {
                if (resolver != null) resolver.close();
            }
        }
        for (VideoHistoryEntry entry : relatedHistory) recycleHistoryPreview(entry);
        relatedHistory.clear();
        requestedRelatedThumbnails.clear();
        io.shutdownNow();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
