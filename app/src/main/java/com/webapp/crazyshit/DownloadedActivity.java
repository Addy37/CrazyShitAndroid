package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.card.MaterialCardView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Local view of videos queued through Android's download service. */
public final class DownloadedActivity extends Activity {
    private static final long REFRESH_MS = 750L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView subtitle;
    private boolean polling;
    private String renderedState = "";

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            renderDownloads();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        renderedState = "";
        main.removeCallbacks(refresh);
        main.post(refresh);
    }

    @Override
    protected void onPause() {
        polling = false;
        main.removeCallbacks(refresh);
        super.onPause();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 13, 15));
        root.setPadding(dp(16), dp(14), dp(16), dp(22));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 34, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(circle(Color.rgb(30, 30, 35)));
        back.setContentDescription("Back");
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            finish();
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(text("Downloads", 26, Color.WHITE, true));
        subtitle = text("Saved videos and active downloads", 12, Color.rgb(166, 166, 176), false);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(62)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8), 0, dp(24));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private void renderDownloads() {
        List<VideoDownloadStore.Entry> entries = VideoDownloadStore.entries(this);
        String state = stateOf(entries);
        if (state.equals(renderedState)) return;
        renderedState = state;

        list.removeAllViews();
        int active = 0;
        int ready = 0;
        for (VideoDownloadStore.Entry entry : entries) {
            if (entry.status == DownloadManager.STATUS_SUCCESSFUL) ready++;
            else if (entry.status != DownloadManager.STATUS_FAILED) active++;
        }
        if (entries.isEmpty()) {
            subtitle.setText("Saved videos and active downloads");
            addEmptyState();
            return;
        }
        String summary = ready + (ready == 1 ? " saved video" : " saved videos");
        if (active > 0) {
            summary += "  •  " + active +
                    (active == 1 ? " active download" : " active downloads");
        }
        subtitle.setText(summary);
        for (VideoDownloadStore.Entry entry : entries) {
            list.addView(downloadCard(entry), cardParams());
        }
    }

    private View downloadCard(VideoDownloadStore.Entry entry) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(23, 23, 27));
        card.setRadius(dp(18));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.rgb(46, 46, 53));
        card.setCardElevation(0f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(8), dp(8), dp(9), dp(8));

        row.addView(thumbnail(entry), new LinearLayout.LayoutParams(dp(126), dp(82)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), dp(3), 0, 0);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = text(entry.title, 15, Color.WHITE, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        int statusColor = entry.status == DownloadManager.STATUS_FAILED
                ? Color.rgb(255, 130, 120)
                : entry.status == DownloadManager.STATUS_SUCCESSFUL
                ? UiPalette.PRIMARY
                : Color.rgb(205, 205, 214);
        TextView status = text(VideoDownloadStore.statusText(entry), 12, statusColor, true);
        status.setPadding(0, dp(5), 0, 0);
        copy.addView(status, new LinearLayout.LayoutParams(-1, -2));

        if (entry.status == DownloadManager.STATUS_RUNNING ||
                entry.status == DownloadManager.STATUS_PENDING ||
                entry.status == DownloadManager.STATUS_PAUSED) {
            ProgressBar progress = new ProgressBar(
                    this,
                    null,
                    android.R.attr.progressBarStyleHorizontal
            );
            progress.setMax(100);
            progress.setIndeterminate(entry.totalBytes <= 0L);
            if (entry.totalBytes > 0L) {
                progress.setProgress((int) Math.min(
                        100L,
                        entry.downloadedBytes * 100L / entry.totalBytes
                ));
            }
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(4));
            progressParams.setMargins(0, dp(6), 0, dp(2));
            copy.addView(progress, progressParams);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(4), 0, 0);

        String detail = detailText(entry);
        TextView details = text(detail, 10, Color.rgb(137, 137, 147), false);
        details.setMaxLines(1);
        details.setEllipsize(TextUtils.TruncateAt.END);
        footer.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));

        if (entry.status == DownloadManager.STATUS_SUCCESSFUL) {
            footer.addView(action("PLAY", () -> VideoDownloadStore.open(this, entry)),
                    new LinearLayout.LayoutParams(-2, dp(36)));
        } else if (entry.status == DownloadManager.STATUS_FAILED) {
            footer.addView(action("RETRY", () -> {
                VideoDownloadStore.remove(this, entry);
                VideoDownloadStore.downloadKnown(
                        this,
                        entry.title,
                        entry.pageUrl,
                        entry.imageUrl,
                        entry.mediaUrl,
                        "",
                        ""
                );
                renderedState = "";
                renderDownloads();
            }), new LinearLayout.LayoutParams(-2, dp(36)));
        }
        footer.addView(action("REMOVE", () -> confirmRemove(entry)),
                new LinearLayout.LayoutParams(-2, dp(36)));
        copy.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(entry.title + ". " + VideoDownloadStore.statusText(entry));
        row.setOnClickListener(v -> VideoDownloadStore.open(this, entry));
        card.addView(row);
        return card;
    }

    private View thumbnail(VideoDownloadStore.Entry entry) {
        MaterialCardView frame = new MaterialCardView(this);
        frame.setRadius(dp(12));
        frame.setCardBackgroundColor(Color.rgb(34, 34, 39));
        frame.setCardElevation(0f);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.BLACK);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        if (!entry.imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(entry.imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .centerCrop()
                    .into(image);
        } else {
            image.setImageResource(R.drawable.ic_action_download);
            image.setColorFilter(UiPalette.PRIMARY);
            image.setPadding(dp(34), dp(18), dp(34), dp(18));
        }
        return frame;
    }

    private TextView action(String label, Runnable run) {
        TextView view = text(label, 10, UiPalette.PRIMARY, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), 0, dp(9), 0);
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            run.run();
        });
        return view;
    }

    private void confirmRemove(VideoDownloadStore.Entry entry) {
        String message = entry.status == DownloadManager.STATUS_SUCCESSFUL
                ? "Remove this downloaded video from the device?"
                : "Cancel and remove this download?";
        new AlertDialog.Builder(this)
                .setTitle("Remove download")
                .setMessage(message)
                .setNegativeButton("Keep", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    VideoDownloadStore.remove(this, entry);
                    renderedState = "";
                    renderDownloads();
                })
                .show();
    }

    private void addEmptyState() {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(54), dp(24), dp(54));
        GradientDrawable background = rounded(Color.rgb(23, 23, 27), dp(20));
        background.setStroke(dp(1), Color.rgb(45, 45, 52));
        empty.setBackground(background);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_action_download);
        icon.setColorFilter(UiPalette.PRIMARY);
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        icon.setBackground(circle(UiPalette.PRIMARY_CONTAINER));
        empty.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView title = text("No downloads yet", 18, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, 0);
        empty.addView(title);

        TextView body = text(
                "Use Download from any video menu to save a clip for offline playback.",
                12,
                Color.rgb(166, 166, 176),
                false
        );
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(18), dp(7), dp(18), 0);
        empty.addView(body);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(18), 0, 0);
        list.addView(empty, params);
    }

    private String stateOf(List<VideoDownloadStore.Entry> entries) {
        StringBuilder state = new StringBuilder();
        for (VideoDownloadStore.Entry entry : entries) {
            state.append(entry.id).append(':')
                    .append(entry.status).append(':')
                    .append(entry.downloadedBytes).append(':')
                    .append(entry.totalBytes).append(';');
        }
        return state.toString();
    }

    private String detailText(VideoDownloadStore.Entry entry) {
        String date = entry.createdAt <= 0L
                ? ""
                : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(entry.createdAt));
        long bytes = entry.status == DownloadManager.STATUS_SUCCESSFUL
                ? Math.max(entry.totalBytes, entry.downloadedBytes)
                : entry.downloadedBytes;
        String size = bytes > 0L ? Formatter.formatShortFileSize(this, bytes) : "";
        if (date.isEmpty()) return size;
        if (size.isEmpty()) return date;
        return size + "  •  " + date;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
