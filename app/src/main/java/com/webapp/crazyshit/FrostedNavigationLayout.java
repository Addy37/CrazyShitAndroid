package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/** Draws a hardware-backed backdrop blur beneath the portrait navigation on Android 12+. */
final class FrostedNavigationLayout extends LinearLayout {
    private final FrostedBackdropRenderer backdropRenderer = new FrostedBackdropRenderer();
    private final FrostedBackdropRenderer.Drawer backdropDrawer =
            new FrostedBackdropRenderer.Drawer() {
                @Override
                public void drawContent(Canvas canvas, View overlay, long drawingTime) {
                    drawContentChildren(canvas, overlay, drawingTime);
                }

                @Override
                public void drawOverlay(Canvas canvas, View overlay, long drawingTime) {
                    drawChild(canvas, overlay, drawingTime);
                }
            };
    private View navigationView;

    FrostedNavigationLayout(Context context) {
        super(context);
    }

    FrostedNavigationLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setFrostedNavigationView(View navigationView) {
        if (this.navigationView == navigationView) return;
        this.navigationView = navigationView;
        invalidate();
    }

    View frostedNavigationViewForTest() {
        return navigationView;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (backdropRenderer.draw(
                this, canvas, navigationView, getDrawingTime(), backdropDrawer)) {
            return;
        }
        super.dispatchDraw(canvas);
    }

    private void drawContentChildren(Canvas canvas, View navigation, long drawingTime) {
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child != navigation && child.getVisibility() != View.GONE) {
                drawChild(canvas, child, drawingTime);
            }
        }
    }

}
