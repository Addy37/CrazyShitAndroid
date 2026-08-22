package com.webapp.crazyshit;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * A FrameLayout that lets child controls handle normal taps/seeks, but takes over
 * once a deliberate downward vertical drag is detected.
 */
public final class SwipeMinimizeFrameLayout extends FrameLayout {
    public interface Listener {
        void onDrag(float distancePx, float progress);
        void onRelease(boolean minimize, float distancePx);
    }

    private final int touchSlop;
    private Listener listener;
    private boolean swipeEnabled = true;
    private boolean dragging;
    private float downX;
    private float downY;
    private float lastDistance;

    public SwipeMinimizeFrameLayout(Context context) {
        this(context, null);
    }

    public SwipeMinimizeFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeMinimizeFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSwipeEnabled(boolean enabled) {
        swipeEnabled = enabled;
        if (!enabled) resetGesture();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!swipeEnabled) return super.onInterceptTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                lastDistance = 0f;
                dragging = false;
                return super.onInterceptTouchEvent(event);

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragging && dy > touchSlop && dy > Math.abs(dx) * 1.15f) {
                    dragging = true;
                    lastDistance = Math.max(0f, dy);
                    dispatchDrag(lastDistance);
                    return true;
                }
                return dragging || super.onInterceptTouchEvent(event);

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) resetGesture();
                return false;

            default:
                return dragging || super.onInterceptTouchEvent(event);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!swipeEnabled) return super.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return true;
                lastDistance = Math.max(0f, event.getY() - downY);
                dispatchDrag(lastDistance);
                return true;

            case MotionEvent.ACTION_UP:
                if (dragging) {
                    lastDistance = Math.max(lastDistance, event.getY() - downY);
                    float threshold = Math.min(Math.max(getHeight() * 0.24f, dp(84)), dp(150));
                    boolean minimize = lastDistance >= threshold;
                    if (listener != null) listener.onRelease(minimize, lastDistance);
                    resetGesture();
                    return true;
                }
                resetGesture();
                return super.onTouchEvent(event);

            case MotionEvent.ACTION_CANCEL:
                if (dragging && listener != null) listener.onRelease(false, lastDistance);
                resetGesture();
                return true;

            default:
                return true;
        }
    }

    private void dispatchDrag(float distance) {
        if (listener == null) return;
        float range = Math.max(dp(180), getHeight() * 0.75f);
        float progress = Math.max(0f, Math.min(1f, distance / range));
        listener.onDrag(distance, progress);
    }

    private void resetGesture() {
        dragging = false;
        lastDistance = 0f;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
