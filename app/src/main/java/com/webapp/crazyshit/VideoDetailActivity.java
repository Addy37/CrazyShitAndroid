package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class VideoDetailActivity extends Activity {
    public static final String EXTRA_VIEWS = "views";
    public static final String EXTRA_UPLOADER = "uploader";
    public static final String EXTRA_COMMENTS = "comments";

    private static final String SITE = "https://crazyshit.com/";
    private static final String THUMB_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final Map<String, ImageView> relatedImages = new LinkedHashMap<>();

    private FrameLayout root;
    private LinearLayout shell;
    private FrameLayout playerContainer;
    private PlayerView playerView;
    private ScrollView detailsScroll;
    private LinearLayout detailsColumn;
    private LinearLayout relatedContainer;
    private TextView titleView;
    private TextView metaView;
    private TextView commentsTitle;
    private TextView commentsSubtitle;
    private ProgressBar loading;
    private ExoPlayer player;
    private RenderedThumbnailResolver thumbnailResolver;
    private OnBackInvokedCallback backCallback;

    private String mediaUrl;
    private String pageUrl;
    private String title;
    private String views;
    private String uploader;
    private String comments;
    private String userAgent;
    private String cookies;
    private long requestedStartPosition;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private boolean failureShown;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        mediaUrl = getIntent().getStringExtra(PlayerActivity.EXTRA_MEDIA_URL);
        pageUrl = getIntent().getStringExtra(PlayerActivity.EXTRA_PAGE_URL);
        title = clean(getIntent().getStringExtra(PlayerActivity.EXTRA_TITLE));
        views = clean(getIntent().getStringExtra(EXTRA_VIEWS));
        uploader = clean(getIntent().getStringExtra(EXTRA_UPLOADER));
        comments = clean(getIntent().getStringExtra(EXTRA_COMMENTS));
        userAgent = clean(getIntent().getStringExtra(PlayerActivity.EXTRA_USER_AGENT));
        cookies = clean(getIntent().getStringExtra(PlayerActivity.EXTRA_COOKIES));
        requestedStartPosition = getIntent().getLongExtra(PlayerActivity.EXTRA_START_POSITION, -1L);

        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            finish();
            return;
        }
        if (title.isEmpty()) title = "Video";
        if (pageUrl == null) pageUrl = "";

        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        buildUi();
        buildPlayer(requestedStartPosition);
        thumbnailResolver = new RenderedThumbnailResolver(this, this::onThumbnailResolved);
        configureBackHandling();
        applyOrientation(getResources().getConfiguration().orientation);
        loadRelated();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(13, 13, 15));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
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

        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        shell.addView(playerContainer, new LinearLayout.LayoutParams(-1, portraitPlayerHeight()));

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(resizeMode);
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        TextView back = overlayButton("‹", 34);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> {
            haptic(v);
            handleBack();
        });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(46), dp(46));
        bp.gravity = Gravity.TOP | Gravity.START;
        bp.setMargins(dp(8), dp(8), 0, 0);
        playerContainer.addView(back, bp);

        TextView menu = overlayButton("⋮", 26);
        menu.setContentDescription("Video menu");
        menu.setOnClickListener(v -> {
            haptic(v);
            showPlayerMenu(menu);
        });
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(46), dp(46));
        mp.gravity = Gravity.TOP | Gravity.END;
        mp.setMargins(0, dp(8), dp(8), 0);
        playerContainer.addView(menu, mp);

        detailsScroll = new ScrollView(this);
        detailsScroll.setFillViewport(true);
        detailsScroll.setBackgroundColor(Color.rgb(13, 13, 15));
        shell.addView(detailsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        detailsColumn = new LinearLayout(this);
        detailsColumn.setOrientation(LinearLayout.VERTICAL);
        detailsColumn.setPadding(dp(14), dp(14), dp(14), dp(26));
        detailsScroll.addView(detailsColumn, new ScrollView.LayoutParams(-1, -2));

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(20);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setLineSpacing(0f, 1.05f);
        detailsColumn.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        metaView = new TextView(this);
        metaView.setTextColor(Color.rgb(165, 165, 174));
        metaView.setTextSize(12);
        metaView.setPadding(0, dp(7), 0, dp(12));
        detailsColumn.addView(metaView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        detailsColumn.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        actions.addView(actionButton("Comments", this::openComments), new LinearLayout.LayoutParams(0, dp(46), 1f));
        actions.addView(actionButton("Watch later", this::toggleWatchLater), new LinearLayout.LayoutParams(0, dp(46), 1f));
        actions.addView(actionButton("Share", this::sharePage), new LinearLayout.LayoutParams(0, dp(46), 1f));

        detailsColumn.addView(buildCommentsCard(), marginParams(dp(14), dp(12)));

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

    private View buildCommentsCard() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(25, 25, 28));
        card.setStrokeColor(Color.rgb(49, 49, 55));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openComments());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.addView(body, new MaterialCardView.LayoutParams(-1, -2));

        commentsTitle = new TextView(this);
        commentsTitle.setTextColor(Color.WHITE);
        commentsTitle.setTextSize(16);
        commentsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(commentsTitle);

        commentsSubtitle = new TextView(this);
        commentsSubtitle.setTextColor(Color.rgb(170, 170, 180));
        commentsSubtitle.setTextSize(12);
        commentsSubtitle.setPadding(0, dp(5), 0, 0);
        body.addView(commentsSubtitle);
        return card;
    }

    private TextView actionButton(String text, Runnable action) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.rgb(31, 31, 35), dp(14)));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });
        LinearLayout.LayoutParams own = new LinearLayout.LayoutParams(0, dp(46), 1f);
        own.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(own);
        return button;
    }

    private TextView overlayButton(String label, int size) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(Color.argb(165, 10, 10, 12), dp(18)));
        view.setClickable(true);
        view.setFocusable(true);
        view.setElevation(dp(8));
        return view;
    }

    private void buildPlayer(long startPosition) {
        releasePlayer();
        failureShown = false;

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
        if (!userAgent.isEmpty()) httpFactory.setUserAgent(userAgent);

        Map<String, String> headers = new LinkedHashMap<>();
        if (!pageUrl.isEmpty()) {
            headers.put("Referer", pageUrl);
            try {
                Uri page = Uri.parse(pageUrl);
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

        long position = startPosition;
        if (position < 0L && rememberPositionEnabled()) {
            position = getSharedPreferences("player_positions", MODE_PRIVATE)
                    .getLong(positionKey(), 0L);
        }
        if (position > 0L) player.seekTo(position);
        player.setPlayWhenReady(true);
        player.prepare();
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                showPlaybackFailure();
            }
        });
    }

    private void updateMetadataUi() {
        if (titleView != null) titleView.setText(title);
        if (metaView != null) {
            ArrayList<String> parts = new ArrayList<>();
            if (!views.isEmpty()) parts.add(views + " views");
            if (!uploader.isEmpty()) parts.add(uploader);
            metaView.setText(TextUtils.join("  •  ", parts));
        }
        if (commentsTitle != null) {
            commentsTitle.setText(comments.isEmpty() ? "Comments" : "Comments  " + comments);
        }
        if (commentsSubtitle != null) {
            commentsSubtitle.setText("Open the native comment section without leaving the video page");
        }
    }

    private void loadRelated() {
        if (relatedContainer == null) return;
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
            ArrayList<NativeContentItem> result = new ArrayList<>();
            for (NativeContentItem item : merged.values()) {
                result.add(item);
                if (result.size() >= 12) break;
            }
            runOnUiThread(() -> renderRelated(result));
        });
    }

    private void renderRelated(List<NativeContentItem> items) {
        if (relatedContainer == null) return;
        relatedContainer.removeAllViews();
        relatedImages.clear();
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
        card.setCardBackgroundColor(Color.rgb(25, 25, 28));
        card.setStrokeColor(Color.rgb(49, 49, 55));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(14));
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
            commentCount.setTextColor(Color.rgb(255, 112, 60));
            commentCount.setTextSize(11);
            commentCount.setPadding(0, dp(5), 0, 0);
            copy.addView(commentCount);
        }

        relatedImages.put(item.url, image);
        if (!clean(item.imageUrl).isEmpty()) loadImage(image, item.imageUrl, item.url);
        if (thumbnailResolver != null) thumbnailResolver.request(item.url);
        return card;
    }

    private void onThumbnailResolved(String page, String imageUrl) {
        ImageView target = relatedImages.get(page);
        if (target == null || imageUrl == null || imageUrl.isEmpty()) return;
        loadImage(target, imageUrl, page);
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
        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = repository.resolvePlayable(this, item.url);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    openWebsite(item.url);
                    return;
                }
                savePosition();
                mediaUrl = resolved.mediaUrl;
                pageUrl = item.url;
                title = clean(item.title).isEmpty() ? resolved.title : item.title;
                views = clean(item.views);
                uploader = clean(item.uploader);
                comments = clean(item.comments);
                userAgent = defaultUserAgent();
                cookies = cookiesFor(mediaUrl, pageUrl);
                requestedStartPosition = 0L;
                updateMetadataUi();
                buildPlayer(0L);
                if (detailsScroll != null) detailsScroll.smoothScrollTo(0, 0);
                loadRelated();
            });
        });
    }

    private void openComments() {
        if (pageUrl.isEmpty()) return;
        Intent intent = new Intent(this, CommentsActivity.class);
        intent.putExtra(CommentsActivity.EXTRA_PAGE_URL, pageUrl);
        intent.putExtra(CommentsActivity.EXTRA_TITLE, title);
        intent.putExtra(CommentsActivity.EXTRA_COUNT, comments);
        startActivity(intent);
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

    private void showPlayerMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Playback speed");
        menu.getMenu().add(0, 2, 1, "Fit / Fill / Zoom");
        menu.getMenu().add(0, 3, 2, "Comments");
        menu.getMenu().add(0, 4, 3, "Watch Later");
        menu.getMenu().add(0, 5, 4, "Share");
        menu.getMenu().add(0, 6, 5, "Open webpage");
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            menu.getMenu().add(0, 7, 6, "Fullscreen");
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) showSpeedMenu();
            else if (item.getItemId() == 2) showResizeMenu();
            else if (item.getItemId() == 3) openComments();
            else if (item.getItemId() == 4) toggleWatchLater();
            else if (item.getItemId() == 5) sharePage();
            else if (item.getItemId() == 6) openWebsite(pageUrl);
            else if (item.getItemId() == 7) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            return true;
        });
        menu.show();
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
                .setPositiveButton("Open page", (dialog, which) -> openWebsite(pageUrl))
                .show();
    }

    private void openWebsite(String url) {
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL,
                url == null || url.isEmpty() ? CrazyShitRepository.HOME : url);
        startActivity(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientation(newConfig.orientation);
    }

    private void applyOrientation(int orientation) {
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (detailsScroll != null) detailsScroll.setVisibility(landscape ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) playerContainer.getLayoutParams();
        if (landscape) {
            params.height = 0;
            params.weight = 1f;
            setFullscreenUi(true);
        } else {
            params.height = portraitPlayerHeight();
            params.weight = 0f;
            setFullscreenUi(false);
        }
        playerContainer.setLayoutParams(params);
        shell.requestApplyInsets();
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
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        if (getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("minimize_on_back", true)) {
            minimizeToFeed();
        } else {
            finish();
        }
    }

    private void minimizeToFeed() {
        Intent result = new Intent();
        result.putExtra(PlayerActivity.EXTRA_MINIMIZED, true);
        result.putExtra(PlayerActivity.EXTRA_MEDIA_URL, mediaUrl);
        result.putExtra(PlayerActivity.EXTRA_PAGE_URL, pageUrl);
        result.putExtra(PlayerActivity.EXTRA_TITLE, title);
        result.putExtra(PlayerActivity.EXTRA_USER_AGENT, userAgent);
        result.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
        if (player != null) result.putExtra(PlayerActivity.EXTRA_START_POSITION, player.getCurrentPosition());
        setResult(RESULT_OK, result);
        finish();
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
        return "position_" + Integer.toHexString(mediaUrl.hashCode());
    }

    private void savePosition() {
        if (player == null || !rememberPositionEnabled()) return;
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        SharedPreferences prefs = getSharedPreferences("player_positions", MODE_PRIVATE);
        if (position > 3000L && (duration <= 0L || position < duration - 5000L)) {
            prefs.edit().putLong(positionKey(), position).apply();
        }
    }

    private void releasePlayer() {
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
    protected void onStop() {
        savePosition();
        if (player != null && !isChangingConfigurations()) player.pause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            } catch (Exception ignored) {
            }
            backCallback = null;
        }
        savePosition();
        releasePlayer();
        io.shutdownNow();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
