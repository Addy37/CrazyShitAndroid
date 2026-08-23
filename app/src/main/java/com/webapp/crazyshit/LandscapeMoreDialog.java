package com.webapp.crazyshit;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified More surface for portrait and landscape.
 *
 * Portrait uses a compact rounded bottom panel. Landscape uses the same grouped content as a
 * right-side panel. The old large equal-weight cards are replaced by shorter grouped rows.
 */
final class LandscapeMoreDialog {
    private static final int FAVORITES_REQUEST = 3002;
    private static final int NAV_MORE = 5;
    private static final int ORANGE = Color.rgb(255, 90, 31);

    private LandscapeMoreDialog() {
    }

    static void attachSoon(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.postDelayed(() -> attach(activity, 0), 120L);
    }

    private static void attach(NativeMainActivity activity, int attempt) {
        if (activity == null || activity.isFinishing()) return;

        View more = isLandscape(activity)
                ? findRailMore(activity.findViewById(android.R.id.content))
                : findPortraitMore(activity);

        if (more == null) {
            if (attempt < 8) {
                activity.getWindow().getDecorView().postDelayed(
                        () -> attach(activity, attempt + 1),
                        140L
                );
            }
            return;
        }

        more.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            show(activity);
        });
    }

    private static View findPortraitMore(NativeMainActivity activity) {
        BottomNavigationView nav = fieldValue(activity, "bottomNavigation", BottomNavigationView.class);
        if (nav == null) return null;
        return nav.findViewById(NAV_MORE);
    }

    private static void show(NativeMainActivity activity) {
        final boolean landscape = isLandscape(activity);
        final CharSequence oldTitle = textValue(activity, "headerTitle");
        final CharSequence oldSubtitle = textValue(activity, "headerSubtitle");
        setHeader(activity, "More", "Settings, library and account");

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 16));
        panel.setBackground(panelBackground(activity));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 2), 0, 0, dp(activity, 8));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(activity, "More", 23, Color.WHITE, true);
        TextView subtitle = text(
                activity,
                "CrazyShit v" + BuildConfig.VERSION_NAME,
                12,
                Color.rgb(170, 170, 180),
                false
        );
        subtitle.setPadding(0, dp(activity, 2), 0, 0);
        labels.addView(title);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView close = text(activity, "×", 25, Color.rgb(210, 210, 218), false);
        close.setGravity(Gravity.CENTER);
        close.setClickable(true);
        close.setFocusable(true);
        close.setContentDescription("Close More");
        close.setBackground(circleBackground(activity, Color.rgb(34, 34, 39)));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(activity, 2), 0, dp(activity, 4));

        addSection(
                activity,
                dialog,
                content,
                "YOUR STUFF",
                actions(
                        new Action("◎", "Login / account", "Sign in here and return automatically",
                                () -> activity.startActivity(new Intent(activity, LoginActivity.class))),
                        new Action("◉", "My profile", "Open your signed-in profile",
                                () -> activity.startActivity(new Intent(activity, ProfileActivity.class))),
                        new Action("▣", "Library", "Continue, History and Watch Later",
                                () -> activity.startActivityForResult(
                                        new Intent(activity, FavoritesActivity.class),
                                        FAVORITES_REQUEST
                                ))
                )
        );

        addSection(
                activity,
                dialog,
                content,
                "BROWSE",
                actions(
                        new Action("⚡", "Trending", "Browse the classic Trending feed",
                                () -> activity.startActivity(NativeFeedBrowserActivity.create(
                                        activity,
                                        "Trending",
                                        CrazyShitRepository.TRENDING,
                                        false
                                ))),
                        new Action("▧", "Memes", "Browse the classic Memes feed",
                                () -> activity.startActivity(NativeFeedBrowserActivity.create(
                                        activity,
                                        "Memes",
                                        MemeRepository.MEMES,
                                        true
                                ))),
                        new Action("↗", "Open full website", "Use the compatibility browser",
                                () -> {
                                    Intent intent = new Intent(activity, WebFallbackActivity.class);
                                    intent.putExtra(WebFallbackActivity.EXTRA_URL, CrazyShitRepository.HOME);
                                    activity.startActivity(intent);
                                })
                )
        );

        addSection(
                activity,
                dialog,
                content,
                "APP",
                actions(
                        new Action("⚙︎", "Settings", "Playback, privacy, haptics and app options",
                                () -> activity.startActivity(new Intent(activity, SettingsActivity.class))),
                        new Action("≡", "View style", "Change how posts are displayed",
                                () -> invokeNoArgs(activity, "showViewStyleDialog")),
                        new Action("↻", "Check for updates", "Download and install updates inside the app",
                                () -> invokeBoolean(activity, "checkForUpdates", true))
                )
        );

        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> restoreHeader(activity, oldTitle, oldSubtitle));
        dialog.show();

        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.dimAmount = 0.52f;

        if (landscape) {
            attrs.width = Math.min((int) (screenWidth * 0.70f), dp(activity, 430));
            attrs.height = (int) (screenHeight * 0.90f);
            attrs.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        } else {
            attrs.width = Math.max(dp(activity, 280), screenWidth - dp(activity, 16));
            attrs.height = (int) (screenHeight * 0.84f);
            attrs.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            attrs.y = dp(activity, 8);
        }
        window.setAttributes(attrs);

        panel.setAlpha(0f);
        if (landscape) panel.setTranslationX(dp(activity, 42));
        else panel.setTranslationY(dp(activity, 28));
        panel.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(220L)
                .start();
    }

    private static void addSection(
            NativeMainActivity activity,
            Dialog dialog,
            LinearLayout parent,
            String label,
            List<Action> actions
    ) {
        TextView section = text(activity, label, 11, Color.rgb(145, 145, 155), true);
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(activity, 6), dp(activity, 10), dp(activity, 6), dp(activity, 6));
        parent.addView(section, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), dp(activity, 2));
        group.setBackground(groupBackground(activity));

        for (int i = 0; i < actions.size(); i++) {
            addActionRow(activity, dialog, group, actions.get(i));
            if (i < actions.size() - 1) {
                View divider = new View(activity);
                divider.setBackgroundColor(Color.rgb(43, 43, 49));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(activity, 1));
                dividerParams.setMargins(dp(activity, 58), 0, dp(activity, 8), 0);
                group.addView(divider, dividerParams);
            }
        }

        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(-1, -2);
        groupParams.setMargins(0, 0, 0, dp(activity, 4));
        parent.addView(group, groupParams);
    }

    private static void addActionRow(
            NativeMainActivity activity,
            Dialog dialog,
            LinearLayout group,
            Action action
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        row.setClickable(true);
        row.setFocusable(true);
        applySelectableForeground(activity, row);

        TextView icon = text(activity, action.icon, 20, ORANGE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(circleBackground(activity, Color.rgb(53, 32, 28)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(activity, 12), 0, dp(activity, 8), 0);

        TextView title = text(activity, action.title, 16, Color.WHITE, true);
        title.setMaxLines(1);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text(activity, action.subtitle, 11, Color.rgb(170, 170, 180), false);
        subtitle.setMaxLines(2);
        subtitle.setPadding(0, dp(activity, 2), 0, 0);
        labels.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView chevron = text(activity, "›", 25, Color.rgb(120, 120, 132), false);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 42)));

        row.setOnTouchListener((v, event) -> {
            int touch = event.getActionMasked();
            if (touch == MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(80L).start();
            } else if (touch == MotionEvent.ACTION_UP || touch == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f).setDuration(130L).start();
            }
            return false;
        });
        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dialog.dismiss();
            action.run.run();
        });

        group.addView(row, new LinearLayout.LayoutParams(-1, dp(activity, 68)));
    }

    private static List<Action> actions(Action... actions) {
        ArrayList<Action> result = new ArrayList<>();
        for (Action action : actions) result.add(action);
        return result;
    }

    private static void applySelectableForeground(NativeMainActivity activity, View view) {
        TypedValue typed = new TypedValue();
        if (!activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, typed, true)) return;
        try {
            view.setForeground(activity.getDrawable(typed.resourceId));
        } catch (Exception ignored) {
        }
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
        background.setCornerRadius(dp(activity, 24));
        background.setColor(Color.rgb(18, 18, 21));
        background.setStroke(dp(activity, 1), Color.rgb(51, 51, 58));
        return background;
    }

    private static GradientDrawable groupBackground(NativeMainActivity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(activity, 16));
        background.setColor(Color.rgb(25, 25, 29));
        background.setStroke(dp(activity, 1), Color.rgb(45, 45, 52));
        return background;
    }

    private static GradientDrawable circleBackground(NativeMainActivity activity, int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private static void setHeader(NativeMainActivity activity, String title, String subtitle) {
        TextView headerTitle = fieldValue(activity, "headerTitle", TextView.class);
        TextView headerSubtitle = fieldValue(activity, "headerSubtitle", TextView.class);
        if (headerTitle != null) {
            headerTitle.animate().cancel();
            headerTitle.setText(title);
        }
        if (headerSubtitle != null) {
            headerSubtitle.animate().cancel();
            headerSubtitle.setText(subtitle);
        }
    }

    private static void restoreHeader(
            NativeMainActivity activity,
            CharSequence title,
            CharSequence subtitle
    ) {
        if (activity == null || activity.isFinishing()) return;
        TextView headerTitle = fieldValue(activity, "headerTitle", TextView.class);
        TextView headerSubtitle = fieldValue(activity, "headerSubtitle", TextView.class);
        if (headerTitle != null && title != null) headerTitle.setText(title);
        if (headerSubtitle != null && subtitle != null) headerSubtitle.setText(subtitle);
    }

    private static CharSequence textValue(NativeMainActivity activity, String fieldName) {
        TextView view = fieldValue(activity, fieldName, TextView.class);
        return view == null ? null : view.getText();
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

    private static <T> T fieldValue(Object target, String name, Class<T> type) {
        if (target == null) return null;
        Field field = findField(target.getClass(), name);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            Object value = field.get(target);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static TextView findRailMore(View view) {
        if (view == null) return null;
        if (view instanceof TextView) {
            CharSequence description = view.getContentDescription();
            if (description != null && "More".contentEquals(description)) return (TextView) view;
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
        final String icon;
        final String title;
        final String subtitle;
        final Runnable run;

        Action(String icon, String title, String subtitle, Runnable run) {
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.run = run;
        }
    }
}
