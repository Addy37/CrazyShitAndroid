package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.ArrayList;
import java.util.List;

/** In-app activity inbox for source updates. */
public final class UpdateInboxActivity extends Activity {
    private static final String FILTER_ALL = "";
    private static final String FILTER_CREATORS = UpdateInboxStore.CATEGORY_ONLYFAP;
    private static final String FILTER_APP = UpdateInboxStore.CATEGORY_APP;

    private RecyclerView recycler;
    private UpdateAdapter adapter;
    private TextView count;
    private TextView empty;
    private TextView allFilter;
    private TextView creatorsFilter;
    private TextView appFilter;
    private TextView markAll;
    private String activeFilter = FILTER_ALL;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) activeFilter = state.getString("filter", FILTER_ALL);
        buildUi();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("filter", activeFilter);
        super.onSaveInstanceState(state);
    }

    private void buildUi() {
        LinearLayout root = BrowseUi.screen(this);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(4));

        header.addView(
                BrowseUi.action(this, "‹", "Back", v -> finish()),
                new LinearLayout.LayoutParams(dp(48), dp(48))
        );

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, dp(8), 0);
        TextView title = BrowseUi.text(this, "Updates", 22, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titles.addView(title);
        count = BrowseUi.text(this, "", 11, BrowseUi.MUTED);
        titles.addView(count);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        markAll = BrowseUi.action(this, "Read all", "Mark all updates read", v -> {
            UpdateInboxStore.markAllRead(this);
            render();
        });
        markAll.setTextSize(12);
        header.addView(markAll, new LinearLayout.LayoutParams(dp(80), dp(44)));
        root.addView(header);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER);
        filters.setPadding(dp(12), dp(4), dp(12), dp(8));

        allFilter = filter("All", FILTER_ALL);
        creatorsFilter = filter("Creators", FILTER_CREATORS);
        appFilter = filter("App", FILTER_APP);
        filters.addView(allFilter, filterParams(0));
        filters.addView(creatorsFilter, filterParams(1));
        filters.addView(appFilter, filterParams(2));
        root.addView(filters);

        empty = BrowseUi.text(this, "", 15, BrowseUi.MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(48), dp(24), dp(24));
        root.addView(empty);

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setItemAnimator(null);
        adapter = new UpdateAdapter();
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        ZeroChillUi.applySystemBars(this);
    }

    private TextView filter(String label, String filter) {
        TextView view = BrowseUi.action(this, label, label + " updates", v -> {
            activeFilter = filter;
            render();
        });
        view.setTextSize(13);
        return view;
    }

    private LinearLayout.LayoutParams filterParams(int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        if (index == 0) params.setMargins(0, 0, dp(4), 0);
        else if (index == 1) params.setMargins(dp(2), 0, dp(2), 0);
        else params.setMargins(dp(4), 0, 0, 0);
        return params;
    }

    private void render() {
        int unread = UpdateInboxStore.unreadCount(this);
        List<UpdateInboxStore.Entry> items = UpdateInboxStore.filtered(this, activeFilter);
        adapter.replace(items);

        count.setText(unread == 0
                ? "You're caught up"
                : unread + (unread == 1 ? " unread update" : " unread updates"));
        markAll.setVisibility(unread == 0 ? View.INVISIBLE : View.VISIBLE);

        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(UpdateInboxStore.all(this).isEmpty()
                ? "No updates yet\n\nZEROCHILL will collect new content from your favorite creators here."
                : "No updates in this section.");

        styleFilter(allFilter, FILTER_ALL.equals(activeFilter));
        styleFilter(creatorsFilter, FILTER_CREATORS.equals(activeFilter));
        styleFilter(appFilter, FILTER_APP.equals(activeFilter));
    }

    private void styleFilter(TextView view, boolean selected) {
        view.setTextColor(selected ? Color.WHITE : BrowseUi.MUTED);
        view.setBackground(BrowseUi.rounded(
                this,
                selected ? UiPalette.PRIMARY_CONTAINER : BrowseUi.SURFACE,
                14
        ));
    }

    private void open(UpdateInboxStore.Entry entry) {
        UpdateInboxStore.markRead(this, entry.id);
        render();

        if (UpdateInboxStore.CATEGORY_APP.equals(entry.category)) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_CHECK_FOR_UPDATES, true);
            startActivity(intent);
            return;
        }

        if (UpdateInboxStore.CATEGORY_ONLYFAP.equals(entry.category) &&
                entry.creatorName != null && !entry.creatorName.trim().isEmpty()) {
            Intent intent = NativeFeedBrowserActivity.createCreatorGallery(
                    this,
                    entry.creatorName,
                    entry.creatorName,
                    FapelloRepository.isModelUrl(entry.fapelloProfileUrl)
                            ? entry.fapelloProfileUrl
                            : ""
            );
            intent.putStringArrayListExtra(
                    NativeFeedBrowserActivity.EXTRA_NOTIFICATION_FRESH_URLS,
                    new ArrayList<>(entry.freshUrls)
            );
            startActivity(intent);
            return;
        }

        NativeContentItem item = entry.firstItem();
        if (item != null && item.url != null && !item.url.trim().isEmpty()) {
            Intent intent = new Intent(this, WebFallbackActivity.class);
            intent.putExtra(WebFallbackActivity.EXTRA_URL, item.url);
            startActivity(intent);
        }
    }

    private int dp(int value) {
        return BrowseUi.dp(this, value);
    }

    private final class UpdateAdapter extends RecyclerView.Adapter<UpdateAdapter.Holder> {
        private final ArrayList<UpdateInboxStore.Entry> items = new ArrayList<>();

        void replace(List<UpdateInboxStore.Entry> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(UpdateInboxActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(10), dp(10));
            row.setBackground(BrowseUi.rounded(UpdateInboxActivity.this, BrowseUi.SURFACE, 16));
            row.setFocusable(true);
            row.setClickable(true);

            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(dp(12), dp(4), dp(12), dp(4));
            row.setLayoutParams(params);

            ImageView avatar = new ImageView(UpdateInboxActivity.this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setClipToOutline(true);
            avatar.setBackground(BrowseUi.rounded(UpdateInboxActivity.this, BrowseUi.SURFACE, 26));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

            LinearLayout labels = new LinearLayout(UpdateInboxActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12), 0, dp(8), 0);

            TextView title = BrowseUi.text(UpdateInboxActivity.this, "", 16, Color.WHITE);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(title);

            TextView subtitle = BrowseUi.text(UpdateInboxActivity.this, "", 12, BrowseUi.MUTED);
            subtitle.setMaxLines(2);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setPadding(0, dp(2), 0, 0);
            labels.addView(subtitle);

            TextView time = BrowseUi.text(UpdateInboxActivity.this, "", 11, BrowseUi.MUTED);
            time.setPadding(0, dp(3), 0, 0);
            labels.addView(time);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView unread = BrowseUi.text(UpdateInboxActivity.this, "NEW", 10, UiPalette.PRIMARY);
            unread.setTypeface(null, android.graphics.Typeface.BOLD);
            unread.setGravity(Gravity.CENTER);
            unread.setPadding(dp(6), dp(3), dp(6), dp(3));
            unread.setBackground(BrowseUi.rounded(
                    UpdateInboxActivity.this,
                    UiPalette.PRIMARY_CONTAINER,
                    10
            ));
            row.addView(unread, new LinearLayout.LayoutParams(-2, dp(26)));

            return new Holder(row, avatar, title, subtitle, time, unread);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            UpdateInboxStore.Entry entry = items.get(position);
            holder.title.setText(entry.title);
            String detail = entry.subtitle + (entry.sourceLabel.isEmpty() ? "" : "  •  " + entry.sourceLabel);
            if (UpdateInboxStore.CATEGORY_ONLYFAP.equals(entry.category)) {
                detail += "  •  Tap to see marked new items";
            }
            holder.subtitle.setText(detail);
            holder.time.setText(relativeTime(entry.timestamp));
            holder.unread.setVisibility(entry.read ? View.INVISIBLE : View.VISIBLE);
            holder.itemView.setAlpha(entry.read ? 0.76f : 1f);
            holder.itemView.setContentDescription(
                    entry.title + ". " + entry.subtitle +
                            (entry.read ? ". Read." : ". New update.")
            );
            holder.itemView.setOnClickListener(v -> open(entry));

            Glide.with(holder.avatar).clear(holder.avatar);
            int fallback = UpdateInboxStore.CATEGORY_ONLYFAP.equals(entry.category)
                    ? R.drawable.ic_launcher_legacy
                    : R.drawable.ic_more_update;
            holder.avatar.setImageResource(fallback);

            String imageUrl = entry.avatarUrl;
            String referer = entry.avatarReferer;
            if ((imageUrl == null || imageUrl.trim().isEmpty()) && entry.firstItem() != null) {
                NativeContentItem first = entry.firstItem();
                imageUrl = first.imageUrl;
                referer = first.uploader == null || first.uploader.trim().isEmpty()
                        ? first.url
                        : first.uploader;
            }
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                LazyHeaders.Builder headers = new LazyHeaders.Builder()
                        .addHeader(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139.0 Mobile Safari/537.36"
                        );
                if (referer != null && !referer.trim().isEmpty()) {
                    headers.addHeader("Referer", referer.trim());
                }
                GlideUrl model = new GlideUrl(imageUrl.trim(), headers.build());
                Glide.with(holder.avatar)
                        .load(model)
                        .circleCrop()
                        .dontAnimate()
                        .placeholder(fallback)
                        .error(fallback)
                        .into(holder.avatar);
            }
        }

        @Override
        public void onViewRecycled(Holder holder) {
            Glide.with(holder.avatar).clear(holder.avatar);
            super.onViewRecycled(holder);
        }

        private String relativeTime(long timestamp) {
            long delta = Math.max(0L, System.currentTimeMillis() - timestamp);
            long minutes = delta / 60_000L;
            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + (minutes == 1 ? " min ago" : " mins ago");
            long hours = minutes / 60L;
            if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
            long days = hours / 24L;
            if (days < 7) return days + (days == 1 ? " day ago" : " days ago");
            return (days / 7L) + ((days / 7L) == 1 ? " week ago" : " weeks ago");
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView title;
            final TextView subtitle;
            final TextView time;
            final TextView unread;

            Holder(
                    LinearLayout row,
                    ImageView avatar,
                    TextView title,
                    TextView subtitle,
                    TextView time,
                    TextView unread
            ) {
                super(row);
                this.avatar = avatar;
                this.title = title;
                this.subtitle = subtitle;
                this.time = time;
                this.unread = unread;
            }
        }
    }
}
