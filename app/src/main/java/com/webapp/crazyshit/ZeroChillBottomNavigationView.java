package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Bottom navigation with one continuous glass selection capsule driven by ViewPager2 progress. */
final class ZeroChillBottomNavigationView extends BottomNavigationView {
    private static final int[] PAGE_ITEM_IDS = {1, 2, 4, 3};
    private static final int MORE_ITEM_ID = 5;

    private final Drawable indicator;
    private final RectF fromBounds = new RectF();
    private final RectF toBounds = new RectF();
    private final RectF indicatorBounds = new RectF();
    private final ZeroChillReactiveGlassRenderer reflection = new ZeroChillReactiveGlassRenderer();

    private float pagerPosition;
    private float previousPagerPosition;
    private long previousFrameNanos;
    private float velocityBias;
    private float touchPosition = 0.5f;
    private boolean hasPagerPosition;
    private boolean userInteractionActive;
    private ValueAnimator settleAnimator;

    ZeroChillBottomNavigationView(Context context) {
        super(context);
        Drawable source = ContextCompat.getDrawable(context, R.drawable.zc_nav_selected_glass);
        indicator = source == null ? null : source.mutate();
        setWillNotDraw(false);
    }

    void setPagerProgress(int position, float offset) {
        if (!ZeroChillMotion.animationsEnabled(getContext())) return;
        float next = clamp(position + offset, 0f, PAGE_ITEM_IDS.length - 1f);
        updateVelocity(next);
        pagerPosition = next;
        hasPagerPosition = true;
        applyInterpolatedTints();
        postInvalidateOnAnimation();
    }

    void setSettledPage(int position) {
        pagerPosition = clamp(position, 0f, PAGE_ITEM_IDS.length - 1f);
        previousPagerPosition = pagerPosition;
        previousFrameNanos = 0L;
        hasPagerPosition = true;
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            velocityBias = 0f;
            touchPosition = 0.5f;
        }
        applyInterpolatedTints();
        invalidate();
    }

    void onPagerScrollStateChanged(int state) {
        if (state == ViewPager2.SCROLL_STATE_IDLE) settleReflection();
    }

    boolean isUserInteractionActive() {
        return userInteractionActive;
    }

    float pagerPositionForTest() {
        return pagerPosition;
    }

    boolean gpuReflectionSupportedForTest() {
        return reflection.isGpuSupported();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null && getWidth() > 0) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                userInteractionActive = true;
                cancelSettle();
                touchPosition = clamp(event.getX() / getWidth(), 0f, 1f);
                postInvalidateOnAnimation();
            } else if (action == MotionEvent.ACTION_MOVE) {
                touchPosition = clamp(event.getX() / getWidth(), 0f, 1f);
                postInvalidateOnAnimation();
            } else if (action == MotionEvent.ACTION_UP) {
                touchPosition = clamp(event.getX() / getWidth(), 0f, 1f);
                postDelayed(() -> userInteractionActive = false, 80L);
                settleReflection();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                userInteractionActive = false;
                settleReflection();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawMovingIndicator(canvas);
        super.dispatchDraw(canvas);
    }

    private void drawMovingIndicator(Canvas canvas) {
        if (indicator == null || !hasPagerPosition || getWidth() <= 0 || getHeight() <= 0) return;

        int from = Math.max(0, Math.min(PAGE_ITEM_IDS.length - 1, (int) Math.floor(pagerPosition)));
        int to = Math.max(0, Math.min(PAGE_ITEM_IDS.length - 1, from + 1));
        float fraction = clamp(pagerPosition - from, 0f, 1f);

        if (!fillItemBounds(PAGE_ITEM_IDS[from], fromBounds)) return;
        if (!fillItemBounds(PAGE_ITEM_IDS[to], toBounds)) toBounds.set(fromBounds);

        indicatorBounds.set(
                lerp(fromBounds.left, toBounds.left, fraction),
                lerp(fromBounds.top, toBounds.top, fraction),
                lerp(fromBounds.right, toBounds.right, fraction),
                lerp(fromBounds.bottom, toBounds.bottom, fraction)
        );

        float horizontalInset = dp(4);
        float verticalInset = dp(3);
        indicatorBounds.inset(horizontalInset, verticalInset);

        indicator.setBounds(
                Math.round(indicatorBounds.left),
                Math.round(indicatorBounds.top),
                Math.round(indicatorBounds.right),
                Math.round(indicatorBounds.bottom)
        );
        indicator.draw(canvas);

        float radius = Math.max(1f, indicatorBounds.height() / 2f);
        reflection.draw(
                canvas,
                indicatorBounds,
                radius,
                velocityBias * 0.45f,
                touchPosition,
                velocityBias,
                0.82f
        );
    }

    private boolean fillItemBounds(int id, RectF out) {
        View item = findViewById(id);
        if (item == null || item.getWidth() <= 0 || item.getHeight() <= 0) return false;

        out.set(item.getLeft(), item.getTop(), item.getRight(), item.getBottom());
        ViewParent parent = item.getParent();
        while (parent instanceof View && parent != this) {
            View parentView = (View) parent;
            out.offset(
                    parentView.getLeft() - parentView.getScrollX(),
                    parentView.getTop() - parentView.getScrollY()
            );
            parent = parentView.getParent();
        }
        return parent == this;
    }

    private void applyInterpolatedTints() {
        int inactive = ZeroChillUi.color(getContext(), R.color.zc_text_secondary);
        int active = ZeroChillUi.color(getContext(), R.color.zc_cyan);

        for (int index = 0; index < PAGE_ITEM_IDS.length; index++) {
            View item = findViewById(PAGE_ITEM_IDS[index]);
            if (item == null) continue;
            float weight = clamp(1f - Math.abs(pagerPosition - index), 0f, 1f);
            tintDescendants(item, blend(inactive, active, weight));
        }

        View more = findViewById(MORE_ITEM_ID);
        if (more != null) tintDescendants(more, inactive);
    }

    private void tintDescendants(View view, int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ImageView) {
            ((ImageView) view).setColorFilter(color);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            tintDescendants(group.getChildAt(index), color);
        }
    }

    private void updateVelocity(float next) {
        long now = System.nanoTime();
        if (previousFrameNanos != 0L) {
            float seconds = Math.max(0.001f, (now - previousFrameNanos) / 1_000_000_000f);
            float pagesPerSecond = (next - previousPagerPosition) / seconds;
            velocityBias = clamp(pagesPerSecond / 7f, -1f, 1f);
        }
        previousPagerPosition = next;
        previousFrameNanos = now;
    }

    private void settleReflection() {
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            velocityBias = 0f;
            touchPosition = 0.5f;
            invalidate();
            return;
        }
        cancelSettle();
        final float startVelocity = velocityBias;
        final float startTouch = touchPosition;
        settleAnimator = ValueAnimator.ofFloat(0f, 1f);
        settleAnimator.setDuration(ZeroChillMotion.STANDARD_MS);
        settleAnimator.setInterpolator(new DecelerateInterpolator());
        settleAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            velocityBias = lerp(startVelocity, 0f, fraction);
            touchPosition = lerp(startTouch, 0.5f, fraction);
            postInvalidateOnAnimation();
        });
        settleAnimator.start();
    }

    private void cancelSettle() {
        if (settleAnimator != null) {
            settleAnimator.cancel();
            settleAnimator = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int blend(int start, int end, float fraction) {
        float t = clamp(fraction, 0f, 1f);
        int a = Math.round(Color.alpha(start) + (Color.alpha(end) - Color.alpha(start)) * t);
        int r = Math.round(Color.red(start) + (Color.red(end) - Color.red(start)) * t);
        int g = Math.round(Color.green(start) + (Color.green(end) - Color.green(start)) * t);
        int b = Math.round(Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t);
        return Color.argb(a, r, g, b);
    }

    private static float lerp(float start, float end, float fraction) {
        return start + ((end - start) * fraction);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
