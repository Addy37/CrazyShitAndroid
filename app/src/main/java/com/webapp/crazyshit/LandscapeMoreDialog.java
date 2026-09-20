package com.webapp.crazyshit;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** One shared More surface: bottom sheet on phones and a side panel on wide screens. */
final class LandscapeMoreDialog {
    private static final int FAVORITES_REQUEST = 3002;
    private static final int NAV_MORE = 5;

    private LandscapeMoreDialog() {
    }

    static void attachSoon(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.getWindow().getDecorView().postDelayed(() -> attach(activity, 0), 120L);
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

    static void show(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean sidePanel = isLandscape(activity);
        final CharSequence oldTitle = textValue(activity, "headerTitle");
        final CharSequence oldSubtitle = textValue(activity, "headerSubtitle");
        setHeader(activity, "More", "Library, account and settings");

        Dialog dialog = sidePanel ? new Dialog(activity) : new BottomSheetDialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog instanceof BottomSheetDialog) {
            ((BottomSheetDialog) dialog).setDismissWithAnimation(true);
        }

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int panelHeight = sidePanel
                ? (int) (screenHeight * 0.90f)
                : Math.min((int) (screenHeight * 0.86f), dp(activity, 680));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), sidePanel ? dp(activity, 14) : dp(activity, 8),
                dp(activity, 16), dp(activity, 16));
        panel.setBackground(panelBackground(activity));
        panel.setClipToOutline(true);

        if (!sidePanel) addDragHandle(activity, panel);
        addHeader(activity, dialog, panel);
        addQuickActions(activity, dialog, panel);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(activity, 3), 0, dp(activity, 4));

        addSection(
                activity,
                dialog,
                content,
                "LIBRARY",
                actions(
                        new Action(
                                R.drawable.ic_more_account,
                                "Favorite creators",
                                "Your starred creators, in one place",
                                () -> activity.startActivity(new Intent(activity, CreatorsActivity.class))
                        ),
                        new Action(
                                R.drawable.ic_action_download,
                                "Downloads",
                                "Saved videos and active downloads",
                                () -> activity.startActivity(
                                        new Intent(activity, DownloadedActivity.class)
                                )
                        )
                )
        );

        addSection(
                activity,
                dialog,
                content,
                "BROWSE",
                actions(
                        new Action(R.drawable.ic_nav_trending, "Trending", "The classic Trending feed",
                                () -> activity.startActivity(NativeFeedBrowserActivity.create(
                                        activity,
                                        "Trending",
                                        CrazyShitRepository.TRENDING,
                                        false
                                ))),
                        new Action(R.drawable.ic_more_memes, "Memes", "The classic Memes feed",
                                () -> activity.startActivity(NativeFeedBrowserActivity.create(
                                        activity,
                                        "Memes",
                                        MemeRepository.MEMES,
                                        true
                                )))
                )
        );

        addSection(
                activity,
                dialog,
                content,
                "DISPLAY",
                actions(
                        new Action(R.drawable.ic_more_view_style, "View style",
                                "Cards, List, Grid or Posters",
                                () -> FeedViewStyleController.showMain(activity))
                )
        );

        addSection(
                activity,
                dialog,
                content,
                "APP",
                actions(
                        new Action(R.drawable.ic_action_feedback, "Send feedback",
                                "Suggest a feature, report a problem or rate the app",
                                () -> activity.startActivity(new Intent(activity, FeedbackActivity.class))),
                        new Action(R.drawable.ic_more_website, "Open full website",
                                "Use the compatibility browser",
                                () -> {
                                    Intent intent = new Intent(activity, WebFallbackActivity.class);
                                    intent.putExtra(WebFallbackActivity.EXTRA_URL, CrazyShitRepository.HOME);
                                    activity.startActivity(intent);
                                }),
                        new Action(R.drawable.ic_more_update, "Check for updates",
                                "Download and install app updates",
                                () -> invokeBoolean(activity, "checkForUpdates", true)),
                        new Action(R.drawable.ic_more_help, "Gesture guide",
                                "Player and ShitTok controls",
                                () -> GestureGuideDialog.show(activity))
                )
        );

        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        dialog.setContentView(panel, new ViewGroup.LayoutParams(-1, panelHeight));
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> restoreHeader(activity, oldTitle, oldSubtitle));
        dialog.show();

        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.dimAmount = 0.56f;
        if (sidePanel) {
            attrs.width = Math.min((int) (screenWidth * 0.70f), dp(activity, 430));
            attrs.height = panelHeight;
            attrs.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        } else {
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.gravity = Gravity.BOTTOM;
        }
        window.setAttributes(attrs);

        if (dialog instanceof BottomSheetDialog) {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        }

        // BottomSheetDialog already owns portrait motion. Add one lightweight entrance only to
        // the custom landscape panel so animations never stack on phones.
        if (sidePanel) ZeroChillMotion.enterFromEnd(panel, dp(activity, 36));
    }

    private static void addDragHandle(NativeMainActivity activity, LinearLayout panel) {
        View handle = new View(activity);
        handle.setBackground(roundedBackground(
                activity,
                ZeroChillUi.color(activity, R.color.zc_text_muted),
                2
        ));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, 0, 0, dp(activity, 8));
        panel.addView(handle, params);
    }

    private static void addHeader(
            NativeMainActivity activity,
            Dialog dialog,
            LinearLayout panel
    ) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 2), 0, 0, dp(activity, 10));

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.ic_launcher_legacy);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(logo, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(activity, 10), 0, dp(activity, 8), 0);
        TextView title = text(activity, "More", 22, Color.WHITE, true);
        TextView subtitle = text(
                activity,
                "ZeroChill " + BuildConfig.VERSION_NAME,
                11,
                ZeroChillUi.color(activity, R.color.zc_text_secondary),
                false
        );
        labels.addView(title);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView close = iconView(activity, R.drawable.ic_more_close, 40, 10);
        close.setBackground(circleBackground(ZeroChillUi.color(activity, R.color.zc_cyan_container)));
        close.setContentDescription("Close More");
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void addQuickActions(
            NativeMainActivity activity,
            Dialog dialog,
            LinearLayout panel
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        addQuickTile(activity, dialog, row, new Action(
                R.drawable.ic_more_library,
                "Library",
                "Saved viewing, creators and downloads",
                () -> activity.startActivityForResult(
                        new Intent(activity, LibraryHubActivity.class),
                        FAVORITES_REQUEST
                )
        ), 0);
        addQuickTile(activity, dialog, row, new Action(
                R.drawable.ic_more_account,
                "Account",
                "Profile and sign in",
                () -> activity.startActivity(new Intent(activity, ProfileActivity.class))
        ), 1);
        addQuickTile(activity, dialog, row, new Action(
                R.drawable.ic_more_settings,
                "Settings",
                "Playback and app options",
                () -> activity.startActivity(new Intent(activity, SettingsActivity.class))
        ), 2);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 88));
        params.setMargins(0, 0, 0, dp(activity, 4));
        panel.addView(row, params);
    }

    private static void addQuickTile(
            NativeMainActivity activity,
            Dialog dialog,
            LinearLayout row,
            Action action,
            int index
    ) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(activity, 6), dp(activity, 8), dp(activity, 6), dp(activity, 7));
        tile.setBackground(ZeroChillUi.glass(activity));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(action.title + ". " + action.subtitle);
        applySelectableForeground(activity, tile);

        ImageView icon = iconView(activity, action.iconRes, 34, 6);
        icon.setBackground(circleBackground(UiPalette.PRIMARY_CONTAINER));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34)));

        TextView title = text(activity, action.title, 12, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(activity, 5), 0, 0);
        tile.addView(title, new LinearLayout.LayoutParams(-1, -2));
        installPressFeedback(tile);
        tile.setOnClickListener(v -> runAction(v, dialog, action));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
        if (index == 0) params.setMargins(0, 0, dp(activity, 4), 0);
        else if (index == 1) params.setMargins(dp(activity, 2), 0, dp(activity, 2), 0);
        else params.setMargins(dp(activity, 4), 0, 0, 0);
        row.addView(tile, params);
    }

    private static void addSection(
            NativeMainActivity activity,
            Dialog dialog,
            LinearLayout parent,
            String label,
            List<Action> actions
    ) {
        TextView section = text(
                activity,
                label,
                10,
                ZeroChillUi.color(activity, R.color.zc_text_muted),
                true
        );
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(activity, 6), dp(activity, 9), dp(activity, 6), dp(activity, 5));
        parent.addView(section, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), dp(activity, 2));
        group.setBackground(groupBackground(activity));

        for (int i = 0; i < actions.size(); i++) {
            addActionRow(activity, dialog, group, actions.get(i));
            if (i < actions.size() - 1) {
                View divider = new View(activity);
                divider.setBackgroundColor(ZeroChillUi.color(activity, R.color.zc_divider));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(activity, 1));
                dividerParams.setMargins(dp(activity, 56), 0, dp(activity, 8), 0);
                group.addView(divider, dividerParams);
            }
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(activity, 3));
        parent.addView(group, params);
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
        row.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 7), dp(activity, 7));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(action.title + ". " + action.subtitle);
        applySelectableForeground(activity, row);

        ImageView icon = iconView(activity, action.iconRes, 40, 9);
        icon.setBackground(circleBackground(UiPalette.PRIMARY_CONTAINER));
        row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(activity, 11), 0, dp(activity, 7), 0);
        TextView title = text(activity, action.title, 15, Color.WHITE, true);
        title.setMaxLines(1);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = text(
                activity,
                action.subtitle,
                11,
                ZeroChillUi.color(activity, R.color.zc_text_secondary),
                false
        );
        subtitle.setMaxLines(1);
        subtitle.setPadding(0, dp(activity, 1), 0, 0);
        labels.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView chevron = iconView(activity, R.drawable.ic_more_chevron, 28, 6);
        chevron.setImageTintList(ColorStateList.valueOf(Color.rgb(116, 116, 128)));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 40)));

        installPressFeedback(row);
        row.setOnClickListener(v -> runAction(v, dialog, action));
        group.addView(row, new LinearLayout.LayoutParams(-1, dp(activity, 60)));
    }

    private static void runAction(View view, Dialog dialog, Action action) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        dialog.dismiss();
        action.run.run();
    }

    private static void installPressFeedback(View view) {
        ZeroChillMotion.installPressFeedback(view);
    }

    private static ImageView iconView(
            NativeMainActivity activity,
            int iconRes,
            int sizeDp,
            int paddingDp
    ) {
        ImageView view = new ImageView(activity);
        view.setImageResource(iconRes);
        view.setImageTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setPadding(dp(activity, paddingDp), dp(activity, paddingDp),
                dp(activity, paddingDp), dp(activity, paddingDp));
        view.setMinimumWidth(dp(activity, sizeDp));
        view.setMinimumHeight(dp(activity, sizeDp));
        return view;
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

    private static Drawable panelBackground(NativeMainActivity activity) {
        return ZeroChillUi.panelGlass(activity);
    }

    private static GradientDrawable groupBackground(NativeMainActivity activity) {
        GradientDrawable background = roundedBackground(
                activity,
                ZeroChillUi.color(activity, R.color.zc_surface_glass),
                16
        );
        background.setStroke(
                ZeroChillUi.dimension(activity, R.dimen.zc_stroke),
                ZeroChillUi.color(activity, R.color.zc_divider)
        );
        return background;
    }

    private static GradientDrawable roundedBackground(
            NativeMainActivity activity,
            int color,
            int radiusDp
    ) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(activity, radiusDp));
        background.setColor(color);
        return background;
    }

    private static GradientDrawable circleBackground(int color) {
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
        FeedViewStyleController.attachMain(activity);
    }

    private static CharSequence textValue(NativeMainActivity activity, String fieldName) {
        TextView view = fieldValue(activity, fieldName, TextView.class);
        return view == null ? null : view.getText();
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
        final int iconRes;
        final String title;
        final String subtitle;
        final Runnable run;

        Action(int iconRes, String title, String subtitle, Runnable run) {
            this.iconRes = iconRes;
            this.title = title;
            this.subtitle = subtitle;
            this.run = run;
        }
    }
}
