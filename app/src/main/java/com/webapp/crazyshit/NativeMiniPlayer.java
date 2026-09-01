package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@UnstableApi
public final class NativeMiniPlayer {
    static final int CARD_HEIGHT_DP = 82;
    static final int CARD_SIDE_MARGIN_DP = 12;
    static final int CARD_BOTTOM_MARGIN_DP = 78;
    static final int ROW_PAD_X_DP = 4;
    static final int ROW_PAD_TOP_DP = 4;
    static final int VIDEO_WIDTH_DP = 132;
    static final int VIDEO_HEIGHT_DP = 74;

    public interface Host {
        void reopenMiniPlayer(Intent intent);
    }

    private final Activity activity;
    private final FrameLayout overlayRoot;
    private final Host host;

    private MaterialCardView card;
    private PlayerView playerView;
    private ImageView handoffPoster;
    private ImageView travelingPoster;
    private TextView titleView;
    private View progressFill;
    private ExoPlayer player;
    private String mediaUrl;
    private String pageUrl;
    private String title;
    private String userAgent;
    private String cookies;
    private String views;
    private String uploader;
    private String comments;
    private String relatedFeedUrl;
    private String source;
    private String mediaReferer;
    private String handoffSnapshotPath;
    private boolean reopenDetail;
    private boolean resumeAfterPause;
    private boolean playerReady;
    private boolean travelActive;
    private boolean travelFinished;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (player == null || progressFill == null || card == null || card.getVisibility() != View.VISIBLE) return;
            long duration = Math.max(0L, player.getDuration());
            long position = Math.max(0L, player.getCurrentPosition());
            float progress = duration > 0L ? Math.min(1f, position / (float) duration) : 0f;
            progressFill.setScaleX(progress);
            progressFill.setPivotX(0f);
            card.postDelayed(this, 350L);
        }
    };

    public NativeMiniPlayer(Activity activity, FrameLayout overlayRoot, Host host) {
        this.activity = activity;
        this.overlayRoot = overlayRoot;
        this.host = host;
    }

    public boolean isVisible() {
        return card != null && card.getVisibility() == View.VISIBLE && player != null;
    }

    public void start(Intent data) {
        stop();
        if (data == null) return;

        mediaUrl = data.getStringExtra(PlayerActivity.EXTRA_MEDIA_URL);
        pageUrl = data.getStringExtra(PlayerActivity.EXTRA_PAGE_URL);
        title = data.getStringExtra(PlayerActivity.EXTRA_TITLE);
        userAgent = data.getStringExtra(PlayerActivity.EXTRA_USER_AGENT);
        cookies = data.getStringExtra(PlayerActivity.EXTRA_COOKIES);
        reopenDetail = data.getBooleanExtra(VideoDetailActivity.EXTRA_REOPEN_DETAIL, false);
        views = data.getStringExtra(VideoDetailActivity.EXTRA_VIEWS);
        uploader = data.getStringExtra(VideoDetailActivity.EXTRA_UPLOADER);
        comments = data.getStringExtra(VideoDetailActivity.EXTRA_COMMENTS);
        relatedFeedUrl = data.getStringExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL);
        source = data.getStringExtra(VideoDetailActivity.EXTRA_SOURCE);
        mediaReferer = data.getStringExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER);
        boolean directHandoff = data.getBooleanExtra(MiniPlayerHandoffPolish.EXTRA_DIRECT_HANDOFF, false);
        handoffSnapshotPath = data.getStringExtra(MiniPlayerHandoffPolish.EXTRA_SNAPSHOT_PATH);
        int sourceLeft = data.getIntExtra(MiniPlayerHandoffPolish.EXTRA_SOURCE_LEFT, -1);
        int sourceTop = data.getIntExtra(MiniPlayerHandoffPolish.EXTRA_SOURCE_TOP, -1);
        int sourceWidth = data.getIntExtra(MiniPlayerHandoffPolish.EXTRA_SOURCE_WIDTH, -1);
        int sourceHeight = data.getIntExtra(MiniPlayerHandoffPolish.EXTRA_SOURCE_HEIGHT, -1);
        long start = data.getLongExtra(PlayerActivity.EXTRA_START_POSITION, 0L);
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) return;

        ensureUi();
        titleView.setText(title == null || title.trim().isEmpty() ? "Video" : title);
        playerReady = false;
        travelActive = false;
        travelFinished = false;

        boolean canTravel = directHandoff &&
                handoffSnapshotPath != null && !handoffSnapshotPath.trim().isEmpty() &&
                sourceLeft >= 0 && sourceTop >= 0 && sourceWidth > 0 && sourceHeight > 0;

        if (canTravel) prepareCardForTravel();
        else showHandoffPoster(handoffSnapshotPath);

        try {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
            if (userAgent != null && !userAgent.isEmpty()) http.setUserAgent(userAgent);

            Map<String, String> headers = new HashMap<>();
            String requestPage = mediaReferer == null || mediaReferer.isEmpty() ? pageUrl : mediaReferer;
            if (requestPage != null && !requestPage.isEmpty()) {
                headers.put("Referer", requestPage);
                try {
                    Uri page = Uri.parse(requestPage);
                    if (page.getScheme() != null && page.getHost() != null) {
                        headers.put("Origin", page.getScheme() + "://" + page.getHost());
                    }
                } catch (Exception ignored) {
                }
            }
            if (cookies != null && !cookies.isEmpty()) headers.put("Cookie", cookies);
            if (!headers.isEmpty()) http.setDefaultRequestProperties(headers);

            player = new ExoPlayer.Builder(activity)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(activity)
                            .setDataSourceFactory(http))
                    .build();
            playerView.setPlayer(player);

            MediaItem.Builder item = new MediaItem.Builder().setUri(mediaUrl);
            String lower = mediaUrl.toLowerCase();
            if (lower.contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if (lower.contains(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
            player.setMediaItem(item.build());
            if (start > 0L) player.seekTo(start);
            player.prepare();
            player.play();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        playerReady = true;
                        if (travelActive) {
                            maybeFinishTravelPoster();
                        } else if (handoffPoster != null && handoffPoster.getVisibility() == View.VISIBLE) {
                            handoffPoster.postDelayed(NativeMiniPlayer.this::fadeHandoffPoster, 80L);
                        }
                    }
                    if (playbackState == Player.STATE_ENDED) recordHistory(true);
                }
            });
            resumeAfterPause = true;

            if (canTravel) {
                final int left = sourceLeft;
                final int top = sourceTop;
                final int width = sourceWidth;
                final int height = sourceHeight;
                overlayRoot.post(() -> startTravelHandoff(left, top, width, height, 0));
            } else if (directHandoff) {
                showCardDirect();
            } else {
                showCardAnimated();
            }

            card.removeCallbacks(progressTicker);
            card.post(progressTicker);
        } catch (Exception e) {
            stop();
            Toast.makeText(activity, "Couldn't start the mini-player.", Toast.LENGTH_SHORT).show();
        }
    }

    public void onPause() {
        if (player == null) return;
        resumeAfterPause = player.isPlaying();
        recordHistory(false);
        player.pause();
    }

    public void onResume() {
        if (player != null && resumeAfterPause) player.play();
    }

    public void stop() {
        resumeAfterPause = false;
        recordHistory(false);
        if (card != null) card.removeCallbacks(progressTicker);
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        playerReady = false;
        travelActive = false;
        travelFinished = false;
        clearTravelPoster(true);
        hideCard();
        clearHandoffPoster(true);
        mediaUrl = null;
        pageUrl = null;
        title = null;
        userAgent = null;
        cookies = null;
        views = null;
        uploader = null;
        comments = null;
        relatedFeedUrl = null;
        source = null;
        mediaReferer = null;
        reopenDetail = false;
    }

    private void reopen() {
        if (player == null || mediaUrl == null) return;
        long position = player.getCurrentPosition();
        recordHistory(false);

        Class<?> target = reopenDetail ? VideoDetailActivity.class : PlayerActivity.class;
        Intent intent = new Intent(activity, target);
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, mediaUrl);
        intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, pageUrl);
        intent.putExtra(PlayerActivity.EXTRA_TITLE, title);
        intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, userAgent);
        intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
        intent.putExtra(PlayerActivity.EXTRA_START_POSITION, position);
        if (reopenDetail) {
            intent.putExtra(VideoDetailActivity.EXTRA_REOPEN_DETAIL, true);
            intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, views);
            intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, uploader);
            intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, comments);
            intent.putExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL, relatedFeedUrl);
            intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, source);
            intent.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, mediaReferer);
        }
        stopWithoutRecording();
        host.reopenMiniPlayer(intent);
    }

    private void recordHistory(boolean ended) {
        if (player == null || pageUrl == null || pageUrl.trim().isEmpty()) return;
        PlaybackHistoryStore.record(
                activity,
                title,
                pageUrl,
                player.getCurrentPosition(),
                Math.max(0L, player.getDuration()),
                ended
        );
    }

    private void stopWithoutRecording() {
        resumeAfterPause = false;
        if (card != null) card.removeCallbacks(progressTicker);
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        playerReady = false;
        travelActive = false;
        travelFinished = false;
        clearTravelPoster(true);
        hideCard();
        clearHandoffPoster(true);
        mediaUrl = null;
        pageUrl = null;
        title = null;
        userAgent = null;
        cookies = null;
        views = null;
        uploader = null;
        comments = null;
        reopenDetail = false;
    }

    private void prepareCardForTravel() {
        if (card == null) return;
        card.animate().cancel();
        card.setVisibility(View.VISIBLE);
        card.setAlpha(0f);
        card.setTranslationX(0f);
        card.setTranslationY(0f);
        card.setScaleX(1f);
        card.setScaleY(1f);
        if (handoffPoster != null) {
            handoffPoster.animate().cancel();
            handoffPoster.setImageDrawable(null);
            handoffPoster.setVisibility(View.GONE);
            handoffPoster.setAlpha(1f);
        }
    }

    private void startTravelHandoff(
            int sourceLeft,
            int sourceTop,
            int sourceWidth,
            int sourceHeight,
            int attempt
    ) {
        if (card == null || playerView == null || overlayRoot == null) {
            fallbackDirectHandoff();
            return;
        }
        if (playerView.getWidth() <= 0 || playerView.getHeight() <= 0 ||
                overlayRoot.getWidth() <= 0 || overlayRoot.getHeight() <= 0) {
            if (attempt < 4) {
                overlayRoot.postDelayed(
                        () -> startTravelHandoff(sourceLeft, sourceTop, sourceWidth, sourceHeight, attempt + 1),
                        16L
                );
            } else {
                fallbackDirectHandoff();
            }
            return;
        }

        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(handoffSnapshotPath);
        } catch (Exception ignored) {
            bitmap = null;
        }
        if (bitmap == null) {
            fallbackDirectHandoff();
            return;
        }

        int[] rootLocation = new int[2];
        int[] targetLocation = new int[2];
        overlayRoot.getLocationOnScreen(rootLocation);
        playerView.getLocationOnScreen(targetLocation);

        float startX = sourceLeft - rootLocation[0];
        float startY = sourceTop - rootLocation[1];
        float targetX = targetLocation[0] - rootLocation[0];
        float targetY = targetLocation[1] - rootLocation[1];

        travelingPoster = new ImageView(activity);
        travelingPoster.setImageBitmap(bitmap);
        travelingPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        travelingPoster.setBackground(roundedBackground(Color.BLACK, dp(14)));
        travelingPoster.setClipToOutline(true);
        travelingPoster.setElevation(dp(28));
        travelingPoster.setPivotX(0f);
        travelingPoster.setPivotY(0f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sourceWidth, sourceHeight);
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = Math.round(startX);
        params.topMargin = Math.round(startY);
        overlayRoot.addView(travelingPoster, params);

        travelActive = true;
        travelFinished = false;

        card.animate().cancel();
        card.setVisibility(View.VISIBLE);
        card.setAlpha(0f);
        card.animate()
                .alpha(1f)
                .setStartDelay(72L)
                .setDuration(172L)
                .setInterpolator(new DecelerateInterpolator(1.25f))
                .start();

        float targetScaleX = playerView.getWidth() / (float) sourceWidth;
        float targetScaleY = playerView.getHeight() / (float) sourceHeight;
        travelingPoster.animate().cancel();
        travelingPoster.animate()
                .x(targetX)
                .y(targetY)
                .scaleX(targetScaleX)
                .scaleY(targetScaleY)
                .setDuration(268L)
                .setInterpolator(new PathInterpolator(0.20f, 0.86f, 0.28f, 1f))
                .withEndAction(() -> {
                    travelFinished = true;
                    maybeFinishTravelPoster();
                })
                .start();
    }

    private void fallbackDirectHandoff() {
        travelActive = false;
        travelFinished = false;
        clearTravelPoster(false);
        showHandoffPoster(handoffSnapshotPath);
        showCardDirect();
        if (playerReady && handoffPoster != null && handoffPoster.getVisibility() == View.VISIBLE) {
            handoffPoster.postDelayed(this::fadeHandoffPoster, 80L);
        }
    }

    private void maybeFinishTravelPoster() {
        if (!travelActive || !travelFinished || !playerReady || travelingPoster == null) return;
        travelActive = false;
        travelingPoster.animate().cancel();
        travelingPoster.animate()
                .alpha(0f)
                .setDuration(105L)
                .withEndAction(() -> clearTravelPoster(true))
                .start();
    }

    private void clearTravelPoster(boolean deleteFile) {
        if (travelingPoster != null) {
            travelingPoster.animate().cancel();
            travelingPoster.setImageDrawable(null);
            try {
                if (travelingPoster.getParent() instanceof FrameLayout) {
                    ((FrameLayout) travelingPoster.getParent()).removeView(travelingPoster);
                }
            } catch (Exception ignored) {
            }
            travelingPoster = null;
        }
        if (deleteFile) deleteHandoffSnapshot();
    }

    private void showCardAnimated() {
        if (card == null) return;
        card.animate().cancel();
        card.setVisibility(View.VISIBLE);
        card.setAlpha(0f);
        card.setTranslationY(dp(14));
        card.setScaleX(0.985f);
        card.setScaleY(0.985f);
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(190L)
                .start();
    }

    private void showCardDirect() {
        if (card == null) return;
        card.animate().cancel();
        card.setVisibility(View.VISIBLE);
        card.setAlpha(1f);
        card.setTranslationX(0f);
        card.setTranslationY(0f);
        card.setScaleX(1f);
        card.setScaleY(1f);
    }

    private void hideCard() {
        if (card == null) return;
        card.animate().cancel();
        card.setVisibility(View.GONE);
        card.setAlpha(1f);
        card.setTranslationX(0f);
        card.setTranslationY(0f);
        card.setScaleX(1f);
        card.setScaleY(1f);
        if (progressFill != null) progressFill.setScaleX(0f);
    }

    private void showHandoffPoster(String path) {
        if (handoffPoster == null) return;
        handoffPoster.animate().cancel();
        handoffPoster.setAlpha(1f);
        handoffPoster.setImageDrawable(null);
        if (path == null || path.trim().isEmpty()) {
            handoffPoster.setVisibility(View.GONE);
            return;
        }
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null) {
                handoffPoster.setVisibility(View.GONE);
                return;
            }
            handoffPoster.setImageBitmap(bitmap);
            handoffPoster.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
            handoffPoster.setVisibility(View.GONE);
        }
    }

    private void fadeHandoffPoster() {
        if (handoffPoster == null || handoffPoster.getVisibility() != View.VISIBLE) {
            clearHandoffPoster(true);
            return;
        }
        handoffPoster.animate().cancel();
        handoffPoster.animate()
                .alpha(0f)
                .setDuration(115L)
                .withEndAction(() -> clearHandoffPoster(true))
                .start();
    }

    private void clearHandoffPoster(boolean deleteFile) {
        if (handoffPoster != null) {
            handoffPoster.animate().cancel();
            handoffPoster.setImageDrawable(null);
            handoffPoster.setAlpha(1f);
            handoffPoster.setVisibility(View.GONE);
        }
        if (deleteFile) deleteHandoffSnapshot();
    }

    private void deleteHandoffSnapshot() {
        if (handoffSnapshotPath == null || handoffSnapshotPath.isEmpty()) return;
        try {
            File file = new File(handoffSnapshotPath);
            if (file.getParentFile() != null && file.getParentFile().equals(activity.getCacheDir())) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
        handoffSnapshotPath = null;
    }

    private void ensureUi() {
        if (card != null) return;

        card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.rgb(24, 24, 28));
        card.setRadius(dp(18));
        card.setCardElevation(dp(15));
        card.setStrokeColor(Color.rgb(70, 68, 18));
        card.setStrokeWidth(dp(1));
        card.setVisibility(View.GONE);

        FrameLayout content = new FrameLayout(activity);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ROW_PAD_X_DP), dp(ROW_PAD_TOP_DP), dp(4), dp(5));
        content.addView(row, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout mediaFrame = new FrameLayout(activity);
        row.addView(mediaFrame, new LinearLayout.LayoutParams(dp(VIDEO_WIDTH_DP), dp(VIDEO_HEIGHT_DP)));

        playerView = new PlayerView(activity);
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setOnClickListener(v -> reopen());
        mediaFrame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        handoffPoster = new ImageView(activity);
        handoffPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        handoffPoster.setBackgroundColor(Color.BLACK);
        handoffPoster.setVisibility(View.GONE);
        handoffPoster.setOnClickListener(v -> reopen());
        mediaFrame.addView(handoffPoster, new FrameLayout.LayoutParams(-1, -1));

        titleView = new TextView(activity);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(13.5f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setPadding(dp(10), 0, dp(5), 0);
        titleView.setOnClickListener(v -> reopen());
        row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView expand = button("↗", "Open video details");
        expand.setOnClickListener(v -> reopen());
        row.addView(expand, new LinearLayout.LayoutParams(dp(38), dp(50)));

        TextView close = button("×", "Close mini-player");
        close.setOnClickListener(v -> stop());
        row.addView(close, new LinearLayout.LayoutParams(dp(38), dp(50)));

        View track = new View(activity);
        track.setBackgroundColor(Color.rgb(50, 50, 56));
        FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams(-1, dp(3));
        trackParams.gravity = Gravity.BOTTOM;
        content.addView(track, trackParams);

        progressFill = new View(activity);
        progressFill.setBackgroundColor(UiPalette.PRIMARY);
        progressFill.setScaleX(0f);
        progressFill.setPivotX(0f);
        FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(-1, dp(3));
        fillParams.gravity = Gravity.BOTTOM;
        content.addView(progressFill, fillParams);

        card.addView(content);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(CARD_HEIGHT_DP));
        params.gravity = Gravity.BOTTOM;
        params.setMargins(dp(CARD_SIDE_MARGIN_DP), 0, dp(CARD_SIDE_MARGIN_DP), dp(CARD_BOTTOM_MARGIN_DP));
        overlayRoot.addView(card, params);
    }

    private GradientDrawable roundedBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private TextView button(String text, String description) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(Color.rgb(235, 235, 240));
        view.setTextSize(21);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setClickable(true);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
