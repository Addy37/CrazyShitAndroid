package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.ImageView;

/** Matrix-backed photo view with pinch zoom and drag panning. */
final class ZoomableImageView extends ImageView {
    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 5f;

    private final Matrix zoomMatrix = new Matrix();
    private final RectF drawableBounds = new RectF();
    private final ScaleGestureDetector scaleDetector;
    private final int touchSlop;

    private float zoom = MIN_SCALE;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private boolean moved;
    private boolean multiTouch;
    private boolean zoomEnabled = true;

    ZoomableImageView(Context context) {
        super(context);
        super.setScaleType(ScaleType.MATRIX);
        setBackgroundColor(android.graphics.Color.BLACK);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        multiTouch = true;
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
    }

    void setZoomEnabled(boolean enabled) {
        zoomEnabled = enabled;
        if (!enabled) resetZoom();
    }

    void resetZoom() {
        zoom = MIN_SCALE;
        moved = false;
        multiTouch = false;
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
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                moved = false;
                multiTouch = false;
                disallowPager(zoom > MIN_SCALE + 0.01f);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiTouch = true;
                disallowPager(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop) {
                    moved = true;
                }
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
                multiTouch = true;
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved && !multiTouch) performClick();
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

    private void disallowPager(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
