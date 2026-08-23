package com.webapp.crazyshit;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the portrait-style More bottom sheet with a compact, scrollable landscape panel.
 * Portrait keeps using NativeMainActivity's existing BottomSheetDialog.
 */
final class LandscapeMoreDialog {
    private static final int FAVORITES_REQUEST = 3002;

    private LandscapeMoreDialog() {
    }

    static void attachSoon(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.getWindow().getDecorView().postDelayed(() -> attach(activity), 180L);
    }

    private static void attach(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing() || !isLandscape(activity)) return;
        View root = activity.findViewById(android.R.id.content);
        TextView more = findRailMore(root);
        if (more == null) {
            activity.getWindow().getDecorView().postDelayed(() -> attach(activity), 180L);
            return;
        }
        more.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            show(activity);
        });
    }

    private static void show(NativeMainActivity activity) {
        if (!isLandscape(activity)) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        panel.setBackground(panelBackground(activity));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(activity, "More", 21, Color.WHITE, true);
        TextView subtitle = text(activity, "CrazyShit controls", 12, Color.rgb(170, 170, 180), false);
        labels.addView(title);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView close = text(activity, "✕", 20, Color.rgb(205, 205, 212), false);
        close.setGravity(Gravity.CENTER);
        close.setClickable(true);
        close.setFocusable(true);
        close.setContentDescription("Close More");
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(activity, 8), 0, dp(activity, 2));

        List<Action> actions = new ArrayList<>();
        actions.add(new Action(
                "View style",
                "Change how posts are displayed",
                () -> invokeNoArgs(activity, "showViewStyleDialog")
        ));
        actions.add(new Action(
                "Settings",
                "Playback, privacy, haptics and app options",
                () -> activity.startActivity(new Intent(activity, SettingsActivity.class))
        ));
        actions.add(new Action(
                "Login / account",
                "Sign in here and return automatically",
                () -> activity.startActivity(new Intent(activity, LoginActivity.class))
        ));
        actions.add(new Action(
                "Library",
                "Continue, History and Watch Later",
                () -> activity.startActivityForResult(
                        new Intent(activity, FavoritesActivity.class),
                        FAVORITES_REQUEST
                )
        ));
        actions.add(new Action(
                "Categories",
                "Browse every CrazyShit category",
                () -> invokeNoArgs(activity, "showCategories")
        ));
        actions.add(new Action(
                "My profile",
                "Open the profile for your signed-in account",
                () -> activity.startActivity(new Intent(activity, ProfileActivity.class))
        ));
        actions.add(new Action(
                "Open full website",
                "Use the compatibility browser",
                () -> {
                    Intent intent = new Intent(activity, WebFallbackActivity.class);
                    intent.putExtra(WebFallbackActivity.EXTRA_URL, CrazyShitRepository.HOME);
                    activity.startActivity(intent);
                }
        ));
        actions.add(new Action(
                "Check for updates",
                "Download and install updates inside the app",
                () -> invokeBoolean(activity, "checkForUpdates", true)
        ));

        for (int i = 0; i < actions.size(); i += 2) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);

            addActionCard(activity, dialog, row, actions.get(i));
            if (i + 1 < actions.size()) {
                addActionCard(activity, dialog, row, actions.get(i + 1));
            } else {
                View spacer = new View(activity);
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1f);
                spacerParams.setMargins(dp(activity, 4), 0, dp(activity, 4), 0);
                row.addView(spacer, spacerParams);
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int width = Math.min((int) (screenWidth * 0.72f), dp(activity, 420));
        int height = (int) (screenHeight * 0.88f);

        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = width;
        attrs.height = height;
        attrs.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        attrs.dimAmount = 0.48f;
        window.setAttributes(attrs);
    }

    private static void addActionCard(
            NativeMainActivity activity,
            Dialog dialog,
            LinearLayout row,
            Action action
    ) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 11), dp(activity, 9), dp(activity, 11), dp(activity, 9));
        card.setBackground(actionBackground(activity));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dialog.dismiss();
            action.run.run();
        });

        TextView title = text(activity, action.title, 15, Color.WHITE, true);
        title.setMaxLines(1);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text(activity, action.subtitle, 10, Color.rgb(174, 174, 184), false);
        subtitle.setMaxLines(2);
        subtitle.setPadding(0, dp(activity, 3), 0, 0);
        card.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        row.addView(card, params);
    }

    private static TextView text(
            NativeMainActivity activity,
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private static GradientDrawable panelBackground(NativeMainActivity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(activity, 20));
        background.setColor(Color.rgb(18, 18, 21));
        background.setStroke(dp(activity, 1), Color.rgb(54, 54, 60));
        return background;
    }

    private static GradientDrawable actionBackground(NativeMainActivity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(activity, 12));
        background.setColor(Color.rgb(28, 28, 32));
        background.setStroke(dp(activity, 1), Color.rgb(48, 48, 55));
        return background;
    }

    private static void invokeNoArgs(NativeMainActivity activity, String name) {
        try {
            Method method = NativeMainActivity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(activity);
        } catch (Exception ignored) {
        }
    }

    private static void invokeBoolean(NativeMainActivity activity, String name, boolean value) {
        try {
            Method method = NativeMainActivity.class.getDeclaredMethod(name, boolean.class);
            method.setAccessible(true);
            method.invoke(activity, value);
        } catch (Exception ignored) {
        }
    }

    private static TextView findRailMore(View view) {
        if (view == null) return null;
        if (view instanceof TextView) {
            CharSequence description = view.getContentDescription();
            if (description != null && "More".contentEquals(description)) {
                return (TextView) view;
            }
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findRailMore(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean isLandscape(NativeMainActivity activity) {
        return activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class Action {
        final String title;
        final String subtitle;
        final Runnable run;

        Action(String title, String subtitle, Runnable run) {
            this.title = title;
            this.subtitle = subtitle;
            this.run = run;
        }
    }
}
