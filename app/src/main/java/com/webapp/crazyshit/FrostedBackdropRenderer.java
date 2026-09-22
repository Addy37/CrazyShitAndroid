package com.webapp.crazyshit;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
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

    static boolean supportsLensRefraction(int sdkInt) {
        return sdkInt >= 33;
    }

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
        private final RenderNode blurNode = new RenderNode("ZeroChill backdrop glass");
        private final Path clipPath = new Path();
        private final RectF clipBounds = new RectF();
        private RenderEffect blurEffect;
        private int blurRadius;
        private Api33LensState lensState;
        private boolean lensUnavailable;

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
            }

            int padding = Math.max(1, radius);
            int sampleLeft = Math.max(0, overlay.getLeft() - padding);
            int sampleTop = Math.max(0, overlay.getTop() - padding);
            int sampleRight = Math.min(width, overlay.getRight() + padding);
            int sampleBottom = Math.min(height, overlay.getBottom() + padding);
            int sampleWidth = sampleRight - sampleLeft;
            int sampleHeight = sampleBottom - sampleTop;

            if (sampleWidth > 0 && sampleHeight > 0) {
                RenderEffect backdropEffect = blurEffect;
                if (supportsLensRefraction(Build.VERSION.SDK_INT) && !lensUnavailable) {
                    try {
                        if (lensState == null) lensState = new Api33LensState();
                        float lensLeft = overlay.getLeft() - sampleLeft;
                        float lensTop = overlay.getTop() - sampleTop;
                        float strength = layout.getResources().getDimension(
                                R.dimen.zc_glass_refraction_strength);
                        backdropEffect = lensState.effect(
                                blurEffect,
                                sampleWidth,
                                sampleHeight,
                                lensLeft,
                                lensTop,
                                overlay.getWidth(),
                                overlay.getHeight(),
                                strength
                        );
                    } catch (Throwable ignored) {
                        // Keep the proven blur path if AGSL compilation is unavailable on a device.
                        lensUnavailable = true;
                        lensState = null;
                        backdropEffect = blurEffect;
                    }
                }

                blurNode.setRenderEffect(backdropEffect);
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

    @RequiresApi(33)
    private static final class Api33LensState {
        private static final String SHADER =
                "uniform shader content;\n" +
                "uniform float2 sampleSize;\n" +
                "uniform float2 lensOrigin;\n" +
                "uniform float2 lensSize;\n" +
                "uniform float strength;\n" +
                "half4 main(float2 p) {\n" +
                "    float2 halfLens = max(lensSize * 0.5, float2(1.0, 1.0));\n" +
                "    float2 center = lensOrigin + halfLens;\n" +
                "    float2 n = (p - center) / halfLens;\n" +
                "    float radial = min(1.0, length(n));\n" +
                "    float edgeX = smoothstep(0.78, 1.0, abs(n.x));\n" +
                "    float edgeY = smoothstep(0.68, 1.0, abs(n.y));\n" +
                "    float rim = smoothstep(0.74, 1.0, radial);\n" +
                "    float edge = max(rim, max(edgeX * 0.65, edgeY));\n" +
                "    float2 direction = n / max(length(n), 0.001);\n" +
                "    float2 warped = p + direction * (edge * strength);\n" +
                "    warped.x += sin(n.y * 3.14159265) * strength * 0.08 * edge;\n" +
                "    warped = clamp(warped, float2(0.0, 0.0), sampleSize - float2(1.0, 1.0));\n" +
                "    return content.eval(warped);\n" +
                "}";

        private final RuntimeShader shader = new RuntimeShader(SHADER);
        private final RenderEffect lensEffect =
                RenderEffect.createRuntimeShaderEffect(shader, "content");
        private RenderEffect chainedEffect;
        private RenderEffect chainedBlur;
        private boolean disabled;

        RenderEffect effect(
                RenderEffect blur,
                float sampleWidth,
                float sampleHeight,
                float lensLeft,
                float lensTop,
                float lensWidth,
                float lensHeight,
                float strength
        ) {
            if (disabled) return blur;
            try {
                shader.setFloatUniform("sampleSize", sampleWidth, sampleHeight);
                shader.setFloatUniform("lensOrigin", lensLeft, lensTop);
                shader.setFloatUniform("lensSize", lensWidth, lensHeight);
                shader.setFloatUniform("strength", strength);
                if (chainedEffect == null || chainedBlur != blur) {
                    chainedBlur = blur;
                    chainedEffect = RenderEffect.createChainEffect(lensEffect, blur);
                }
                return chainedEffect;
            } catch (Throwable ignored) {
                disabled = true;
                chainedEffect = null;
                chainedBlur = null;
                return blur;
            }
        }
    }
}
