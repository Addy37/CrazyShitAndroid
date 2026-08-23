package com.webapp.crazyshit;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.google.android.material.card.MaterialCardView;

import java.lang.reflect.Field;

/** Keeps the unified full/mini video layers above shell-only overlay polish. */
final class UnifiedVideoLayerGuard {
    private UnifiedVideoLayerGuard() {
    }

    static void raise(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        FrameLayout root = root(activity);
        if (root == null) return;

        View detail = null;
        View mini = null;
        View player = null;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof SwipeMinimizeFrameLayout) {
                player = child;
            } else if (child instanceof MaterialCardView) {
                mini = child;
            } else if (child instanceof FrameLayout && containsScrollView((ViewGroup) child)) {
                detail = child;
            }
        }

        if (detail != null && detail.getVisibility() == View.VISIBLE) root.bringChildToFront(detail);
        if (mini != null && mini.getVisibility() == View.VISIBLE) root.bringChildToFront(mini);
        if (player != null && player.getVisibility() == View.VISIBLE) root.bringChildToFront(player);
    }

    private static boolean containsScrollView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ScrollView) return true;
        }
        return false;
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
}
