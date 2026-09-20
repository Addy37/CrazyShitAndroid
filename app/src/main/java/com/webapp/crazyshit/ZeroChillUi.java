package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

/** Lightweight, resource-backed styling shared by the ZeroChill application shell. */
final class ZeroChillUi {
    private ZeroChillUi() {
    }

    static int color(Context context, int resource) {
        return ContextCompat.getColor(context, resource);
    }

    static int dimension(Context context, int resource) {
        return context.getResources().getDimensionPixelSize(resource);
    }

    static int background(Context context) {
        boolean oled = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("oled_black_enabled", true);
        return color(context, oled ? R.color.zc_background : R.color.app_background);
    }

    static void applySystemBars(Activity activity) {
        Window window = activity.getWindow();
        int background = background(activity);
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    static Drawable glass(Context context) {
        return ContextCompat.getDrawable(context, R.drawable.zc_glass_surface);
    }

    static Drawable navigationGlass(Context context) {
        return ContextCompat.getDrawable(context, R.drawable.zc_glass_navigation);
    }

    static Drawable panelGlass(Context context) {
        return ContextCompat.getDrawable(context, R.drawable.zc_glass_panel);
    }

    static GradientDrawable rounded(Context context, int fillColor, int strokeColor, int radiusResource) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(context.getResources().getDimension(radiusResource));
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dimension(context, R.dimen.zc_stroke), strokeColor);
        }
        return drawable;
    }

    static void styleTopBar(View bar) {
        if (bar == null) return;
        Context context = bar.getContext();
        boolean glow = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("ambient_feed_glow", true);
        bar.setBackground(ContextCompat.getDrawable(
                context,
                glow ? R.drawable.zc_glass_top_bar : R.drawable.zc_glass_top_bar_plain
        ));
        bar.setElevation(dimension(context, R.dimen.zc_elevation_low));
    }

    static void styleTitle(TextView view) {
        if (view == null) return;
        view.setTextColor(color(view.getContext(), R.color.zc_text_primary));
        view.setTextSize(22f);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    static void styleSecondary(TextView view) {
        if (view == null) return;
        view.setTextColor(color(view.getContext(), R.color.zc_text_secondary));
    }

    static void styleEmpty(TextView view) {
        if (view == null) return;
        Context context = view.getContext();
        styleSecondary(view);
        view.setTextSize(15f);
        view.setLineSpacing(0f, 1.12f);
        view.setGravity(android.view.Gravity.CENTER);
        int padding = dimension(context, R.dimen.zc_space_xl);
        view.setPadding(padding, padding, padding, padding);
    }

    static void styleProgress(ProgressBar progress) {
        if (progress == null) return;
        progress.setIndeterminateTintList(ColorStateList.valueOf(
                color(progress.getContext(), R.color.zc_cyan)));
    }

    static void styleChip(TextView chip, boolean selected) {
        if (chip == null) return;
        Context context = chip.getContext();
        int fill = selected
                ? color(context, R.color.zc_cyan_container)
                : color(context, R.color.zc_surface_glass_strong);
        int stroke = selected
                ? color(context, R.color.zc_cyan)
                : color(context, R.color.zc_divider);
        chip.setTextColor(selected
                ? color(context, R.color.zc_cyan)
                : color(context, R.color.zc_text_secondary));
        chip.setBackground(rounded(context, fill, stroke, R.dimen.zc_radius_pill));
        chip.setSelected(selected);
        ZeroChillMotion.installPressFeedback(chip);
        ZeroChillMotion.animateSelection(chip, selected);
    }

    static void styleCard(View card) {
        if (card == null) return;
        card.setBackground(glass(card.getContext()));
        card.setElevation(dimension(card.getContext(), R.dimen.zc_elevation_low));
        ZeroChillMotion.installPressFeedback(card);
    }

    static void styleMaterialCard(MaterialCardView card, int radiusResource) {
        if (card == null) return;
        Context context = card.getContext();
        card.setCardBackgroundColor(color(context, R.color.zc_surface_glass));
        card.setRadius(dimension(context, radiusResource));
        card.setCardElevation(dimension(context, R.dimen.zc_elevation_low));
        card.setStrokeWidth(dimension(context, R.dimen.zc_stroke));
        card.setStrokeColor(color(context, R.color.zc_edge));
        card.setRippleColor(ColorStateList.valueOf(color(context, R.color.zc_cyan_container)));
    }
}
