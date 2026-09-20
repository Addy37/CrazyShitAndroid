package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.RequiresApi;

/** Draws a hardware-backed backdrop blur beneath the portrait navigation on Android 12+. */
final class FrostedNavigationLayout extends LinearLayout {
    private View navigationView;
    private Api31RenderState renderState;
    private boolean blurDisabled;

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
        View navigation = navigationView;
        if (blurDisabled || Build.VERSION.SDK_INT < 31 || !canvas.isHardwareAccelerated()
                || navigation == null || navigation.getVisibility() != View.VISIBLE
                || navigation.getWidth() <= 0 || navigation.getHeight() <= 0) {
            super.dispatchDraw(canvas);
            return;
        }

        try {
            if (renderState == null) renderState = new Api31RenderState();
            renderState.draw(this, canvas, navigation, getDrawingTime());
        } catch (Throwable ignored) {
            blurDisabled = true;
            renderState = null;
            super.dispatchDraw(canvas);
        }
    }

    private void drawContentChildren(Canvas canvas, View navigation, long drawingTime) {
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child != navigation && child.getVisibility() != View.GONE) {
                drawChild(canvas, child, drawingTime);
            }
        }
    }

    private void drawNavigation(Canvas canvas, View navigation, long drawingTime) {
        drawChild(canvas, navigation, drawingTime);
    }

    @RequiresApi(31)
    private static final class Api31RenderState {
        private final RenderNode contentNode = new RenderNode("ZeroChill content");
        private final RenderNode blurNode = new RenderNode("ZeroChill navigation blur");
        private final Path clipPath = new Path();
        private final RectF clipBounds = new RectF();
        private RenderEffect blurEffect;
        private int blurRadius;

        void draw(FrostedNavigationLayout layout, Canvas canvas, View navigation, long drawingTime) {
            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0) {
                layout.drawNavigation(canvas, navigation, drawingTime);
                return;
            }

            contentNode.setPosition(0, 0, width, height);
            RecordingCanvas contentCanvas = contentNode.beginRecording(width, height);
            layout.drawContentChildren(contentCanvas, navigation, drawingTime);
            contentNode.endRecording();
            canvas.drawRenderNode(contentNode);

            int radius = layout.getResources().getDimensionPixelSize(
                    R.dimen.zc_navigation_blur_radius);
            if (blurEffect == null || blurRadius != radius) {
                blurRadius = radius;
                blurEffect = RenderEffect.createBlurEffect(
                        radius,
                        radius,
                        Shader.TileMode.CLAMP
                );
                blurNode.setRenderEffect(blurEffect);
            }

            int padding = Math.max(1, radius);
            int sampleLeft = Math.max(0, navigation.getLeft() - padding);
            int sampleTop = Math.max(0, navigation.getTop() - padding);
            int sampleRight = Math.min(width, navigation.getRight() + padding);
            int sampleBottom = Math.min(height, navigation.getBottom() + padding);
            int sampleWidth = sampleRight - sampleLeft;
            int sampleHeight = sampleBottom - sampleTop;

            if (sampleWidth > 0 && sampleHeight > 0) {
                blurNode.setPosition(0, 0, sampleWidth, sampleHeight);
                blurNode.setTranslationX(sampleLeft);
                blurNode.setTranslationY(sampleTop);
                RecordingCanvas blurCanvas = blurNode.beginRecording(sampleWidth, sampleHeight);
                blurCanvas.translate(-sampleLeft, -sampleTop);
                blurCanvas.drawRenderNode(contentNode);
                blurNode.endRecording();

                float cornerRadius = layout.getResources().getDimension(
                        R.dimen.zc_radius_pill);
                int checkpoint = canvas.save();
                clipBounds.set(
                        navigation.getLeft(),
                        navigation.getTop(),
                        navigation.getRight(),
                        navigation.getBottom()
                );
                clipPath.rewind();
                clipPath.addRoundRect(
                        clipBounds,
                        cornerRadius,
                        cornerRadius,
                        Path.Direction.CW
                );
                canvas.clipPath(clipPath);
                canvas.drawRenderNode(blurNode);
                canvas.restoreToCount(checkpoint);
            }

            layout.drawNavigation(canvas, navigation, drawingTime);
        }
    }
}
