package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Reusable, small ZEROCHILL loading state. Animation stops with visibility and system motion. */
final class ZeroChillLoadingView extends LinearLayout {
    private final ImageView mascot;
    private final boolean galleryMotion;
    private ValueAnimator breath;

    ZeroChillLoadingView(Context context, String label) {
        this(context, label, false);
    }

    ZeroChillLoadingView(Context context, String label, boolean galleryMotion) {
        super(context);
        this.galleryMotion = galleryMotion;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        mascot = new ImageView(context);
        mascot.setImageResource(R.drawable.ic_zerochill_loader_devil);
        // The visible TextView already announces a labeled loader to TalkBack.
        mascot.setContentDescription(label == null ? "Loading" : null);
        int size = dp(label == null ? 56 : 76);
        addView(mascot, new LayoutParams(size, size));
        if (label != null && !label.isEmpty()) {
            TextView text = new TextView(context);
            text.setText(label);
            text.setTextSize(13);
            text.setTextColor(Color.rgb(211, 228, 235));
            text.setGravity(Gravity.CENTER);
            LayoutParams params = new LayoutParams(-2, -2);
            params.topMargin = dp(10);
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
        if (galleryMotion) {
            mascot.animate()
                    .alpha(0f)
                    .translationY(-dp(5))
                    .rotation(0f)
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(160L)
                    .withEndAction(() -> {
                        setVisibility(View.GONE);
                        resetMascotTransform();
                    })
                    .start();
        } else {
            mascot.animate().alpha(1f).scaleX(1.08f).scaleY(1.08f)
                    .setDuration(110L)
                    .withEndAction(() -> {
                        setVisibility(View.GONE);
                        resetMascotTransform();
                    }).start();
        }
    }

    private void updateAnimation() {
        if (mascot == null) return;
        mascot.animate().cancel();
        if (!isAttachedToWindow() || getVisibility() != View.VISIBLE ||
                !ZeroChillMotion.animationsEnabled(getContext())) {
            stopAnimation();
            resetMascotTransform();
            return;
        }
        if (breath != null) return;

        if (galleryMotion) {
            breath = ValueAnimator.ofFloat(0f, 1f);
            breath.setDuration(1800L);
            breath.setRepeatCount(ValueAnimator.INFINITE);
            breath.setInterpolator(new LinearInterpolator());
            breath.addUpdateListener(animation -> {
                float phase = (float) animation.getAnimatedValue();
                double wave = phase * Math.PI * 2d;
                float bob = (float) Math.sin(wave);
                float tilt = (float) Math.sin(wave + Math.PI / 2d);
                float pulse = (float) Math.sin(wave - Math.PI / 2d);

                mascot.setTranslationY(-dp(4) * bob);
                mascot.setRotation(2.2f * tilt);
                float scale = 1f + (0.025f * pulse);
                mascot.setScaleX(scale);
                mascot.setScaleY(scale);
                mascot.setAlpha(0.90f + (0.10f * ((bob + 1f) / 2f)));
            });
        } else {
            mascot.setAlpha(0.86f);
            breath = ValueAnimator.ofFloat(0.86f, 1f);
            breath.setDuration(950L);
            breath.setRepeatMode(ValueAnimator.REVERSE);
            breath.setRepeatCount(ValueAnimator.INFINITE);
            breath.addUpdateListener(animation ->
                    mascot.setAlpha((float) animation.getAnimatedValue()));
        }
        breath.start();
    }

    private void stopAnimation() {
        if (breath != null) breath.cancel();
        breath = null;
    }

    private void resetMascotTransform() {
        mascot.setAlpha(1f);
        mascot.setTranslationY(0f);
        mascot.setRotation(0f);
        mascot.setScaleX(1f);
        mascot.setScaleY(1f);
    }

    boolean usesGalleryMotion() {
        return galleryMotion;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
