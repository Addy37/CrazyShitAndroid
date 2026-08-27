package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** More-style action surface shared by Chaos, feed cards and regular video players. */
final class VideoActionSheet {
    private VideoActionSheet() {
    }

    static Action action(int iconRes, String title, String subtitle, Runnable run) {
        return new Action(iconRes, title, subtitle, run);
    }

    static Section section(String label, Action... actions) {
        return new Section(label, new ArrayList<>(Arrays.asList(actions)));
    }

    static void show(Activity activity, String videoTitle, Section... sections) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        boolean sidePanel = activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        Dialog dialog = sidePanel ? new Dialog(activity) : new BottomSheetDialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog instanceof BottomSheetDialog) {
            ((BottomSheetDialog) dialog).setDismissWithAnimation(true);
        }

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int panelHeight = sidePanel
                ? (int) (screenHeight * 0.90f)
                : Math.min((int) (screenHeight * 0.84f), dp(activity, 700));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), sidePanel ? dp(activity, 14) : dp(activity, 8),
                dp(activity, 16), dp(activity, 16));
        panel.setBackground(panelBackground(activity));
        panel.setClipToOutline(true);

        if (!sidePanel) addDragHandle(activity, panel);
        addHeader(activity, dialog, panel, videoTitle);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(activity, 4));
        for (Section section : sections) addSection(activity, dialog, content, section);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        dialog.setContentView(panel, new ViewGroup.LayoutParams(-1, panelHeight));
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.dimAmount = 0.58f;
        if (sidePanel) {
            attrs.width = Math.min((int) (screenWidth * 0.70f), dp(activity, 440));
            attrs.height = panelHeight;
            attrs.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        } else {
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.gravity = Gravity.BOTTOM;
        }
        window.setAttributes(attrs);

        if (dialog instanceof BottomSheetDialog) {
            BottomSheetDialog sheet = (BottomSheetDialog) dialog;
            sheet.getBehavior().setSkipCollapsed(true);
            sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            View bottom = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottom != null) bottom.setBackgroundColor(Color.TRANSPARENT);
        } else {
            panel.setAlpha(0f);
            panel.setTranslationX(dp(activity, 22));
            panel.animate().alpha(1f).translationX(0f).setDuration(160L).start();
        }
    }

    private static void addDragHandle(Activity activity, LinearLayout panel) {
        View handle = new View(activity);
        handle.setBackground(rounded(Color.rgb(91, 91, 101), dp(activity, 2)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, 0, 0, dp(activity, 8));
        panel.addView(handle, params);
    }

    private static void addHeader(
            Activity activity,
            Dialog dialog,
            LinearLayout panel,
            String videoTitle
    ) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 2), 0, 0, dp(activity, 10));

        ImageView logo = icon(activity, R.drawable.ic_player_more, 42, 10);
        logo.setBackground(circle(UiPalette.PRIMARY_CONTAINER));
        header.addView(logo, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(activity, 10), 0, dp(activity, 8), 0);
        labels.addView(text(activity, "Video actions", 21, Color.WHITE, true));
        TextView subtitle = text(activity,
                TextUtils.isEmpty(videoTitle) ? "Current video" : videoTitle,
                11,
                Color.rgb(166, 166, 176),
                false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView close = icon(activity, R.drawable.ic_more_close, 40, 10);
        close.setBackground(circle(Color.rgb(34, 34, 39)));
        close.setContentDescription("Close video actions");
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void addSection(
            Activity activity,
            Dialog dialog,
            LinearLayout parent,
            Section section
    ) {
        if (section == null || section.actions.isEmpty()) return;
        TextView label = text(activity, section.label, 10, Color.rgb(145, 145, 155), true);
        label.setLetterSpacing(0.08f);
        label.setPadding(dp(activity, 6), dp(activity, 8), dp(activity, 6), dp(activity, 5));
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), dp(activity, 2));
        GradientDrawable background = rounded(Color.rgb(24, 24, 28), dp(activity, 16));
        background.setStroke(dp(activity, 1), Color.rgb(43, 43, 50));
        group.setBackground(background);

        for (int i = 0; i < section.actions.size(); i++) {
            addRow(activity, dialog, group, section.actions.get(i));
            if (i < section.actions.size() - 1) {
                View divider = new View(activity);
                divider.setBackgroundColor(Color.rgb(43, 43, 49));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 1));
                params.setMargins(dp(activity, 56), 0, dp(activity, 8), 0);
                group.addView(divider, params);
            }
        }
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(-1, -2);
        groupParams.setMargins(0, 0, 0, dp(activity, 3));
        parent.addView(group, groupParams);
    }

    private static void addRow(
            Activity activity,
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
        selectableForeground(activity, row);

        ImageView icon = icon(activity, action.iconRes, 40, 9);
        icon.setBackground(circle(UiPalette.PRIMARY_CONTAINER));
        row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(activity, 11), 0, dp(activity, 7), 0);
        TextView title = text(activity, action.title, 15, Color.WHITE, true);
        title.setMaxLines(1);
        labels.addView(title);
        TextView subtitle = text(activity, action.subtitle, 11, Color.rgb(166, 166, 176), false);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, dp(activity, 1), 0, 0);
        labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView chevron = icon(activity, R.drawable.ic_more_chevron, 28, 6);
        chevron.setImageTintList(ColorStateList.valueOf(Color.rgb(116, 116, 128)));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 40)));
        row.setOnTouchListener((v, event) -> {
            int actionType = event.getActionMasked();
            if (actionType == MotionEvent.ACTION_DOWN) v.setAlpha(0.78f);
            else if (actionType == MotionEvent.ACTION_UP || actionType == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
            }
            return false;
        });
        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dialog.dismiss();
            action.run.run();
        });
        group.addView(row, new LinearLayout.LayoutParams(-1, dp(activity, 60)));
    }

    private static ImageView icon(Activity activity, int res, int sizeDp, int paddingDp) {
        ImageView view = new ImageView(activity);
        view.setImageResource(res);
        view.setImageTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setPadding(dp(activity, paddingDp), dp(activity, paddingDp),
                dp(activity, paddingDp), dp(activity, paddingDp));
        view.setMinimumWidth(dp(activity, sizeDp));
        view.setMinimumHeight(dp(activity, sizeDp));
        return view;
    }

    private static TextView text(
            Activity activity,
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

    private static void selectableForeground(Activity activity, View view) {
        TypedValue value = new TypedValue();
        if (!activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) return;
        try {
            view.setForeground(activity.getDrawable(value.resourceId));
        } catch (Exception ignored) {
        }
    }

    private static GradientDrawable panelBackground(Activity activity) {
        GradientDrawable background = rounded(Color.rgb(15, 15, 18), dp(activity, 26));
        background.setStroke(dp(activity, 1), Color.rgb(47, 47, 54));
        return background;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static final class Action {
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

    static final class Section {
        final String label;
        final List<Action> actions;

        Section(String label, List<Action> actions) {
            this.label = label;
            this.actions = actions;
        }
    }
}
