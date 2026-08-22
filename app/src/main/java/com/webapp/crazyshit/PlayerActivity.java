package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@UnstableApi
public class PlayerActivity extends Activity {
    public static final String EXTRA_MEDIA_URL = "media_url";
    public static final String EXTRA_PAGE_URL = "page_url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_USER_AGENT = "user_agent";
    public static final String EXTRA_COOKIES = "cookies";
    public static final String EXTRA_FALLBACK_PAGE = "fallback_page";
    public static final String EXTRA_MINIMIZED = "minimized";
    public static final String EXTRA_START_POSITION = "start_position";

    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout videoSurface;
    private TextView menuButton;
    private TextView titleView;
    private TextView gestureLabel;
    private LinearLayout topBar;

    private String mediaUrl;
    private String pageUrl;
    private String title;
    private String userAgent;
    private String cookies;

    private boolean failureShown;
    private boolean minimizing;
    private boolean dragMinimize;
    private float dragStartY;
    private float dragLastY;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private AudioManager audioManager;
    private OnBackInvokedCallback systemBackCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        mediaUrl = getIntent().getStringExtra(EXTRA_MEDIA_URL);
        pageUrl = getIntent().getStringExtra(EXTRA_PAGE_URL);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        userAgent = getIntent().getStringExtra(EXTRA_USER_AGENT);
        cookies = getIntent().getStringExtra(EXTRA_COOKIES);

        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            finish();
            return;
        }
        if (title == null || title.trim().isEmpty()) title = "Video";

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        buildUi();
        buildPlayer();
        configurePip();
        setFullscreenUi(true);
        configureBackHandling();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 8, 10));

        videoSurface = new FrameLayout(this);
        videoSurface.setBackgroundColor(Color.BLACK);
        videoSurface.setPivotX(getResources().getDisplayMetrics().widthPixels / 2f);
        videoSurface.setPivotY(0f);
        root.addView(videoSurface, new FrameLayout.LayoutParams(-1, -1));

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setKeepScreenOn(true);
        playerView.setResizeMode(resizeMode);
        videoSurface.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        topBar.setBackground(rounded(Color.argb(185, 12, 12, 14), dp(22)));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, dp(60));
        topParams.gravity = Gravity.TOP;
        topParams.setMargins(dp(10), dp(10), dp(10), 0);
        videoSurface.addView(topBar, topParams);

        TextView backButton = topButton("‹", "Back or minimize");
        backButton.setTextSize(34);
        backButton.setOnClickListener(v -> {
            haptic(v);
            handleBackNavigation();
        });
        topBar.addView(backButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setPadding(dp(8), 0, dp(8), 0);
        topBar.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        menuButton = topButton("⋮", "Player menu");
        menuButton.setTextSize(28);
        menuButton.setOnClickListener(v -> {
            haptic(v);
            showPlayerMenu();
        });
        topBar.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        gestureLabel = new TextView(this);
        gestureLabel.setTextColor(Color.WHITE);
        gestureLabel.setTextSize(17);
        gestureLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        gestureLabel.setGravity(Gravity.CENTER);
        gestureLabel.setPadding(dp(18), dp(12), dp(18), dp(12));
        gestureLabel.setBackground(rounded(Color.argb(205, 20, 20, 24), dp(18)));
        gestureLabel.setVisibility(View.GONE);
        gestureLabel.setElevation(dp(12));
        FrameLayout.LayoutParams gestureParams = new FrameLayout.LayoutParams(-2, -2);
        gestureParams.gravity = Gravity.CENTER;
        videoSurface.addView(gestureLabel, gestureParams);

        setContentView(root);
        configureGestures();
    }

    private TextView topButton(String label, String description) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(rounded(Color.argb(110, 255, 255, 255), dp(18)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void buildPlayer() {
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
        if (userAgent != null && !userAgent.isEmpty()) httpFactory.setUserAgent(userAgent);

        Map<String, String> headers = new HashMap<>();
        if (pageUrl != null && !pageUrl.isEmpty()) {
            headers.put("Referer", pageUrl);
            try {
                Uri page = Uri.parse(pageUrl);
                if (page.getScheme() != null && page.getHost() != null) {
                    headers.put("Origin", page.getScheme() + "://" + page.getHost());
                }
            } catch (Exception ignored) {
            }
        }
        if (cookies != null && !cookies.isEmpty()) headers.put("Cookie", cookies);
        if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);

        DefaultMediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        playerView.setPlayer(player);

        MediaItem.Builder item = new MediaItem.Builder().setUri(mediaUrl);
        String lower = mediaUrl.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.contains(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(item.build());

        long requestedPosition = getIntent().getLongExtra(EXTRA_START_POSITION, -1L);
        long savedPosition = 0L;
        if (rememberPositionEnabled()) {
            savedPosition = getSharedPreferences("player_positions", MODE_PRIVATE)
                    .getLong(positionKey(), 0L);
        }
        long startPosition = requestedPosition >= 0L ? requestedPosition : savedPosition;
        if (startPosition > 0L) player.seekTo(startPosition);

        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    if (rememberPositionEnabled()) {
                        getSharedPreferences("player_positions", MODE_PRIVATE)
                                .edit()
                                .remove(positionKey())
                                .apply();
                    }
                    recordHistory(true);
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                if (titleView != null && videoSize.width > 0 && videoSize.height > 0) {
                    titleView.setContentDescription(
                            title + ", " + videoSize.width + " by " + videoSize.height
                    );
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                showPlaybackFailure();
            }
        });
    }

    private void configureGestures() {
        final GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (dragMinimize) return true;
                        if (playerView.isControllerFullyVisible()) {
                            playerView.hideController();
                            topBar.setVisibility(View.GONE);
                        } else {
                            playerView.showController();
                            topBar.setVisibility(View.VISIBLE);
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (player == null || dragMinimize) return true;
                        long delta = e.getX() < playerView.getWidth() / 2f ? -10_000L : 10_000L;
                        long duration = player.getDuration();
                        long target = Math.max(0L, player.getCurrentPosition() + delta);
                        if (duration > 0L) target = Math.min(duration, target);
                        player.seekTo(target);
                        haptic(playerView);
                        showGesture(delta < 0 ? "−10 seconds" : "+10 seconds");
                        return true;
                    }
                });

        playerView.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            long downPosition;
            int startVolume;
            float startBrightness;
            boolean moved;
            boolean horizontal;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                detector.onTouchEvent(event);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        dragStartY = downY;
                        dragLastY = downY;
                        downPosition = player == null ? 0L : player.getCurrentPosition();
                        startVolume = audioManager == null
                                ? 0
                                : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        startBrightness = getWindow().getAttributes().screenBrightness;
                        if (startBrightness < 0f) startBrightness = 0.5f;
                        moved = false;
                        horizontal = false;
                        dragMinimize = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - downX;
                        float dy = event.getY() - downY;
                        dragLastY = event.getY();

                        if (!moved && Math.hypot(dx, dy) > dp(18)) {
                            moved = true;
                            horizontal = Math.abs(dx) > Math.abs(dy);
                            boolean upperStart = downY < playerView.getHeight() * 0.38f;
                            if (!horizontal && dy > 0 && upperStart &&
                                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                                            .getBoolean("swipe_down_minimize", true)) {
                                dragMinimize = true;
                                playerView.hideController();
                                topBar.setVisibility(View.GONE);
                            }
                        }
                        if (!moved) return true;

                        if (dragMinimize) {
                            if (dy < 0f) dy = 0f;
                            applyMinimizeDrag(dy);
                            return true;
                        }

                        if (horizontal) {
                            if (player == null) return true;
                            long duration = player.getDuration();
                            long range = duration > 0L ? Math.min(duration, 180_000L) : 120_000L;
                            long delta = (long) ((dx / Math.max(1f, playerView.getWidth())) * range);
                            long target = Math.max(0L, downPosition + delta);
                            if (duration > 0L) target = Math.min(duration, target);
                            player.seekTo(target);
                            showGesture(formatTime(target));
                        } else if (event.getX() < playerView.getWidth() / 2f) {
                            float change = -dy / Math.max(1f, playerView.getHeight());
                            WindowManager.LayoutParams attrs = getWindow().getAttributes();
                            attrs.screenBrightness = clamp(startBrightness + change, 0.02f, 1f);
                            getWindow().setAttributes(attrs);
                            showGesture("Brightness  " + Math.round(attrs.screenBrightness * 100f) + "%");
                        } else if (audioManager != null) {
                            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            float change = -dy / Math.max(1f, playerView.getHeight());
                            int value = Math.max(0, Math.min(max,
                                    startVolume + Math.round(change * max)));
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
                            showGesture("Volume  " + Math.round((value * 100f) / Math.max(1, max)) + "%");
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragMinimize) {
                            float drag = Math.max(0f, event.getY() - downY);
                            finishMinimizeDrag(drag);
                            return true;
                        }
                        if (moved) {
                            haptic(playerView);
                            hideGestureSoon();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void applyMinimizeDrag(float dy) {
        if (videoSurface == null) return;
        float height = Math.max(1f, playerView.getHeight());
        float progress = clamp(dy / (height * 0.55f), 0f, 1f);
        float scale = 1f - (0.30f * progress);
        videoSurface.setScaleX(scale);
        videoSurface.setScaleY(scale);
        videoSurface.setTranslationY(dy * 0.62f);
        videoSurface.setAlpha(1f - (0.08f * progress));
    }

    private void finishMinimizeDrag(float dy) {
        float threshold = playerView.getHeight() * 0.20f;
        if (dy >= threshold) {
            haptic(playerView);
            minimizing = true;
            recordHistory(false);
            videoSurface.animate()
                    .scaleX(0.68f)
                    .scaleY(0.68f)
                    .translationY(playerView.getHeight() * 0.34f)
                    .alpha(0.96f)
                    .setDuration(170L)
                    .withEndAction(this::minimizeToBrowser)
                    .start();
        } else {
            videoSurface.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        dragMinimize = false;
                        topBar.setVisibility(View.VISIBLE);
                        playerView.showController();
                    })
                    .start();
        }
    }

    private void showPlayerMenu() {
        PopupMenu menu = new PopupMenu(this, menuButton);
        menu.getMenu().add(0, 1, 0, "Restart video");
        menu.getMenu().add(0, 2, 1, "Playback speed");
        menu.getMenu().add(0, 5, 2, "Fit / Fill / Zoom");
        menu.getMenu().add(0, 6, 3, qualityLabel()).setEnabled(false);
        menu.getMenu().add(0, 7, 4,
                FavoriteStore.contains(this, pageUrl) ? "Remove from Watch Later" : "Save to Watch Later");
        menu.getMenu().add(0, 8, 5, "Minimize to browser");
        menu.getMenu().add(0, 3, 6, "Share page");
        menu.getMenu().add(0, 4, 7, "Open normal page");

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (player != null) {
                        player.seekTo(0L);
                        player.play();
                    }
                    return true;
                case 2:
                    showSpeedMenu();
                    return true;
                case 3:
                    sharePage();
                    return true;
                case 4:
                    returnToWebPage();
                    return true;
                case 5:
                    showResizeMenu();
                    return true;
                case 7:
                    toggleWatchLater();
                    return true;
                case 8:
                    minimizing = true;
                    recordHistory(false);
                    minimizeToBrowser();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private String qualityLabel() {
        if (player == null) return "Quality: Auto";
        VideoSize size = player.getVideoSize();
        if (size.width <= 0 || size.height <= 0) return "Quality: Auto";
        return "Playing: " + size.width + "×" + size.height;
    }

    private void showSpeedMenu() {
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        new AlertDialog.Builder(this)
                .setTitle("Playback speed")
                .setItems(labels, (dialog, which) -> {
                    if (player != null) {
                        player.setPlaybackParameters(new PlaybackParameters(speeds[which]));
                        haptic(playerView);
                        showGesture(labels[which]);
                    }
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
                    showGesture(labels[which]);
                    haptic(playerView);
                })
                .show();
    }

    private void toggleWatchLater() {
        if (pageUrl == null || pageUrl.isEmpty()) {
            Toast.makeText(this, "This video has no page to save.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (FavoriteStore.contains(this, pageUrl)) {
            FavoriteStore.remove(this, pageUrl);
            Toast.makeText(this, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
        } else {
            FavoriteStore.add(this, title, pageUrl);
            Toast.makeText(this, "Saved to Watch Later.", Toast.LENGTH_SHORT).show();
        }
        haptic(playerView);
    }

    private void sharePage() {
        String shareUrl = pageUrl == null || pageUrl.isEmpty() ? mediaUrl : pageUrl;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareUrl);
        if (title != null && !title.isEmpty()) share.putExtra(Intent.EXTRA_SUBJECT, title);
        startActivity(Intent.createChooser(share, "Share video"));
    }

    private void showPlaybackFailure() {
        if (failureShown || isFinishing()) return;
        failureShown = true;
        new AlertDialog.Builder(this)
                .setTitle("Couldn't play this stream")
                .setMessage("This video isn't exposing a stream the native player can use. You can open the normal webpage instead.")
                .setCancelable(false)
                .setNegativeButton("Close", (dialog, which) -> finish())
                .setPositiveButton("Open page", (dialog, which) -> returnToWebPage())
                .show();
    }

    private void returnToWebPage() {
        recordHistory(false);
        if (pageUrl != null && !pageUrl.isEmpty()) {
            Intent result = new Intent();
            result.putExtra(EXTRA_FALLBACK_PAGE, pageUrl);
            setResult(RESULT_CANCELED, result);
        }
        finish();
    }

    private void minimizeToBrowser() {
        if (isFinishing()) return;
        minimizing = true;
        savePosition();
        recordHistory(false);
        Intent result = new Intent();
        result.putExtra(EXTRA_MINIMIZED, true);
        result.putExtra(EXTRA_MEDIA_URL, mediaUrl);
        result.putExtra(EXTRA_PAGE_URL, pageUrl);
        result.putExtra(EXTRA_TITLE, title);
        result.putExtra(EXTRA_USER_AGENT, userAgent);
        result.putExtra(EXTRA_COOKIES, cookies);
        if (player != null) result.putExtra(EXTRA_START_POSITION, player.getCurrentPosition());
        setResult(RESULT_OK, result);
        finish();
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
        long duration = safeDuration();
        SharedPreferences prefs = getSharedPreferences("player_positions", MODE_PRIVATE);
        if (position > 3000L && (duration <= 0L || position < duration - 5000L)) {
            prefs.edit().putLong(positionKey(), position).apply();
        }
    }

    private void recordHistory(boolean ended) {
        if (player == null || pageUrl == null || pageUrl.trim().isEmpty()) return;
        PlaybackHistoryStore.record(
                this,
                title,
                pageUrl,
                Math.max(0L, player.getCurrentPosition()),
                safeDuration(),
                ended
        );
    }

    private long safeDuration() {
        if (player == null) return 0L;
        long duration = player.getDuration();
        return duration > 0L && duration < Long.MAX_VALUE / 2 ? duration : 0L;
    }

    private boolean pipSupported() {
        return Build.VERSION.SDK_INT >= 26 &&
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    private boolean autoPipEnabled() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("player_auto_pip", true);
    }

    private void configurePip() {
        if (!pipSupported()) return;
        try {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9));
            if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(autoPipEnabled());
            setPictureInPictureParams(builder.build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (minimizing) return;
        if (autoPipEnabled() && pipSupported() && Build.VERSION.SDK_INT < 31 &&
                player != null && player.isPlaying()) {
            try {
                enterPictureInPictureMode(
                        new PictureInPictureParams.Builder()
                                .setAspectRatio(new Rational(16, 9))
                                .build()
                );
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        resetMinimizeTransform();
        if (topBar != null) topBar.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        if (gestureLabel != null) gestureLabel.setVisibility(View.GONE);
        if (playerView != null) playerView.setUseController(!isInPictureInPictureMode);
    }

    private void configureBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) {
            systemBackCallback = this::handleBackNavigation;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    systemBackCallback
            );
        }
    }

    private void handleBackNavigation() {
        if (getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("minimize_on_back", true)) {
            minimizeToBrowser();
        } else {
            recordHistory(false);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
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

    private void showGesture(String text) {
        if (gestureLabel == null || dragMinimize) return;
        gestureLabel.setText(text);
        gestureLabel.setAlpha(1f);
        gestureLabel.setVisibility(View.VISIBLE);
        hideGestureSoon();
    }

    private void hideGestureSoon() {
        if (gestureLabel == null) return;
        gestureLabel.removeCallbacks(hideGestureRunnable);
        gestureLabel.postDelayed(hideGestureRunnable, 650L);
    }

    private final Runnable hideGestureRunnable = () -> {
        if (gestureLabel != null) {
            gestureLabel.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (gestureLabel != null) gestureLabel.setVisibility(View.GONE);
                    })
                    .start();
        }
    };

    private void resetMinimizeTransform() {
        dragMinimize = false;
        if (videoSurface == null) return;
        videoSurface.animate().cancel();
        videoSurface.setScaleX(1f);
        videoSurface.setScaleY(1f);
        videoSurface.setTranslationY(0f);
        videoSurface.setAlpha(1f);
    }

    private String formatTime(long millis) {
        long total = Math.max(0L, millis / 1000L);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void haptic(View view) {
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onStop() {
        savePosition();
        recordHistory(false);
        if (player != null && !isInPictureInPictureMode()) player.pause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && systemBackCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
            } catch (Exception ignored) {
            }
            systemBackCallback = null;
        }
        savePosition();
        recordHistory(false);
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
