package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/** Scroll-away creator-hub header for the primary OnlyFap tab. */
final class OnlyFapHubHeaderAdapter
        extends RecyclerView.Adapter<OnlyFapHubHeaderAdapter.Holder> {

    interface Listener {
        void onSearch();
        void onFavorites();
        void onModeSelected(int mode);
        void onOpenCreator(NativeContentItem creator);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final Context context;
    private final Listener listener;
    private int mode;

    OnlyFapHubHeaderAdapter(Context context, int mode, Listener listener) {
        this.context = context;
        this.mode = mode;
        this.listener = listener;
        setHasStableIds(true);
    }

    void setMode(int mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        notifyItemChanged(0);
    }

    void refreshFavorites() {
        notifyItemChanged(0);
    }

    @Override
    public long getItemId(int position) {
        return 0x0F4F4E59L;
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout root = new LinearLayout(parent.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(14), dp(12), dp(8));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
        root.setLayoutParams(params);
        return new Holder(root);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LinearLayout root = holder.root;
        root.removeAllViews();

        TextView eyebrow = text("CREATOR HUB", 11f, UiPalette.PRIMARY);
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(-1, -2);
        eyebrowParams.setMargins(dp(4), 0, dp(4), dp(8));
        root.addView(eyebrow, eyebrowParams);

        root.addView(buildSearchRow(), new LinearLayout.LayoutParams(-1, dp(52)));

        List<NativeContentItem> favorites =
                CreatorCatalog.matching(context, "", true, 12);
        if (!favorites.isEmpty()) {
            root.addView(buildFavoriteSection(favorites),
                    new LinearLayout.LayoutParams(-1, -2));
        }

        root.addView(buildModes(), new LinearLayout.LayoutParams(-1, dp(46)));

        View caption = buildCaption();
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, dp(66));
        captionParams.setMargins(0, dp(7), 0, dp(4));
        root.addView(caption, captionParams);
    }

    private View buildSearchRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView search = text("Search OnlyFap creators", 15.5f,
                ZeroChillUi.color(context, R.color.zc_text_secondary));
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(BrowseUi.rounded(context,
                ZeroChillUi.color(context, R.color.zc_surface_glass), 16));
        search.setClickable(true);
        search.setFocusable(true);
        search.setContentDescription("Search OnlyFap creators");
        search.setOnClickListener(v -> listener.onSearch());
        ZeroChillMotion.installPressFeedback(search);
        row.addView(search, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView favorites = BrowseUi.action(
                context,
                "★",
                "Open favorite creators",
                v -> listener.onFavorites()
        );
        favorites.setTextSize(19f);
        LinearLayout.LayoutParams favoriteParams =
                new LinearLayout.LayoutParams(dp(52), dp(52));
        favoriteParams.setMarginStart(dp(8));
        row.addView(favorites, favoriteParams);
        return row;
    }

    private View buildFavoriteSection(List<NativeContentItem> favorites) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(15), 0, dp(8));

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("Favorite creators", 17f, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView viewAll = text("View all", 12.5f, UiPalette.PRIMARY);
        viewAll.setGravity(Gravity.CENTER);
        viewAll.setPadding(dp(10), dp(6), dp(4), dp(6));
        viewAll.setClickable(true);
        viewAll.setFocusable(true);
        viewAll.setContentDescription("View all favorite creators");
        viewAll.setOnClickListener(v -> listener.onFavorites());
        heading.addView(viewAll);
        section.addView(heading);

        TextView hint = text("Jump straight back into creators you saved", 11.5f,
                ZeroChillUi.color(context, R.color.zc_text_muted));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(1);
        section.addView(hint, hintParams);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        scroll.setContentDescription("Favorite creators rail");

        LinearLayout rail = new LinearLayout(context);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setGravity(Gravity.TOP);
        rail.setPadding(0, dp(10), dp(6), 0);

        int limit = Math.min(10, favorites.size());
        for (int i = 0; i < limit; i++) {
            NativeContentItem creator = favorites.get(i);
            rail.addView(creatorShortcut(creator, i),
                    new LinearLayout.LayoutParams(dp(88), dp(102)));
        }
        scroll.addView(rail, new HorizontalScrollView.LayoutParams(-2, -1));

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(-1, dp(108));
        section.addView(scroll, scrollParams);
        return section;
    }

    private View creatorShortcut(NativeContentItem creator, int index) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription("Open " + creator.title);
        wrapper.setOnClickListener(v -> listener.onOpenCreator(creator));
        ZeroChillMotion.installPressFeedback(wrapper);

        MaterialCardView avatarCard = new MaterialCardView(context);
        avatarCard.setRadius(dp(31));
        avatarCard.setCardElevation(0f);
        avatarCard.setStrokeWidth(0);
        avatarCard.setCardBackgroundColor(
                ZeroChillUi.color(context, R.color.zc_surface_pressed));

        FrameLayout frame = new FrameLayout(context);
        avatarCard.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        TextView initials = text(initials(creator.title), 18f,
                Color.argb(112, 255, 255, 255));
        initials.setTypeface(null, android.graphics.Typeface.BOLD);
        initials.setGravity(Gravity.CENTER);
        frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.TRANSPARENT);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loadAvatar(image, creator);

        wrapper.addView(avatarCard, new LinearLayout.LayoutParams(dp(62), dp(62)));

        TextView name = text(creator.title, 11.5f, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(dp(84), -2);
        nameParams.topMargin = dp(5);
        wrapper.addView(name, nameParams);

        CreatorGalleryPreloader.warm(
                context,
                creator,
                index < 4
                        ? CreatorGalleryPreloader.PRIORITY_HIGH
                        : CreatorGalleryPreloader.PRIORITY_NORMAL
        );
        return wrapper;
    }

    private View buildModes() {
        LinearLayout modes = new LinearLayout(context);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        modes.setPadding(0, dp(5), 0, dp(5));

        addMode(modes, "Trending", FapzoneCreatorRepository.MODE_TOP_50, 0);
        addMode(modes, "New", FapzoneCreatorRepository.MODE_NEW, 1);
        addMode(modes, "Hot", FapzoneCreatorRepository.MODE_HOT, 2);
        addMode(modes, "Popular", FapzoneCreatorRepository.MODE_POPULAR, 3);
        return modes;
    }

    private void addMode(LinearLayout row, String label, int value, int index) {
        TextView chip = text(label, 12.5f, Color.WHITE);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription("Show " + label + " OnlyFap creators");
        chip.setOnClickListener(v -> listener.onModeSelected(value));
        ZeroChillUi.styleChip(chip, mode == value);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(36), 1f);
        if (index > 0) params.setMarginStart(dp(3));
        if (index < 3) params.setMarginEnd(dp(3));
        row.addView(chip, params);
    }

    private View buildCaption() {
        LinearLayout caption = new LinearLayout(context);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(dp(16), dp(9), dp(14), dp(9));
        caption.setBackground(ZeroChillUi.panelGlass(context));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(FapzoneCreatorRepository.titleFor(mode), 17f, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);

        TextView hint = text(FapzoneCreatorRepository.hintFor(mode), 11.5f,
                ZeroChillUi.color(context, R.color.zc_text_secondary));
        hint.setMaxLines(1);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(1);
        copy.addView(hint, hintParams);
        caption.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView badge = text(FapzoneCreatorRepository.badgeFor(mode), 12f, Color.BLACK);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setContentDescription("Creator list mode");
        badge.setBackground(BrowseUi.rounded(context, UiPalette.PRIMARY, 15));
        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(dp(50), dp(30));
        badgeParams.setMarginStart(dp(10));
        caption.addView(badge, badgeParams);
        return caption;
    }

    private void loadAvatar(ImageView image, NativeContentItem creator) {
        if (creator == null || creator.imageUrl == null || creator.imageUrl.trim().isEmpty()) {
            image.setImageResource(R.drawable.ic_more_account);
            return;
        }
        String referer = creator.uploader != null &&
                (creator.uploader.startsWith("http://") || creator.uploader.startsWith("https://"))
                ? creator.uploader
                : creator.url;
        GlideUrl source = new GlideUrl(
                creator.imageUrl,
                new LazyHeaders.Builder()
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", referer == null ? "" : referer)
                        .build()
        );
        try {
            Glide.with(image)
                    .load(source)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .placeholder(new ColorDrawable(
                            ZeroChillUi.color(context, R.color.zc_surface_pressed)))
                    .error(R.drawable.ic_more_account)
                    .into(image);
        } catch (Exception ignored) {
            image.setImageResource(R.drawable.ic_more_account);
        }
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String initials(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return "?";
        String[] parts = clean.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(java.util.Locale.US);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(java.util.Locale.US);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout root;

        Holder(LinearLayout root) {
            super(root);
            this.root = root;
        }
    }
}
