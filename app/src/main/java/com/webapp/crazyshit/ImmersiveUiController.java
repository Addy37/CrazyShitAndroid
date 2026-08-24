package com.webapp.crazyshit;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v2.7 visual layer: ambient feed color, compact collapsing chrome, floating navigation,
 * translucent media cards and very small scroll depth motion. This controller intentionally
 * leaves feed data and navigation behavior untouched.
 */
final class ImmersiveUiController {
    private static final int APP_BG = Color.rgb(13, 13, 15);
    private static final int CARD_BG = Color.rgb(24, 24, 28);
    private static final Map<Activity, State> STATES = new WeakHashMap<>();

    private ImmersiveUiController() {
    }

    static void attachMain(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> applyMain(activity));
        decor.postDelayed(() -> applyMain(activity), 120L);
        decor.postDelayed(() -> applyMain(activity), 420L);
    }

    static void attachBrowser(NativeFeedBrowserActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> applyBrowser(activity));
        decor.postDelayed(() -> applyBrowser(activity), 140L);
        decor.postDelayed(() -> applyBrowser(activity), 420L);
    }

    static void detach(Activity activity) {
        if (activity == null) return;
        State state = STATES.remove(activity);
        if (state == null) return;
        unbindRecycler(state);
        if (activity instanceof NativeMainActivity && state.pager != null && state.pageCallback != null) {
            try {
                state.pager.unregisterOnPageChangeCallback(state.pageCallback);
            } catch (Exception ignored) {
            }
        }
        if (state.ambientAnimator != null) state.ambientAnimator.cancel();
    }

    private static void applyMain(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State existing = STATES.get(activity);
        if (existing == null) {
            existing = new State(activity);
            STATES.put(activity, existing);
        }
        final State state = existing;

        FrameLayout overlay = field(activity, "overlayRoot", FrameLayout.class);
        LinearLayout shell = field(activity, "shell", LinearLayout.class);
        BottomNavigationView nav = field(activity, "bottomNavigation", BottomNavigationView.class);
        ViewPager2 pager = field(activity, "primaryPager", ViewPager2.class);
        TextView title = field(activity, "headerTitle", TextView.class);
        TextView subtitle = field(activity, "headerSubtitle", TextView.class);
        if (overlay == null || shell == null || pager == null) return;

        boolean portrait = activity.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        installAmbientLayer(activity, state, overlay, shell);
        stylePagerTransparency(pager);

        View topBar = shell.getChildCount() > 0 ? shell.getChildAt(0) : null;
        state.topBar = topBar;
        state.headerTitle = title;
        state.headerSubtitle = subtitle;
        state.bottomNav = nav;

        if (portrait) {
            styleTopBar(activity, topBar);
            styleBottomNav(activity, nav);
        } else {
            resetHeader(activity, state);
        }

        if (state.pager != pager || state.pageCallback == null) {
            if (state.pager != null && state.pageCallback != null) {
                try {
                    state.pager.unregisterOnPageChangeCallback(state.pageCallback);
                } catch (Exception ignored) {
                }
            }
            state.pager = pager;
            state.pageCallback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    state.collapse = 0f;
                    resetHeader(activity, state);
                    pager.postDelayed(() -> bindCurrentMainRecycler(activity, state), 60L);
                }
            };
            pager.registerOnPageChangeCallback(state.pageCallback);
        }

        bindCurrentMainRecycler(activity, state);
    }

    private static void applyBrowser(NativeFeedBrowserActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State existing = STATES.get(activity);
        if (existing == null) {
            existing = new State(activity);
            STATES.put(activity, existing);
        }
        final State state = existing;
        RecyclerView recycler = field(activity, "recycler", RecyclerView.class);
        if (recycler == null) return;

        View content = activity.findViewById(android.R.id.content);
        View shell = content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0
                ? ((ViewGroup) content).getChildAt(0) : content;
        View topBar = shell instanceof ViewGroup && ((ViewGroup) shell).getChildCount() > 0
                ? ((ViewGroup) shell).getChildAt(0) : null;

        state.ambientTarget = shell;
        state.topBar = topBar;
        state.headerTitle = largestText(topBar);
        state.headerSubtitle = null;
        styleTopBar(activity, topBar);
        makeTransparent(recycler);
        if (recycler.getParent() instanceof View) makeTransparent((View) recycler.getParent());
        setAmbientBackground(state, APP_BG, false);
        bindRecycler(activity, state, recycler);
    }

    private static void bindCurrentMainRecycler(NativeMainActivity activity, State state) {
        ViewPager2 pager = field(activity, "primaryPager", ViewPager2.class);
        Object pagerAdapter = rawField(activity, "primaryPagerAdapter");
        if (pager == null || pagerAdapter == null) return;
        int position = pager.getCurrentItem();
        if (position == MainPagerAdapter.PAGE_CHAOS) {
            unbindRecycler(state);
            setAmbientBackground(state, APP_BG, true);
            return;
        }

        Object rawPages = rawField(pagerAdapter, "pages");
        if (!(rawPages instanceof Object[])) return;
        Object[] pages = (Object[]) rawPages;
        if (position < 0 || position >= pages.length || pages[position] == null) return;
        Object page = pages[position];
        RecyclerView recycler = field(page, "recycler", RecyclerView.class);
        View root = field(page, "root", View.class);
        View refresh = field(page, "refresh", View.class);
        makeTransparent(root);
        makeTransparent(refresh);
        makeTransparent(recycler);
        stylePagerTransparency(pager);
        if (recycler != null) bindRecycler(activity, state, recycler);
    }

    private static void bindRecycler(Activity activity, State state, RecyclerView recycler) {
        if (recycler == null) return;
        if (state.recycler == recycler && state.scrollListener != null && state.childAttachListener != null) {
            applyDepth(activity, recycler);
            scheduleAmbient(activity, state, recycler, 30L);
            return;
        }
        unbindRecycler(state);
        state.recycler = recycler;
        state.scrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (activity.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                    updateHeaderCollapse(activity, state, view, dy);
                }
                applyDepth(activity, view);
                scheduleAmbient(activity, state, view, 80L);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView view, int newState) {
                applyDepth(activity, view);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scheduleAmbient(activity, state, view, 0L);
                }
            }
        };
        state.childAttachListener = new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                styleMediaCard(activity, view);
                recycler.post(() -> applyDepth(activity, recycler));
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                view.animate().cancel();
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setAlpha(1f);
                view.setTranslationZ(0f);
                ImageView image = largestImage(view);
                if (image != null) image.setTranslationY(0f);
            }
        };
        recycler.addOnScrollListener(state.scrollListener);
        recycler.addOnChildAttachStateChangeListener(state.childAttachListener);
        recycler.setClipToPadding(false);
        applyDepth(activity, recycler);
        scheduleAmbient(activity, state, recycler, 30L);
    }

    private static void unbindRecycler(State state) {
        if (state.recycler != null && state.scrollListener != null) {
            try {
                state.recycler.removeOnScrollListener(state.scrollListener);
            } catch (Exception ignored) {
            }
        }
        if (state.recycler != null && state.childAttachListener != null) {
            try {
                state.recycler.removeOnChildAttachStateChangeListener(state.childAttachListener);
            } catch (Exception ignored) {
            }
        }
        state.recycler = null;
        state.scrollListener = null;
        state.childAttachListener = null;
    }

    private static void stylePagerTransparency(ViewPager2 pager) {
        if (pager == null) return;
        makeTransparent(pager);
        if (pager.getChildCount() > 0 && pager.getChildAt(0) instanceof ViewGroup) {
            ViewGroup internal = (ViewGroup) pager.getChildAt(0);
            makeTransparent(internal);
            for (int i = 0; i < internal.getChildCount(); i++) {
                makeTransparent(internal.getChildAt(i));
            }
        }
    }

    private static void installAmbientLayer(
            NativeMainActivity activity,
            State state,
            FrameLayout overlay,
            LinearLayout shell
    ) {
        if (state.ambientView == null || state.ambientView.getParent() != overlay) {
            View ambient = new View(activity);
            ambient.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            overlay.addView(ambient, 0, new FrameLayout.LayoutParams(-1, -1));
            state.ambientView = ambient;
            state.ambientTarget = ambient;
        }
        shell.setBackgroundColor(Color.TRANSPARENT);
        overlay.setBackgroundColor(APP_BG);
        setAmbientBackground(state, state.currentAmbient, false);
    }

    private static void styleTopBar(Activity activity, View topBar) {
        if (topBar == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(244, 18, 18, 22));
        float radius = dp(activity, 22);
        bg.setCornerRadii(new float[] {0f, 0f, 0f, 0f, radius, radius, radius, radius});
        topBar.setBackground(bg);
        topBar.setElevation(dp(activity, 5));
    }

    private static void styleBottomNav(Activity activity, BottomNavigationView nav) {
        if (nav == null || nav.getVisibility() != View.VISIBLE) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(238, 23, 23, 28));
        bg.setCornerRadius(dp(activity, 30));
        bg.setStroke(dp(activity, 1), Color.rgb(53, 53, 61));
        nav.setBackground(bg);
        nav.setElevation(dp(activity, 18));
        nav.setItemRippleColor(ColorStateList.valueOf(Color.argb(40, 255, 90, 31)));
        try {
            nav.setItemActiveIndicatorEnabled(true);
            nav.setItemActiveIndicatorColor(ColorStateList.valueOf(Color.argb(72, 255, 90, 31)));
        } catch (Throwable ignored) {
        }

        if (nav.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) nav.getLayoutParams();
            lp.height = dp(activity, 70);
            lp.setMargins(dp(activity, 10), dp(activity, 3), dp(activity, 10), dp(activity, 8));
            nav.setLayoutParams(lp);
        }
    }

    private static void updateHeaderCollapse(
            Activity activity,
            State state,
            RecyclerView recycler,
            int dy
    ) {
        if (state.topBar == null) return;
        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        if (!prefs.getBoolean("collapse_header_enabled", true)) {
            state.collapse = 0f;
            resetHeader(activity, state);
            return;
        }
        if (!recycler.canScrollVertically(-1)) {
            state.collapse = 0f;
        } else {
            float delta = dy / (float) Math.max(1, dp(activity, 150));
            state.collapse = clamp(state.collapse + delta, 0f, 1f);
        }
        applyHeaderProgress(activity, state, state.collapse);
    }

    private static void resetHeader(Activity activity, State state) {
        state.collapse = 0f;
        applyHeaderProgress(activity, state, 0f);
    }

    private static void applyHeaderProgress(Activity activity, State state, float progress) {
        View top = state.topBar;
        if (top == null) return;
        ViewGroup.LayoutParams raw = top.getLayoutParams();
        if (raw != null) {
            int expanded = activity instanceof NativeFeedBrowserActivity ? 64 : 70;
            int collapsed = 50;
            raw.height = dp(activity, Math.round(expanded + (collapsed - expanded) * progress));
            top.setLayoutParams(raw);
        }
        if (state.headerTitle != null) {
            float normal = activity instanceof NativeFeedBrowserActivity ? 20f : 19f;
            state.headerTitle.setTextSize(normal - (2.2f * progress));
            state.headerTitle.setTranslationY(dp(activity, 2) * progress);
        }
        if (state.headerSubtitle != null) {
            state.headerSubtitle.setAlpha(1f - progress);
            state.headerSubtitle.setScaleY(1f - (0.15f * progress));
        }
        ImageView icon = firstImage(top);
        if (icon != null) {
            float s = 1f - (0.18f * progress);
            icon.setScaleX(s);
            icon.setScaleY(s);
            icon.setAlpha(1f - (0.18f * progress));
        }
    }

    private static void applyDepth(Activity activity, RecyclerView recycler) {
        if (recycler == null || recycler.getHeight() <= 0) return;
        boolean motion = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("immersive_motion_enabled", true);
        float center = recycler.getHeight() * 0.5f;
        float range = Math.max(1f, recycler.getHeight() * 0.62f);
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            styleMediaCard(activity, child);
            if (!motion || !hasMedia(child)) {
                child.setScaleX(1f);
                child.setScaleY(1f);
                child.setAlpha(1f);
                child.setTranslationZ(0f);
                ImageView image = largestImage(child);
                if (image != null) image.setTranslationY(0f);
                continue;
            }
            float childCenter = (child.getTop() + child.getBottom()) * 0.5f;
            float focus = 1f - Math.min(1f, Math.abs(childCenter - center) / range);
            float scale = 0.985f + (0.015f * focus);
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(0.92f + (0.08f * focus));
            child.setTranslationZ(dp(activity, 1f + (3f * focus)));

            ImageView image = largestImage(child);
            if (image != null) {
                float parallax = clamp((center - childCenter) / range, -1f, 1f);
                image.setTranslationY(dp(activity, 2.5f) * parallax);
            }
        }
    }

    private static void styleMediaCard(Activity activity, View child) {
        MaterialCardView card = child instanceof MaterialCardView
                ? (MaterialCardView) child : findCard(child);
        if (card == null || !hasMedia(card)) return;
        card.setCardBackgroundColor(Color.argb(239, Color.red(CARD_BG), Color.green(CARD_BG), Color.blue(CARD_BG)));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(activity, 1));
        card.setStrokeColor(Color.rgb(54, 54, 62));
        card.setRadius(Math.max(card.getRadius(), dp(activity, 17)));
    }

    private static void scheduleAmbient(Activity activity, State state, RecyclerView recycler, long delay) {
        if (state.ambientPosted) return;
        state.ambientPosted = true;
        recycler.postDelayed(() -> {
            state.ambientPosted = false;
            if (state.recycler != recycler || activity.isFinishing()) return;
            updateAmbient(activity, state, recycler);
        }, delay);
    }

    private static void updateAmbient(Activity activity, State state, RecyclerView recycler) {
        if (recycler == null || recycler.getHeight() <= 0 || state.ambientTarget == null) return;
        boolean enabled = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("ambient_feed_glow", true);
        if (!enabled) {
            setAmbientBackground(state, APP_BG, true);
            return;
        }

        float center = recycler.getHeight() * 0.45f;
        View best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            if (!hasMedia(child)) continue;
            float childCenter = (child.getTop() + child.getBottom()) * 0.5f;
            float d = Math.abs(childCenter - center);
            if (d < bestDistance) {
                bestDistance = d;
                best = child;
            }
        }
        ImageView image = largestImage(best);
        int sampled = sampleImageColor(image);
        if (sampled == 0) return;
        int target = blend(APP_BG, sampled, 0.42f);
        setAmbientBackground(state, target, true);
    }

    private static void setAmbientBackground(State state, int target, boolean animate) {
        if (state.ambientTarget == null) return;
        if (state.currentAmbient == 0) state.currentAmbient = APP_BG;
        if (colorDistance(state.currentAmbient, target) < 10) return;
        if (state.ambientAnimator != null) state.ambientAnimator.cancel();
        if (!animate) {
            state.currentAmbient = target;
            applyAmbientDrawable(state.ambientTarget, target);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), state.currentAmbient, target);
        state.ambientAnimator = animator;
        animator.setDuration(320L);
        animator.addUpdateListener(value -> {
            int color = (Integer) value.getAnimatedValue();
            state.currentAmbient = color;
            applyAmbientDrawable(state.ambientTarget, color);
        });
        animator.start();
    }

    private static void applyAmbientDrawable(View target, int color) {
        int mid = blend(APP_BG, color, 0.45f);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {color, mid, APP_BG, APP_BG}
        );
        target.setBackground(gradient);
    }

    private static int sampleImageColor(ImageView image) {
        if (image == null) return 0;
        Drawable drawable = image.getDrawable();
        if (!(drawable instanceof BitmapDrawable)) return 0;
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() < 2 || bitmap.getHeight() < 2) return 0;
        long r = 0L, g = 0L, b = 0L, count = 0L;
        int steps = 5;
        for (int y = 1; y <= steps; y++) {
            int py = Math.min(bitmap.getHeight() - 1, Math.max(0, y * bitmap.getHeight() / (steps + 1)));
            for (int x = 1; x <= steps; x++) {
                int px = Math.min(bitmap.getWidth() - 1, Math.max(0, x * bitmap.getWidth() / (steps + 1)));
                int c;
                try {
                    c = bitmap.getPixel(px, py);
                } catch (Exception ignored) {
                    continue;
                }
                if (Color.alpha(c) < 128) continue;
                float[] hsv = new float[3];
                Color.colorToHSV(c, hsv);
                if (hsv[2] < 0.08f) continue;
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
                count++;
            }
        }
        if (count == 0L) return 0;
        int average = Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        float[] hsv = new float[3];
        Color.colorToHSV(average, hsv);
        hsv[1] = Math.min(0.82f, Math.max(0.26f, hsv[1] * 1.2f));
        hsv[2] = Math.min(0.56f, Math.max(0.28f, hsv[2] * 0.72f));
        return Color.HSVToColor(hsv);
    }

    private static int blend(int a, int b, float amount) {
        float t = clamp(amount, 0f, 1f);
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t)
        );
    }

    private static int colorDistance(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) +
                Math.abs(Color.green(a) - Color.green(b)) +
                Math.abs(Color.blue(a) - Color.blue(b));
    }

    private static boolean hasMedia(View view) {
        return largestImage(view) != null;
    }

    private static ImageView firstImage(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ImageView found = firstImage(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static ImageView largestImage(View view) {
        if (view == null) return null;
        ImageView[] best = new ImageView[1];
        int[] area = new int[] {0};
        findLargestImage(view, best, area);
        return best[0];
    }

    private static void findLargestImage(View view, ImageView[] best, int[] area) {
        if (view instanceof ImageView) {
            int next = Math.max(1, view.getWidth()) * Math.max(1, view.getHeight());
            if (next > area[0]) {
                area[0] = next;
                best[0] = (ImageView) view;
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            findLargestImage(group.getChildAt(i), best, area);
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

    private static TextView largestText(View view) {
        if (view == null) return null;
        TextView[] best = new TextView[1];
        float[] size = new float[] {0f};
        findLargestText(view, best, size);
        return best[0];
    }

    private static void findLargestText(View view, TextView[] best, float[] size) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getTextSize() > size[0]) {
                size[0] = text.getTextSize();
                best[0] = text;
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            findLargestText(group.getChildAt(i), best, size);
        }
    }

    private static void makeTransparent(View view) {
        if (view != null) view.setBackgroundColor(Color.TRANSPARENT);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name, Class<T> type) {
        Object value = rawField(target, name);
        return type.isInstance(value) ? (T) value : null;
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static float dp(Activity activity, float value) {
        return value * activity.getResources().getDisplayMetrics().density;
    }

    private static final class State {
        final Activity activity;
        View ambientView;
        View ambientTarget;
        View topBar;
        TextView headerTitle;
        TextView headerSubtitle;
        BottomNavigationView bottomNav;
        RecyclerView recycler;
        RecyclerView.OnScrollListener scrollListener;
        RecyclerView.OnChildAttachStateChangeListener childAttachListener;
        ViewPager2 pager;
        ViewPager2.OnPageChangeCallback pageCallback;
        ValueAnimator ambientAnimator;
        int currentAmbient = APP_BG;
        float collapse;
        boolean ambientPosted;

        State(Activity activity) {
            this.activity = activity;
        }
    }
}
