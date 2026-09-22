package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

/** Segmented rail whose selected glass capsule physically travels between child controls. */
final class ZeroChillSegmentedRail extends LinearLayout {
    private final Drawable indicator;
    private final RectF indicatorBounds = new RectF();
    private final RectF startBounds = new RectF();
    private final RectF targetBounds = new RectF();
    private final ZeroChillReactiveGlassRenderer reflection = new ZeroChillReactiveGlassRenderer();

    private int selectedIndex = -1;
    private boolean hasIndicatorBounds;
    private float motionBias;
    private float touchPosition = 0.5f;
    private ValueAnimator indicatorAnimator;
    private ValueAnimator settleAnimator;

    ZeroChillSegmentedRail(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        Drawable source = ContextCompat.getDrawable(context, R.drawable.zc_source_rail_selected_glass);
        indicator = source == null ? null : source.mutate();
    }

    void setSelectedIndex(int index, boolean animate) {
        if (index < 0) return;
        selectedIndex = index;
        if (getWidth() <= 0 || index >= getChildCount() || getChildAt(index).getWidth() <= 0) {
            requestLayout();
            post(this::syncSelectedIndicator);
            return;
        }

        fillChildBounds(index, targetBounds);
        if (!hasIndicatorBounds || !animate || !ZeroChillMotion.animationsEnabled(getContext())) {
            cancelIndicatorAnimation();
            indicatorBounds.set(targetBounds);
            hasIndicatorBounds = true;
            motionBias = 0f;
            invalidate();
            return;
        }

        startBounds.set(indicatorBounds);
        float direction = Math.signum(targetBounds.centerX() - startBounds.centerX());
        cancelIndicatorAnimation();
        indicatorAnimator = ValueAnimator.ofFloat(0f, 1f);
        indicatorAnimator.setDuration(ZeroChillMotion.STANDARD_MS);
        indicatorAnimator.setInterpolator(new DecelerateInterpolator());
        indicatorAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            indicatorBounds.set(
                    lerp(startBounds.left, targetBounds.left, fraction),
                    lerp(startBounds.top, targetBounds.top, fraction),
                    lerp(startBounds.right, targetBounds.right, fraction),
                    lerp(startBounds.bottom, targetBounds.bottom, fraction)
            );
            motionBias = direction * (1f - fraction);
            postInvalidateOnAnimation();
        });
        indicatorAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                indicatorBounds.set(targetBounds);
                motionBias = 0f;
                postInvalidateOnAnimation();
            }
        });
        indicatorAnimator.start();
    }

    int selectedIndexForTest() {
        return selectedIndex;
    }

    boolean gpuReflectionSupportedForTest() {
        return reflection.isGpuSupported();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (selectedIndex >= 0 && selectedIndex < getChildCount()
                && (indicatorAnimator == null || !indicatorAnimator.isRunning())) {
            fillChildBounds(selectedIndex, indicatorBounds);
            hasIndicatorBounds = true;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null && getWidth() > 0) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                cancelSettleAnimation();
                touchPosition = clamp(event.getX() / getWidth(), 0f, 1f);
                postInvalidateOnAnimation();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                settleReflection();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (indicator != null && hasIndicatorBounds && !indicatorBounds.isEmpty()) {
            indicator.setBounds(
                    Math.round(indicatorBounds.left),
                    Math.round(indicatorBounds.top),
                    Math.round(indicatorBounds.right),
                    Math.round(indicatorBounds.bottom)
            );
            indicator.draw(canvas);
            reflection.draw(
                    canvas,
                    indicatorBounds,
                    Math.max(1f, indicatorBounds.height() / 2f),
                    motionBias * 0.35f,
                    touchPosition,
                    motionBias,
                    0.58f
            );
        }
        super.dispatchDraw(canvas);
    }

    private void syncSelectedIndicator() {
        if (selectedIndex < 0 || selectedIndex >= getChildCount() || getWidth() <= 0) return;
        View child = getChildAt(selectedIndex);
        if (child == null || child.getWidth() <= 0) return;
        fillChildBounds(selectedIndex, indicatorBounds);
        hasIndicatorBounds = true;
        invalidate();
    }

    private void fillChildBounds(int index, RectF out) {
        View child = getChildAt(index);
        if (child == null) {
            out.setEmpty();
            return;
        }
        out.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
        float inset = dp(1);
        out.inset(inset, inset);
    }

    private void settleReflection() {
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            touchPosition = 0.5f;
            motionBias = 0f;
            invalidate();
            return;
        }
        cancelSettleAnimation();
        float startTouch = touchPosition;
        float startMotion = motionBias;
        settleAnimator = ValueAnimator.ofFloat(0f, 1f);
        settleAnimator.setDuration(ZeroChillMotion.STANDARD_MS);
        settleAnimator.setInterpolator(new DecelerateInterpolator());
        settleAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            touchPosition = lerp(startTouch, 0.5f, fraction);
            motionBias = lerp(startMotion, 0f, fraction);
            postInvalidateOnAnimation();
        });
        settleAnimator.start();
    }

    private void cancelIndicatorAnimation() {
        if (indicatorAnimator != null) {
            indicatorAnimator.cancel();
            indicatorAnimator = null;
        }
    }

    private void cancelSettleAnimation() {
        if (settleAnimator != null) {
            settleAnimator.cancel();
            settleAnimator = null;
        }
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
}
