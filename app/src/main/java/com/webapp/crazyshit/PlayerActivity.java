package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

@UnstableApi
public class PlayerActivity extends Activity {
    public static final String EXTRA_MEDIA_URL = "media_url";
    public static final String EXTRA_PAGE_URL = "page_url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_USER_AGENT = "user_agent";
    public static final String EXTRA_COOKIES = "cookies";
    public static final String EXTRA_FALLBACK_PAGE = "fallback_page";

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView backButton;
    private TextView menuButton;
    private String mediaUrl;
    private String pageUrl;
    private String title;
    private boolean failureShown;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        mediaUrl = getIntent().getStringExtra(EXTRA_MEDIA_URL);
        pageUrl = getIntent().getStringExtra(EXTRA_PAGE_URL);
        title = getIntent().getStringExtra(EXTRA_TITLE);

        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            finish();
            return;
        }

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        buildUi();
        buildPlayer();
        configurePip();
        setFullscreenUi(true);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setKeepScreenOn(true);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        backButton = new TextView(this);
        backButton.setText("‹");
        backButton.setTextColor(Color.WHITE);
        backButton.setTextSize(38);
        backButton.setGravity(Gravity.CENTER);
        backButton.setBackgroundColor(Color.argb(150, 0, 0, 0));
        backButton.setContentDescription("Back");
        backButton.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(52), dp(52));
        backParams.gravity = Gravity.TOP | Gravity.START;
        backParams.setMargins(dp(10), dp(10), 0, 0);
        root.addView(backButton, backParams);

        menuButton = new TextView(this);
        menuButton.setText("⋮");
        menuButton.setTextColor(Color.WHITE);
        menuButton.setTextSize(28);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setBackgroundColor(Color.argb(150, 0, 0, 0));
        menuButton.setContentDescription("Player menu");
        menuButton.setOnClickListener(v -> showPlayerMenu());
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(52), dp(52));
        menuParams.gravity = Gravity.TOP | Gravity.END;
        menuParams.setMargins(0, dp(10), dp(10), 0);
        root.addView(menuButton, menuParams);

        setContentView(root);
    }

    private void buildPlayer() {
        String userAgent = getIntent().getStringExtra(EXTRA_USER_AGENT);
        String cookies = getIntent().getStringExtra(EXTRA_COOKIES);

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
        String lower = mediaUrl.toLowerCase();
        if (lower.contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.contains(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);

        player.setMediaItem(item.build());
        long savedPosition = getPreferences(MODE_PRIVATE).getLong(positionKey(), 0L);
        if (savedPosition > 0L) player.seekTo(savedPosition);
        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    getPreferences(MODE_PRIVATE).edit().remove(positionKey()).apply();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                showPlaybackFailure();
            }
        });
    }

    private void showPlayerMenu() {
        PopupMenu menu = new PopupMenu(this, menuButton);
        menu.getMenu().add(0, 1, 0, "Restart video");
        menu.getMenu().add(0, 2, 1, "Playback speed");
        menu.getMenu().add(0, 3, 2, "Share page");
        menu.getMenu().add(0, 4, 3, "Open normal page");

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
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void showSpeedMenu() {
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        new AlertDialog.Builder(this)
                .setTitle("Playback speed")
                .setItems(labels, (dialog, which) -> {
                    if (player != null) {
                        player.setPlaybackParameters(new PlaybackParameters(speeds[which]));
                    }
                })
                .show();
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
        if (pageUrl != null && !pageUrl.isEmpty()) {
            Intent result = new Intent();
            result.putExtra(EXTRA_FALLBACK_PAGE, pageUrl);
            setResult(RESULT_CANCELED, result);
        }
        finish();
    }

    private String positionKey() {
        return "position_" + Integer.toHexString(mediaUrl.hashCode());
    }

    private void savePosition() {
        if (player == null) return;
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        if (position > 3000L && (duration <= 0L || position < duration - 5000L)) {
            getPreferences(MODE_PRIVATE).edit().putLong(positionKey(), position).apply();
        }
    }

    private boolean pipSupported() {
        return Build.VERSION.SDK_INT >= 26 &&
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    private void configurePip() {
        if (!pipSupported()) return;
        try {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9));
            if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(true);
            setPictureInPictureParams(builder.build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (pipSupported() && Build.VERSION.SDK_INT < 31 && player != null && player.isPlaying()) {
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
        if (backButton != null) backButton.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        if (menuButton != null) menuButton.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        if (playerView != null) playerView.setUseController(!isInPictureInPictureMode);
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

    @Override
    protected void onStop() {
        savePosition();
        if (player != null && !isInPictureInPictureMode()) player.pause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        savePosition();
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
