package com.webapp.crazyshit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

/** Matrix-backed photo view with pinch zoom and drag panning. */
final class ZoomableImageView extends ImageView {
    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 5f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;

    private final Matrix zoomMatrix = new Matrix();
    private final RectF drawableBounds = new RectF();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private float zoom = MIN_SCALE;
    private float lastX;
    private float lastY;
    private boolean zoomEnabled = true;
    private ValueAnimator zoomAnimator;

    ZoomableImageView(Context context) {
        super(context);
        super.setScaleType(ScaleType.MATRIX);
        setBackgroundColor(android.graphics.Color.BLACK);
        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        cancelZoomAnimation();
                        disallowPager(true);
                        return zoomEnabled;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (!zoomEnabled || getDrawable() == null) return false;
                        float requested = detector.getScaleFactor();
                        float next = clamp(zoom * requested, MIN_SCALE, MAX_SCALE);
                        float applied = next / zoom;
                        zoom = next;
                        zoomMatrix.postScale(
                                applied,
                                applied,
                                detector.getFocusX(),
                                detector.getFocusY()
                        );
                        constrain();
                        setImageMatrix(zoomMatrix);
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        if (zoom <= MIN_SCALE + 0.01f) resetZoom();
                    }
                }
        );
        gestureDetector = new GestureDetector(
                context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent event) {
                        return performClick();
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        if (!zoomEnabled || getDrawable() == null) return false;
                        float target = zoom > MIN_SCALE + 0.05f
                                ? MIN_SCALE
                                : DOUBLE_TAP_SCALE;
                        animateZoom(target, event.getX(), event.getY());
                        return true;
                    }
                }
        );
    }

    void setZoomEnabled(boolean enabled) {
        zoomEnabled = enabled;
        if (!enabled) resetZoom();
    }

    void resetZoom() {
        cancelZoomAnimation();
        zoom = MIN_SCALE;
        fitDrawable();
        disallowPager(false);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (zoomMatrix != null) post(this::resetZoom);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        post(this::resetZoom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!zoomEnabled) return super.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                disallowPager(zoom > MIN_SCALE + 0.01f);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                disallowPager(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                if (zoom > MIN_SCALE + 0.01f && !scaleDetector.isInProgress()) {
                    disallowPager(true);
                    zoomMatrix.postTranslate(x - lastX, y - lastY);
                    constrain();
                    setImageMatrix(zoomMatrix);
                } else if (!scaleDetector.isInProgress()) {
                    disallowPager(false);
                }
                lastX = x;
                lastY = y;
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                disallowPager(zoom > MIN_SCALE + 0.01f);
                return true;
            case MotionEvent.ACTION_CANCEL:
                disallowPager(false);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void fitDrawable() {
        Drawable drawable = getDrawable();
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (drawable == null || width <= 0 || height <= 0 ||
                drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            zoomMatrix.reset();
            setImageMatrix(zoomMatrix);
            return;
        }

        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();
        float base = Math.min(width / drawableWidth, height / drawableHeight);
        float left = getPaddingLeft() + (width - drawableWidth * base) / 2f;
        float top = getPaddingTop() + (height - drawableHeight * base) / 2f;
        zoomMatrix.reset();
        zoomMatrix.postScale(base, base);
        zoomMatrix.postTranslate(left, top);
        setImageMatrix(zoomMatrix);
    }

    private void constrain() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() <= 0 || getHeight() <= 0 ||
                drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) return;
        drawableBounds.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        zoomMatrix.mapRect(drawableBounds);

        float dx = 0f;
        float dy = 0f;
        if (drawableBounds.width() <= getWidth()) {
            dx = getWidth() / 2f - drawableBounds.centerX();
        } else if (drawableBounds.left > 0f) {
            dx = -drawableBounds.left;
        } else if (drawableBounds.right < getWidth()) {
            dx = getWidth() - drawableBounds.right;
        }
        if (drawableBounds.height() <= getHeight()) {
            dy = getHeight() / 2f - drawableBounds.centerY();
        } else if (drawableBounds.top > 0f) {
            dy = -drawableBounds.top;
        } else if (drawableBounds.bottom < getHeight()) {
            dy = getHeight() - drawableBounds.bottom;
        }
        zoomMatrix.postTranslate(dx, dy);
    }

    private void animateZoom(float requestedTarget, float focusX, float focusY) {
        cancelZoomAnimation();
        float start = zoom;
        float target = clamp(requestedTarget, MIN_SCALE, MAX_SCALE);
        if (Math.abs(start - target) < 0.01f) return;

        final float[] applied = {start};
        ValueAnimator animator = ValueAnimator.ofFloat(start, target);
        zoomAnimator = animator;
        animator.setDuration(220L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float next = (float) animation.getAnimatedValue();
            float factor = next / applied[0];
            applied[0] = next;
            zoom = next;
            zoomMatrix.postScale(factor, factor, focusX, focusY);
            constrain();
            setImageMatrix(zoomMatrix);
            disallowPager(true);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (zoomAnimator != animation) return;
                zoomAnimator = null;
                if (target <= MIN_SCALE + 0.01f) resetZoom();
                else disallowPager(true);
            }
        });
        animator.start();
    }

    private void cancelZoomAnimation() {
        ValueAnimator running = zoomAnimator;
        zoomAnimator = null;
        if (running != null) running.cancel();
    }

    private void disallowPager(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
