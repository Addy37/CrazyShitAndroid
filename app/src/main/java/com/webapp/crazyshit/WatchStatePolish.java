package com.webapp.crazyshit;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Small v2.4.1 polish layer for feed watch-state indicators.
 *
 * Keeps the v2.4 adapter/data behavior intact while tightening the badge treatment and applying
 * the same styling to newly attached RecyclerView children. It also gives a short resume cue when
 * a saved in-progress video is reopened.
 */
final class WatchStatePolish {
    private static final long MIN_RESUME_MS = 5_000L;
    private static final WeakHashMap<RecyclerView, RecyclerView.OnChildAttachStateChangeListener>
            ATTACHED_RECYCLERS = new WeakHashMap<>();

    private WatchStatePolish() {
    }

    static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        applyTree(root);
        attachRecyclerListeners(root);

        View decor = activity.getWindow().getDecorView();
        decor.postDelayed(() -> {
            if (activity.isFinishing()) return;
            View current = activity.findViewById(android.R.id.content);
            if (current != null) {
                applyTree(current);
                attachRecyclerListeners(current);
            }
        }, 180L);
        decor.postDelayed(() -> {
            if (activity.isFinishing()) return;
            View current = activity.findViewById(android.R.id.content);
            if (current != null) applyTree(current);
        }, 650L);
    }

    static void showResumeToast(VideoDetailActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isShowsOrigin()) return;
        if (!activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("remember_video_position", true)) return;

        Intent intent = activity.getIntent();
        if (intent == null) return;
        String pageUrl = clean(intent.getStringExtra(PlayerActivity.EXTRA_PAGE_URL));
        if (pageUrl.isEmpty()) return;

        for (PlaybackHistoryStore.Item item : PlaybackHistoryStore.load(activity)) {
            if (!pageUrl.equals(item.pageUrl)) continue;
            if (item.complete || item.positionMs < MIN_RESUME_MS) return;
            if (item.durationMs > 0L && item.positionMs >= (long) (item.durationMs * 0.95f)) return;
            Toast.makeText(
                    activity,
                    "Resuming at " + formatTime(item.positionMs),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
    }

    private static void attachRecyclerListeners(View view) {
        if (view instanceof RecyclerView) {
            RecyclerView recycler = (RecyclerView) view;
            synchronized (ATTACHED_RECYCLERS) {
                if (!ATTACHED_RECYCLERS.containsKey(recycler)) {
                    RecyclerView.OnChildAttachStateChangeListener listener =
                            new RecyclerView.OnChildAttachStateChangeListener() {
                                @Override
                                public void onChildViewAttachedToWindow(@NonNull View child) {
                                    child.post(() -> applyTree(child));
                                }

                                @Override
                                public void onChildViewDetachedFromWindow(@NonNull View child) {
                                }
                            };
                    recycler.addOnChildAttachStateChangeListener(listener);
                    ATTACHED_RECYCLERS.put(recycler, listener);
                }
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            attachRecyclerListeners(group.getChildAt(i));
        }
    }

    private static void applyTree(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence value = text.getText();
            String label = value == null ? "" : value.toString().trim();
            if (label.startsWith("Continue")) {
                styleContinue(text, label);
            } else if ("✓ Watched".equals(label)) {
                styleWatched(text);
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            applyTree(group.getChildAt(i));
        }
    }

    private static void styleContinue(TextView badge, String currentLabel) {
        String time = currentLabel.substring("Continue".length()).replace("·", "").trim();
        badge.setText(time.isEmpty() ? "Continue" : "Continue · " + time);
        badge.setTextSize(9.5f);
        badge.setPadding(dp(badge, 7), dp(badge, 3), dp(badge, 7), dp(badge, 3));
        badge.setBackground(rounded(Color.argb(205, 77, 75, 6), dp(badge, 11)));
        styleMediaFrame(badge, true);
    }

    private static void styleWatched(TextView badge) {
        badge.setTextSize(9.5f);
        badge.setPadding(dp(badge, 7), dp(badge, 3), dp(badge, 7), dp(badge, 3));
        badge.setBackground(rounded(Color.argb(205, 22, 22, 26), dp(badge, 11)));
        styleMediaFrame(badge, false);
    }

    private static void styleMediaFrame(TextView badge, boolean partial) {
        if (!(badge.getParent() instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) badge.getParent();
        for (int i = 0; i < frame.getChildCount(); i++) {
            View child = frame.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && "▶".contentEquals(text)) {
                    child.setBackground(new ColorDrawable(Color.argb(partial ? 96 : 112, 0, 0, 0)));
                }
                continue;
            }
            if (!(child instanceof FrameLayout)) continue;
            ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (!(raw instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
            if ((params.gravity & Gravity.BOTTOM) == 0) continue;
            if (params.height <= 0 || params.height > dp(child, 5)) continue;
            child.setBackgroundColor(Color.argb(105, 16, 16, 20));
        }
    }

    private static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private static String formatTime(long millis) {
        long total = Math.max(0L, millis / 1000L);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0L) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
