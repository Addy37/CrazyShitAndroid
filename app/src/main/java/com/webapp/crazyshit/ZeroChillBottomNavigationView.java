package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
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
    private static final int[] PAGE_NAV_IDS = {1, 2, 4, 3};
    private static final long REFLECTION_SETTLE_MS = 180L;

    private final Drawable selectedGlass;
    private final Rect firstRect = new Rect();
    private final Rect secondRect = new Rect();
    private final RectF indicatorRect = new RectF();
    private final Runnable settleReflectionRunnable = this::settleReflection;

    private float pagerPosition;
    private float reflectionPosition;
    private boolean touchReflectionActive;
    private boolean gpuReflectionDisabled;
    private ValueAnimator reflectionAnimator;
    private Api33Reflection shaderReflection;

    ZeroChillBottomNavigationView(Context context) {
        super(context);
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.zc_nav_selected_glass);
        selectedGlass = drawable == null ? null : drawable.mutate();
        setWillNotDraw(false);
    }

    void setPagerPosition(float position) {
        float clamped = clamp(position, 0f, PAGE_NAV_IDS.length - 1f);
        pagerPosition = clamped;
        if (!touchReflectionActive) {
            if (reflectionAnimator != null) reflectionAnimator.cancel();
            reflectionPosition = PAGE_NAV_IDS.length <= 1
                    ? 0.5f
                    : clamped / (PAGE_NAV_IDS.length - 1f);
        }
        invalidate();
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
        if (event != null && getWidth() > 0) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                removeCallbacks(settleReflectionRunnable);
                if (reflectionAnimator != null) reflectionAnimator.cancel();
                touchReflectionActive = true;
                reflectionPosition = clamp(event.getX() / getWidth(), 0f, 1f);
                invalidate();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchReflectionActive = false;
                removeCallbacks(settleReflectionRunnable);
                postDelayed(settleReflectionRunnable, 70L);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(settleReflectionRunnable);
        if (reflectionAnimator != null) reflectionAnimator.cancel();
        reflectionAnimator = null;
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
        float width = Math.max(
                getResources().getDimensionPixelSize(R.dimen.zc_nav_indicator_width),
                lerp(firstRect.width(), secondRect.width(), fraction) - dp(8)
        );

        float top = Math.max(dp(3), lerp(firstRect.top, secondRect.top, fraction) + dp(4));
        float bottom = Math.min(
                getHeight() - dp(1),
                lerp(firstRect.bottom, secondRect.bottom, fraction) - dp(1)
        );
        if (bottom <= top) return;

        indicatorRect.set(center - width / 2f, top, center + width / 2f, bottom);

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
            float focusX = reflectionPosition * getWidth();
            float localFocus = (focusX - indicatorRect.left)
                    / Math.max(1f, indicatorRect.width());
            shaderReflection.draw(
                    canvas,
                    indicatorRect,
                    clamp(localFocus, -0.4f, 1.4f),
                    getResources().getDimension(R.dimen.zc_radius_pill)
            );
        } catch (Throwable ignored) {
            gpuReflectionDisabled = true;
            shaderReflection = null;
        }
    }

    private void settleReflection() {
        float target = PAGE_NAV_IDS.length <= 1
                ? 0.5f
                : clamp(pagerPosition / (PAGE_NAV_IDS.length - 1f), 0f, 1f);
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            reflectionPosition = target;
            invalidate();
            return;
        }
        if (reflectionAnimator != null) reflectionAnimator.cancel();
        reflectionAnimator = ValueAnimator.ofFloat(reflectionPosition, target);
        reflectionAnimator.setDuration(REFLECTION_SETTLE_MS);
        reflectionAnimator.setInterpolator(new DecelerateInterpolator());
        reflectionAnimator.addUpdateListener(animation -> {
            reflectionPosition = (float) animation.getAnimatedValue();
            invalidate();
        });
        reflectionAnimator.start();
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
                "  float alpha = min(0.16, (band * top * 0.13) + (rim * 0.025));\n" +
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
