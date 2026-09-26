package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Bottom navigation with a selected glass capsule that tracks ViewPager swipes.
 *
 * Android 13+ adds a hardware RuntimeShader reflection over the approved static glass. Older
 * Android versions keep the same static selected-glass drawable.
 */
final class ZeroChillBottomNavigationView extends BottomNavigationView {
    interface OnNavigationDragListener {
        boolean onNavigationDragStart();
        void onNavigationDragBy(float deltaPageFraction);
        void onNavigationDragEnd(boolean canceled);
    }

    private static final int[] PAGE_NAV_IDS = {2, 4, 3, 6};
    private static final long REFLECTION_SETTLE_MS = 180L;
    private static final float HORIZONTAL_DOMINANCE = 1.25f;

    private final Drawable selectedGlass;
    private final Rect firstRect = new Rect();
    private final Rect secondRect = new Rect();
    private final RectF indicatorRect = new RectF();
    private final Runnable settleReflectionRunnable = this::settleReflection;

    private float pagerPosition;
    private float reflectionFocus = 0.5f;
    private float pressScale = 1f;
    private ValueAnimator pressAnimator;
    private boolean touchReflectionActive;
    private boolean gpuReflectionDisabled;
    private ValueAnimator reflectionAnimator;
    private Api33Reflection shaderReflection;
    private final int swipeTouchSlop;
    private float dragDownX;
    private float dragDownY;
    private float dragLastX;
    private float navigationDragPageStep = 1f;
    private boolean navigationDragCandidate;
    private boolean navigationDragActive;
    private OnNavigationDragListener navigationDragListener;

    ZeroChillBottomNavigationView(Context context) {
        super(context);
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.zc_nav_selected_glass);
        selectedGlass = drawable == null ? null : drawable.mutate();
        swipeTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);
        addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateItemColors());
    }

    void setPagerPosition(float position) {
        float clamped = clamp(position, 0f, PAGE_NAV_IDS.length - 1f);
        pagerPosition = clamped;
        updateItemColors();
        invalidate();
    }

    void refreshItemColors() {
        updateItemColors();
    }

    void setOnNavigationDragListener(OnNavigationDragListener listener) {
        navigationDragListener = listener;
    }

    float pagerPositionForTest() {
        return pagerPosition;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawSelectedGlass(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event == null) return super.dispatchTouchEvent(null);

        int action = event.getActionMasked();
        updateTouchReflection(event, action);

        if (action == MotionEvent.ACTION_DOWN) {
            dragDownX = event.getX();
            dragDownY = event.getY();
            dragLastX = dragDownX;
            navigationDragPageStep = pageStepDistance();
            navigationDragActive = false;
            navigationDragCandidate = isInsideSelectedCapsule(dragDownX, dragDownY);
            return super.dispatchTouchEvent(event);
        }

        float dx = event.getX() - dragDownX;
        float dy = event.getY() - dragDownY;
        boolean horizontalIntent = navigationDragCandidate
                && Math.abs(dx) > swipeTouchSlop
                && Math.abs(dx) > Math.abs(dy) * HORIZONTAL_DOMINANCE;

        if (action == MotionEvent.ACTION_MOVE && !navigationDragActive && horizontalIntent) {
            boolean started = navigationDragListener != null
                    && navigationDragListener.onNavigationDragStart();
            if (started) {
                navigationDragActive = true;
                cancelChildTouch(event);
                navigationDragListener.onNavigationDragBy(dx / navigationDragPageStep);
                dragLastX = event.getX();
                return true;
            }
            navigationDragCandidate = false;
        }

        if (action == MotionEvent.ACTION_MOVE && navigationDragActive) {
            float deltaX = event.getX() - dragLastX;
            dragLastX = event.getX();
            if (navigationDragListener != null && deltaX != 0f) {
                navigationDragListener.onNavigationDragBy(deltaX / navigationDragPageStep);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP && navigationDragActive) {
            navigationDragActive = false;
            navigationDragCandidate = false;
            if (navigationDragListener != null) {
                navigationDragListener.onNavigationDragEnd(false);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL && navigationDragActive) {
            navigationDragActive = false;
            navigationDragCandidate = false;
            if (navigationDragListener != null) {
                navigationDragListener.onNavigationDragEnd(true);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            navigationDragCandidate = false;
        }
        return super.dispatchTouchEvent(event);
    }

    private float pageStepDistance() {
        View first = findViewById(PAGE_NAV_IDS[0]);
        View second = findViewById(PAGE_NAV_IDS[1]);
        if (first == null || second == null ||
                first.getWidth() <= 0 || second.getWidth() <= 0) {
            return Math.max(1f, getWidth() / 4f);
        }
        descendantRect(first, firstRect);
        descendantRect(second, secondRect);
        return Math.max(1f, Math.abs(secondRect.exactCenterX() - firstRect.exactCenterX()));
    }

    private boolean isInsideSelectedCapsule(float x, float y) {
        int index = Math.max(0, Math.min(PAGE_NAV_IDS.length - 1, Math.round(pagerPosition)));
        View item = findViewById(PAGE_NAV_IDS[index]);
        if (item == null || item.getWidth() <= 0 || item.getHeight() <= 0) return false;

        descendantRect(item, firstRect);
        float left = firstRect.left + dp(4);
        float right = firstRect.right - dp(4);
        float top = firstRect.top + dp(3);
        float bottom = firstRect.bottom + dp(10);
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private void updateTouchReflection(MotionEvent event, int action) {
        if (getWidth() <= 0) return;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            removeCallbacks(settleReflectionRunnable);
            if (reflectionAnimator != null) reflectionAnimator.cancel();
            touchReflectionActive = true;
            float capsuleCenter = capsuleCenterX();
            float capsuleWidth = Math.max(1f, capsuleWidth());
            // A finger can nudge the sheen within the capsule, never across the bar.
            reflectionFocus = clamp(0.5f + (event.getX() - capsuleCenter)
                    / capsuleWidth * 0.12f, 0.38f, 0.62f);
            if (action == MotionEvent.ACTION_DOWN) animatePress(0.975f);
            invalidate();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchReflectionActive = false;
            removeCallbacks(settleReflectionRunnable);
            animatePress(1f);
            postDelayed(settleReflectionRunnable, 30L);
        }
    }

    private void cancelChildTouch(MotionEvent source) {
        MotionEvent cancel = MotionEvent.obtain(source);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(settleReflectionRunnable);
        if (reflectionAnimator != null) reflectionAnimator.cancel();
        if (pressAnimator != null) pressAnimator.cancel();
        reflectionAnimator = null;
        navigationDragListener = null;
        super.onDetachedFromWindow();
    }

    private void drawSelectedGlass(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;

        float position = clamp(pagerPosition, 0f, PAGE_NAV_IDS.length - 1f);
        int lower = Math.min(PAGE_NAV_IDS.length - 1, (int) Math.floor(position));
        int upper = Math.min(PAGE_NAV_IDS.length - 1, lower + 1);
        float fraction = position - lower;

        View first = findViewById(PAGE_NAV_IDS[lower]);
        View second = findViewById(PAGE_NAV_IDS[upper]);
        if (first == null || second == null || first.getWidth() <= 0 || second.getWidth() <= 0) {
            return;
        }

        descendantRect(first, firstRect);
        descendantRect(second, secondRect);

        float firstCenter = firstRect.exactCenterX();
        float secondCenter = secondRect.exactCenterX();
        float center = lerp(firstCenter, secondCenter, fraction);
        // Match the approved zc_nav_item_background inset bounds exactly.
        float width = lerp(firstRect.width(), secondRect.width(), fraction) - dp(8);
        float top = lerp(firstRect.top, secondRect.top, fraction) + dp(4);
        float bottom = lerp(firstRect.bottom, secondRect.bottom, fraction) + dp(10);
        if (bottom <= top) return;

        float midY = (top + bottom) / 2f;
        float halfWidth = width * pressScale / 2f;
        float halfHeight = (bottom - top) * pressScale / 2f;
        indicatorRect.set(center - halfWidth, midY - halfHeight,
                center + halfWidth, midY + halfHeight);

        if (selectedGlass != null) {
            selectedGlass.setBounds(
                    Math.round(indicatorRect.left),
                    Math.round(indicatorRect.top),
                    Math.round(indicatorRect.right),
                    Math.round(indicatorRect.bottom)
            );
            selectedGlass.draw(canvas);
        }

        drawGpuReflection(canvas);
    }

    private void drawGpuReflection(Canvas canvas) {
        if (gpuReflectionDisabled || Build.VERSION.SDK_INT < 33 || !canvas.isHardwareAccelerated()) {
            return;
        }
        try {
            if (shaderReflection == null) shaderReflection = new Api33Reflection();
            float localFocus = reflectionFocus;
            shaderReflection.draw(
                    canvas,
                    indicatorRect,
                    localFocus,
                    getResources().getDimension(R.dimen.zc_radius_pill)
            );
        } catch (Throwable ignored) {
            gpuReflectionDisabled = true;
            shaderReflection = null;
        }
    }

    private void settleReflection() {
        float target = 0.5f;
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            reflectionFocus = target;
            invalidate();
            return;
        }
        if (reflectionAnimator != null) reflectionAnimator.cancel();
        reflectionAnimator = ValueAnimator.ofFloat(reflectionFocus, target);
        reflectionAnimator.setDuration(REFLECTION_SETTLE_MS);
        reflectionAnimator.setInterpolator(new DecelerateInterpolator());
        reflectionAnimator.addUpdateListener(animation -> {
            reflectionFocus = (float) animation.getAnimatedValue();
            invalidate();
        });
        reflectionAnimator.start();
    }

    private float capsuleCenterX() {
        int index = Math.round(pagerPosition);
        View item = findViewById(PAGE_NAV_IDS[index]);
        if (item == null) return getWidth() * 0.5f;
        descendantRect(item, firstRect);
        return firstRect.exactCenterX();
    }

    private float capsuleWidth() {
        View item = findViewById(PAGE_NAV_IDS[Math.round(pagerPosition)]);
        return item == null ? dp(50) : item.getWidth() - dp(8);
    }

    private void animatePress(float target) {
        if (pressAnimator != null) pressAnimator.cancel();
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            pressScale = 1f;
            invalidate();
            return;
        }
        pressAnimator = ValueAnimator.ofFloat(pressScale, target);
        pressAnimator.setDuration(target < 1f ? ZeroChillMotion.PRESS_IN_MS : ZeroChillMotion.STANDARD_MS);
        pressAnimator.setInterpolator(new DecelerateInterpolator());
        pressAnimator.addUpdateListener(animation -> {
            pressScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        pressAnimator.start();
    }

    private void updateItemColors() {
        int active = ContextCompat.getColor(getContext(), R.color.zc_cyan);
        int inactive = ContextCompat.getColor(getContext(), R.color.zc_text_secondary);
        for (int index = 0; index < PAGE_NAV_IDS.length; index++) {
            View item = findViewById(PAGE_NAV_IDS[index]);
            if (item != null) tintChildren(item, mix(inactive, active,
                    Math.max(0f, 1f - Math.abs(pagerPosition - index))));
        }
    }

    private static void tintChildren(View view, int color) {
        if (view instanceof ImageView) {
            ((ImageView) view).setImageTintList(ColorStateList.valueOf(color));
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintChildren(group.getChildAt(i), color);
            }
        }
    }

    private static int mix(int from, int to, float fraction) {
        return Color.rgb(
                Math.round(lerp(Color.red(from), Color.red(to), fraction)),
                Math.round(lerp(Color.green(from), Color.green(to), fraction)),
                Math.round(lerp(Color.blue(from), Color.blue(to), fraction)));
    }

    private void descendantRect(View view, Rect out) {
        out.set(0, 0, view.getWidth(), view.getHeight());
        offsetDescendantRectToMyCoords(view, out);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static float lerp(float start, float end, float fraction) {
        return start + ((end - start) * fraction);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @RequiresApi(33)
    private static final class Api33Reflection {
        private static final String SHADER_SOURCE =
                "uniform float2 size;\n" +
                "uniform float2 origin;\n" +
                "uniform float focus;\n" +
                "half4 main(float2 p) {\n" +
                "  float2 uv = (p - origin) / max(size, float2(1.0));\n" +
                "  float diagonal = uv.x + ((1.0 - uv.y) * 0.22);\n" +
                "  float band = exp(-pow((diagonal - focus) * 6.5, 2.0));\n" +
                "  float top = pow(max(0.0, 1.0 - uv.y), 2.1);\n" +
                "  float rim = exp(-pow((uv.y - 0.08) * 18.0, 2.0));\n" +
                "  float alpha = min(0.10, (band * top * 0.075) + (rim * 0.018));\n" +
                "  half3 tint = half3(0.72, 0.91, 1.0);\n" +
                "  return half4(tint * half(alpha), half(alpha));\n" +
                "}";

        private final RuntimeShader shader = new RuntimeShader(SHADER_SOURCE);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Api33Reflection() {
            paint.setShader(shader);
        }

        void draw(Canvas canvas, RectF rect, float focus, float radius) {
            shader.setFloatUniform("size", rect.width(), rect.height());
            shader.setFloatUniform("origin", rect.left, rect.top);
            shader.setFloatUniform("focus", focus);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
