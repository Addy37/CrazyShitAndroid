package com.webapp.crazyshit;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * One-shot responsive fitment pass for the native screens that are mostly built in Java.
 *
 * This intentionally does not animate or restyle the app. Geometry belongs here, while OLED,
 * motion and navigation chrome remain in their own controllers. Keeping this pass event-driven
 * avoids another permanent view-tree polling loop.
 */
final class ResponsiveFitmentController {
    private static final WeakHashMap<Activity, View> INSET_TARGETS = new WeakHashMap<>();

    private ResponsiveFitmentController() {
    }

    static void applySoon(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> apply(activity));
        decor.postDelayed(() -> apply(activity), 120L);
        decor.postDelayed(() -> apply(activity), 360L);
    }

    static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Configuration config = activity.getResources().getConfiguration();
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        int widthDp = Math.max(1, config.screenWidthDp);

        if (activity instanceof SearchActivity) {
            fitSearch(activity, landscape, widthDp);
        } else if (activity instanceof SettingsActivity) {
            installSafeInsets(activity);
            fitSingleColumn(activity, widthDp, 720);
        } else if (activity instanceof FavoritesActivity) {
            installSafeInsets(activity);
            fitSingleColumn(activity, widthDp, 920);
        } else if (activity instanceof CommentsActivity) {
            fitSecondaryShell(activity, landscape, widthDp, 920, 56);
        } else if (activity instanceof LoginActivity || activity instanceof ProfileActivity) {
            fitSecondaryShell(activity, landscape, widthDp, 980, 56);
        } else if (activity instanceof WebFallbackActivity) {
            fitSecondaryShell(activity, landscape, widthDp, 1100, 54);
        } else if (activity instanceof MemeViewerActivity) {
            installSafeInsets(activity);
            fitSecondaryShell(activity, landscape, widthDp, 1100, 56);
        } else if (activity instanceof NativeFeedBrowserActivity) {
            installSafeInsets(activity);
            fitSecondaryShell(activity, landscape, widthDp, 1100, 54);
        }

        if (activity instanceof NativeMainActivity) {
            NativeMainActivity main = (NativeMainActivity) activity;
            // The responsive shell gets final ownership of geometry after theme/motion passes.
            LandscapeUiController.apply(main);
            SeriesCategoriesNavController.apply(main);
        }
    }

    static void release(Activity activity) {
        if (activity == null) return;
        INSET_TARGETS.remove(activity);
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        if (activity instanceof SearchActivity) {
            closeResolvers(rawField(activity, "adapter"), visited);
        } else if (activity instanceof FavoritesActivity) {
            closeResolvers(activity, visited);
        } else if (activity instanceof VideoDetailActivity) {
            closeResolvers(activity, visited);
        } else if (activity instanceof NativeFeedBrowserActivity) {
            closeResolvers(rawField(activity, "adapter"), visited);
        } else if (activity instanceof NativeMainActivity) {
            Object pagerAdapter = rawField(activity, "primaryPagerAdapter");
            Object pages = rawField(pagerAdapter, "pages");
            if (pages instanceof Object[]) {
                for (Object page : (Object[]) pages) {
                    closeResolvers(rawField(page, "feedAdapter"), visited);
                }
            }
        }
    }

    private static void fitSearch(Activity activity, boolean landscape, int widthDp) {
        View content = activity.findViewById(android.R.id.content);
        View root = firstChild(content);
        if (!(root instanceof FrameLayout)) return;
        View shellView = firstChild(root);
        if (!(shellView instanceof LinearLayout)) return;
        LinearLayout shell = (LinearLayout) shellView;

        setCenteredWidth(activity, shell, widthDp, 980);
        if (shell.getChildCount() >= 3) {
            setHeight(activity, shell.getChildAt(0), landscape ? 54 : 66);
            setHeight(activity, shell.getChildAt(1), landscape ? 50 : 62);
            setHeight(activity, shell.getChildAt(2), landscape ? 44 : 52);
        }

        RecyclerView recycler = findFirst(shell, RecyclerView.class);
        if (recycler != null) {
            recycler.setPadding(0, dp(activity, landscape ? 2 : 4), 0, dp(activity, landscape ? 12 : 22));
        }
    }

    private static void fitSingleColumn(Activity activity, int widthDp, int maxWidthDp) {
        View content = activity.findViewById(android.R.id.content);
        View page = firstChild(content);
        if (page == null) return;
        setCenteredWidth(activity, page, widthDp, maxWidthDp);
    }

    private static void fitSecondaryShell(
            Activity activity,
            boolean landscape,
            int widthDp,
            int maxWidthDp,
            int landscapeTopDp
    ) {
        View content = activity.findViewById(android.R.id.content);
        View root = firstChild(content);
        if (root == null) return;

        View shell = root;
        if (root instanceof FrameLayout) {
            View candidate = firstChild(root);
            if (candidate != null) shell = candidate;
        }
        setCenteredWidth(activity, shell, widthDp, maxWidthDp);

        if (landscape && shell instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) shell;
            if (group.getChildCount() > 0) {
                View top = group.getChildAt(0);
                ViewGroup.LayoutParams params = top.getLayoutParams();
                if (params != null && params.height > 0 && params.height <= dp(activity, 80)) {
                    params.height = dp(activity, landscapeTopDp);
                    top.setLayoutParams(params);
                }
            }
        }
    }

    private static void installSafeInsets(Activity activity) {
        if (INSET_TARGETS.containsKey(activity)) return;
        View content = activity.findViewById(android.R.id.content);
        View target = firstChild(content);
        if (target == null) return;

        final int baseLeft = target.getPaddingLeft();
        final int baseTop = target.getPaddingTop();
        final int baseRight = target.getPaddingRight();
        final int baseBottom = target.getPaddingBottom();
        target.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(
                    baseLeft + left,
                    baseTop + top,
                    baseRight + right,
                    baseBottom + bottom
            );
            return insets;
        });
        INSET_TARGETS.put(activity, target);
        target.requestApplyInsets();
    }

    private static void setCenteredWidth(Activity activity, View view, int widthDp, int maxWidthDp) {
        if (view == null) return;
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw == null) return;
        int wanted = widthDp > maxWidthDp + 48 ? dp(activity, maxWidthDp) : ViewGroup.LayoutParams.MATCH_PARENT;
        if (raw.width == wanted) return;
        raw.width = wanted;
        if (raw instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) raw).gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        }
        view.setLayoutParams(raw);
    }

    private static void setHeight(Activity activity, View view, int valueDp) {
        if (view == null || view.getLayoutParams() == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        int px = dp(activity, valueDp);
        if (params.height == px) return;
        params.height = px;
        view.setLayoutParams(params);
    }

    private static View firstChild(View view) {
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        return group.getChildCount() == 0 ? null : group.getChildAt(0);
    }

    private static <T> T findFirst(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            T found = findFirst(group.getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }

    private static void closeResolvers(Object owner, Set<Object> visited) {
        if (owner == null || visited.contains(owner)) return;
        visited.add(owner);

        if (owner instanceof RenderedThumbnailResolver) {
            ((RenderedThumbnailResolver) owner).close();
            return;
        }

        Object one = rawField(owner, "thumbnailResolver");
        if (one instanceof RenderedThumbnailResolver) closeResolvers(one, visited);

        Object many = rawField(owner, "thumbnailResolvers");
        if (many instanceof RenderedThumbnailResolver[]) {
            for (RenderedThumbnailResolver resolver : (RenderedThumbnailResolver[]) many) {
                closeResolvers(resolver, visited);
            }
        }
    }

    private static Object rawField(Object target, String name) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
