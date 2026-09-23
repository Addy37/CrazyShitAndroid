package com.webapp.crazyshit;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

/** Shared Android 12+ backdrop renderer for floating ZeroChill glass overlays. */
final class FrostedBackdropRenderer {
    interface Drawer {
        void drawContent(Canvas canvas, View overlay, long drawingTime);
        void drawOverlay(Canvas canvas, View overlay, long drawingTime);
    }

    private Api31RenderState renderState;
    private boolean blurDisabled;

    boolean draw(
            ViewGroup layout,
            Canvas canvas,
            View overlay,
            long drawingTime,
            Drawer drawer
    ) {
        if (blurDisabled || Build.VERSION.SDK_INT < 31 || !canvas.isHardwareAccelerated()
                || overlay == null || overlay.getVisibility() != View.VISIBLE
                || overlay.getWidth() <= 0 || overlay.getHeight() <= 0) {
            return false;
        }

        try {
            if (renderState == null) renderState = new Api31RenderState();
            renderState.draw(layout, canvas, overlay, drawingTime, drawer);
            return true;
        } catch (Throwable ignored) {
            blurDisabled = true;
            renderState = null;
            return false;
        }
    }

    @RequiresApi(31)
    private static final class Api31RenderState {
        private final RenderNode contentNode = new RenderNode("ZeroChill content");
        private final RenderNode blurNode = new RenderNode("ZeroChill backdrop blur");
        private final Path clipPath = new Path();
        private final RectF clipBounds = new RectF();
        private RenderEffect blurEffect;
        private int blurRadius;

        void draw(
                ViewGroup layout,
                Canvas canvas,
                View overlay,
                long drawingTime,
                Drawer drawer
        ) {
            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0) {
                drawer.drawOverlay(canvas, overlay, drawingTime);
                return;
            }

            contentNode.setPosition(0, 0, width, height);
            RecordingCanvas contentCanvas = contentNode.beginRecording(width, height);
            drawer.drawContent(contentCanvas, overlay, drawingTime);
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
            int sampleLeft = Math.max(0, overlay.getLeft() - padding);
            int sampleTop = Math.max(0, overlay.getTop() - padding);
            int sampleRight = Math.min(width, overlay.getRight() + padding);
            int sampleBottom = Math.min(height, overlay.getBottom() + padding);
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
                        overlay.getLeft(),
                        overlay.getTop(),
                        overlay.getRight(),
                        overlay.getBottom()
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

            drawer.drawOverlay(canvas, overlay, drawingTime);
        }
    }
}
