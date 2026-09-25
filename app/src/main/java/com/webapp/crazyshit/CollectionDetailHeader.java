package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Collapsing artwork header for Series and Category detail screens. */
final class CollectionDetailHeader extends FrameLayout {
    private final boolean series;
    private final String sourceLabel;
    private final TextView meta;
    private final TextView description;

    CollectionDetailHeader(
            Context context,
            String title,
            String collectionUrl,
            String source,
            boolean series
    ) {
        super(context);
        this.series = series;
        this.sourceLabel = NativeFeedBrowserActivity.SOURCE_EFUKT.equals(source)
                ? "EFukt"
                : "CrazyShit";

        setBackgroundColor(Color.BLACK);
        setClipChildren(true);
        setClipToPadding(true);

        ImageView artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setContentDescription(null);
        byte[] localArtwork = EmbeddedBrowseArtwork.get(context, collectionUrl);
        if (localArtwork != null && localArtwork.length > 0) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(localArtwork, 0, localArtwork.length);
            artwork.setImageBitmap(bitmap);
            artwork.setAlpha(0.92f);
            artwork.setScaleX(1.04f);
            artwork.setScaleY(1.04f);
            addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        } else {
            GradientDrawable fallback = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{
                            Color.rgb(3, 8, 10),
                            Color.rgb(4, 24, 30),
                            Color.BLACK
                    }
            );
            artwork.setBackground(fallback);
            addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        }

        View topShade = new View(context);
        topShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(160, 0, 0, 0), Color.TRANSPARENT}
        ));
        FrameLayout.LayoutParams topShadeParams = new FrameLayout.LayoutParams(-1, dp(76));
        topShadeParams.gravity = Gravity.TOP;
        addView(topShade, topShadeParams);

        View bottomShade = new View(context);
        bottomShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb(110, 0, 0, 0),
                        Color.argb(232, 0, 0, 0),
                        Color.BLACK
                }
        ));
        FrameLayout.LayoutParams bottomShadeParams = new FrameLayout.LayoutParams(-1, dp(176));
        bottomShadeParams.gravity = Gravity.BOTTOM;
        addView(bottomShade, bottomShadeParams);

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM);
        copy.setPadding(dp(18), dp(16), dp(18), dp(18));

        TextView eyebrow = new TextView(context);
        eyebrow.setText(series ? "SERIES" : "CATEGORY");
        eyebrow.setTextColor(UiPalette.PRIMARY);
        eyebrow.setTextSize(11f);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        copy.addView(eyebrow, new LinearLayout.LayoutParams(-1, -2));

        TextView heading = new TextView(context);
        heading.setText(title == null ? "" : title.trim());
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(29f);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setMaxLines(2);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(5);
        copy.addView(heading, headingParams);

        meta = new TextView(context);
        meta.setText(buildLoadingMeta());
        meta.setTextColor(Color.rgb(195, 198, 205));
        meta.setTextSize(12f);
        meta.setMaxLines(1);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(6);
        copy.addView(meta, metaParams);

        description = new TextView(context);
        description.setTextColor(Color.rgb(210, 212, 218));
        description.setTextSize(12.5f);
        description.setMaxLines(2);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        description.setVisibility(View.GONE);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(7);
        copy.addView(description, descriptionParams);

        addView(copy, new FrameLayout.LayoutParams(-1, -1));
    }

    void setItemCount(int count) {
        int safe = Math.max(0, count);
        String noun;
        if (series) noun = safe == 1 ? "episode" : "episodes";
        else noun = safe == 1 ? "video" : "videos";
        meta.setText(sourceLabel + "  •  " + safe + " " + noun);
    }

    void setLoading() {
        meta.setText(buildLoadingMeta());
    }

    void setDescription(String value) {
        String clean = value == null ? "" : value.trim();
        description.setText(clean);
        description.setVisibility(clean.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String buildLoadingMeta() {
        return sourceLabel + "  •  Loading " + (series ? "episodes" : "videos");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
