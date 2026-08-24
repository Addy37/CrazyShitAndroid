package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/** v2.7 cleanup for the native video details page. */
final class VideoDetailImmersivePolish {
    private static final Map<VideoDetailActivity, Boolean> ENTERED = new WeakHashMap<>();

    private VideoDetailImmersivePolish() {
    }

    static void applySoon(VideoDetailActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> apply(activity));
        decor.postDelayed(() -> apply(activity), 180L);
        decor.postDelayed(() -> apply(activity), 620L);
    }

    static void detach(VideoDetailActivity activity) {
        ENTERED.remove(activity);
    }

    private static void apply(VideoDetailActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        LinearLayout details = field(activity, "detailsColumn", LinearLayout.class);
        ScrollView scroll = field(activity, "detailsScroll", ScrollView.class);
        View player = field(activity, "playerContainer", View.class);
        LinearLayout related = field(activity, "relatedContainer", LinearLayout.class);
        TextView commentsTitle = field(activity, "commentsTitle", TextView.class);
        TextView meta = field(activity, "metaView", TextView.class);
        TextView title = field(activity, "titleView", TextView.class);
        if (details == null) return;

        details.setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 30));
        if (title != null) {
            title.setTextSize(21f);
            title.setLineSpacing(0f, 1.08f);
        }
        if (meta != null) {
            meta.setPadding(0, dp(activity, 6), 0, dp(activity, 9));
        }

        styleActionRow(activity, details, meta);
        removeDuplicateCommentsCard(details, commentsTitle);
        styleRelated(activity, related);
        styleDetailsBackground(activity, scroll);
        playEntranceOnce(activity, player, scroll);
    }

    private static void styleActionRow(Activity activity, LinearLayout details, TextView meta) {
        int start = meta == null ? 0 : details.indexOfChild(meta) + 1;
        LinearLayout actions = null;
        for (int i = Math.max(0, start); i < Math.min(details.getChildCount(), start + 3); i++) {
            View child = details.getChildAt(i);
            if (child instanceof LinearLayout && ((LinearLayout) child).getOrientation() == LinearLayout.HORIZONTAL) {
                actions = (LinearLayout) child;
                break;
            }
        }
        if (actions == null) return;

        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, 0, 0, dp(activity, 3));
        String comments = stringField(activity, "comments");
        for (int i = 0; i < actions.getChildCount(); i++) {
            View child = actions.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView button = (TextView) child;
            String old = button.getText() == null ? "" : button.getText().toString();
            if (old.toLowerCase().contains("comment")) {
                button.setText(comments.isEmpty() ? "💬 Comments" : "💬 " + comments);
            } else if (old.toLowerCase().contains("watch")) {
                button.setText("♡ Later");
            } else if (old.toLowerCase().contains("share")) {
                button.setText("↗ Share");
            }
            button.setTextSize(13f);
            button.setGravity(Gravity.CENTER);
            button.setTextColor(Color.rgb(238, 238, 242));
            button.setBackground(pill(activity));
            LinearLayout.LayoutParams lp;
            if (button.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                lp = (LinearLayout.LayoutParams) button.getLayoutParams();
            } else {
                lp = new LinearLayout.LayoutParams(0, dp(activity, 40), 1f);
            }
            lp.height = dp(activity, 40);
            lp.weight = 1f;
            lp.setMargins(dp(activity, 3), 0, dp(activity, 3), 0);
            button.setLayoutParams(lp);
        }
    }

    private static GradientDrawable pill(Activity activity) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(27, 27, 32));
        bg.setCornerRadius(dp(activity, 20));
        bg.setStroke(dp(activity, 1), Color.rgb(55, 55, 63));
        return bg;
    }

    private static void removeDuplicateCommentsCard(LinearLayout details, TextView commentsTitle) {
        if (details == null || commentsTitle == null) return;
        View direct = commentsTitle;
        while (direct.getParent() instanceof View && direct.getParent() != details) {
            direct = (View) direct.getParent();
        }
        if (direct.getParent() == details && direct instanceof MaterialCardView) {
            details.removeView(direct);
        }
    }

    private static void styleRelated(Activity activity, LinearLayout related) {
        if (related == null) return;
        for (int i = 0; i < related.getChildCount(); i++) {
            View child = related.getChildAt(i);
            MaterialCardView card = child instanceof MaterialCardView
                    ? (MaterialCardView) child : findCard(child);
            if (card == null) continue;
            card.setCardBackgroundColor(Color.rgb(23, 23, 27));
            card.setRadius(dp(activity, 18));
            card.setCardElevation(0f);
            card.setStrokeWidth(dp(activity, 1));
            card.setStrokeColor(Color.rgb(49, 49, 57));
        }
    }

    private static void styleDetailsBackground(Activity activity, ScrollView scroll) {
        if (scroll == null) return;
        int orangeGlow = Color.rgb(36, 20, 15);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {orangeGlow, Color.rgb(16, 16, 19), Color.rgb(13, 13, 15)}
        );
        scroll.setBackground(bg);
    }

    private static void playEntranceOnce(VideoDetailActivity activity, View player, ScrollView scroll) {
        if (ENTERED.containsKey(activity)) return;
        ENTERED.put(activity, Boolean.TRUE);
        if (player != null) {
            player.animate().cancel();
            player.setPivotX(player.getWidth() * 0.5f);
            player.setPivotY(0f);
            player.setScaleX(0.94f);
            player.setScaleY(0.94f);
            player.setAlpha(0.25f);
            player.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(260L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        if (scroll != null) {
            scroll.animate().cancel();
            scroll.setAlpha(0f);
            scroll.setTranslationY(dp(activity, 18));
            scroll.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(70L)
                    .setDuration(280L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private static MaterialCardView findCard(View view) {
        if (view instanceof MaterialCardView) return (MaterialCardView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            MaterialCardView card = findCard(group.getChildAt(i));
            if (card != null) return card;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name, Class<T> type) {
        Object value = rawField(target, name);
        return type.isInstance(value) ? (T) value : null;
    }

    private static String stringField(Object target, String name) {
        Object value = rawField(target, name);
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static Object rawField(Object target, String name) {
        if (target == null) return null;
        Field field = findField(target.getClass(), name);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
