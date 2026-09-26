package com.webapp.crazyshit;

import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Face-only ZEROCHILL playback ident used while Shows video orientation and first frame settle.
 *
 * Mirrors the layered mascot reveal from the app splash at the same 188dp size, but loops quickly
 * and intentionally omits the ZEROCHILL wordmark/tagline.
 */
final class ShowsPlaybackSplashView extends FrameLayout {
    static final int MASCOT_SIZE_DP = 188;
    static final long FIRST_REVEAL_COMPLETE_MS = 390L;
    static final long LOOP_MS = 690L;

    private final ImageView horns;
    private final ImageView face;
    private final ImageView xEye;
    private final ImageView angryEye;
    private final ImageView teeth;
    private final ImageView tongue;
    private final ImageView outline;
    private boolean running;

    ShowsPlaybackSplashView(Context context) {
        super(context);
        setContentDescription("Preparing video");
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        horns = layer(R.drawable.zc_devil_horns);
        face = layer(R.drawable.zc_devil_face);
        xEye = layer(R.drawable.zc_devil_x_eye);
        angryEye = layer(R.drawable.zc_devil_angry_eye);
        teeth = layer(R.drawable.zc_devil_teeth);
        tongue = layer(R.drawable.zc_devil_tongue);
        outline = layer(R.drawable.zc_devil_outline);

        addView(horns);
        addView(face);
        addView(xEye);
        addView(angryEye);
        addView(teeth);
        addView(tongue);
        addView(outline);

        resetLayers();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (horns != null) updateAnimation();
    }

    void stop() {
        running = false;
        removeCallbacks(loopRunnable);
        cancelLayerAnimations();
    }

    private void updateAnimation() {
        if (!isAttachedToWindow() || getVisibility() != View.VISIBLE) {
            stop();
            return;
        }
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            stop();
            showStatic();
            return;
        }
        if (running) return;
        running = true;
        playLoop();
    }

    private final Runnable loopRunnable = () -> {
        if (!running || !isAttachedToWindow() || getVisibility() != View.VISIBLE) return;
        playLoop();
    };

    private void playLoop() {
        removeCallbacks(loopRunnable);
        cancelLayerAnimations();
        resetLayers();

        reveal(horns, 0L, 110L);
        reveal(face, 55L, 120L);
        reveal(outline, 115L, 115L);
        reveal(xEye, 220L, 55L);
        reveal(angryEye, 255L, 70L);
        reveal(teeth, 315L, 65L);
        reveal(tongue, 355L, 60L);

        outline.animate()
                .alpha(0.74f)
                .setStartDelay(430L)
                .setDuration(70L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (!running) return;
                    outline.animate()
                            .alpha(1f)
                            .setDuration(85L)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();

        postDelayed(() -> {
            if (!running || !isAttachedToWindow() || getVisibility() != View.VISIBLE) return;
            fadeForRepeat();
        }, 570L);
        postDelayed(loopRunnable, LOOP_MS);
    }

    private void fadeForRepeat() {
        for (ImageView layer : layers()) {
            layer.animate()
                    .alpha(0f)
                    .setStartDelay(0L)
                    .setDuration(95L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void reveal(ImageView layer, long delayMs, long durationMs) {
        layer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delayMs)
                .setDuration(durationMs)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private ImageView layer(int drawableRes) {
        ImageView view = new ImageView(getContext());
        view.setImageResource(drawableRes);
        view.setContentDescription(null);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setAlpha(0f);
        view.setScaleX(0.985f);
        view.setScaleY(0.985f);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
        return view;
    }

    private void resetLayers() {
        for (ImageView layer : layers()) {
            layer.setAlpha(0f);
            layer.setScaleX(0.985f);
            layer.setScaleY(0.985f);
        }
    }

    private void showStatic() {
        cancelLayerAnimations();
        for (ImageView layer : layers()) {
            layer.setAlpha(1f);
            layer.setScaleX(1f);
            layer.setScaleY(1f);
        }
    }

    private void cancelLayerAnimations() {
        for (ImageView layer : layers()) layer.animate().cancel();
    }

    private ImageView[] layers() {
        return new ImageView[] {horns, face, xEye, angryEye, teeth, tongue, outline};
    }
}
