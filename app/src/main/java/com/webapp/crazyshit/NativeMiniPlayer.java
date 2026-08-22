package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.google.android.material.card.MaterialCardView;

import java.util.HashMap;
import java.util.Map;

@UnstableApi
public final class NativeMiniPlayer {
    public interface Host {
        void reopenMiniPlayer(Intent intent);
    }

    private final Activity activity;
    private final FrameLayout overlayRoot;
    private final Host host;

    private MaterialCardView card;
    private PlayerView playerView;
    private TextView titleView;
    private ExoPlayer player;
    private String mediaUrl;
    private String pageUrl;
    private String title;
    private String userAgent;
    private String cookies;
    private boolean resumeAfterPause;

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
        long start = data.getLongExtra(PlayerActivity.EXTRA_START_POSITION, 0L);
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) return;

        ensureUi();
        titleView.setText(title == null || title.trim().isEmpty() ? "Video" : title);

        try {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
            if (userAgent != null && !userAgent.isEmpty()) http.setUserAgent(userAgent);

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
            resumeAfterPause = true;
            card.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            stop();
            Toast.makeText(activity, "Couldn't start the mini-player.", Toast.LENGTH_SHORT).show();
        }
    }

    public void onPause() {
        if (player == null) return;
        resumeAfterPause = player.isPlaying();
        player.pause();
    }

    public void onResume() {
        if (player != null && resumeAfterPause) player.play();
    }

    public void stop() {
        resumeAfterPause = false;
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        if (card != null) card.setVisibility(View.GONE);
        mediaUrl = null;
        pageUrl = null;
        title = null;
        userAgent = null;
        cookies = null;
    }

    private void reopen() {
        if (player == null || mediaUrl == null) return;
        long position = player.getCurrentPosition();
        Intent intent = new Intent(activity, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, mediaUrl);
        intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, pageUrl);
        intent.putExtra(PlayerActivity.EXTRA_TITLE, title);
        intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, userAgent);
        intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
        intent.putExtra(PlayerActivity.EXTRA_START_POSITION, position);
        stop();
        host.reopenMiniPlayer(intent);
    }

    private void ensureUi() {
        if (card != null) return;

        card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.rgb(24, 24, 28));
        card.setRadius(dp(20));
        card.setCardElevation(dp(12));
        card.setStrokeColor(Color.rgb(60, 60, 68));
        card.setStrokeWidth(dp(1));
        card.setVisibility(View.GONE);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));

        playerView = new PlayerView(activity);
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setOnClickListener(v -> reopen());
        row.addView(playerView, new LinearLayout.LayoutParams(dp(124), dp(72)));

        titleView = new TextView(activity);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(14);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setPadding(dp(12), 0, dp(8), 0);
        titleView.setOnClickListener(v -> reopen());
        row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView expand = button("↗", "Open full player");
        expand.setOnClickListener(v -> reopen());
        row.addView(expand, new LinearLayout.LayoutParams(dp(42), dp(54)));

        TextView close = button("×", "Close mini-player");
        close.setOnClickListener(v -> stop());
        row.addView(close, new LinearLayout.LayoutParams(dp(42), dp(54)));

        card.addView(row);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(84));
        params.gravity = Gravity.BOTTOM;
        params.setMargins(dp(10), 0, dp(10), dp(88));
        overlayRoot.addView(card, params);
    }

    private TextView button(String text, String description) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(22);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setClickable(true);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
