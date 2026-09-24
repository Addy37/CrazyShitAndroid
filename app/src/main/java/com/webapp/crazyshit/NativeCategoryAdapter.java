package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;
import com.google.android.material.card.MaterialCardView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visual browser cards used by Categories and Collections. */
public final class NativeCategoryAdapter extends RecyclerView.Adapter<NativeCategoryAdapter.Holder> {
    private static final int VIEW_TYPE_STANDARD = 0;
    private static final int VIEW_TYPE_CREATOR_HERO = 1;
    private static final int VIEW_TYPE_CREATOR_FEATURED = 2;
    private static final int VIEW_TYPE_CREATOR_PORTRAIT = 3;
    private static final int VIEW_TYPE_CREATOR_SQUARE = 4;
    private static final int VIEW_TYPE_CREATOR_WIDE = 5;

    private static final int COMPACT_COPY_HEIGHT_DP = 52;
    private static final int DESCRIPTION_COPY_HEIGHT_DP = 132;
    private static final int CREATOR_COPY_HEIGHT_DP = 70;
    private static final int CREATOR_SHADE_HEIGHT_DP = 118;
    private static final String CREATOR_ARTWORK_PREFS = "creator_artwork_cache_v3";
    private static final float ASPECT_HERO = 16f / 9f;
    private static final float ASPECT_PORTRAIT = 4f / 5f;
    private static final float ASPECT_SQUARE = 1f;

    private static final DrawableCrossFadeFactory CREATOR_CROSS_FADE =
            new DrawableCrossFadeFactory.Builder(260).setCrossFadeEnabled(true).build();

    public interface Listener {
        void onOpen(NativeContentItem item);
    }

    private final Context appContext;
    private final List<NativeContentItem> items = new ArrayList<>();
    private final Listener listener;
    private final Map<String, Artwork> resolvedArtwork = new HashMap<>();
    private final Map<String, List<Artwork>> artworkCandidates = new HashMap<>();
    private final Map<String, Float> creatorAspectRatios = new HashMap<>();
    private final Set<String> requestedArtwork = new HashSet<>();
    private final Set<String> exhaustedArtwork = new HashSet<>();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bunkrArtworkIo = Executors.newFixedThreadPool(8);
    private boolean wideCreatorCards;
    private volatile boolean closed;

    public NativeCategoryAdapter(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        setHasStableIds(true);
    }

    public void replace(List<NativeContentItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        if (wideCreatorCards) restoreAllCreatorArtwork();
        notifyDataSetChanged();
    }

    public void setWideCreatorCards(boolean enabled) {
        if (wideCreatorCards == enabled) return;
        wideCreatorCards = enabled;
        if (enabled) restoreAllCreatorArtwork();
        notifyDataSetChanged();
    }

    public int creatorSpanSize(int position, int columnCount) {
        int columns = Math.max(1, columnCount);
        if (!wideCreatorCards || position < 0 || position >= items.size() ||
                !items.get(position).isCreator()) return 1;
        int viewType = getItemViewType(position);
        if (viewType == VIEW_TYPE_CREATOR_HERO) return columns;
        if (viewType == VIEW_TYPE_CREATOR_WIDE) return columns <= 2 ? columns : 2;
        return 1;
    }

    /** Browse artwork is bundled inside the APK, so no extra rendered artwork pass is needed. */
    public boolean hasMissingArtwork() {
        return false;
    }

    /** Kept for source compatibility with the pager while remote browse artwork is retired. */
    public void applyArtwork(Map<String, String> artwork) {
        // Intentionally empty. Series and Categories use EmbeddedBrowseArtwork.
    }

    public void close() {
        closed = true;
        bunkrArtworkIo.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        requestedArtwork.clear();
    }

    @Override
    public long getItemId(int position) {
        NativeContentItem item = items.get(position);
        return (item.kind + "\n" + item.title + "\n" + item.url).hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        if (!wideCreatorCards || !items.get(position).isCreator()) return VIEW_TYPE_STANDARD;
        if (position == 0) return VIEW_TYPE_CREATOR_HERO;
        if (position < 5) return VIEW_TYPE_CREATOR_FEATURED;

        int planned = plannedCreatorViewType(position);
        if (planned == VIEW_TYPE_CREATOR_WIDE) return VIEW_TYPE_CREATOR_WIDE;
        Float ratio = creatorAspectRatios.get(artworkKey(items.get(position)));
        if (ratio != null && ratio > 0f) {
            if (ratio <= 0.88f) return VIEW_TYPE_CREATOR_PORTRAIT;
            return VIEW_TYPE_CREATOR_SQUARE;
        }
        return planned;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean creatorCard = isCreatorViewType(viewType);
        MaterialCardView card = new MaterialCardView(parent.getContext());
        ZeroChillUi.styleMaterialCard(card, R.dimen.zc_radius_medium);
        card.setRadius(dp(parent, creatorCard ? 9 : 16));
        card.setCardElevation(dp(parent, creatorCard ? 1 : 2));
        card.setClickable(true);
        card.setFocusable(true);
        ZeroChillMotion.installPressFeedback(card);

        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                -1,
                dp(parent, creatorCard
                        ? creatorCardHeightDp(parent, viewType)
                        : responsiveHeightDp(parent))
        );
        int horizontalMargin = dp(parent, creatorCard ? 4 : 7);
        int verticalMargin = dp(parent, creatorCard ? 4 : 7);
        params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
        card.setLayoutParams(params);

        FrameLayout frame = new FrameLayout(parent.getContext());
        frame.setClipChildren(true);
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        TextView initials = new TextView(parent.getContext());
        initials.setGravity(Gravity.CENTER);
        initials.setTextColor(Color.argb(74, 255, 255, 255));
        initials.setTextSize(viewType == VIEW_TYPE_CREATOR_HERO ? 48f : 34f);
        initials.setTypeface(null, android.graphics.Typeface.BOLD);
        initials.setVisibility(creatorCard ? View.VISIBLE : View.GONE);
        frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));

        ImageView backdrop = new ImageView(parent.getContext());
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setScaleX(1.12f);
        backdrop.setScaleY(1.12f);
        backdrop.setAlpha(0.62f);
        backdrop.setVisibility(creatorCard ? View.VISIBLE : View.GONE);
        frame.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        View backdropTint = new View(parent.getContext());
        backdropTint.setBackgroundColor(Color.argb(92, 0, 0, 0));
        backdropTint.setVisibility(creatorCard ? View.VISIBLE : View.GONE);
        frame.addView(backdropTint, new FrameLayout.LayoutParams(-1, -1));

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(creatorCard
                ? ImageView.ScaleType.FIT_CENTER
                : ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.TRANSPARENT);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(parent.getContext());
        if (creatorCard) {
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{
                            Color.TRANSPARENT,
                            Color.argb(78, 0, 0, 0),
                            Color.argb(238, 0, 0, 0)
                    }
            );
            shade.setBackground(gradient);
        } else {
            shade.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.argb(20, 0, 0, 0), Color.argb(232, 5, 9, 12)}
            ));
        }
        FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(
                -1,
                dp(parent, creatorCard ? CREATOR_SHADE_HEIGHT_DP : COMPACT_COPY_HEIGHT_DP)
        );
        shadeParams.gravity = Gravity.BOTTOM;
        frame.addView(shade, shadeParams);

        TextView rank = new TextView(parent.getContext());
        rank.setTextColor(UiPalette.PRIMARY);
        rank.setTextSize(viewType == VIEW_TYPE_CREATOR_HERO ? 13f : 11.5f);
        rank.setTypeface(null, android.graphics.Typeface.BOLD);
        rank.setGravity(Gravity.CENTER);
        rank.setPadding(dp(parent, 10), 0, dp(parent, 10), 0);
        rank.setVisibility(creatorCard ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams rankParams = new FrameLayout.LayoutParams(
                -2,
                dp(parent, viewType == VIEW_TYPE_CREATOR_HERO ? 30 : 27)
        );
        rankParams.gravity = Gravity.TOP | Gravity.START;
        rankParams.setMargins(dp(parent, 11), dp(parent, 11), 0, 0);
        frame.addView(rank, rankParams);

        TextView favoriteStar = new TextView(parent.getContext());
        favoriteStar.setText("★");
        favoriteStar.setTextColor(UiPalette.PRIMARY);
        favoriteStar.setTextSize(viewType == VIEW_TYPE_CREATOR_HERO ? 20f : 18f);
        favoriteStar.setGravity(Gravity.CENTER);
        favoriteStar.setContentDescription("Favorite creator");
        favoriteStar.setVisibility(View.GONE);
        GradientDrawable favoriteBackground = new GradientDrawable();
        favoriteBackground.setShape(GradientDrawable.OVAL);
        favoriteBackground.setColor(ZeroChillUi.color(parent.getContext(), R.color.zc_surface_glass_strong));
        favoriteBackground.setStroke(
                dp(parent, 1),
                ZeroChillUi.color(parent.getContext(), R.color.zc_cyan_dim)
        );
        favoriteStar.setBackground(favoriteBackground);
        FrameLayout.LayoutParams favoriteParams = new FrameLayout.LayoutParams(
                dp(parent, 36),
                dp(parent, 36),
                Gravity.TOP | Gravity.END
        );
        favoriteParams.setMargins(0, dp(parent, 10), dp(parent, 10), 0);
        frame.addView(favoriteStar, favoriteParams);

        FrameLayout copy = new FrameLayout(parent.getContext());
        copy.setPadding(
                dp(parent, creatorCard ? 14 : 11),
                dp(parent, 5),
                dp(parent, creatorCard ? 50 : 11),
                dp(parent, creatorCard ? 10 : 7)
        );
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(
                -1,
                dp(parent, creatorCard ? CREATOR_COPY_HEIGHT_DP : COMPACT_COPY_HEIGHT_DP)
        );
        copyParams.gravity = Gravity.BOTTOM;
        frame.addView(copy, copyParams);

        LinearLayout textColumn = new LinearLayout(parent.getContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(textColumn, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(creatorTitleSize(viewType));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = new TextView(parent.getContext());
        description.setTextColor(creatorCard
                ? Color.rgb(188, 188, 198)
                : Color.rgb(210, 210, 218));
        description.setTextSize(creatorCard ? 10.5f : 11f);
        description.setMaxLines(creatorCard ? 1 : 5);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setVisibility(View.GONE);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(parent, creatorCard ? 1 : 3);
        textColumn.addView(description, descriptionParams);

        TextView arrow = new TextView(parent.getContext());
        arrow.setText("›");
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(26f);
        arrow.setGravity(Gravity.CENTER);
        arrow.setContentDescription("Open gallery");
        arrow.setVisibility(creatorCard ? View.VISIBLE : View.GONE);
        GradientDrawable arrowBackground = new GradientDrawable();
        arrowBackground.setShape(GradientDrawable.OVAL);
        arrowBackground.setColor(Color.argb(112, 14, 14, 17));
        arrowBackground.setStroke(dp(parent, 1), Color.argb(112, 255, 255, 255));
        arrow.setBackground(arrowBackground);
        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(
                dp(parent, 33),
                dp(parent, 33),
                Gravity.END | Gravity.CENTER_VERTICAL
        );
        copy.addView(arrow, arrowParams);

        return new Holder(
                card,
                frame,
                initials,
                backdrop,
                image,
                shade,
                copy,
                title,
                description,
                rank,
                favoriteStar,
                arrow,
                viewType
        );
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        boolean creatorCard = holder.creatorCard();
        boolean creatorFavorite = creatorCard &&
                CreatorFavoriteStore.contains(holder.card.getContext(), item);
        holder.title.setText(item.title);
        holder.favoriteStar.setVisibility(creatorFavorite ? View.VISIBLE : View.GONE);

        if (creatorCard) {
            CreatorGalleryPreloader.warm(holder.card.getContext(), item);
            int rank = creatorRank(item, position);
            holder.rank.setText("#" + rank);
            holder.description.setText("Pictures + videos");
            holder.description.setVisibility(View.VISIBLE);
            holder.initials.setText(initials(item.title));
            holder.frame.setBackground(creatorPlaceholder(item.title));
            styleCreatorCard(holder, rank);
            holder.card.setContentDescription(
                    "Rank " + rank + ", " + item.title + ". Open pictures and videos. " +
                            (creatorFavorite
                                    ? "Long press to remove from favorites."
                                    : "Long press to add to favorites.")
            );
        } else {
            boolean hasDescription = item.description != null && !item.description.trim().isEmpty();
            holder.description.setText(hasDescription ? item.description.trim() : "");
            holder.description.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
            holder.card.setContentDescription(hasDescription
                    ? item.title + ". " + item.description.trim()
                    : item.title);
        }

        resizeForDescription(holder, !creatorCard && holder.description.getVisibility() == View.VISIBLE);
        holder.card.setOnClickListener(v -> listener.onOpen(item));
        if (creatorCard) {
            holder.card.setOnLongClickListener(v -> {
                int currentPosition = holder.getBindingAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION) return false;
                NativeContentItem current = items.get(currentPosition);
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                boolean favorite = CreatorFavoriteStore.toggle(v.getContext(), current);
                holder.favoriteStar.setVisibility(favorite ? View.VISIBLE : View.GONE);
                holder.card.setContentDescription(
                        "Rank " + creatorRank(current, currentPosition) + ", " +
                                current.title + ". Open pictures and videos. " +
                                (favorite
                                        ? "Long press to remove from favorites."
                                        : "Long press to add to favorites.")
                );
                Toast.makeText(
                        v.getContext(),
                        favorite
                                ? current.title + " added to favorites."
                                : current.title + " removed from favorites.",
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            });
        } else {
            holder.card.setOnLongClickListener(null);
        }
        restoreCreatorArtwork(item);
        loadImage(holder, item);
        requestBunkrArtwork(item);
    }

    private void styleCreatorCard(Holder holder, int rank) {
        if (rank == 1) {
            holder.card.setStrokeWidth(dp(holder.card, 2));
            holder.card.setStrokeColor(ZeroChillUi.color(holder.card.getContext(), R.color.zc_cyan));
        } else {
            holder.card.setStrokeWidth(dp(holder.card, 1));
            holder.card.setStrokeColor(rank <= 3
                    ? ZeroChillUi.color(holder.card.getContext(), R.color.zc_cyan_dim)
                    : ZeroChillUi.color(holder.card.getContext(), R.color.zc_edge));
        }

        GradientDrawable rankBackground = new GradientDrawable();
        rankBackground.setCornerRadius(dp(holder.card, 15));
        if (rank == 1) {
            rankBackground.setColor(UiPalette.PRIMARY);
            holder.rank.setTextColor(Color.BLACK);
        } else {
            rankBackground.setColor(ZeroChillUi.color(
                    holder.card.getContext(), R.color.zc_surface_glass_strong));
            rankBackground.setStroke(
                    dp(holder.card, 1),
                    ZeroChillUi.color(holder.card.getContext(), R.color.zc_cyan_dim)
            );
            holder.rank.setTextColor(UiPalette.PRIMARY);
        }
        holder.rank.setBackground(rankBackground);
    }

    private void loadImage(Holder holder, NativeContentItem item) {
        byte[] embedded = EmbeddedBrowseArtwork.get(holder.image.getContext(), item.url);
        if (embedded != null && embedded.length >= 512) {
            Glide.with(holder.image)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .dontAnimate()
                    .centerCrop()
                    .placeholder(new ColorDrawable(Color.rgb(31, 31, 35)))
                    .error(new ColorDrawable(Color.rgb(31, 31, 35)))
                    .into(holder.image);
            return;
        }

        if (holder.creatorCard()) {
            loadCreatorImage(holder, item);
            return;
        }

        Artwork artwork = BunkrRepository.isAlbumUrl(item.url)
                ? resolvedArtwork.get(artworkKey(item))
                : null;
        String imageUrl = artwork == null ? item.imageUrl : artwork.imageUrl;
        if (imageUrl == null || imageUrl.trim().isEmpty()) imageUrl = item.imageUrl;
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(31, 31, 35)));
            return;
        }

        Glide.with(holder.image)
                .load(remoteImage(imageUrl.trim(), artwork == null ? item.url : artwork.referer))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .centerCrop()
                .placeholder(new ColorDrawable(Color.rgb(31, 31, 35)))
                .error(new ColorDrawable(Color.rgb(31, 31, 35)))
                .into(holder.image);
    }

    private void loadCreatorImage(Holder holder, NativeContentItem item) {
        String key = artworkKey(item);
        boolean sameCard = key.equals(holder.boundArtworkKey);
        Drawable foregroundPlaceholder = sameCard ? holder.image.getDrawable() : null;
        Drawable backdropPlaceholder = sameCard ? holder.backdrop.getDrawable() : null;
        holder.boundArtworkKey = key;
        Artwork artwork = resolvedArtwork.get(key);
        String itemPreview = exhaustedArtwork.contains(key) ? "" : clean(item.imageUrl);
        String foregroundUrl = artwork == null ? itemPreview : clean(artwork.imageUrl);
        String foregroundReferer = artwork == null
                ? creatorPreviewReferer(item)
                : artwork.referer;

        if (foregroundUrl.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            Glide.with(holder.backdrop).clear(holder.backdrop);
            holder.image.setImageDrawable(null);
            holder.backdrop.setImageDrawable(null);
            return;
        }

        int[] foregroundSize = creatorRequestSize(holder);
        String requestedUrl = foregroundUrl;
        RequestBuilder<Drawable> foreground = Glide.with(holder.image)
                .load(remoteImage(foregroundUrl, foregroundReferer))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .override(foregroundSize[0], foregroundSize[1])
                .placeholder(foregroundPlaceholder == null
                        ? new ColorDrawable(Color.TRANSPARENT)
                        : foregroundPlaceholder)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            GlideException error,
                            Object model,
                            Target<Drawable> target,
                            boolean firstResource
                    ) {
                        if (artwork != null) {
                            mainHandler.post(() -> onCreatorArtworkFailed(item, requestedUrl));
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            Drawable resource,
                            Object model,
                            Target<Drawable> target,
                            DataSource source,
                            boolean firstResource
                    ) {
                        if (artwork != null) {
                            mainHandler.post(() -> onCreatorImageReady(
                                    item,
                                    requestedUrl,
                                    resource
                            ));
                        }
                        return false;
                    }
                });
        if (ZeroChillMotion.animationsEnabled(holder.image.getContext())) {
            foreground = foreground.transition(DrawableTransitionOptions.with(CREATOR_CROSS_FADE));
        } else {
            foreground = foreground.dontAnimate();
        }
        foreground = foreground.error(new ColorDrawable(Color.TRANSPARENT));
        foreground.into(holder.image);

        RequestBuilder<Drawable> backdrop = Glide.with(holder.backdrop)
                .load(remoteImage(foregroundUrl, foregroundReferer))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .override(220, 220)
                .placeholder(backdropPlaceholder == null
                        ? new ColorDrawable(Color.TRANSPARENT)
                        : backdropPlaceholder)
                .error(new ColorDrawable(Color.TRANSPARENT));
        if (ZeroChillMotion.animationsEnabled(holder.backdrop.getContext())) {
            backdrop = backdrop.transition(DrawableTransitionOptions.with(CREATOR_CROSS_FADE));
        } else {
            backdrop = backdrop.dontAnimate();
        }
        backdrop.into(holder.backdrop);
    }

    private void resizeForDescription(Holder holder, boolean hasDescription) {
        boolean creatorCard = holder.creatorCard();
        int copyHeight = creatorCard
                ? CREATOR_COPY_HEIGHT_DP
                : hasDescription ? DESCRIPTION_COPY_HEIGHT_DP : COMPACT_COPY_HEIGHT_DP;
        int shadeHeight = creatorCard ? CREATOR_SHADE_HEIGHT_DP : copyHeight;
        RecyclerView.LayoutParams cardParams = (RecyclerView.LayoutParams) holder.card.getLayoutParams();
        cardParams.height = dp(
                holder.card,
                creatorCard
                        ? creatorCardHeightDp(holder.card, holder.viewType)
                        : hasDescription
                        ? responsiveDescriptionHeightDp(holder.card)
                        : responsiveHeightDp(holder.card)
        );
        holder.card.setLayoutParams(cardParams);

        FrameLayout.LayoutParams shadeParams = (FrameLayout.LayoutParams) holder.shade.getLayoutParams();
        shadeParams.height = dp(holder.card, shadeHeight);
        holder.shade.setLayoutParams(shadeParams);

        FrameLayout.LayoutParams copyParams = (FrameLayout.LayoutParams) holder.copy.getLayoutParams();
        copyParams.height = dp(holder.card, copyHeight);
        holder.copy.setLayoutParams(copyParams);
    }

    private int[] creatorRequestSize(Holder holder) {
        Configuration config = holder.card.getResources().getConfiguration();
        int columns = creatorGridColumnCount(config);
        int span = creatorSpanForViewType(holder.viewType, columns);
        int screenWidth = holder.card.getResources().getDisplayMetrics().widthPixels;
        int approximateWidth = Math.max(1, Math.round(screenWidth * (span / (float) columns)));
        int width = holder.viewType == VIEW_TYPE_CREATOR_HERO
                ? Math.max(720, Math.min(1440, approximateWidth))
                : Math.max(600, Math.min(1080, approximateWidth));
        int height = Math.max(1, Math.round(width / targetAspectForViewType(holder.viewType)));
        return new int[]{width, height};
    }

    private GlideUrl remoteImage(String imageUrl, String requestReferer) {
        String referer = requestReferer == null || requestReferer.isEmpty()
                ? EfuktRepository.BASE
                : requestReferer;
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", EfuktRepository.USER_AGENT)
                .addHeader("Referer", referer)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.isEmpty()) && !referer.isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(referer);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private void requestBunkrArtwork(NativeContentItem item) {
        if (closed || item == null || !BunkrRepository.isAlbumUrl(item.url)) return;
        String key = artworkKey(item);
        Artwork resolved = resolvedArtwork.get(key);
        if (resolved != null && !resolved.imageUrl.isEmpty()) return;
        if (!requestedArtwork.add(key)) return;
        String albumUrl = item.url;
        boolean creator = item.isCreator();
        try {
            bunkrArtworkIo.execute(() -> {
                Artwork artwork = null;
                boolean creatorCandidatesLoaded = false;
                try {
                    if (creator) {
                        List<BunkrRepository.CreatorArtwork> previews =
                                bunkrRepository.fetchCreatorArtworkPreviews(appContext, albumUrl);
                        mainHandler.post(() -> onCreatorArtworkCandidates(key, item, previews));
                        creatorCandidatesLoaded = true;
                    } else {
                        String imageUrl = bunkrRepository.fetchAlbumArtwork(appContext, albumUrl);
                        artwork = new Artwork(imageUrl, albumUrl, false, 0f);
                    }
                } catch (Exception ignored) {
                }
                Artwork result = artwork;
                if (!creator) {
                    mainHandler.post(() -> onBunkrArtwork(key, result));
                } else if (!creatorCandidatesLoaded) {
                    mainHandler.post(() -> requestedArtwork.remove(key));
                }
            });
        } catch (RuntimeException ignored) {
            requestedArtwork.remove(key);
        }
    }

    private void onBunkrArtwork(String key, Artwork artwork) {
        if (closed || key == null || key.isEmpty()) return;
        if (artwork == null || artwork.imageUrl.isEmpty()) {
            requestedArtwork.remove(key);
            return;
        }
        resolvedArtwork.put(key, artwork);
        if (artwork.persistent) writeCreatorArtworkCache(key, artwork);
        notifyArtworkChanged(key);
    }

    private void onCreatorArtworkCandidates(
            String key,
            NativeContentItem item,
            List<BunkrRepository.CreatorArtwork> previews
    ) {
        if (closed || key == null || key.isEmpty()) return;
        ArrayList<Artwork> candidates = new ArrayList<>();
        HashSet<String> urls = new HashSet<>();
        String originalPreview = item == null ? "" : clean(item.imageUrl);
        String originalReferer = creatorPreviewReferer(item);
        boolean preferOriginal = FapelloRepository.isFapelloUrl(originalReferer);
        if (preferOriginal && !originalPreview.isEmpty() && urls.add(originalPreview)) {
            candidates.add(new Artwork(originalPreview, originalReferer, true, 0f));
        }
        if (previews != null) {
            for (BunkrRepository.CreatorArtwork preview : previews) {
                if (preview == null) continue;
                String url = clean(preview.imageUrl);
                if (url.isEmpty() || !urls.add(url)) continue;
                candidates.add(new Artwork(url, preview.requestReferer, true, 0f));
            }
        }
        if (!originalPreview.isEmpty() && urls.add(originalPreview)) {
            candidates.add(new Artwork(originalPreview, originalReferer, true, 0f));
        }
        if (candidates.isEmpty()) {
            requestedArtwork.remove(key);
            return;
        }

        artworkCandidates.put(key, candidates);
        exhaustedArtwork.remove(key);
        resolvedArtwork.put(key, candidates.get(0));
        notifyArtworkChanged(key);
    }

    private void restoreAllCreatorArtwork() {
        for (NativeContentItem item : items) {
            if (item != null && item.isCreator()) restoreCreatorArtwork(item);
        }
    }

    private void restoreCreatorArtwork(NativeContentItem item) {
        if (item == null || !item.isCreator()) return;
        String artworkKey = artworkKey(item);
        if (resolvedArtwork.containsKey(artworkKey)) return;
        SharedPreferences prefs = appContext.getSharedPreferences(
                CREATOR_ARTWORK_PREFS,
                Context.MODE_PRIVATE
        );
        String key = artworkCacheKey(artworkKey);
        String imageUrl = prefs.getString(key + "_url", "");
        if (imageUrl == null || imageUrl.isEmpty()) return;
        String referer = prefs.getString(key + "_referer", item.url);
        float ratio = prefs.getFloat(key + "_ratio", 0f);
        resolvedArtwork.put(artworkKey, new Artwork(imageUrl, referer, true, ratio));
        if (ratio > 0f) creatorAspectRatios.put(artworkKey, ratio);
    }

    private void writeCreatorArtworkCache(String artworkKey, Artwork artwork) {
        if (artwork == null || artwork.imageUrl.isEmpty()) return;
        String key = artworkCacheKey(artworkKey);
        SharedPreferences.Editor editor = appContext.getSharedPreferences(
                CREATOR_ARTWORK_PREFS,
                Context.MODE_PRIVATE
        ).edit()
                .putString(key + "_url", artwork.imageUrl)
                .putString(key + "_referer", artwork.referer);
        if (artwork.aspectRatio > 0f) editor.putFloat(key + "_ratio", artwork.aspectRatio);
        editor.apply();
    }

    private void onCreatorImageReady(
            NativeContentItem item,
            String loadedUrl,
            Drawable resource
    ) {
        if (closed || item == null || resource == null || !item.isCreator()) return;
        int width = resource.getIntrinsicWidth();
        int height = resource.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return;
        float ratio = width / (float) height;
        if (ratio < 0.25f || ratio > 4f) return;

        String key = artworkKey(item);
        Artwork artwork = resolvedArtwork.get(key);
        if (artwork == null || artwork.imageUrl.isEmpty() ||
                !artwork.imageUrl.equals(loadedUrl)) return;
        Float oldRatio = creatorAspectRatios.get(key);
        if (oldRatio != null && Math.abs(oldRatio - ratio) < 0.025f) return;

        int oldViewType = viewTypeForItem(item);
        creatorAspectRatios.put(key, ratio);
        Artwork measured = new Artwork(
                artwork.imageUrl,
                artwork.referer,
                artwork.persistent,
                ratio
        );
        resolvedArtwork.put(key, measured);
        if (measured.persistent) writeCreatorArtworkCache(key, measured);

        int newViewType = viewTypeForItem(item);
        if (oldViewType != newViewType) notifyArtworkChanged(key);
    }

    private void onCreatorArtworkFailed(NativeContentItem item, String failedUrl) {
        if (closed || item == null || !item.isCreator()) return;
        String key = artworkKey(item);
        Artwork current = resolvedArtwork.get(key);
        if (current == null || !current.imageUrl.equals(failedUrl)) return;
        removeCreatorArtworkCache(key);

        List<Artwork> candidates = artworkCandidates.get(key);
        if (candidates == null || candidates.isEmpty()) {
            resolvedArtwork.remove(key);
            creatorAspectRatios.remove(key);
            requestedArtwork.remove(key);
            int position = indexOfArtwork(key);
            if (position >= 0) requestBunkrArtwork(item);
            return;
        }

        int failedIndex = -1;
        for (int index = 0; index < candidates.size(); index++) {
            if (failedUrl.equals(candidates.get(index).imageUrl)) {
                failedIndex = index;
                break;
            }
        }
        int nextIndex = failedIndex + 1;
        if (failedIndex >= 0 && nextIndex < candidates.size()) {
            resolvedArtwork.put(key, candidates.get(nextIndex));
            creatorAspectRatios.remove(key);
            notifyArtworkChanged(key);
            return;
        }

        resolvedArtwork.remove(key);
        creatorAspectRatios.remove(key);
        exhaustedArtwork.add(key);
        notifyArtworkChanged(key);
    }

    private void removeCreatorArtworkCache(String artworkKey) {
        String preferenceKey = artworkCacheKey(artworkKey);
        appContext.getSharedPreferences(CREATOR_ARTWORK_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(preferenceKey + "_url")
                .remove(preferenceKey + "_referer")
                .remove(preferenceKey + "_ratio")
                .apply();
    }

    private void notifyArtworkChanged(String artworkKey) {
        for (int i = 0; i < items.size(); i++) {
            NativeContentItem candidate = items.get(i);
            if (candidate != null && artworkKey.equals(artworkKey(candidate))) {
                notifyItemChanged(i);
            }
        }
    }

    private int indexOfArtwork(String artworkKey) {
        for (int i = 0; i < items.size(); i++) {
            NativeContentItem candidate = items.get(i);
            if (candidate != null && artworkKey.equals(artworkKey(candidate))) return i;
        }
        return -1;
    }

    private int viewTypeForItem(NativeContentItem item) {
        int index = items.indexOf(item);
        return index < 0 ? VIEW_TYPE_STANDARD : getItemViewType(index);
    }

    private String artworkKey(NativeContentItem item) {
        if (item == null) return "";
        return clean(item.url) + "\n" + clean(item.searchQuery);
    }

    private String creatorPreviewReferer(NativeContentItem item) {
        if (item == null) return "";
        String candidate = clean(item.uploader);
        return candidate.startsWith("http://") || candidate.startsWith("https://")
                ? candidate
                : clean(item.url);
    }

    private String artworkCacheKey(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder key = new StringBuilder("cover_");
            for (byte item : digest) key.append(String.format(Locale.US, "%02x", item & 0xff));
            return key.toString();
        } catch (Exception ignored) {
            return "cover_" + Integer.toHexString(value.hashCode());
        }
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.image).clear(holder.image);
        Glide.with(holder.backdrop).clear(holder.backdrop);
        holder.boundArtworkKey = "";
        holder.favoriteStar.setVisibility(View.GONE);
        holder.card.setOnClickListener(null);
        holder.card.setOnLongClickListener(null);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int plannedCreatorViewType(int position) {
        if (position <= 0) return VIEW_TYPE_CREATOR_HERO;
        if (position < 5) return VIEW_TYPE_CREATOR_FEATURED;
        int pattern = Math.floorMod(position - 5, 7);
        if (pattern == 0) return VIEW_TYPE_CREATOR_WIDE;
        if (pattern == 1 || pattern == 2 || pattern == 5 || pattern == 6) {
            return VIEW_TYPE_CREATOR_PORTRAIT;
        }
        return VIEW_TYPE_CREATOR_SQUARE;
    }

    private static boolean isCreatorViewType(int viewType) {
        return viewType >= VIEW_TYPE_CREATOR_HERO && viewType <= VIEW_TYPE_CREATOR_WIDE;
    }

    private static int creatorGridColumnCount(Configuration config) {
        if (config.screenWidthDp >= 720) return 4;
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                config.screenWidthDp >= 600 ? 4 : 2;
    }

    private static int creatorSpanForViewType(int viewType, int columns) {
        if (viewType == VIEW_TYPE_CREATOR_HERO) return columns;
        if (viewType == VIEW_TYPE_CREATOR_WIDE) return columns <= 2 ? columns : 2;
        return 1;
    }

    private static float targetAspectForViewType(int viewType) {
        if (viewType == VIEW_TYPE_CREATOR_HERO || viewType == VIEW_TYPE_CREATOR_WIDE) {
            return ASPECT_HERO;
        }
        if (viewType == VIEW_TYPE_CREATOR_FEATURED ||
                viewType == VIEW_TYPE_CREATOR_PORTRAIT) return ASPECT_PORTRAIT;
        return ASPECT_SQUARE;
    }

    private static float creatorTitleSize(int viewType) {
        if (viewType == VIEW_TYPE_CREATOR_HERO) return 21f;
        if (viewType == VIEW_TYPE_CREATOR_FEATURED) return 16.5f;
        if (viewType == VIEW_TYPE_CREATOR_WIDE) return 18f;
        if (viewType == VIEW_TYPE_STANDARD) return 14.5f;
        return 15.5f;
    }

    private static int creatorCardHeightDp(View parent, int viewType) {
        Configuration config = parent.getResources().getConfiguration();
        int columns = creatorGridColumnCount(config);
        int span = creatorSpanForViewType(viewType, columns);
        int rail = config.orientation == Configuration.ORIENTATION_LANDSCAPE ? 68 : 0;
        float available = Math.max(300f, config.screenWidthDp - rail - (columns * 9f));
        float width = (available / columns) * span;
        float rawHeight = width / targetAspectForViewType(viewType);

        if (viewType == VIEW_TYPE_CREATOR_HERO) {
            int maximum = config.orientation == Configuration.ORIENTATION_LANDSCAPE ? 286 : 360;
            return Math.max(190, Math.min(maximum, Math.round(rawHeight)));
        }
        if (viewType == VIEW_TYPE_CREATOR_WIDE) {
            return Math.max(154, Math.min(290, Math.round(rawHeight)));
        }
        if (viewType == VIEW_TYPE_CREATOR_SQUARE) {
            return Math.max(158, Math.min(270, Math.round(rawHeight)));
        }
        return Math.max(190, Math.min(330, Math.round(rawHeight)));
    }

    private static int responsiveHeightDp(View parent) {
        Configuration config = parent.getResources().getConfiguration();
        int widthDp = Math.max(320, config.screenWidthDp);
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        int columns;
        int rail = 0;
        if (landscape) {
            columns = widthDp >= 900 ? 4 : 3;
            rail = 68;
        } else {
            columns = 2;
        }
        float available = Math.max(280f, widthDp - rail - (columns * 14f));
        float cardWidth = available / columns;
        return Math.max(136, Math.min(180, Math.round(cardWidth * 0.72f)));
    }

    private static int responsiveDescriptionHeightDp(View parent) {
        return Math.max(218, Math.min(260, responsiveHeightDp(parent) + 78));
    }

    private static int creatorRank(NativeContentItem item, int position) {
        try {
            int stored = Integer.parseInt(clean(item.views));
            if (stored > 0 && stored <= 999) return stored;
        } catch (Exception ignored) {
        }
        return position + 1;
    }

    private static Drawable creatorPlaceholder(String title) {
        int[][] palettes = {
                {Color.rgb(5, 30, 48), Color.rgb(8, 12, 16)},
                {Color.rgb(6, 36, 58), Color.rgb(8, 12, 16)},
                {Color.rgb(7, 29, 51), Color.rgb(8, 12, 16)},
                {Color.rgb(7, 34, 54), Color.rgb(8, 12, 16)},
                {Color.rgb(8, 28, 47), Color.rgb(8, 12, 16)}
        };
        int[] colors = palettes[Math.floorMod(title == null ? 0 : title.hashCode(), palettes.length)];
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
    }

    private static String initials(String title) {
        String clean = title == null ? "" : title.trim();
        if (clean.isEmpty()) return "ZC";
        String[] words = clean.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            result.append(Character.toUpperCase(word.charAt(0)));
            if (result.length() == 2) break;
        }
        return result.length() == 0 ? "ZC" : result.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final FrameLayout frame;
        final TextView initials;
        final ImageView backdrop;
        final ImageView image;
        final View shade;
        final FrameLayout copy;
        final TextView title;
        final TextView description;
        final TextView rank;
        final TextView favoriteStar;
        final TextView arrow;
        final int viewType;
        String boundArtworkKey = "";

        Holder(
                MaterialCardView card,
                FrameLayout frame,
                TextView initials,
                ImageView backdrop,
                ImageView image,
                View shade,
                FrameLayout copy,
                TextView title,
                TextView description,
                TextView rank,
                TextView favoriteStar,
                TextView arrow,
                int viewType
        ) {
            super(card);
            this.card = card;
            this.frame = frame;
            this.initials = initials;
            this.backdrop = backdrop;
            this.image = image;
            this.shade = shade;
            this.copy = copy;
            this.title = title;
            this.description = description;
            this.rank = rank;
            this.favoriteStar = favoriteStar;
            this.arrow = arrow;
            this.viewType = viewType;
        }

        boolean creatorCard() {
            return isCreatorViewType(viewType);
        }
    }

    private static final class Artwork {
        final String imageUrl;
        final String referer;
        final boolean persistent;
        final float aspectRatio;

        Artwork(String imageUrl, String referer, boolean persistent, float aspectRatio) {
            this.imageUrl = clean(imageUrl);
            this.referer = clean(referer);
            this.persistent = persistent;
            this.aspectRatio = aspectRatio;
        }
    }
}
