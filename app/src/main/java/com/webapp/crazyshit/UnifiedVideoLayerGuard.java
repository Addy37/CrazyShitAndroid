package com.webapp.crazyshit;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.google.android.material.card.MaterialCardView;

import java.lang.reflect.Field;

/**
 * Event-driven visual polish for the unified video session.
 *
 * v2.3.2 intentionally does not run a frame loop. It configures the unified layers once and then
 * reacts only to real swipe progress/release callbacks from SwipeMinimizeFrameLayout.
 */
final class UnifiedVideoLayerGuard {
    private UnifiedVideoLayerGuard() {
    }

    static void raise(NativeMainActivity activity) {
        attach(activity, 0);
    }

    private static void attach(NativeMainActivity activity, int attempt) {
        if (activity == null || activity.isFinishing()) return;
        FrameLayout root = root(activity);
        if (root == null) return;

        View detail = null;
        ScrollView details = null;
        MaterialCardView mini = null;
        SwipeMinimizeFrameLayout player = null;

        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof SwipeMinimizeFrameLayout) {
                player = (SwipeMinimizeFrameLayout) child;
            } else if (child instanceof MaterialCardView) {
                mini = (MaterialCardView) child;
            } else if (child instanceof FrameLayout) {
                ScrollView found = findScrollView((ViewGroup) child);
                if (found != null) {
                    detail = child;
                    details = found;
                }
            }
        }

        if (player == null) {
            if (attempt < 3) {
                activity.getWindow().getDecorView().postDelayed(
                        () -> attach(activity, attempt + 1),
                        120L + (attempt * 80L)
                );
            }
            return;
        }

        final View detailView = detail;
        final ScrollView detailsView = details;
        final MaterialCardView miniCard = mini;
        final SwipeMinimizeFrameLayout livePlayer = player;

        // Permanent stacking for this unified session. The live TextureView must sit above the
        // mini-card placeholder, but we never reorder these views every frame.
        if (miniCard != null) {
            miniCard.setCardElevation(dp(activity, 9));
            miniCard.setTranslationZ(0f);
            FrameLayout slot = findEmptyFrame(miniCard);
            if (slot != null) {
                slot.setBackgroundColor(Color.TRANSPARENT);
                slot.setElevation(0f);
                slot.setTranslationZ(0f);
            }
        }
        livePlayer.setElevation(dp(activity, 28));
        livePlayer.setTranslationZ(0f);
        if (livePlayer.getVisibility() == View.VISIBLE) root.bringChildToFront(livePlayer);

        livePlayer.setVisualObserver(new SwipeMinimizeFrameLayout.Listener() {
            private boolean prepared;

            @Override
            public void onDrag(float distancePx, float progress) {
                if (!prepared) {
                    prepareStack(root, miniCard, livePlayer);
                    prepared = true;
                }
                if (detailsView != null) {
                    detailsView.animate().cancel();
                    // Text/cards disappear during the first part of the gesture. The controller's
                    // dark detail backdrop still fades normally underneath the live video.
                    float alpha = 1f - clamp(progress * 4.2f);
                    detailsView.setAlpha(alpha);
                }
            }

            @Override
            public void onRelease(boolean minimize, float distancePx) {
                if (minimize) {
                    prepareStack(root, miniCard, livePlayer);
                    if (detailsView != null) {
                        detailsView.animate().cancel();
                        detailsView.animate()
                                .alpha(0f)
                                .setDuration(75L)
                                .start();
                    }
                } else if (detailsView != null) {
                    detailsView.animate().cancel();
                    detailsView.animate()
                            .alpha(1f)
                            .setDuration(160L)
                            .start();
                }
                prepared = false;
            }
        });

        // Full mode should start with normal detail content. Expand/showFull in the unified
        // controller also resets this to 1, so mini -> full remains deterministic.
        if (detailView != null && detailView.getVisibility() == View.VISIBLE &&
                (miniCard == null || miniCard.getVisibility() != View.VISIBLE)) {
            if (detailsView != null) detailsView.setAlpha(1f);
        }
    }

    private static void prepareStack(
            FrameLayout root,
            MaterialCardView mini,
            SwipeMinimizeFrameLayout player
    ) {
        if (mini != null) {
            mini.setCardElevation(dp(player, 9));
            FrameLayout slot = findEmptyFrame(mini);
            if (slot != null) slot.setBackgroundColor(Color.TRANSPARENT);
        }
        player.setElevation(dp(player, 28));
        root.bringChildToFront(player);
    }

    private static ScrollView findScrollView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ScrollView) return (ScrollView) child;
        }
        return null;
    }

    private static FrameLayout findEmptyFrame(View view) {
        if (view instanceof FrameLayout) {
            FrameLayout frame = (FrameLayout) view;
            if (frame.getChildCount() == 0 && frame.getLayoutParams() != null &&
                    frame.getLayoutParams().width > 0 && frame.getLayoutParams().height > 0) {
                return frame;
            }
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            FrameLayout found = findEmptyFrame(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static FrameLayout root(NativeMainActivity activity) {
        Class<?> type = activity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("overlayRoot");
                field.setAccessible(true);
                Object value = field.get(activity);
                return value instanceof FrameLayout ? (FrameLayout) value : null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
