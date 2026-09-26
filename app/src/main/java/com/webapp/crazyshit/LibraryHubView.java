package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Personal ZEROCHILL media hub.
 *
 * Existing stores remain the source of truth. This view only gives Continue Watching, creators,
 * Watch Later, Downloads and History a first-class visual presentation inside the primary pager.
 */
final class LibraryHubView extends ScrollView {
    interface Listener {
        void onOpenItem(NativeContentItem item);
        void onOpenHistory(PlaybackHistoryStore.Item item);
        void onOpenCreator(NativeContentItem creator);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final Activity activity;
    private final Listener listener;
    private final LinearLayout content;
    private final Map<String, List<ImageView>> thumbnailTargets = new HashMap<>();
    private final Map<String, String> resolvedThumbnails = new HashMap<>();
    private final java.util.Set<String> requestedThumbnails = new java.util.HashSet<>();
    private final RenderedThumbnailResolver[] thumbnailResolvers;
    private final ShowsContinueFrameStore.Listener frameListener = this::onContinueFrameUpdated;
    private int resolverCursor;
    private boolean closed;

    LibraryHubView(Activity activity, Listener listener) {
        super(activity);
        this.activity = activity;
        this.listener = listener;

        setFillViewport(true);
        setContentDescription("Library media hub");
        setVerticalScrollBarEnabled(false);
        setClipToPadding(false);
        setBackgroundColor(ZeroChillUi.background(activity));

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(18), dp(12), dp(36));
        addView(content, new ScrollView.LayoutParams(-1, -2));

        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(activity, this::onThumbnailResolved),
                new RenderedThumbnailResolver(activity, this::onThumbnailResolved)
        };
        ShowsContinueFrameStore.addListener(frameListener);
        refresh();
    }

    void refresh() {
        if (closed) return;
        int scrollY = getScrollY();
        content.removeAllViews();
        thumbnailTargets.clear();
        requestedThumbnails.clear();

        List<PlaybackHistoryStore.Item> history = PlaybackHistoryStore.load(activity);
        List<PlaybackHistoryStore.Item> continueItems = PlaybackHistoryStore.continueWatching(activity);
        List<NativeContentItem> creators = CreatorCatalog.matching(activity, "", true, 20);
        List<FavoriteStore.Item> watchLater = FavoriteStore.load(activity);
        List<VideoDownloadStore.Entry> downloads = VideoDownloadStore.entries(activity);

        Map<String, PlaybackHistoryStore.Item> historyByUrl = new HashMap<>();
        for (PlaybackHistoryStore.Item item : history) {
            if (item != null && item.pageUrl != null && !item.pageUrl.isEmpty()) {
                historyByUrl.put(item.pageUrl, item);
            }
        }

        int sections = 0;
        if (!continueItems.isEmpty()) {
            addContinueSection(continueItems);
            sections++;
        }
        if (!creators.isEmpty()) {
            addCreatorSection(creators);
            sections++;
        }
        if (!watchLater.isEmpty()) {
            addWatchLaterSection(watchLater, historyByUrl);
            sections++;
        }
        if (!downloads.isEmpty()) {
            addDownloadsSection(downloads);
            sections++;
        }
        if (!history.isEmpty()) {
            addHistorySection(history);
            sections++;
        }

        if (sections == 0) addGlobalEmptyState();

        post(() -> scrollTo(0, Math.min(scrollY, Math.max(0, content.getHeight() - getHeight()))));
    }

    void close() {
        if (closed) return;
        closed = true;
        ShowsContinueFrameStore.removeListener(frameListener);
        for (RenderedThumbnailResolver resolver : thumbnailResolvers) {
            if (resolver != null) resolver.close();
        }
        thumbnailTargets.clear();
        requestedThumbnails.clear();
    }

    private void addContinueSection(List<PlaybackHistoryStore.Item> items) {
        LinearLayout rail = addSectionShell(
                "Continue Watching",
                "Pick up where you left off",
                "View all",
                () -> openSavedVideos(FavoritesActivity.START_CONTINUE)
        );

        int count = Math.min(10, items.size());
        for (int i = 0; i < count; i++) {
            PlaybackHistoryStore.Item item = items.get(i);
            rail.addView(continueCard(item), mediaRailParams(248, 140, i == count - 1));
        }
    }

    private View continueCard(PlaybackHistoryStore.Item item) {
        MaterialCardView card = mediaCard(22);
        FrameLayout frame = new FrameLayout(activity);
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = mediaImage(frame);
        loadMediaImage(image, item.pageUrl, item.posterUrl, true);

        View shade = new View(activity);
        shade.setBackground(bottomShade());
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView remaining = pill(formatRemaining(item.positionMs, item.durationMs));
        remaining.setVisibility(item.durationMs > 0L ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams remainingParams =
                new FrameLayout.LayoutParams(-2, dp(28), Gravity.TOP | Gravity.END);
        remainingParams.setMargins(0, dp(10), dp(10), 0);
        frame.addView(remaining, remainingParams);

        TextView title = mediaTitle(item.title, 16.5f);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(12), 0, dp(12), dp(17));
        frame.addView(title, titleParams);

        if (item.durationMs > 0L) {
            FrameLayout track = new FrameLayout(activity);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setColor(Color.argb(118, 255, 255, 255));
            trackBg.setCornerRadius(dp(2));
            track.setBackground(trackBg);
            FrameLayout.LayoutParams trackParams =
                    new FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM);
            trackParams.setMargins(dp(10), 0, dp(10), dp(7));
            frame.addView(track, trackParams);

            View fill = new View(activity);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setColor(UiPalette.PRIMARY);
            fillBg.setCornerRadius(dp(2));
            fill.setBackground(fillBg);
            int fillWidth = Math.max(dp(4),
                    Math.round(dp(228) * (item.progressPercent() / 100f)));
            track.addView(fill, new FrameLayout.LayoutParams(fillWidth, -1));
        }

        card.setContentDescription("Continue watching " + item.title);
        card.setOnClickListener(v -> openHistoryItem(item));
        return card;
    }

    private void addCreatorSection(List<NativeContentItem> creators) {
        LinearLayout rail = addSectionShell(
                "Favorite Creators",
                "Your saved creators",
                "View all",
                () -> activity.startActivity(new Intent(activity, CreatorsActivity.class))
        );

        int count = Math.min(14, creators.size());
        for (int i = 0; i < count; i++) {
            NativeContentItem creator = creators.get(i);
            CreatorGalleryPreloader.warm(activity, creator);
            rail.addView(creatorCard(creator), creatorRailParams(i == count - 1));
        }
    }

    private View creatorCard(NativeContentItem creator) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription("Open " + creator.title);
        ZeroChillMotion.installPressFeedback(wrapper);

        MaterialCardView avatar = mediaCard(42);
        avatar.setRadius(dp(42));
        avatar.setCardBackgroundColor(Color.rgb(19, 23, 27));

        FrameLayout frame = new FrameLayout(activity);
        avatar.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        TextView initials = text(initials(creator.title), 24f,
                Color.argb(105, 255, 255, 255));
        initials.setTypeface(null, android.graphics.Typeface.BOLD);
        initials.setGravity(Gravity.CENTER);
        frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.TRANSPARENT);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loadCreatorImage(image, creator);

        wrapper.addView(avatar, new LinearLayout.LayoutParams(dp(84), dp(84)));

        TextView name = text(creator.title, 12.5f,
                ZeroChillUi.color(activity, R.color.zc_text_primary));
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(dp(102), -2);
        nameParams.topMargin = dp(7);
        wrapper.addView(name, nameParams);

        wrapper.setOnClickListener(v -> {
            if (listener != null) listener.onOpenCreator(creator);
        });
        return wrapper;
    }

    private void addWatchLaterSection(
            List<FavoriteStore.Item> items,
            Map<String, PlaybackHistoryStore.Item> historyByUrl
    ) {
        LinearLayout rail = addSectionShell(
                "Watch Later",
                "Saved for another time",
                "View all",
                () -> openSavedVideos(FavoritesActivity.START_WATCH_LATER)
        );

        int count = Math.min(10, items.size());
        for (int i = 0; i < count; i++) {
            FavoriteStore.Item item = items.get(i);
            PlaybackHistoryStore.Item historyItem = historyByUrl.get(item.url);
            String poster = historyItem == null ? "" : historyItem.posterUrl;
            rail.addView(
                    compactMediaCard(item.title, item.url, poster, "", false),
                    mediaRailParams(196, 122, i == count - 1)
            );
        }
    }

    private void addDownloadsSection(List<VideoDownloadStore.Entry> entries) {
        LinearLayout rail = addSectionShell(
                "Downloads",
                "Available offline and in progress",
                "View all",
                () -> activity.startActivity(new Intent(activity, DownloadedActivity.class))
        );

        int count = Math.min(10, entries.size());
        for (int i = 0; i < count; i++) {
            VideoDownloadStore.Entry entry = entries.get(i);
            rail.addView(downloadCard(entry), mediaRailParams(196, 122, i == count - 1));
        }
    }

    private View downloadCard(VideoDownloadStore.Entry entry) {
        MaterialCardView card = mediaCard(18);
        FrameLayout frame = new FrameLayout(activity);
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = mediaImage(frame);
        if (!entry.imageUrl.isEmpty()) {
            loadMediaImage(image, entry.pageUrl, entry.imageUrl, false);
        } else if (!entry.pageUrl.isEmpty()) {
            loadMediaImage(image, entry.pageUrl, "", false);
        } else {
            image.setImageResource(R.drawable.ic_action_download);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            image.setPadding(dp(58), dp(32), dp(58), dp(32));
            image.setColorFilter(UiPalette.PRIMARY);
        }

        View shade = new View(activity);
        shade.setBackground(bottomShade());
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView status = pill(VideoDownloadStore.statusText(entry));
        status.setTextSize(9.5f);
        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(-2, dp(26), Gravity.TOP | Gravity.END);
        statusParams.setMargins(0, dp(8), dp(8), 0);
        frame.addView(status, statusParams);

        TextView title = mediaTitle(entry.title, 14.5f);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(10), dp(9));
        frame.addView(title, titleParams);

        card.setContentDescription(entry.title + ". " + VideoDownloadStore.statusText(entry));
        card.setOnClickListener(v -> VideoDownloadStore.open(activity, entry));
        return card;
    }

    private void addHistorySection(List<PlaybackHistoryStore.Item> items) {
        LinearLayout rail = addSectionShell(
                "History",
                "Recently watched",
                "View all",
                () -> openSavedVideos(FavoritesActivity.START_HISTORY)
        );

        int count = Math.min(12, items.size());
        for (int i = 0; i < count; i++) {
            PlaybackHistoryStore.Item item = items.get(i);
            rail.addView(
                    historyCard(item),
                    mediaRailParams(168, 105, i == count - 1)
            );
        }
    }

    private View historyCard(PlaybackHistoryStore.Item item) {
        MaterialCardView card = mediaCard(16);
        FrameLayout frame = new FrameLayout(activity);
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = mediaImage(frame);
        loadMediaImage(image, item.pageUrl, item.posterUrl, false);

        View shade = new View(activity);
        shade.setBackground(bottomShade());
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView title = mediaTitle(item.title, 13.5f);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(10), dp(8));
        frame.addView(title, titleParams);

        card.setContentDescription(item.title);
        card.setOnClickListener(v -> openHistoryItem(item));
        return card;
    }

    private View compactMediaCard(
            String title,
            String pageUrl,
            String posterUrl,
            String badge,
            boolean small
    ) {
        MaterialCardView card = mediaCard(small ? 16 : 18);
        FrameLayout frame = new FrameLayout(activity);
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = mediaImage(frame);
        loadMediaImage(image, pageUrl, posterUrl, false);

        View shade = new View(activity);
        shade.setBackground(bottomShade());
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        if (badge != null && !badge.isEmpty()) {
            TextView badgeView = pill(badge);
            FrameLayout.LayoutParams badgeParams =
                    new FrameLayout.LayoutParams(-2, dp(26), Gravity.TOP | Gravity.END);
            badgeParams.setMargins(0, dp(8), dp(8), 0);
            frame.addView(badgeView, badgeParams);
        }

        TextView titleView = mediaTitle(title, small ? 13.5f : 14.5f);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(10), dp(8));
        frame.addView(titleView, titleParams);

        card.setContentDescription(title);
        card.setOnClickListener(v -> openVideo(title, pageUrl, posterUrl));
        return card;
    }

    private LinearLayout addSectionShell(
            String title,
            String subtitle,
            String actionLabel,
            Runnable action
    ) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout headingRow = new LinearLayout(activity);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.setPadding(dp(4), 0, dp(2), 0);

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);

        TextView heading = text(title, 21f,
                ZeroChillUi.color(activity, R.color.zc_text_primary));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setContentDescription(title);
        copy.addView(heading);

        TextView hint = text(subtitle, 11.5f,
                ZeroChillUi.color(activity, R.color.zc_text_muted));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(2);
        copy.addView(hint, hintParams);

        headingRow.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView viewAll = text(actionLabel, 11.5f, UiPalette.PRIMARY);
        viewAll.setTypeface(null, android.graphics.Typeface.BOLD);
        viewAll.setGravity(Gravity.CENTER);
        viewAll.setPadding(dp(12), dp(10), dp(8), dp(10));
        viewAll.setClickable(true);
        viewAll.setFocusable(true);
        viewAll.setContentDescription(actionLabel + " " + title);
        ZeroChillMotion.installPressFeedback(viewAll);
        viewAll.setOnClickListener(v -> action.run());
        headingRow.addView(viewAll, new LinearLayout.LayoutParams(-2, dp(42)));

        section.addView(headingRow, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setPadding(dp(4), 0, dp(4), 0);

        LinearLayout rail = new LinearLayout(activity);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setGravity(Gravity.TOP);
        rail.setClipChildren(false);
        rail.setClipToPadding(false);
        scroll.addView(rail, new HorizontalScrollView.LayoutParams(-2, -2));

        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(-1, -2);
        railParams.topMargin = dp(10);
        section.addView(scroll, railParams);

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.setMargins(0, 0, 0, dp(25));
        content.addView(section, sectionParams);
        return rail;
    }

    private void addGlobalEmptyState() {
        LinearLayout empty = new LinearLayout(activity);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(26), dp(54), dp(26), dp(42));

        TextView icon = text("♡", 36f, UiPalette.PRIMARY);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon, new LinearLayout.LayoutParams(dp(60), dp(60)));

        TextView title = text("Your Library is ready", 20f,
                ZeroChillUi.color(activity, R.color.zc_text_primary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(10);
        empty.addView(title, titleParams);

        TextView body = text(
                "Keep watching, favorite a creator, save something for later, or download a video.",
                13f,
                ZeroChillUi.color(activity, R.color.zc_text_secondary)
        );
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.setMargins(dp(18), dp(8), dp(18), 0);
        empty.addView(body, bodyParams);

        content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private MaterialCardView mediaCard(int radiusDp) {
        MaterialCardView card = new MaterialCardView(activity);
        ZeroChillUi.styleMediaCard(card, R.dimen.zc_radius_medium);
        card.setRadius(dp(radiusDp));
        card.setStrokeWidth(0);
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);
        card.setClipToOutline(true);
        ZeroChillMotion.installPressFeedback(card);
        return card;
    }

    private ImageView mediaImage(FrameLayout frame) {
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(13, 16, 19));
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        return image;
    }

    private void loadMediaImage(
            ImageView image,
            String pageUrl,
            String preferredImage,
            boolean preferContinueFrame
    ) {
        String cleanPage = clean(pageUrl);
        if (!cleanPage.isEmpty()) {
            thumbnailTargets.computeIfAbsent(cleanPage, key -> new ArrayList<>()).add(image);
        }

        if (preferContinueFrame && !cleanPage.isEmpty()) {
            File frame = ShowsContinueFrameStore.find(activity, cleanPage);
            if (frame != null) {
                loadResolvedImage(image, frame.toURI().toString(), cleanPage);
                return;
            }
        }

        String preferred = clean(preferredImage);
        if (!preferred.isEmpty()) {
            loadResolvedImage(image, preferred, cleanPage);
            return;
        }

        String resolved = resolvedThumbnails.get(cleanPage);
        if (resolved != null && !resolved.isEmpty()) {
            loadResolvedImage(image, resolved, cleanPage);
            return;
        }

        image.setImageDrawable(new ColorDrawable(Color.rgb(22, 25, 29)));
        requestThumbnail(cleanPage);
    }

    private void loadCreatorImage(ImageView image, NativeContentItem creator) {
        String url = creator == null ? "" : clean(creator.imageUrl);
        if (url.isEmpty()) return;
        try {
            Glide.with(image)
                    .load(remoteImage(url, clean(creator.url)))
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .into(image);
        } catch (Exception ignored) {
        }
    }

    private void requestThumbnail(String pageUrl) {
        if (pageUrl.isEmpty() || closed || !requestedThumbnails.add(pageUrl)) return;
        RenderedThumbnailResolver resolver =
                thumbnailResolvers[resolverCursor++ % thumbnailResolvers.length];
        resolver.request(pageUrl);
    }

    private void onThumbnailResolved(String pageUrl, String thumbnailUrl) {
        if (closed || clean(pageUrl).isEmpty() || clean(thumbnailUrl).isEmpty()) return;
        resolvedThumbnails.put(pageUrl, thumbnailUrl);
        List<ImageView> targets = thumbnailTargets.get(pageUrl);
        if (targets == null) return;
        for (ImageView image : new ArrayList<>(targets)) {
            loadResolvedImage(image, thumbnailUrl, pageUrl);
        }
    }

    private void onContinueFrameUpdated(String pageUrl) {
        if (closed || pageUrl == null || pageUrl.isEmpty()) return;
        File frame = ShowsContinueFrameStore.find(activity, pageUrl);
        if (frame == null) return;
        List<ImageView> targets = thumbnailTargets.get(pageUrl);
        if (targets == null) return;
        String uri = frame.toURI().toString();
        for (ImageView image : new ArrayList<>(targets)) {
            loadResolvedImage(image, uri, pageUrl);
        }
    }

    private void loadResolvedImage(ImageView image, String source, String pageUrl) {
        if (closed || image == null || clean(source).isEmpty()) return;
        Object model = source.startsWith("file:")
                ? source
                : remoteImage(source, pageUrl);
        try {
            Glide.with(image)
                    .load(model)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.rgb(22, 25, 29)))
                    .error(new ColorDrawable(Color.rgb(22, 25, 29)))
                    .into(image);
        } catch (Exception ignored) {
        }
    }

    private Object remoteImage(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT);
        if (pageUrl != null && !pageUrl.isEmpty()) headers.addHeader("Referer", pageUrl);
        return new GlideUrl(imageUrl, headers.build());
    }

    private void openHistoryItem(PlaybackHistoryStore.Item item) {
        if (item == null || clean(item.pageUrl).isEmpty()) return;
        if (listener != null && item.fromShows) {
            listener.onOpenHistory(item);
            return;
        }
        openVideo(item.title, item.pageUrl, item.posterUrl);
    }

    private void openVideo(String title, String pageUrl, String posterUrl) {
        if (listener == null || clean(pageUrl).isEmpty()) return;
        listener.onOpenItem(new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                clean(title).isEmpty() ? "Saved video" : title,
                pageUrl,
                posterUrl,
                "",
                "",
                ""
        ));
    }

    private void openSavedVideos(int startTab) {
        Intent intent = new Intent(activity, FavoritesActivity.class)
                .putExtra(FavoritesActivity.EXTRA_START_TAB, startTab);
        activity.startActivityForResult(intent, NativeMainActivity.FAVORITES_REQUEST);
    }

    private TextView mediaTitle(String value, float size) {
        TextView title = text(
                clean(value).isEmpty() ? "Saved video" : value,
                size,
                Color.WHITE
        );
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(5f, 0f, dp(1), Color.BLACK);
        return title;
    }

    private TextView pill(String value) {
        TextView badge = text(value, 10.5f, Color.WHITE);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), 0, dp(9), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(190, 5, 7, 9));
        background.setCornerRadius(dp(9));
        badge.setBackground(background);
        return badge;
    }

    private GradientDrawable bottomShade() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(0, 0, 0, 0),
                        Color.argb(32, 0, 0, 0),
                        Color.argb(226, 0, 0, 0)
                }
        );
    }

    private LinearLayout.LayoutParams mediaRailParams(
            int widthDp,
            int heightDp,
            boolean last
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp));
        params.setMarginEnd(last ? 0 : dp(10));
        return params;
    }

    private LinearLayout.LayoutParams creatorRailParams(boolean last) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(104), dp(124));
        params.setMarginEnd(last ? 0 : dp(5));
        return params;
    }

    private TextView text(String value, float size, int color) {
        TextView text = new TextView(activity);
        text.setText(value == null ? "" : value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private String formatRemaining(long positionMs, long durationMs) {
        if (durationMs <= 0L) return "";
        long remainingMs = Math.max(0L, durationMs - Math.max(0L, positionMs));
        long totalMinutes = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L && minutes > 0L) return hours + "h " + minutes + "m left";
        if (hours > 0L) return hours + "h left";
        return minutes + "m left";
    }

    private String initials(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return "?";
        String[] parts = clean.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(java.util.Locale.US);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(java.util.Locale.US);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
