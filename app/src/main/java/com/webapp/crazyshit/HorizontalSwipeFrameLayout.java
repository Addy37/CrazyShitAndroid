package com.webapp.crazyshit;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Intercepts deliberate horizontal swipes while leaving vertical scrolling to children.
 */
public final class HorizontalSwipeFrameLayout extends FrameLayout {
    public interface Listener {
        void onSwipeLeft();
        void onSwipeRight();
    }

    private final int touchSlop;
    private final int minFlingVelocity;
    private Listener listener;
    private float downX;
    private float downY;
    private float lastX;
    private boolean dragging;
    private VelocityTracker velocityTracker;

    public HorizontalSwipeFrameLayout(Context context) {
        this(context, null);
    }

    public HorizontalSwipeFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HorizontalSwipeFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ViewConfiguration config = ViewConfiguration.get(context);
        touchSlop = config.getScaledTouchSlop();
        minFlingVelocity = config.getScaledMinimumFlingVelocity() * 3;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        track(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = event.getY();
                dragging = false;
                return false;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                lastX = event.getX();
                if (!dragging && Math.abs(dx) > touchSlop * 1.35f &&
                        Math.abs(dx) > Math.abs(dy) * 1.35f) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return dragging;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finish(event, false);
                return false;

            default:
                return dragging;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        track(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                lastX = event.getX();
                return true;
            case MotionEvent.ACTION_UP:
                finish(event, true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                finish(event, false);
                return true;
            default:
                return true;
        }
    }

    private void track(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (velocityTracker != null) velocityTracker.recycle();
            velocityTracker = VelocityTracker.obtain();
        }
        if (velocityTracker != null) velocityTracker.addMovement(event);
    }

    private void finish(MotionEvent event, boolean dispatch) {
        float dx = event.getX() - downX;
        float velocityX = 0f;
        if (velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000);
            velocityX = velocityTracker.getXVelocity();
            velocityTracker.recycle();
            velocityTracker = null;
        }

        if (dispatch && dragging && listener != null) {
            float threshold = Math.max(dp(72), getWidth() * 0.18f);
            boolean farEnough = Math.abs(dx) >= threshold;
            boolean fastEnough = Math.abs(velocityX) >= minFlingVelocity && Math.abs(dx) >= dp(28);
            if (farEnough || fastEnough) {
                if (dx < 0f || velocityX < -minFlingVelocity) listener.onSwipeLeft();
                else listener.onSwipeRight();
            }
        }

        dragging = false;
        getParent().requestDisallowInterceptTouchEvent(false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
