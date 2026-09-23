package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Reusable, small ZEROCHILL loading state. Animation stops with visibility and system motion. */
final class ZeroChillLoadingView extends LinearLayout {
    private final ImageView mascot;
    private ValueAnimator breath;

    ZeroChillLoadingView(Context context, String label) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        mascot = new ImageView(context);
        mascot.setImageResource(R.drawable.ic_zerochill_loader_devil);
        mascot.setContentDescription(label == null ? "Loading" : label);
        int size = dp(64);
        addView(mascot, new LayoutParams(size, size));
        if (label != null && !label.isEmpty()) {
            TextView text = new TextView(context);
            text.setText(label);
            text.setTextSize(12);
            text.setTextColor(Color.rgb(184, 205, 213));
            text.setGravity(Gravity.CENTER);
            LayoutParams params = new LayoutParams(-2, -2);
            params.topMargin = dp(4);
            addView(text, params);
        }
    }

    @Override public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        updateAnimation();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        mascot.animate().cancel();
        super.onDetachedFromWindow();
    }

    /** Briefly acknowledges the first usable result while content is already visible underneath. */
    void finish() {
        if (getVisibility() != View.VISIBLE) return;
        stopAnimation();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            setVisibility(View.GONE);
            return;
        }
        mascot.animate().cancel();
        mascot.animate().alpha(1f).scaleX(1.08f).scaleY(1.08f)
                .setDuration(110L)
                .withEndAction(() -> {
                    setVisibility(View.GONE);
                    mascot.setScaleX(1f);
                    mascot.setScaleY(1f);
                }).start();
    }

    private void updateAnimation() {
        if (mascot == null) return;
        mascot.animate().cancel();
        if (!isAttachedToWindow() || getVisibility() != View.VISIBLE ||
                !ValueAnimator.areAnimatorsEnabled()) {
            stopAnimation();
            mascot.setAlpha(1f);
            return;
        }
        if (breath != null) return;
        mascot.setAlpha(0.72f);
        breath = ValueAnimator.ofFloat(0.72f, 1f);
        breath.setDuration(950L);
        breath.setRepeatMode(ValueAnimator.REVERSE);
        breath.setRepeatCount(ValueAnimator.INFINITE);
        breath.addUpdateListener(animation -> mascot.setAlpha((float) animation.getAnimatedValue()));
        breath.start();
    }

    private void stopAnimation() {
        if (breath != null) breath.cancel();
        breath = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
