package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
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

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * v2.3 unified video session.
 *
 * Normal video detail, swipe-down minimization and mini-player expansion all live in
 * NativeMainActivity and share one PlayerView/ExoPlayer. The player is never recreated during
 * minimize/expand and never crosses an Activity boundary after the initial legacy launch is
 * intercepted before it becomes visible.
 */
@UnstableApi
final class UnifiedVideoController {
    private enum State { HIDDEN, FULL, MINI, TRANSITION }

    private static final WeakHashMap<NativeMainActivity, UnifiedVideoController> INSTANCES =
            new WeakHashMap<>();

    private static final String SITE = "https://crazyshit.com/";
    private static final String THUMB_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private static final int MINI_HEIGHT_DP = 82;
    private static final int MINI_SIDE_DP = 12;
    private static final int MINI_BOTTOM_DP = 82;
    private static final int MINI_VIDEO_W_DP = 132;
    private static final int MINI_VIDEO_H_DP = 74;
    private static final int MINI_PAD_X_DP = 4;
    private static final int MINI_PAD_TOP_DP = 4;
    private static final int CONTROL_TIMEOUT_MS = 2600;

    static boolean routeLegacyDetail(NativeMainActivity host, VideoDetailActivity launched) {
        if (host == null || launched == null || launched.isFinishing()) return false;
        Intent intent = launched.getIntent();
        if (intent == null || clean(intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_URL)).isEmpty()) {
            return false;
        }
        UnifiedVideoController controller = get(host);
        if (controller == null) return false;
        controller.openIntent(intent);
        launched.finish();
        suppressCloseTransition(launched);
        return true;
    }

    static void onHostResumed(NativeMainActivity host) {
        UnifiedVideoController c = peek(host);
        if (c != null) c.onResume();
    }

    static void onHostPaused(NativeMainActivity host) {
        UnifiedVideoController c = peek(host);
        if (c != null) c.onPause();
    }

    static void onHostDestroyed(NativeMainActivity host) {
        UnifiedVideoController c;
        synchronized (INSTANCES) {
            c = INSTANCES.remove(host);
        }
        if (c != null) c.destroy();
    }

    static void onHostConfigurationChanged(NativeMainActivity host) {
        UnifiedVideoController c = peek(host);
        if (c != null) c.onConfigurationChanged();
    }

    private static UnifiedVideoController peek(NativeMainActivity host) {
        if (host == null) return null;
        synchronized (INSTANCES) {
            return INSTANCES.get(host);
        }
    }

    private static UnifiedVideoController get(NativeMainActivity host) {
        synchronized (INSTANCES) {
            UnifiedVideoController existing = INSTANCES.get(host);
            if (existing != null) return existing;
            FrameLayout root = reflectRoot(host);
            if (root == null) return null;
            UnifiedVideoController created = new UnifiedVideoController(host, root);
            INSTANCES.put(host, created);
            return created;
        }
    }

    private static FrameLayout reflectRoot(NativeMainActivity activity) {
        Class<?> type = activity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("overlayRoot");
                field.setAccessible(true);
                Object value = field.get(activity);
                return value instanceof FrameLayout ? (FrameLayout) value : null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private final NativeMainActivity activity;
    private final FrameLayout root;
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<String, ImageView> relatedImages = new LinkedHashMap<>();

    private FrameLayout detailLayer;
    private ScrollView detailsScroll;
    private LinearLayout detailsColumn;
    private LinearLayout relatedContainer;
    private SwipeMinimizeFrameLayout playerContainer;
    private PlayerView playerView;
    private TextView playerTitleView;
    private TextView titleView;
    private TextView metaView;
    private TextView commentsTitle;
    private TextView miniTitle;
    private MaterialCardView miniCard;
    private FrameLayout miniMediaSlot;
    private View miniProgressFill;
    private ProgressBar loading;

    private ExoPlayer player;
    private OnBackInvokedCallback backCallback;
    private boolean backRegistered;
    private boolean hostResumed;
    private boolean resumeAfterPause;
    private boolean failureShown;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private int sessionGeneration;

    private String mediaUrl = "";
    private String mediaReferer = "";
    private final PlaybackRecovery playbackRecovery = new PlaybackRecovery();
    private String pageUrl = "";
    private String title = "Video";
    private String views = "";
    private String uploader = "";
    private String comments = "";
    private String userAgent = "";
    private String cookies = "";

    private State state = State.HIDDEN;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (state != State.MINI || player == null || miniProgressFill == null) return;
            long duration = Math.max(0L, player.getDuration());
            long position = Math.max(0L, player.getCurrentPosition());
            float progress = duration > 0L ? Math.min(1f, position / (float) duration) : 0f;
            miniProgressFill.setPivotX(0f);
            miniProgressFill.setScaleX(progress);
            miniCard.postDelayed(this, 350L);
        }
    };

    private UnifiedVideoController(NativeMainActivity activity, FrameLayout root) {
        this.activity = activity;
        this.root = root;
        buildUi();
    }

    private void buildUi() {
        detailLayer = new FrameLayout(activity);
        detailLayer.setBackgroundColor(Color.rgb(13, 13, 15));
        detailLayer.setClickable(true);
        detailLayer.setFocusable(true);
        detailLayer.setVisibility(View.GONE);
        root.addView(detailLayer, new FrameLayout.LayoutParams(-1, -1));

        detailsScroll = new ScrollView(activity);
        detailsScroll.setFillViewport(true);
        detailsScroll.setBackgroundColor(Color.rgb(13, 13, 15));
        detailLayer.addView(detailsScroll, new FrameLayout.LayoutParams(-1, -1));

        detailsColumn = new LinearLayout(activity);
        detailsColumn.setOrientation(LinearLayout.VERTICAL);
        detailsColumn.setPadding(dp(14), dp(14), dp(14), dp(28));
        detailsScroll.addView(detailsColumn, new ScrollView.LayoutParams(-1, -2));

        titleView = new TextView(activity);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(20);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setLineSpacing(0f, 1.05f);
        detailsColumn.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        metaView = new TextView(activity);
        metaView.setTextColor(Color.rgb(165, 165, 174));
        metaView.setTextSize(12);
        metaView.setPadding(0, dp(7), 0, dp(12));
        detailsColumn.addView(metaView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        detailsColumn.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        actions.addView(actionButton("Comments", this::openComments), new LinearLayout.LayoutParams(0, dp(46), 1f));
        actions.addView(actionButton("Watch later", this::toggleWatchLater), new LinearLayout.LayoutParams(0, dp(46), 1f));
        actions.addView(actionButton("Share", this::sharePage), new LinearLayout.LayoutParams(0, dp(46), 1f));

        detailsColumn.addView(buildCommentsCard(), marginParams(dp(14), dp(12)));

        TextView relatedTitle = new TextView(activity);
        relatedTitle.setText("Related videos");
        relatedTitle.setTextColor(Color.WHITE);
        relatedTitle.setTextSize(18);
        relatedTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        relatedTitle.setPadding(dp(2), dp(18), dp(2), dp(8));
        detailsColumn.addView(relatedTitle, new LinearLayout.LayoutParams(-1, -2));

        relatedContainer = new LinearLayout(activity);
        relatedContainer.setOrientation(LinearLayout.VERTICAL);
        detailsColumn.addView(relatedContainer, new LinearLayout.LayoutParams(-1, -2));

        loading = new ProgressBar(activity);
        loading.setVisibility(View.GONE);
        FrameLayout.LayoutParams loadParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        loadParams.gravity = Gravity.CENTER;
        detailLayer.addView(loading, loadParams);

        buildMiniCard();
        buildPlayerContainer();
        root.bringChildToFront(playerContainer);
    }

    private void buildMiniCard() {
        miniCard = new MaterialCardView(activity);
        miniCard.setCardBackgroundColor(Color.rgb(24, 24, 28));
        miniCard.setRadius(dp(18));
        miniCard.setCardElevation(dp(15));
        miniCard.setStrokeColor(Color.rgb(68, 66, 19));
        miniCard.setStrokeWidth(dp(1));
        miniCard.setVisibility(View.INVISIBLE);

        FrameLayout content = new FrameLayout(activity);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(MINI_PAD_X_DP), dp(MINI_PAD_TOP_DP), dp(4), dp(5));
        content.addView(row, new FrameLayout.LayoutParams(-1, -1));

        miniMediaSlot = new FrameLayout(activity);
        miniMediaSlot.setBackgroundColor(Color.BLACK);
        row.addView(miniMediaSlot,
                new LinearLayout.LayoutParams(dp(MINI_VIDEO_W_DP), dp(MINI_VIDEO_H_DP)));

        miniTitle = new TextView(activity);
        miniTitle.setTextColor(Color.WHITE);
        miniTitle.setTextSize(13.5f);
        miniTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        miniTitle.setMaxLines(2);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniTitle.setPadding(dp(10), 0, dp(5), 0);
        miniTitle.setOnClickListener(v -> expandFromMini());
        row.addView(miniTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView expand = miniButton("↗", "Open video details");
        expand.setOnClickListener(v -> expandFromMini());
        row.addView(expand, new LinearLayout.LayoutParams(dp(38), dp(50)));

        TextView close = miniButton("×", "Close mini-player");
        close.setOnClickListener(v -> closeSession(true));
        row.addView(close, new LinearLayout.LayoutParams(dp(38), dp(50)));

        View track = new View(activity);
        track.setBackgroundColor(Color.rgb(50, 50, 56));
        FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams(-1, dp(3));
        trackParams.gravity = Gravity.BOTTOM;
        content.addView(track, trackParams);

        miniProgressFill = new View(activity);
        miniProgressFill.setBackgroundColor(UiPalette.PRIMARY);
        miniProgressFill.setScaleX(0f);
        miniProgressFill.setPivotX(0f);
        FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(-1, dp(3));
        fillParams.gravity = Gravity.BOTTOM;
        content.addView(miniProgressFill, fillParams);

        miniCard.addView(content);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(MINI_HEIGHT_DP));
        params.gravity = Gravity.BOTTOM;
        params.setMargins(dp(MINI_SIDE_DP), 0, dp(MINI_SIDE_DP), dp(MINI_BOTTOM_DP));
        root.addView(miniCard, params);
    }

    private void buildPlayerContainer() {
        playerContainer = new SwipeMinimizeFrameLayout(activity);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setPivotX(0f);
        playerContainer.setPivotY(0f);
        playerContainer.setVisibility(View.GONE);
        playerContainer.setSwipeEnabled(false);
        playerContainer.setListener(new SwipeMinimizeFrameLayout.Listener() {
            @Override
            public void onDrag(float distancePx, float progress) {
                if (state != State.FULL || isLandscape()) return;
                playerView.hideController();
                applyDrag(progress);
            }

            @Override
            public void onRelease(boolean minimize, float distancePx) {
                if (state != State.FULL || isLandscape()) return;
                if (minimize) minimizeToMini();
                else restoreFromDrag();
            }
        });

        playerView = (PlayerView) activity.getLayoutInflater().inflate(
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

        root.addView(playerContainer, new FrameLayout.LayoutParams(-1, portraitPlayerHeight()));
    }

    private void openIntent(Intent intent) {
        if (intent == null) return;
        String nextMedia = clean(intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_URL));
        if (nextMedia.isEmpty()) return;

        savePlaybackState(false);
        releasePlayer();
        sessionGeneration++;

        mediaUrl = nextMedia;
        pageUrl = clean(intent.getStringExtra(PlayerActivity.EXTRA_PAGE_URL));
        mediaReferer = clean(intent.getStringExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER));
        if (mediaReferer.isEmpty()) mediaReferer = pageUrl;
        title = clean(intent.getStringExtra(PlayerActivity.EXTRA_TITLE));
        views = clean(intent.getStringExtra(VideoDetailActivity.EXTRA_VIEWS));
        uploader = clean(intent.getStringExtra(VideoDetailActivity.EXTRA_UPLOADER));
        comments = clean(intent.getStringExtra(VideoDetailActivity.EXTRA_COMMENTS));
        userAgent = clean(intent.getStringExtra(PlayerActivity.EXTRA_USER_AGENT));
        cookies = clean(intent.getStringExtra(PlayerActivity.EXTRA_COOKIES));
        if (title.isEmpty()) title = "Video";
        if (userAgent.isEmpty()) userAgent = defaultUserAgent();
        if (cookies.isEmpty()) cookies = cookiesFor(mediaUrl, pageUrl);

        long start = intent.getLongExtra(PlayerActivity.EXTRA_START_POSITION, -1L);
        updateMetadataUi();
        buildPlayer(start);
        loadRelated();
        showFull(true);
    }

    private void showFull(boolean opening) {
        state = State.FULL;
        miniCard.removeCallbacks(progressTicker);
        miniProgressFill.setScaleX(0f);
        miniCard.animate().cancel();
        miniCard.setAlpha(0f);
        miniCard.setVisibility(View.INVISIBLE);

        detailLayer.animate().cancel();
        detailLayer.setVisibility(View.VISIBLE);
        detailLayer.setAlpha(opening ? 0f : 1f);
        detailsScroll.setAlpha(1f);
        detailsScroll.setTranslationY(0f);

        playerContainer.animate().cancel();
        playerContainer.setVisibility(View.VISIBLE);
        layoutFullBounds();
        setFullPlayerMode();
        playerContainer.setTranslationX(0f);
        playerContainer.setTranslationY(opening ? dp(12) : 0f);
        playerContainer.setScaleX(opening ? 0.97f : 1f);
        playerContainer.setScaleY(opening ? 0.97f : 1f);
        playerContainer.setAlpha(1f);

        if (opening) {
            detailLayer.animate().alpha(1f).setDuration(180L).start();
            playerContainer.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(210L)
                    .setInterpolator(new DecelerateInterpolator(1.35f))
                    .start();
            playerView.post(playerView::showController);
        }
        registerBack();
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void applyDrag(float progress) {
        Geometry g = miniGeometry();
        if (g == null) return;
        float p = clamp(progress);
        miniCard.setVisibility(View.VISIBLE);
        miniCard.setAlpha(clamp((p - 0.10f) / 0.90f) * 0.92f);
        detailLayer.setAlpha(1f - (0.93f * p));
        detailsScroll.setTranslationY(dp(16) * p);

        playerContainer.setPivotX(0f);
        playerContainer.setPivotY(0f);
        playerContainer.setTranslationX(lerp(0f, g.tx, p));
        playerContainer.setTranslationY(lerp(0f, g.ty, p));
        playerContainer.setScaleX(lerp(1f, g.sx, p));
        playerContainer.setScaleY(lerp(1f, g.sy, p));
        setChromeAlpha(1f - Math.min(1f, p * 1.25f));
    }

    private void restoreFromDrag() {
        state = State.TRANSITION;
        playerContainer.setSwipeEnabled(false);
        playerContainer.animate().cancel();
        detailLayer.animate().cancel();
        miniCard.animate().cancel();

        detailLayer.animate().alpha(1f).setDuration(190L).start();
        detailsScroll.animate().translationY(0f).setDuration(190L).start();
        miniCard.animate().alpha(0f).setDuration(150L)
                .withEndAction(() -> miniCard.setVisibility(View.INVISIBLE)).start();
        playerContainer.animate()
                .translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f)
                .setDuration(210L)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    state = State.FULL;
                    setChromeAlpha(1f);
                    setFullPlayerMode();
                })
                .start();
    }

    private void minimizeToMini() {
        if (state != State.FULL) return;
        Geometry g = miniGeometry();
        if (g == null) {
            playerContainer.postDelayed(this::minimizeToMini, 40L);
            return;
        }
        state = State.TRANSITION;
        playerContainer.setSwipeEnabled(false);
        playerView.hideController();
        setChromeAlpha(0f);
        miniCard.setVisibility(View.VISIBLE);
        miniCard.animate().cancel();
        detailLayer.animate().cancel();
        playerContainer.animate().cancel();

        miniCard.animate().alpha(1f).setDuration(190L).start();
        detailLayer.animate()
                .alpha(0f)
                .setDuration(215L)
                .withEndAction(() -> detailLayer.setVisibility(View.GONE))
                .start();
        detailsScroll.animate().translationY(dp(18)).setDuration(180L).start();

        playerContainer.setPivotX(0f);
        playerContainer.setPivotY(0f);
        playerContainer.animate()
                .translationX(g.tx)
                .translationY(g.ty)
                .scaleX(g.sx)
                .scaleY(g.sy)
                .setDuration(255L)
                .setInterpolator(new DecelerateInterpolator(1.7f))
                .withEndAction(() -> {
                    state = State.MINI;
                    setMiniPlayerMode();
                    unregisterBack();
                    miniCard.setAlpha(1f);
                    miniCard.removeCallbacks(progressTicker);
                    miniCard.post(progressTicker);
                })
                .start();
    }

    private void expandFromMini() {
        if (state != State.MINI) return;
        haptic(miniCard);
        state = State.TRANSITION;
        miniCard.removeCallbacks(progressTicker);
        detailLayer.setVisibility(View.VISIBLE);
        detailLayer.setAlpha(0f);
        detailsScroll.setAlpha(1f);
        detailsScroll.setTranslationY(dp(14));
        layoutFullBounds();
        playerContainer.setSwipeEnabled(false);
        setChromeAlpha(0f);

        detailLayer.animate().alpha(1f).setDuration(225L).start();
        detailsScroll.animate().translationY(0f).setDuration(225L).start();
        miniCard.animate().alpha(0f).setDuration(195L).start();
        playerContainer.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(285L)
                .setInterpolator(new DecelerateInterpolator(1.55f))
                .withEndAction(() -> {
                    miniCard.setVisibility(View.INVISIBLE);
                    miniCard.setAlpha(0f);
                    state = State.FULL;
                    setChromeAlpha(1f);
                    setFullPlayerMode();
                    playerView.showController();
                    registerBack();
                })
                .start();
    }

    private void setFullPlayerMode() {
        playerView.setUseController(true);
        playerView.setControllerAutoShow(false);
        playerView.setControllerHideOnTouch(true);
        playerView.setOnClickListener(null);
        playerContainer.setSwipeEnabled(!isLandscape() && swipeEnabled());
        playerContainer.setBackgroundColor(Color.BLACK);
        if (isLandscape()) setSystemBars(true); else setSystemBars(false);
    }

    private void setMiniPlayerMode() {
        setSystemBars(false);
        playerView.hideController();
        playerView.setUseController(false);
        playerView.setOnClickListener(v -> expandFromMini());
        playerContainer.setSwipeEnabled(false);
        playerContainer.setBackground(rounded(Color.BLACK, dp(10)));
    }

    private void layoutFullBounds() {
        if (root.getWidth() <= 0 || root.getHeight() <= 0) {
            root.post(this::layoutFullBounds);
            return;
        }
        boolean landscape = isLandscape();
        int top = landscape ? 0 : topInset();
        int height = landscape ? root.getHeight() : portraitPlayerHeight();

        FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) playerContainer.getLayoutParams();
        pp.width = -1;
        pp.height = height;
        pp.gravity = Gravity.TOP | Gravity.START;
        pp.leftMargin = 0;
        pp.topMargin = top;
        playerContainer.setLayoutParams(pp);

        FrameLayout.LayoutParams dp = (FrameLayout.LayoutParams) detailsScroll.getLayoutParams();
        dp.leftMargin = 0;
        dp.rightMargin = 0;
        dp.topMargin = landscape ? 0 : top + height;
        dp.bottomMargin = 0;
        detailsScroll.setLayoutParams(dp);
        detailsScroll.setVisibility(landscape ? View.GONE : View.VISIBLE);
        if (landscape) detailLayer.setBackgroundColor(Color.BLACK);
        else detailLayer.setBackgroundColor(Color.rgb(13, 13, 15));
    }

    private Geometry miniGeometry() {
        if (root.getWidth() <= 0 || playerContainer.getWidth() <= 0 || miniMediaSlot.getWidth() <= 0) {
            return null;
        }
        int[] rootLoc = new int[2];
        int[] slotLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        miniMediaSlot.getLocationOnScreen(slotLoc);

        FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) playerContainer.getLayoutParams();
        float baseX = rootLoc[0] + pp.leftMargin;
        float baseY = rootLoc[1] + pp.topMargin;
        float tx = slotLoc[0] - baseX;
        float ty = slotLoc[1] - baseY;
        float sx = miniMediaSlot.getWidth() / (float) Math.max(1, playerContainer.getWidth());
        float sy = miniMediaSlot.getHeight() / (float) Math.max(1, playerContainer.getHeight());
        return new Geometry(sx, sy, tx, ty);
    }

    private void updateMetadataUi() {
        titleView.setText(title);
        playerTitleView.setText(title);
        miniTitle.setText(title);
        ArrayList<String> parts = new ArrayList<>();
        if (!views.isEmpty()) parts.add(views + " views");
        if (!uploader.isEmpty()) parts.add(uploader);
        metaView.setText(TextUtils.join("  •  ", parts));
        commentsTitle.setText(comments.isEmpty() ? "Comments" : "Comments  " + comments);
    }

    private void buildPlayer(long startPosition) {
        failureShown = false;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
        if (!userAgent.isEmpty()) http.setUserAgent(userAgent);
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
        if (!headers.isEmpty()) http.setDefaultRequestProperties(headers);

        player = new ExoPlayer.Builder(activity)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(activity).setDataSourceFactory(http))
                .build();
        playerView.setPlayer(player);
        MediaItem.Builder media = new MediaItem.Builder().setUri(mediaUrl);
        String lower = mediaUrl.toLowerCase();
        if (lower.contains(".m3u8")) media.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.contains(".mpd")) media.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(media.build());
        playbackRecovery.bind(player, pageUrl);

        long position = startPosition;
        if (position < 0L && rememberPositionEnabled()) {
            position = activity.getSharedPreferences("player_positions", Activity.MODE_PRIVATE)
                    .getLong(positionKey(), 0L);
        }
        if (position > 0L) player.seekTo(position);
        resumeAfterPause = true;
        player.setPlayWhenReady(hostResumed);
        player.prepare();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    String readyPageUrl = pageUrl;
                    playerView.postDelayed(() -> {
                        if (readyPageUrl.equals(pageUrl)) {
                            NativeCommentsLoader.preload(activity, readyPageUrl);
                        }
                    }, 650L);
                } else if (playbackState == Player.STATE_ENDED) {
                    savePlaybackState(true);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (!playbackRecovery.recover(activity, error, recovered -> {
                    releasePlayer();
                    mediaUrl = recovered.stream.mediaUrl;
                    mediaReferer = recovered.stream.requestReferer;
                    cookies = cookiesFor(mediaUrl, pageUrl);
                    buildPlayer(recovered.position);
                    player.setPlayWhenReady(recovered.playWhenReady && hostResumed);
                }, UnifiedVideoController.this::showPlaybackFailure)) showPlaybackFailure();
            }
        });
    }

    private void loadRelated() {
        final int requestGeneration = sessionGeneration;
        relatedContainer.removeAllViews();
        relatedImages.clear();
        TextView waiting = new TextView(activity);
        waiting.setText("Loading related videos…");
        waiting.setTextColor(Color.rgb(155, 155, 164));
        waiting.setTextSize(13);
        waiting.setPadding(dp(4), dp(10), dp(4), dp(18));
        relatedContainer.addView(waiting);
        final String exclude = pageUrl;

        io.execute(() -> {
            LinkedHashMap<String, NativeContentItem> merged = new LinkedHashMap<>();
            try {
                for (NativeContentItem item : repository.fetchFeed(activity, CrazyShitRepository.HOME, 1)) {
                    if (!item.url.equals(exclude)) merged.put(item.url, item);
                }
            } catch (Exception ignored) {
            }
            try {
                for (NativeContentItem item : repository.fetchFeed(activity, CrazyShitRepository.TRENDING, 1)) {
                    if (!item.url.equals(exclude)) merged.putIfAbsent(item.url, item);
                }
            } catch (Exception ignored) {
            }
            ArrayList<NativeContentItem> result = new ArrayList<>();
            for (NativeContentItem item : merged.values()) {
                result.add(item);
                if (result.size() >= 10) break;
            }
            activity.runOnUiThread(() -> {
                if (requestGeneration != sessionGeneration || state == State.HIDDEN) return;
                renderRelated(result);
            });
        });
    }

    private void renderRelated(List<NativeContentItem> items) {
        relatedContainer.removeAllViews();
        relatedImages.clear();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(activity);
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
        MaterialCardView card = new MaterialCardView(activity);
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

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new MaterialCardView.LayoutParams(-1, -2));

        FrameLayout visual = new FrameLayout(activity);
        row.addView(visual, new LinearLayout.LayoutParams(dp(146), dp(92)));
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(18, 18, 21));
        visual.addView(image, new FrameLayout.LayoutParams(-1, -1));
        TextView play = new TextView(activity);
        play.setText("▶");
        play.setTextColor(Color.WHITE);
        play.setTextSize(18);
        play.setGravity(Gravity.CENTER);
        play.setBackground(new ColorDrawable(Color.argb(120, 0, 0, 0)));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(38), dp(38));
        pp.gravity = Gravity.CENTER;
        visual.addView(play, pp);

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), dp(9), dp(10), dp(9));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = new TextView(activity);
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
        TextView meta = new TextView(activity);
        meta.setText(TextUtils.join("  •  ", info));
        meta.setTextColor(Color.rgb(160, 160, 170));
        meta.setTextSize(11);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(5), 0, 0);
        copy.addView(meta);

        if (!clean(item.comments).isEmpty()) {
            TextView count = new TextView(activity);
            count.setText(item.comments + " comments");
            count.setTextColor(UiPalette.PRIMARY);
            count.setTextSize(11);
            count.setPadding(0, dp(5), 0, 0);
            copy.addView(count);
        }

        if (!clean(item.imageUrl).isEmpty()) loadImage(image, item.imageUrl, item.url);
        return card;
    }

    private void playRelated(NativeContentItem item) {
        if (item == null || clean(item.url).isEmpty()) return;
        loading.setVisibility(View.VISIBLE);
        final int requestGeneration = ++sessionGeneration;
        io.execute(() -> {
            CrazyShitRepository.StreamInfo resolved = null;
            try {
                resolved = PlayableSourceRouter.resolve(activity, item.url);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo result = resolved;
            activity.runOnUiThread(() -> {
                if (requestGeneration != sessionGeneration || state == State.HIDDEN) return;
                loading.setVisibility(View.GONE);
                if (result == null || clean(result.mediaUrl).isEmpty()) {
                    openWebsite(item.url);
                    return;
                }
                savePlaybackState(false);
                releasePlayer();
                mediaUrl = result.mediaUrl;
                mediaReferer = result.requestReferer;
                pageUrl = item.url;
                title = clean(item.title).isEmpty() ? clean(result.title) : item.title;
                if (title.isEmpty()) title = "Video";
                views = clean(item.views);
                uploader = clean(item.uploader);
                comments = clean(item.comments);
                userAgent = defaultUserAgent();
                cookies = cookiesFor(mediaUrl, pageUrl);
                updateMetadataUi();
                buildPlayer(-1L);
                detailsScroll.smoothScrollTo(0, 0);
                loadRelated();
            });
        });
    }

    private View buildCommentsCard() {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.rgb(25, 25, 28));
        card.setStrokeColor(Color.rgb(49, 49, 55));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openComments());
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.addView(body, new MaterialCardView.LayoutParams(-1, -2));
        commentsTitle = new TextView(activity);
        commentsTitle.setTextColor(Color.WHITE);
        commentsTitle.setTextSize(16);
        commentsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(commentsTitle);
        TextView subtitle = new TextView(activity);
        subtitle.setText("Open the native comment section without leaving the video page");
        subtitle.setTextColor(Color.rgb(170, 170, 180));
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(5), 0, 0);
        body.addView(subtitle);
        return card;
    }

    private void openComments() {
        if (pageUrl.isEmpty()) return;
        new InlineCommentsDialog(
                activity,
                pageUrl,
                title,
                comments,
                null
        ).show();
    }

    private void toggleWatchLater() {
        if (pageUrl.isEmpty()) return;
        if (FavoriteStore.contains(activity, pageUrl)) {
            FavoriteStore.remove(activity, pageUrl);
            Toast.makeText(activity, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
        } else {
            FavoriteStore.add(activity, title, pageUrl);
            Toast.makeText(activity, "Saved to Watch Later.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePage() {
        String shareUrl = pageUrl.isEmpty() ? mediaUrl : pageUrl;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareUrl);
        share.putExtra(Intent.EXTRA_SUBJECT, title);
        activity.startActivity(Intent.createChooser(share, "Share video"));
    }

    private void showPlayerMenu() {
        String saveTitle = FavoriteStore.contains(activity, pageUrl)
                ? "Remove from Watch Later"
                : "Watch Later";
        ArrayList<VideoActionSheet.Action> actions = new ArrayList<>();
        actions.add(VideoActionSheet.action(
                R.drawable.ic_action_comments,
                "Comments",
                "Read and reply without leaving the video",
                this::openComments
        ));
        actions.add(VideoActionSheet.action(
                R.drawable.ic_action_share,
                "Share",
                "Send the video page",
                this::sharePage
        ));
        actions.add(VideoActionSheet.action(
                R.drawable.ic_more_website,
                "Video details",
                "View this video on the site",
                () -> openWebsite(pageUrl)
        ));
        if (!isLandscape()) {
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_minimize,
                    "Minimize",
                    "Keep playing while you browse",
                    this::minimizeToMini
            ));
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_fullscreen,
                    "Fullscreen",
                    "Rotate the player to landscape",
                    () -> PhoneOrientationPolicy.enterSensorFullscreen(activity)
            ));
        }

        VideoActionSheet.show(
                activity,
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
                activity,
                title,
                pageUrl,
                "",
                mediaUrl,
                userAgent,
                cookies
        );
    }

    private void showSpeedMenu() {
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        new AlertDialog.Builder(activity)
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
        new AlertDialog.Builder(activity)
                .setTitle("Video size")
                .setItems(labels, (dialog, which) -> {
                    resizeMode = modes[which];
                    playerView.setResizeMode(resizeMode);
                })
                .show();
    }

    private void handleBack() {
        if (state != State.FULL) return;
        if (isLandscape()) {
            PhoneOrientationPolicy.exitFullscreenVideo(activity);
            return;
        }
        boolean minimize = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("minimize_on_back", true);
        if (minimize) minimizeToMini();
        else closeSession(true);
    }

    private void closeSession(boolean record) {
        if (state == State.HIDDEN) return;
        if (record) savePlaybackState(false);
        sessionGeneration++;
        state = State.HIDDEN;
        unregisterBack();
        miniCard.removeCallbacks(progressTicker);
        playerContainer.animate().cancel();
        detailLayer.animate().cancel();
        miniCard.animate().cancel();
        playerContainer.setVisibility(View.GONE);
        detailLayer.setVisibility(View.GONE);
        miniCard.setVisibility(View.INVISIBLE);
        miniCard.setAlpha(0f);
        setSystemBars(false);
        PhoneOrientationPolicy.exitFullscreenVideo(activity);
        releasePlayer();
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void onPause() {
        hostResumed = false;
        if (player == null) return;
        resumeAfterPause = player.isPlaying();
        savePlaybackState(false);
        player.pause();
    }

    private void onResume() {
        hostResumed = true;
        if (player != null && resumeAfterPause) player.play();
        if (state == State.FULL) registerBack();
    }

    private void onConfigurationChanged() {
        if (state == State.HIDDEN) return;
        if (state == State.FULL || state == State.TRANSITION) {
            layoutFullBounds();
            playerContainer.post(() -> {
                if (state == State.FULL) {
                    playerContainer.setTranslationX(0f);
                    playerContainer.setTranslationY(0f);
                    playerContainer.setScaleX(1f);
                    playerContainer.setScaleY(1f);
                    setFullPlayerMode();
                }
            });
        } else if (state == State.MINI) {
            setSystemBars(false);
            layoutFullBounds();
            miniCard.post(() -> {
                Geometry g = miniGeometry();
                if (g != null && state == State.MINI) {
                    playerContainer.setTranslationX(g.tx);
                    playerContainer.setTranslationY(g.ty);
                    playerContainer.setScaleX(g.sx);
                    playerContainer.setScaleY(g.sy);
                }
            });
        }
    }

    private void destroy() {
        state = State.HIDDEN;
        unregisterBack();
        miniCard.removeCallbacks(progressTicker);
        savePlaybackState(false);
        releasePlayer();
        io.shutdownNow();
    }

    private void registerBack() {
        if (Build.VERSION.SDK_INT < 33 || backRegistered || state != State.FULL) return;
        if (backCallback == null) backCallback = this::handleBack;
        try {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                    backCallback
            );
            backRegistered = true;
        } catch (Exception ignored) {
        }
    }

    private void unregisterBack() {
        if (Build.VERSION.SDK_INT < 33 || !backRegistered || backCallback == null) return;
        try {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        } catch (Exception ignored) {
        }
        backRegistered = false;
    }

    private void savePlaybackState(boolean ended) {
        if (player == null) return;
        long position = Math.max(0L, player.getCurrentPosition());
        long duration = Math.max(0L, player.getDuration());
        PlaybackHistoryStore.record(activity, title, pageUrl, position, duration, ended);
        if (!rememberPositionEnabled()) return;
        SharedPreferences prefs = activity.getSharedPreferences("player_positions", Activity.MODE_PRIVATE);
        boolean nearlyFinished = duration > 0L && position >= Math.max(0L, duration - 5000L);
        if (ended || nearlyFinished) prefs.edit().remove(positionKey()).apply();
        else if (position > 3000L) prefs.edit().putLong(positionKey(), position).apply();
    }

    private void releasePlayer() {
        playbackRecovery.cancel();
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    private void showPlaybackFailure() {
        if (failureShown || activity.isFinishing()) return;
        failureShown = true;
        new AlertDialog.Builder(activity)
                .setTitle("Couldn't play this stream")
                .setMessage("The native player couldn't continue this video. You can open the webpage instead.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open page", (dialog, which) -> openWebsite(pageUrl))
                .show();
    }

    private void openWebsite(String url) {
        savePlaybackState(false);
        Intent intent = new Intent(activity, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL,
                clean(url).isEmpty() ? CrazyShitRepository.HOME : url);
        activity.startActivity(intent);
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
                .addHeader("Referer", clean(referer).isEmpty() ? SITE : referer)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String c = CookieManager.getInstance().getCookie(imageUrl);
            if ((c == null || c.isEmpty()) && referer != null) c = CookieManager.getInstance().getCookie(referer);
            if (c != null && !c.isEmpty()) headers.addHeader("Cookie", c);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private TextView actionButton(String text, Runnable action) {
        TextView button = new TextView(activity);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.rgb(31, 31, 35), dp(14)));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> { haptic(v); action.run(); });
        return button;
    }

    private TextView miniButton(String text, String description) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(Color.rgb(235, 235, 240));
        view.setTextSize(21);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setClickable(true);
        return view;
    }

    private boolean swipeEnabled() {
        return activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("swipe_down_minimize", true);
    }

    private boolean rememberPositionEnabled() {
        return activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("remember_video_position", true);
    }

    private String positionKey() {
        String key = pageUrl.isEmpty() ? mediaUrl : pageUrl;
        return "position_" + Integer.toHexString(key.hashCode());
    }

    private String defaultUserAgent() {
        try { return WebSettings.getDefaultUserAgent(activity); }
        catch (Exception ignored) { return THUMB_UA; }
    }

    private String cookiesFor(String media, String page) {
        try {
            String value = CookieManager.getInstance().getCookie(media);
            if ((value == null || value.isEmpty()) && page != null) {
                value = CookieManager.getInstance().getCookie(page);
            }
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void setChromeAlpha(float alpha) {
        // The title bar now lives inside Media3's controller and shares its single animation.
    }

    private void setSystemBars(boolean fullscreen) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                int types = WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars();
                if (fullscreen) {
                    controller.hide(types);
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else controller.show(types);
            }
        } else {
            activity.getWindow().getDecorView().setSystemUiVisibility(fullscreen
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private int topInset() {
        if (Build.VERSION.SDK_INT >= 23 && root.getRootWindowInsets() != null) {
            WindowInsets insets = root.getRootWindowInsets();
            if (Build.VERSION.SDK_INT >= 30) {
                return insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).top;
            }
            return insets.getSystemWindowInsetTop();
        }
        return 0;
    }

    private boolean isLandscape() {
        return activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int portraitPlayerHeight() {
        int width = Math.max(1, activity.getResources().getDisplayMetrics().widthPixels);
        return Math.max(dp(190), Math.round(width * 9f / 16f));
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

    private void haptic(View view) {
        if (!activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float start, float end, float fraction) {
        return start + ((end - start) * fraction);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void suppressCloseTransition(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
            } else {
                activity.overridePendingTransition(0, 0);
            }
        } catch (Exception ignored) {
        }
    }

    private static final class Geometry {
        final float sx;
        final float sy;
        final float tx;
        final float ty;

        Geometry(float sx, float sy, float tx, float ty) {
            this.sx = sx;
            this.sy = sy;
            this.tx = tx;
            this.ty = ty;
        }
    }
}
