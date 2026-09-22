package com.webapp.crazyshit;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;

/**
 * Small GPU-only reflection overlay for selected glass controls.
 *
 * The approved XML glass remains the base material. On Android 13+ this renderer adds a subtle
 * AGSL highlight whose position responds to motion and touch. Older Android versions keep the
 * static XML appearance.
 */
final class ZeroChillReactiveGlassRenderer {
    private ReflectionBackend backend;

    boolean isGpuSupported() {
        return Build.VERSION.SDK_INT >= 33;
    }

    void draw(
            Canvas canvas,
            RectF bounds,
            float radius,
            float motionBias,
            float touchPosition,
            float velocityBias,
            float strength
    ) {
        if (canvas == null || bounds == null || bounds.isEmpty()) return;
        if (!canvas.isHardwareAccelerated() || Build.VERSION.SDK_INT < 33) return;
        if (backend == null) backend = new Api33ReflectionBackend();
        backend.draw(
                canvas,
                bounds,
                radius,
                clamp(motionBias, -1f, 1f),
                clamp(touchPosition, 0f, 1f),
                clamp(velocityBias, -1f, 1f),
                clamp(strength, 0f, 1f)
        );
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface ReflectionBackend {
        void draw(
                Canvas canvas,
                RectF bounds,
                float radius,
                float motionBias,
                float touchPosition,
                float velocityBias,
                float strength
        );
    }

    @android.annotation.TargetApi(33)
    private static final class Api33ReflectionBackend implements ReflectionBackend {
        private static final String AGSL =
                "uniform float2 uOrigin;" +
                "uniform float2 uSize;" +
                "uniform float uMotion;" +
                "uniform float uTouch;" +
                "uniform float uVelocity;" +
                "uniform float uStrength;" +
                "half4 main(float2 p) {" +
                "  float2 uv = (p - uOrigin) / uSize;" +
                "  float center = clamp(0.5 + (uMotion * 0.10) + (uVelocity * 0.035)" +
                "      + ((uTouch - 0.5) * 0.12), 0.18, 0.82);" +
                "  float dx = abs(uv.x - center);" +
                "  float dy = abs(uv.y - 0.075);" +
                "  float horizontal = 1.0 - smoothstep(0.06, 0.46, dx);" +
                "  float vertical = 1.0 - smoothstep(0.015, 0.24, dy);" +
                "  float edgeFade = (1.0 - smoothstep(0.74, 1.0, uv.x))" +
                "      * smoothstep(0.0, 0.18, uv.x);" +
                "  float alpha = horizontal * vertical * edgeFade * uStrength * 0.16;" +
                "  half a = half(alpha);" +
                "  half3 tint = half3(0.72, 0.92, 1.0);" +
                "  return half4(tint * a, a);" +
                "}";

        private final android.graphics.RuntimeShader shader = new android.graphics.RuntimeShader(AGSL);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Api33ReflectionBackend() {
            paint.setShader(shader);
        }

        @Override
        public void draw(
                Canvas canvas,
                RectF bounds,
                float radius,
                float motionBias,
                float touchPosition,
                float velocityBias,
                float strength
        ) {
            shader.setFloatUniform("uOrigin", bounds.left, bounds.top);
            shader.setFloatUniform("uSize", Math.max(1f, bounds.width()), Math.max(1f, bounds.height()));
            shader.setFloatUniform("uMotion", motionBias);
            shader.setFloatUniform("uTouch", touchPosition);
            shader.setFloatUniform("uVelocity", velocityBias);
            shader.setFloatUniform("uStrength", strength);
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }
    }
}
