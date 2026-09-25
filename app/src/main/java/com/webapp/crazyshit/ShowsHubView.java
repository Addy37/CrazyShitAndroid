package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Nuvio-inspired proof-of-concept landing page for the Shows tab. */
final class ShowsHubView extends FrameLayout {
    interface Listener {
        void onOpen(NativeContentItem item);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final Listener listener;
    private final LinearLayout content;
    private final ImageView heroImage;
    private final TextView heroSource;
    private final TextView heroTitle;
    private final TextView heroHint;
    private final TextView heroAction;
    private final TextView loadingLabel;
    private final Shelf crazyShelf;
    private final Shelf efuktShelf;
    private final Shelf categoryShelf;

    private List<NativeContentItem> crazyItems = Collections.emptyList();
    private List<NativeContentItem> efuktItems = Collections.emptyList();
    private List<NativeContentItem> categoryItems = Collections.emptyList();
    private NativeContentItem heroItem;

    ShowsHubView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(ZeroChillUi.background(context));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(66), dp(12), dp(34));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        TextView eyebrow = text("ZEROCHILL SHOWS", 11f,
                ZeroChillUi.color(context, R.color.zc_cyan));
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(-1, -2);
        eyebrowParams.setMargins(dp(4), dp(8), dp(4), dp(7));
        content.addView(eyebrow, eyebrowParams);

        MaterialCardView heroCard = new MaterialCardView(context);
        ZeroChillUi.styleMaterialCard(heroCard, R.dimen.zc_radius_large);
        heroCard.setRadius(dp(22));
        heroCard.setCardElevation(0f);
        heroCard.setStrokeWidth(dp(1));
        heroCard.setStrokeColor(ZeroChillUi.color(context, R.color.zc_edge));
        heroCard.setClickable(true);
        heroCard.setFocusable(true);
        ZeroChillMotion.installPressFeedback(heroCard);

        FrameLayout heroFrame = new FrameLayout(context);
        heroCard.addView(heroFrame, new MaterialCardView.LayoutParams(-1, -1));

        heroImage = new ImageView(context);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackground(new ColorDrawable(Color.rgb(13, 16, 19)));
        heroFrame.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));

        View heroShade = new View(context);
        heroShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(18, 0, 0, 0),
                        Color.argb(64, 0, 0, 0),
                        Color.argb(246, 0, 0, 0)
                }
        ));
        heroFrame.addView(heroShade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout heroCopy = new LinearLayout(context);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setGravity(Gravity.BOTTOM);
        heroCopy.setPadding(dp(18), dp(18), dp(18), dp(18));
        heroFrame.addView(heroCopy,
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        heroSource = text("FEATURED", 11f, UiPalette.PRIMARY);
        heroSource.setTypeface(null, android.graphics.Typeface.BOLD);
        heroSource.setLetterSpacing(0.08f);
        heroCopy.addView(heroSource);

        heroTitle = text("A new way to browse Shows", 27f, Color.WHITE);
        heroTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams heroTitleParams = new LinearLayout.LayoutParams(-1, -2);
        heroTitleParams.topMargin = dp(4);
        heroCopy.addView(heroTitle, heroTitleParams);

        heroHint = text(
                "CrazyShit, EFukt and Categories together in one media hub.",
                13f,
                ZeroChillUi.color(context, R.color.zc_text_secondary)
        );
        heroHint.setMaxLines(2);
        heroHint.setLineSpacing(0f, 1.06f);
        LinearLayout.LayoutParams heroHintParams = new LinearLayout.LayoutParams(-1, -2);
        heroHintParams.topMargin = dp(5);
        heroCopy.addView(heroHint, heroHintParams);

        heroAction = text("VIEW SHOW", 13f, Color.BLACK);
        heroAction.setTypeface(null, android.graphics.Typeface.BOLD);
        heroAction.setGravity(Gravity.CENTER);
        heroAction.setVisibility(View.GONE);
        GradientDrawable actionBackground = new GradientDrawable();
        actionBackground.setColor(UiPalette.PRIMARY);
        actionBackground.setCornerRadius(dp(19));
        heroAction.setBackground(actionBackground);
        LinearLayout.LayoutParams heroActionParams =
                new LinearLayout.LayoutParams(dp(116), dp(38));
        heroActionParams.topMargin = dp(13);
        heroCopy.addView(heroAction, heroActionParams);

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(258));
        heroParams.setMargins(0, 0, 0, dp(18));
        content.addView(heroCard, heroParams);

        loadingLabel = text(
                "Loading Shows…",
                12f,
                ZeroChillUi.color(context, R.color.zc_text_muted)
        );
        loadingLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
        loadingParams.setMargins(0, 0, 0, dp(10));
        content.addView(loadingLabel, loadingParams);

        crazyShelf = addShelf("CrazyShit Shows", "Series and recurring collections", false);
        efuktShelf = addShelf("EFukt Series", "Browse EFukt by series", false);
        categoryShelf = addShelf("Categories", "Jump into a type of content", true);

        heroCard.setOnClickListener(v -> openHero());
        heroAction.setOnClickListener(v -> openHero());
    }

    void clear() {
        crazyItems = Collections.emptyList();
        efuktItems = Collections.emptyList();
        categoryItems = Collections.emptyList();
        heroItem = null;
        crazyShelf.adapter.replace(Collections.emptyList());
        efuktShelf.adapter.replace(Collections.emptyList());
        categoryShelf.adapter.replace(Collections.emptyList());
        crazyShelf.container.setVisibility(View.GONE);
        efuktShelf.container.setVisibility(View.GONE);
        categoryShelf.container.setVisibility(View.GONE);
        loadingLabel.setText("Loading Shows…");
        loadingLabel.setVisibility(View.VISIBLE);
        heroSource.setText("FEATURED");
        heroTitle.setText("A new way to browse Shows");
        heroHint.setText("CrazyShit, EFukt and Categories together in one media hub.");
        heroAction.setVisibility(View.GONE);
        Glide.with(heroImage).clear(heroImage);
        heroImage.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
    }

    void setCrazyShit(List<NativeContentItem> items) {
        crazyItems = safe(items);
        crazyShelf.adapter.replace(crazyItems);
        crazyShelf.container.setVisibility(crazyItems.isEmpty() ? View.GONE : View.VISIBLE);
        updateHero();
    }

    void setEfukt(List<NativeContentItem> items) {
        efuktItems = safe(items);
        efuktShelf.adapter.replace(efuktItems);
        efuktShelf.container.setVisibility(efuktItems.isEmpty() ? View.GONE : View.VISIBLE);
        updateHero();
    }

    void setCategories(List<NativeContentItem> items) {
        categoryItems = safe(items);
        categoryShelf.adapter.replace(categoryItems);
        categoryShelf.container.setVisibility(categoryItems.isEmpty() ? View.GONE : View.VISIBLE);
        updateHero();
    }

    void finishLoading() {
        loadingLabel.setVisibility(itemCount() == 0 ? View.VISIBLE : View.GONE);
        if (itemCount() == 0) {
            loadingLabel.setText("Shows could not load right now. Try one of the source tabs above.");
        }
    }

    int itemCount() {
        return crazyItems.size() + efuktItems.size() + categoryItems.size();
    }

    private void updateHero() {
        if (heroItem != null) {
            if (itemCount() > 0) loadingLabel.setVisibility(View.GONE);
            return;
        }

        NativeContentItem candidate = firstWithArtwork(crazyItems);
        String source = "CRAZYSHIT";
        String hint = "Featured from CrazyShit Shows";

        if (candidate == null) {
            candidate = firstWithArtwork(efuktItems);
            source = "EFUKT";
            hint = "Featured from EFukt Series";
        }
        if (candidate == null) {
            candidate = firstWithArtwork(categoryItems);
            source = "CATEGORY";
            hint = "Featured category";
        }
        if (candidate == null) return;

        heroItem = candidate;
        heroSource.setText(source);
        heroTitle.setText(candidate.title);
        heroHint.setText(hint);
        heroAction.setVisibility(View.VISIBLE);
        loadArtwork(heroImage, candidate, true);
        loadingLabel.setVisibility(View.GONE);
    }

    private NativeContentItem firstWithArtwork(List<NativeContentItem> items) {
        if (items == null || items.isEmpty()) return null;
        for (NativeContentItem item : items) {
            if (item == null) continue;
            if (EmbeddedBrowseArtwork.has(getContext(), item.url)) return item;
            if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) return item;
        }
        return items.get(0);
    }

    private void openHero() {
        if (heroItem != null && listener != null) listener.onOpen(heroItem);
    }

    private Shelf addShelf(String title, String subtitle, boolean wideCards) {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
        blockParams.setMargins(0, 0, 0, dp(20));
        content.addView(block, blockParams);

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(3), 0, dp(3), dp(8));

        TextView titleView = text(title, 20f, Color.WHITE);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.addView(titleView);

        TextView subtitleView = text(
                subtitle,
                12f,
                ZeroChillUi.color(getContext(), R.color.zc_text_secondary)
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(1);
        heading.addView(subtitleView, subtitleParams);
        block.addView(heading);

        RecyclerView rail = new RecyclerView(getContext());
        rail.setClipToPadding(false);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayoutManager manager =
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false);
        manager.setInitialPrefetchItemCount(5);
        rail.setLayoutManager(manager);

        RailAdapter adapter = new RailAdapter(wideCards);
        rail.setAdapter(adapter);
        rail.setPadding(dp(2), 0, dp(22), 0);
        block.addView(rail, new LinearLayout.LayoutParams(
                -1,
                dp(wideCards ? 150 : 218)
        ));

        block.setVisibility(View.GONE);
        return new Shelf(block, adapter);
    }

    private void loadArtwork(ImageView view, NativeContentItem item, boolean hero) {
        byte[] embedded = EmbeddedBrowseArtwork.get(getContext(), item.url);
        if (embedded != null && embedded.length >= 512) {
            Glide.with(view)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .centerCrop()
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.rgb(20, 22, 25)))
                    .error(new ColorDrawable(Color.rgb(20, 22, 25)))
                    .into(view);
            return;
        }

        String imageUrl = item.imageUrl == null ? "" : item.imageUrl.trim();
        if (imageUrl.isEmpty()) {
            Glide.with(view).clear(view);
            view.setImageDrawable(new ColorDrawable(Color.rgb(20, 22, 25)));
            return;
        }

        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT);
        if (item.url != null && item.url.startsWith("http")) {
            headers.addHeader("Referer", item.url);
        }

        Glide.with(view)
                .load(new GlideUrl(imageUrl, headers.build()))
                .diskCacheStrategy(hero ? DiskCacheStrategy.ALL : DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .dontAnimate()
                .placeholder(new ColorDrawable(Color.rgb(20, 22, 25)))
                .error(new ColorDrawable(Color.rgb(20, 22, 25)))
                .into(view);
    }

    private List<NativeContentItem> safe(List<NativeContentItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(items);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class RailAdapter extends RecyclerView.Adapter<RailHolder> {
        private final boolean wide;
        private final List<NativeContentItem> items = new ArrayList<>();

        RailAdapter(boolean wide) {
            this.wide = wide;
            setHasStableIds(true);
        }

        void replace(List<NativeContentItem> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            NativeContentItem item = items.get(position);
            return (item.kind + "\n" + item.url + "\n" + item.title).hashCode();
        }

        @NonNull
        @Override
        public RailHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(getContext());
            ZeroChillUi.styleMaterialCard(card, R.dimen.zc_radius_medium);
            card.setRadius(dp(16));
            card.setCardElevation(0f);
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(ZeroChillUi.color(getContext(), R.color.zc_edge));
            card.setClickable(true);
            card.setFocusable(true);
            ZeroChillMotion.installPressFeedback(card);

            int width = dp(wide ? 205 : 146);
            int height = dp(wide ? 132 : 202);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(width, height);
            params.setMargins(dp(3), dp(2), dp(8), dp(4));
            card.setLayoutParams(params);

            FrameLayout frame = new FrameLayout(getContext());
            card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

            View shade = new View(getContext());
            shade.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {
                            Color.argb(0, 0, 0, 0),
                            Color.argb(36, 0, 0, 0),
                            Color.argb(235, 0, 0, 0)
                    }
            ));
            frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

            TextView title = text("", wide ? 14f : 14.5f, Color.WHITE);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setGravity(Gravity.BOTTOM);
            title.setPadding(dp(11), dp(8), dp(11), dp(10));
            frame.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

            return new RailHolder(card, image, title);
        }

        @Override
        public void onBindViewHolder(@NonNull RailHolder holder, int position) {
            NativeContentItem item = items.get(position);
            holder.title.setText(item.title);
            holder.card.setContentDescription("Open " + item.title);
            holder.card.setOnClickListener(v -> {
                if (listener != null) listener.onOpen(item);
            });
            loadArtwork(holder.image, item, false);
        }

        @Override
        public void onViewRecycled(@NonNull RailHolder holder) {
            Glide.with(holder.image).clear(holder.image);
            holder.card.setOnClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class Shelf {
        final LinearLayout container;
        final RailAdapter adapter;

        Shelf(LinearLayout container, RailAdapter adapter) {
            this.container = container;
            this.adapter = adapter;
        }
    }

    private static final class RailHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView title;

        RailHolder(MaterialCardView card, ImageView image, TextView title) {
            super(card);
            this.card = card;
            this.image = image;
            this.title = title;
        }
    }
}
