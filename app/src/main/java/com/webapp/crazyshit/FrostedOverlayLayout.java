package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/** FrameLayout that draws one pinned child over a blurred copy of its scrolling content. */
final class FrostedOverlayLayout extends FrameLayout {
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
    private View frostedOverlay;

    FrostedOverlayLayout(Context context) {
        super(context);
    }

    FrostedOverlayLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setFrostedOverlay(View frostedOverlay) {
        if (this.frostedOverlay == frostedOverlay) return;
        this.frostedOverlay = frostedOverlay;
        invalidate();
    }

    View frostedOverlayForTest() {
        return frostedOverlay;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (backdropRenderer.draw(
                this, canvas, frostedOverlay, getDrawingTime(), backdropDrawer)) {
            return;
        }
        super.dispatchDraw(canvas);
    }

    private void drawContentChildren(Canvas canvas, View overlay, long drawingTime) {
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child != overlay && child.getVisibility() != View.GONE) {
                drawChild(canvas, child, drawingTime);
            }
        }
    }
}
