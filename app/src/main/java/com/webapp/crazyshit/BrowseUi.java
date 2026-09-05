package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.function.Consumer;

/** Common spacing, focus targets and inset handling for native browsing screens. */
final class BrowseUi {
    static final int BACKGROUND = Color.BLACK;
    static final int SURFACE = Color.rgb(20, 20, 24);
    static final int MUTED = Color.rgb(174, 174, 184);
    private BrowseUi() { }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(Context context, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    static TextView text(Context context, String label, int size, int color) {
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    static TextView action(Context context, String label, String description, View.OnClickListener click) {
        TextView action = text(context, label, 15, UiPalette.PRIMARY);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        action.setMinHeight(dp(context, 48));
        action.setMinWidth(dp(context, 48));
        action.setContentDescription(description);
        action.setBackground(rounded(context, SURFACE, 14));
        action.setFocusable(true);
        action.setOnClickListener(click);
        return action;
    }

    static LinearLayout screen(Activity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        activity.getWindow().setStatusBarColor(Color.BLACK);
        activity.getWindow().setNavigationBarColor(Color.BLACK);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        return root;
    }

    static void hideKeyboard(Activity activity, View input) {
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    static TextWatcher onText(Consumer<String> changed) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            public void afterTextChanged(Editable text) { changed.accept(text.toString()); }
        };
    }
}
