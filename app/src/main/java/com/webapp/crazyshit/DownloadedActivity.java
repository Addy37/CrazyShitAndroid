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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.card.MaterialCardView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Local view of videos queued through Android's download service. */
public final class DownloadedActivity extends Activity {
    private static final long REFRESH_MS = 750L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private androidx.recyclerview.widget.RecyclerView recycler;
    private DownloadsAdapter downloadsAdapter;
    private final java.util.concurrent.ExecutorService loader = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean reading;
    private android.os.Parcelable restoredScroll;
    private EditText input;
    private TextView count;
    private TextView empty;
    private List<VideoDownloadStore.Entry> currentEntries = new ArrayList<>();
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
        if (state != null) {
            restoredScroll = state.getParcelable("scroll");
            input.setText(state.getString("query", ""));
            input.setSelection(input.length());
        }
        loader.execute(() -> VideoDownloadStore.recoverInterrupted(this));
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
        LinearLayout root = BrowseUi.screen(this);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView back = BrowseUi.action(this, "‹", "Back", v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            finish();
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = BrowseUi.text(this, "Downloads", 20, Color.WHITE);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        input = new EditText(this);
        input.setHint("Search Downloads");
        input.setHintTextColor(BrowseUi.MUTED);
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(BrowseUi.rounded(this, BrowseUi.SURFACE, 14));
        input.setContentDescription("Search Downloads");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(50));
        inputParams.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(input, inputParams);

        count = BrowseUi.text(this, "", 12, BrowseUi.MUTED);
        count.setPadding(dp(16), dp(4), dp(16), dp(8));
        root.addView(count);

        empty = BrowseUi.text(this, "", 15, BrowseUi.MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(32), dp(24), dp(24));
        empty.setVisibility(View.GONE);
        root.addView(empty);

        recycler = new androidx.recyclerview.widget.RecyclerView(this);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        recycler.setItemAnimator(null);
        recycler.setPadding(dp(10), dp(2), dp(10), dp(20));
        recycler.setClipToPadding(false);
        downloadsAdapter = new DownloadsAdapter();
        recycler.setAdapter(downloadsAdapter);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        input.addTextChangedListener(BrowseUi.onText(value -> showDownloads(currentEntries)));
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );
    }

    private void renderDownloads() {
        if (reading || isFinishing() || isDestroyed()) return;
        reading = true;
        loader.execute(() -> {
            List<VideoDownloadStore.Entry> entries = VideoDownloadStore.entries(this);
            main.post(() -> {
                reading = false;
                if (!polling || isFinishing() || isDestroyed()) return;
                showDownloads(entries);
            });
        });
    }

    private void showDownloads(List<VideoDownloadStore.Entry> entries) {
        currentEntries = entries == null
                ? new ArrayList<>()
                : new ArrayList<>(entries);

        String query = input == null ? "" : input.getText().toString();
        String state = stateOf(currentEntries) + "|query=" + LibrarySearch.normalize(query);
        if (state.equals(renderedState)) return;
        renderedState = state;

        List<VideoDownloadStore.Entry> filtered = new ArrayList<>();
        for (VideoDownloadStore.Entry entry : currentEntries) {
            if (LibrarySearch.matches(
                    query,
                    entry.title,
                    entry.pageUrl,
                    entry.mediaUrl,
                    VideoDownloadStore.statusText(entry)
            )) {
                filtered.add(entry);
            }
        }
        downloadsAdapter.replace(filtered);

        int active = 0;
        int ready = 0;
        for (VideoDownloadStore.Entry entry : currentEntries) {
            if (entry.status == DownloadManager.STATUS_SUCCESSFUL) ready++;
            else if (entry.status != DownloadManager.STATUS_FAILED) active++;
        }

        boolean searching = !LibrarySearch.normalize(query).isEmpty();
        if (searching) {
            count.setText(filtered.size() + " of " + currentEntries.size()
                    + (currentEntries.size() == 1 ? " download" : " downloads")
                    + " · Search results");
        } else {
            String summary = currentEntries.size()
                    + (currentEntries.size() == 1 ? " download" : " downloads");
            if (!currentEntries.isEmpty()) {
                summary += " · " + ready + (ready == 1 ? " saved" : " saved");
                if (active > 0) summary += " · " + active + " active";
            }
            count.setText(summary);
        }

        if (currentEntries.isEmpty()) {
            empty.setText("No downloads yet\n\nSave a video from its menu to keep it available offline.");
            empty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else if (filtered.isEmpty()) {
            empty.setText("No downloads match your search.");
            empty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            empty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }

        if (restoredScroll != null && recycler.getVisibility() == View.VISIBLE) {
            recycler.getLayoutManager().onRestoreInstanceState(restoredScroll);
            restoredScroll = null;
        }
    }

    private final class DownloadsAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<DownloadHolder> {
        private List<VideoDownloadStore.Entry> entries = new java.util.ArrayList<>();
        void replace(List<VideoDownloadStore.Entry> next) {
            List<VideoDownloadStore.Entry> old = entries;
            androidx.recyclerview.widget.DiffUtil.DiffResult diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
                    new androidx.recyclerview.widget.DiffUtil.Callback() {
                        public int getOldListSize() { return old.size(); }
                        public int getNewListSize() { return next.size(); }
                        public boolean areItemsTheSame(int a, int b) { return old.get(a).id == next.get(b).id; }
                        public boolean areContentsTheSame(int a, int b) {
                            return stateOf(java.util.Collections.singletonList(old.get(a)))
                                    .equals(stateOf(java.util.Collections.singletonList(next.get(b))));
                        }
                    });
            entries = new java.util.ArrayList<>(next);
            diff.dispatchUpdatesTo(this);
        }
        public int getItemCount() { return entries.size(); }
        public DownloadHolder onCreateViewHolder(android.view.ViewGroup parent, int type) {
            FrameLayout frame = new FrameLayout(DownloadedActivity.this);
            androidx.recyclerview.widget.RecyclerView.LayoutParams params =
                    new androidx.recyclerview.widget.RecyclerView.LayoutParams(-1, -2);
            params.setMargins(dp(5), dp(5), dp(5), dp(8));
            frame.setLayoutParams(params);
            return new DownloadHolder(frame);
        }
        public void onBindViewHolder(DownloadHolder holder, int position) {
            holder.frame.removeAllViews();
            holder.frame.addView(downloadCard(entries.get(position)), new FrameLayout.LayoutParams(-1, -2));
        }
    }

    private static final class DownloadHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        final FrameLayout frame;
        DownloadHolder(FrameLayout frame) { super(frame); this.frame = frame; }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        state.putString("query", input == null ? "" : input.getText().toString());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        loader.shutdownNow();
        super.onDestroy();
    }

    private View downloadCard(VideoDownloadStore.Entry entry) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription(entry.title + ". " + VideoDownloadStore.statusText(entry));
        wrapper.setOnClickListener(v -> VideoDownloadStore.open(this, entry));
        ZeroChillMotion.installPressFeedback(wrapper);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(18, 18, 21));
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeWidth(0);

        FrameLayout media = new FrameLayout(this);
        card.addView(media, new MaterialCardView.LayoutParams(-1, -1));
        media.addView(thumbnail(entry), new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.TRANSPARENT,
                        Color.argb(28, 0, 0, 0),
                        Color.argb(225, 0, 0, 0)
                }
        ));
        media.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        int statusColor = entry.status == DownloadManager.STATUS_FAILED
                ? Color.rgb(255, 130, 120)
                : entry.status == DownloadManager.STATUS_SUCCESSFUL
                ? UiPalette.PRIMARY
                : Color.WHITE;
        TextView status = overlayPill(VideoDownloadStore.statusText(entry), statusColor);
        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(-2, dp(24), Gravity.TOP | Gravity.START);
        statusParams.setMargins(dp(8), dp(8), 0, 0);
        media.addView(status, statusParams);

        TextView more = overflowButton("Download options for " + entry.title);
        more.setOnClickListener(v -> showDownloadMenu(v, entry));
        FrameLayout.LayoutParams moreParams =
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP | Gravity.END);
        moreParams.setMargins(0, dp(5), dp(5), 0);
        media.addView(more, moreParams);

        TextView title = text(entry.title, 13, Color.WHITE, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(34), dp(10));
        media.addView(title, titleParams);

        if (entry.status == DownloadManager.STATUS_RUNNING ||
                entry.status == DownloadManager.STATUS_PENDING ||
                entry.status == DownloadManager.STATUS_PAUSED) {
            FrameLayout track = new FrameLayout(this);
            track.setBackground(rounded(Color.argb(105, 255, 255, 255), dp(2)));
            FrameLayout.LayoutParams trackParams =
                    new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM);
            trackParams.setMargins(dp(8), 0, dp(8), dp(6));
            media.addView(track, trackParams);

            View fill = new View(this);
            fill.setBackground(rounded(UiPalette.PRIMARY, dp(2)));
            int percent = entry.totalBytes > 0L
                    ? (int) Math.min(100L, entry.downloadedBytes * 100L / entry.totalBytes)
                    : 8;
            int width = Math.max(dp(3), Math.round(dp(142) * (percent / 100f)));
            track.addView(fill, new FrameLayout.LayoutParams(width, -1));
        }

        wrapper.addView(card, new LinearLayout.LayoutParams(-1, dp(102)));

        TextView details = text(detailText(entry), 10, Color.rgb(137, 137, 147), false);
        details.setMaxLines(1);
        details.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(dp(3), dp(6), dp(3), dp(2));
        wrapper.addView(details, detailParams);
        return wrapper;
    }

    private TextView overlayPill(String value, int textColor) {
        TextView view = text(value, 9, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(rounded(Color.argb(185, 0, 0, 0), dp(12)));
        return view;
    }

    private TextView overflowButton(String description) {
        TextView view = text("⋮", 22, Color.WHITE, false);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(rounded(Color.argb(150, 0, 0, 0), dp(17)));
        return view;
    }

    private void showDownloadMenu(View anchor, VideoDownloadStore.Entry entry) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (entry.status == DownloadManager.STATUS_SUCCESSFUL) {
            menu.getMenu().add("Play");
        } else if (entry.status == DownloadManager.STATUS_FAILED) {
            menu.getMenu().add("Retry");
        } else if (entry.id < 0L && entry.status == DownloadManager.STATUS_PAUSED) {
            menu.getMenu().add("Resume");
        } else if (entry.id < 0L) {
            menu.getMenu().add("Pause");
        }
        menu.getMenu().add("Remove");

        menu.setOnMenuItemClickListener(item -> {
            String action = item.getTitle().toString();
            anchor.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if ("Play".equals(action)) {
                VideoDownloadStore.open(this, entry);
            } else if ("Retry".equals(action) || "Resume".equals(action)) {
                VideoDownloadStore.retry(this, entry);
            } else if ("Pause".equals(action)) {
                VideoDownloadStore.pause(this, entry);
            } else if ("Remove".equals(action)) {
                confirmRemove(entry);
            }
            return true;
        });
        menu.show();
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
