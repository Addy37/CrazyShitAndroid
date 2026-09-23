package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Static gallery-shaped placeholders, shown only until the first media arrives. */
final class CreatorGallerySkeleton extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    CreatorGallerySkeleton(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float gap = dp(6);
        float width = (getWidth() - gap * 3) / 2f;
        paint.setColor(Color.rgb(22, 27, 31));
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 2; column++) {
                float left = gap + column * (width + gap);
                float top = gap + row * (width + gap);
                canvas.drawRoundRect(left, top, left + width, top + width, dp(8), dp(8), paint);
            }
        }
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
