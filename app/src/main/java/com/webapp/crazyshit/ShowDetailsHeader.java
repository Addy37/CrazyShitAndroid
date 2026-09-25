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
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

/** Collapsing hero used by the Shows proof-of-concept details presentation. */
final class ShowDetailsHeader extends FrameLayout {
    interface Listener {
        void onBrowseVideos();
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final ImageView artwork;
    private final TextView count;
    private final String collectionUrl;
    private final String itemNoun;

    ShowDetailsHeader(
            Context context,
            String title,
            String sourceLabel,
            String description,
            String collectionUrl,
            String imageUrl,
            String itemNoun,
            Listener listener
    ) {
        super(context);
        this.collectionUrl = collectionUrl == null ? "" : collectionUrl;
        this.itemNoun = "episode".equalsIgnoreCase(itemNoun) ? "episode" : "video";
        setBackgroundColor(Color.BLACK);

        artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackground(new ColorDrawable(Color.rgb(14, 16, 19)));
        addView(artwork, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(context);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(12, 0, 0, 0),
                        Color.argb(58, 0, 0, 0),
                        Color.argb(158, 0, 0, 0),
                        Color.BLACK
                }
        ));
        addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM);
        copy.setPadding(dp(18), dp(18), dp(18), dp(18));
        addView(copy, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        TextView source = text(clean(sourceLabel, "SHOW"), 11f, UiPalette.PRIMARY);
        source.setTypeface(null, android.graphics.Typeface.BOLD);
        source.setLetterSpacing(0.10f);
        copy.addView(source);

        TextView heading = text(clean(title, "Show"), 28f, Color.WHITE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setMaxLines(2);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(4);
        copy.addView(heading, headingParams);

        String cleanDescription = description == null ? "" : description.trim();
        if (!cleanDescription.isEmpty()) {
            TextView descriptionView = text(
                    cleanDescription,
                    13f,
                    ZeroChillUi.color(context, R.color.zc_text_secondary)
            );
            descriptionView.setMaxLines(2);
            descriptionView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            descriptionView.setLineSpacing(0f, 1.06f);
            LinearLayout.LayoutParams descriptionParams =
                    new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(5);
            copy.addView(descriptionView, descriptionParams);
        }

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(8);
        copy.addView(metaRow, metaParams);

        count = text(
                "Loading " + pluralItemNoun() + "…",
                12f,
                ZeroChillUi.color(context, R.color.zc_text_secondary)
        );
        metaRow.addView(count, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView action = text("BROWSE " + pluralItemNoun().toUpperCase(java.util.Locale.US), 12f, Color.BLACK);
        action.setTypeface(null, android.graphics.Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription(
                "Browse " + pluralItemNoun() + " in " + clean(title, "this show")
        );
        GradientDrawable actionBackground = new GradientDrawable();
        actionBackground.setColor(UiPalette.PRIMARY);
        actionBackground.setCornerRadius(dp(19));
        action.setBackground(actionBackground);
        action.setClickable(true);
        action.setFocusable(true);
        ZeroChillMotion.installPressFeedback(action);
        action.setOnClickListener(v -> {
            if (listener != null) listener.onBrowseVideos();
        });
        metaRow.addView(action, new LinearLayout.LayoutParams(dp(132), dp(38)));

        loadArtwork(imageUrl);
    }

    void setItemCount(int itemCount) {
        if (itemCount <= 0) {
            count.setText("No " + pluralItemNoun() + " loaded yet");
        } else if (itemCount == 1) {
            count.setText("1 " + itemNoun);
        } else {
            count.setText(itemCount + " " + pluralItemNoun());
        }
    }

    private String pluralItemNoun() {
        return itemNoun + "s";
    }

    private void loadArtwork(String imageUrl) {
        byte[] embedded = EmbeddedBrowseArtwork.get(getContext(), collectionUrl);
        if (embedded != null && embedded.length >= 512) {
            Glide.with(artwork)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .centerCrop()
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.rgb(14, 16, 19)))
                    .error(new ColorDrawable(Color.rgb(14, 16, 19)))
                    .into(artwork);
            return;
        }

        String cleanImage = imageUrl == null ? "" : imageUrl.trim();
        if (cleanImage.isEmpty()) return;

        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT);
        if (collectionUrl.startsWith("http")) {
            headers.addHeader("Referer", collectionUrl);
        }

        Glide.with(artwork)
                .load(new GlideUrl(cleanImage, headers.build()))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .dontAnimate()
                .placeholder(new ColorDrawable(Color.rgb(14, 16, 19)))
                .error(new ColorDrawable(Color.rgb(14, 16, 19)))
                .into(artwork);
    }

    @Override
    protected void onDetachedFromWindow() {
        Glide.with(artwork).clear(artwork);
        super.onDetachedFromWindow();
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String clean(String value, String fallback) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? fallback : clean;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
