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
 * OLED immersive feed layer. In 2.8 the top header is deliberately static so RecyclerView scroll
 * geometry never changes under the user's finger.
 */
final class OledImmersiveUiController {
    private static final int OLED_BG = Color.BLACK;
    private static final int CLASSIC_BG = Color.rgb(13, 13, 15);
    private static final int OLED_CARD = Color.rgb(9, 9, 11);
    private static final int CLASSIC_CARD = Color.rgb(24, 24, 28);
    private static final Map<Activity, State> STATES = new WeakHashMap<>();

    private OledImmersiveUiController() {
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
        if (state.pager != null && state.pageCallback != null) {
            try {
                state.pager.unregisterOnPageChangeCallback(state.pageCallback);
            } catch (Exception ignored) {
            }
        }
        if (state.ambientAnimator != null) state.ambientAnimator.cancel();
    }

    private static void applyMain(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State state = stateFor(activity);
        state.baseBg = baseBackground(activity);

        FrameLayout overlay = field(activity, "overlayRoot", FrameLayout.class);
        ViewGroup shell = field(activity, "shell", ViewGroup.class);
        BottomNavigationView nav = field(activity, "bottomNavigation", BottomNavigationView.class);
        ViewPager2 pager = field(activity, "primaryPager", ViewPager2.class);
        TextView title = field(activity, "headerTitle", TextView.class);
        TextView subtitle = field(activity, "headerSubtitle", TextView.class);
        if (overlay == null || shell == null || pager == null) return;

        installAmbientLayer(activity, state, overlay, shell);
        stylePagerTransparency(pager);

        View topBar = shell.getChildCount() > 0 ? shell.getChildAt(0) : null;
        state.topBar = topBar;
        state.headerTitle = title;
        state.headerSubtitle = subtitle;
        state.bottomNav = nav;

        boolean portrait = activity.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
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
            final State callbackState = state;
            state.pageCallback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    resetHeader(activity, callbackState);
                    pager.postDelayed(() -> bindCurrentMainRecycler(activity, callbackState), 80L);
                }
            };
            pager.registerOnPageChangeCallback(state.pageCallback);
        }

        resetHeader(activity, state);
        bindCurrentMainRecycler(activity, state);
    }

    private static void applyBrowser(NativeFeedBrowserActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        State state = stateFor(activity);
        state.baseBg = baseBackground(activity);

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
        resetHeader(activity, state);
        makeTransparent(recycler);
        if (recycler.getParent() instanceof View) makeTransparent((View) recycler.getParent());
        setAmbientBackground(state, state.baseBg, false);
        bindRecycler(activity, state, recycler);
    }

    private static State stateFor(Activity activity) {
        State state = STATES.get(activity);
        if (state == null) {
            state = new State(activity);
            STATES.put(activity, state);
        }
        return state;
    }

    private static int baseBackground(Activity activity) {
        boolean oled = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("oled_black_enabled", true);
        return oled ? OLED_BG : CLASSIC_BG;
    }

    private static boolean oled(Activity activity) {
        return baseBackground(activity) == OLED_BG;
    }

    private static void bindCurrentMainRecycler(NativeMainActivity activity, State state) {
        ViewPager2 pager = field(activity, "primaryPager", ViewPager2.class);
        Object pagerAdapter = rawField(activity, "primaryPagerAdapter");
        if (pager == null || pagerAdapter == null) return;
        int position = pager.getCurrentItem();
        if (position == MainPagerAdapter.PAGE_CHAOS) {
            unbindRecycler(state);
            setAmbientBackground(state, state.baseBg, true);
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
            styleAttachedCards(activity, recycler);
            applyMotion(activity, recycler);
            scheduleAmbient(activity, state, recycler, 80L);
            return;
        }

        unbindRecycler(state);
        state.recycler = recycler;
        state.scrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                // Header geometry is intentionally static in 2.8. Do not touch the top bar here.
                applyMotion(activity, view);
                scheduleAmbient(activity, state, view, 260L);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView view, int newState) {
                applyMotion(activity, view);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scheduleAmbient(activity, state, view, 20L);
                }
            }
        };
        state.childAttachListener = new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                styleMediaCard(activity, view);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                resetMotion(view);
            }
        };
        recycler.addOnScrollListener(state.scrollListener);
        recycler.addOnChildAttachStateChangeListener(state.childAttachListener);
        recycler.setClipToPadding(false);
        styleAttachedCards(activity, recycler);
        applyMotion(activity, recycler);
        scheduleAmbient(activity, state, recycler, 80L);
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

    private static void styleAttachedCards(Activity activity, RecyclerView recycler) {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            styleMediaCard(activity, recycler.getChildAt(i));
        }
    }

    private static void installAmbientLayer(
            Activity activity,
            State state,
            FrameLayout overlay,
            ViewGroup shell
    ) {
        if (state.ambientView == null || state.ambientView.getParent() != overlay) {
            View ambient = new View(activity);
            ambient.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            overlay.addView(ambient, 0, new FrameLayout.LayoutParams(-1, -1));
            state.ambientView = ambient;
            state.ambientTarget = ambient;
        }
        shell.setBackgroundColor(Color.TRANSPARENT);
        overlay.setBackgroundColor(state.baseBg);
        if (state.currentAmbient == 0) state.currentAmbient = state.baseBg;
        setAmbientBackground(state, state.currentAmbient, false);
    }

    private static void stylePagerTransparency(ViewPager2 pager) {
        if (pager == null) return;
        makeTransparent(pager);
        if (pager.getChildCount() > 0 && pager.getChildAt(0) instanceof ViewGroup) {
            ViewGroup internal = (ViewGroup) pager.getChildAt(0);
            makeTransparent(internal);
            for (int i = 0; i < internal.getChildCount(); i++) makeTransparent(internal.getChildAt(i));
        }
    }

    private static void styleTopBar(Activity activity, View topBar) {
        if (topBar == null) return;
        GradientDrawable bg = new GradientDrawable();
        if (oled(activity)) {
            bg.setColor(Color.argb(250, 0, 0, 0));
            bg.setStroke(dp(activity, 1), Color.rgb(20, 20, 23));
        } else {
            bg.setColor(Color.argb(246, 18, 18, 22));
            bg.setStroke(dp(activity, 1), Color.rgb(38, 38, 44));
        }
        float radius = dp(activity, 16);
        bg.setCornerRadii(new float[] {0f, 0f, 0f, 0f, radius, radius, radius, radius});
        topBar.setBackground(bg);
        topBar.setElevation(dp(activity, oled(activity) ? 1 : 3));
    }

    private static void styleBottomNav(Activity activity, BottomNavigationView nav) {
        if (nav == null || nav.getVisibility() != View.VISIBLE) return;
        GradientDrawable bg = new GradientDrawable();
        if (oled(activity)) {
            bg.setColor(Color.argb(252, 0, 0, 0));
            bg.setStroke(dp(activity, 1), Color.rgb(31, 31, 35));
        } else {
            bg.setColor(Color.argb(244, 21, 21, 25));
            bg.setStroke(dp(activity, 1), Color.rgb(48, 48, 55));
        }
        bg.setCornerRadius(dp(activity, 30));
        nav.setBackground(bg);
        nav.setElevation(dp(activity, oled(activity) ? 4 : 6));
        nav.setItemRippleColor(ColorStateList.valueOf(Color.argb(28, 251, 245, 6)));
        try {
            nav.setItemActiveIndicatorEnabled(true);
            nav.setItemActiveIndicatorColor(ColorStateList.valueOf(Color.argb(50, 251, 245, 6)));
        } catch (Throwable ignored) {
        }

        ViewGroup.LayoutParams raw = nav.getLayoutParams();
        if (raw != null) {
            raw.height = dp(activity, 60);
            if (raw instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) raw;
                margins.setMargins(dp(activity, 10), dp(activity, 2), dp(activity, 10), dp(activity, 6));
            }
            nav.setLayoutParams(raw);
        }
    }

    private static void resetHeader(Activity activity, State state) {
        View top = state.topBar;
        if (top == null) return;
        top.animate().cancel();
        ViewGroup.LayoutParams raw = top.getLayoutParams();
        if (raw != null) {
            raw.height = dp(activity, 56);
            top.setLayoutParams(raw);
        }
        if (state.headerTitle != null) {
            state.headerTitle.animate().cancel();
            state.headerTitle.setTextSize(activity instanceof NativeFeedBrowserActivity ? 19f : 18f);
            state.headerTitle.setTranslationY(0f);
            state.headerTitle.setScaleX(1f);
            state.headerTitle.setScaleY(1f);
            state.headerTitle.setAlpha(1f);
        }
        if (state.headerSubtitle != null) {
            state.headerSubtitle.animate().cancel();
            state.headerSubtitle.setAlpha(1f);
            state.headerSubtitle.setScaleX(1f);
            state.headerSubtitle.setScaleY(1f);
            state.headerSubtitle.setTranslationY(0f);
            state.headerSubtitle.setTextSize(11f);
        }
        ImageView icon = firstImage(top);
        if (icon != null) {
            icon.animate().cancel();
            icon.setScaleX(1f);
            icon.setScaleY(1f);
            icon.setAlpha(1f);
            icon.setTranslationY(0f);
            if (activity instanceof NativeMainActivity) {
                ViewGroup.LayoutParams iconParams = icon.getLayoutParams();
                if (iconParams != null) {
                    iconParams.width = dp(activity, 40);
                    iconParams.height = dp(activity, 40);
                    icon.setLayoutParams(iconParams);
                }
            }
        }
    }

    private static void applyMotion(Activity activity, RecyclerView recycler) {
        if (recycler == null || recycler.getHeight() <= 0) return;
        boolean enabled = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getBoolean("immersive_motion_enabled", true);
        float center = recycler.getHeight() * 0.5f;
        float range = Math.max(1f, recycler.getHeight() * 0.65f);

        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            ImageView image = largestImage(child);
            if (!enabled || image == null) {
                resetMotion(child);
                continue;
            }
            float childCenter = (child.getTop() + child.getBottom()) * 0.5f;
            float focus = 1f - Math.min(1f, Math.abs(childCenter - center) / range);
            float scale = 0.996f + (0.004f * focus);
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(0.985f + (0.015f * focus));
            child.setTranslationZ(dp(activity, 0.2f + (0.8f * focus)));

            float parallax = clamp((center - childCenter) / range, -1f, 1f);
            image.setTranslationY(dp(activity, 0.75f) * parallax);
        }
    }

    private static void resetMotion(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setAlpha(1f);
        view.setTranslationZ(0f);
        ImageView image = largestImage(view);
        if (image != null) image.setTranslationY(0f);
    }

    private static void styleMediaCard(Activity activity, View child) {
        MaterialCardView card = child instanceof MaterialCardView
                ? (MaterialCardView) child : findCard(child);
        if (card == null || largestImage(card) == null) return;
        boolean oled = oled(activity);
        card.setCardBackgroundColor(oled ? OLED_CARD : CLASSIC_CARD);
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(activity, 1));
        card.setStrokeColor(oled ? Color.rgb(29, 29, 33) : Color.rgb(50, 50, 57));
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
            setAmbientBackground(state, state.baseBg, true);
            return;
        }

        float center = recycler.getHeight() * 0.46f;
        View best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            ImageView image = largestImage(child);
            if (image == null) continue;
            float childCenter = (child.getTop() + child.getBottom()) * 0.5f;
            float distance = Math.abs(childCenter - center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = child;
            }
        }

        int sampled = sampleImageColor(largestImage(best));
        if (sampled == 0) return;
        float strength = oled(activity) ? 0.075f : 0.14f;
        int target = blend(state.baseBg, sampled, strength);
        setAmbientBackground(state, target, true);
    }

    private static void setAmbientBackground(State state, int target, boolean animate) {
        if (state.ambientTarget == null) return;
        if (state.currentAmbient == 0) state.currentAmbient = state.baseBg;
        if (colorDistance(state.currentAmbient, target) < 5) return;
        if (state.ambientAnimator != null) state.ambientAnimator.cancel();
        if (!animate) {
            state.currentAmbient = target;
            applyAmbientDrawable(state, target);
            return;
        }

        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), state.currentAmbient, target);
        state.ambientAnimator = animator;
        animator.setDuration(720L);
        animator.addUpdateListener(value -> {
            int color = (Integer) value.getAnimatedValue();
            state.currentAmbient = color;
            applyAmbientDrawable(state, color);
        });
        animator.start();
    }

    private static void applyAmbientDrawable(State state, int color) {
        View target = state.ambientTarget;
        if (target == null) return;
        int middle = blend(state.baseBg, color, 0.34f);
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.RECTANGLE);
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.5f, 0.22f);
        float radius = Math.max(target.getWidth(), target.getHeight()) * 0.82f;
        if (radius <= 0f) radius = dp(state.activity, 700f);
        glow.setGradientRadius(radius);
        glow.setColors(new int[] {color, middle, state.baseBg, state.baseBg});
        target.setBackground(glow);
    }

    private static int sampleImageColor(ImageView image) {
        if (image == null) return 0;
        Drawable drawable = image.getDrawable();
        if (!(drawable instanceof BitmapDrawable)) return 0;
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() < 2 || bitmap.getHeight() < 2) return 0;

        long r = 0L, g = 0L, b = 0L, count = 0L;
        int steps = 4;
        for (int y = 1; y <= steps; y++) {
            int py = Math.min(bitmap.getHeight() - 1, y * bitmap.getHeight() / (steps + 1));
            for (int x = 1; x <= steps; x++) {
                int px = Math.min(bitmap.getWidth() - 1, x * bitmap.getWidth() / (steps + 1));
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
        hsv[1] = Math.min(0.72f, Math.max(0.22f, hsv[1]));
        hsv[2] = Math.min(0.48f, Math.max(0.24f, hsv[2] * 0.62f));
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
        return Math.abs(Color.red(a) - Color.red(b))
                + Math.abs(Color.green(a) - Color.green(b))
                + Math.abs(Color.blue(a) - Color.blue(b));
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
        for (int i = 0; i < group.getChildCount(); i++) findLargestImage(group.getChildAt(i), best, area);
    }

    private static MaterialCardView findCard(View view) {
        if (view instanceof MaterialCardView) return (MaterialCardView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            MaterialCardView found = findCard(group.getChildAt(i));
            if (found != null) return found;
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
        for (int i = 0; i < group.getChildCount(); i++) findLargestText(group.getChildAt(i), best, size);
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
        int baseBg = OLED_BG;
        int currentAmbient = OLED_BG;
        boolean ambientPosted;

        State(Activity activity) {
            this.activity = activity;
        }
    }
}
