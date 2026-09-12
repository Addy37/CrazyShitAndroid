package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;

public final class FeedbackActivity extends Activity {
    private static final int MUTED = Color.rgb(172, 172, 181);
    private static final int SURFACE = Color.rgb(24, 24, 28);

    private LinearLayout content;
    private MaterialButton sendTab;
    private MaterialButton historyTab;
    private EditText message;
    private MaterialButton submit;
    private String selectedType = "feature_request";
    private int selectedRating;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showComposer();
    }

    private void buildShell() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back = button("‹");
        back.setTextSize(28);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView title = text("Feedback", 28, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMargins(dp(12), 0, 0, 0);
        header.addView(title, titleParams);
        root.addView(header);

        TextView intro = text("Tell me what works, what is broken, or what you want added.", 14, MUTED, false);
        intro.setPadding(dp(8), dp(8), dp(8), dp(18));
        root.addView(intro);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        sendTab = button("Send feedback");
        historyTab = button("My feedback");
        sendTab.setOnClickListener(v -> showComposer());
        historyTab.setOnClickListener(v -> showHistory());
        tabs.addView(sendTab, weighted());
        tabs.addView(historyTab, weighted());
        root.addView(tabs);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, 0);
        root.addView(content);
        setContentView(scroll);
    }

    private void showComposer() {
        if (content == null) buildShell();
        content.removeAllViews();
        selectTab(sendTab, historyTab);

        label("WHAT WOULD YOU LIKE TO DO?");
        LinearLayout types = new LinearLayout(this);
        types.setOrientation(LinearLayout.VERTICAL);
        MaterialButton feature = choice("Suggest a feature", "feature_request");
        MaterialButton bug = choice("Report a problem", "bug_report");
        MaterialButton general = choice("General feedback", "general_feedback");
        types.addView(feature);
        types.addView(bug);
        types.addView(general);
        content.addView(types);
        updateTypeButtons(types);

        label("YOUR MESSAGE");
        message = new EditText(this);
        message.setHint("What should I know?");
        message.setHintTextColor(Color.rgb(115, 115, 124));
        message.setTextColor(Color.WHITE);
        message.setTextSize(16);
        message.setGravity(Gravity.TOP | Gravity.START);
        message.setMinHeight(dp(150));
        message.setPadding(dp(16), dp(14), dp(16), dp(14));
        message.setBackground(rounded(SURFACE, 16));
        message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        content.addView(message, marginParams(-1, -2, 0, 0, 0, dp(8)));

        TextView privacy = text(
                "No account or name is required. The app includes its version, your phone model, and Android version so bug reports are easier to check.",
                12, MUTED, false);
        privacy.setPadding(dp(6), dp(4), dp(6), dp(8));
        content.addView(privacy);

        label("OPTIONAL RATING");
        LinearLayout stars = new LinearLayout(this);
        stars.setGravity(Gravity.CENTER);
        for (int i = 1; i <= 5; i++) {
            final int rating = i;
            MaterialButton star = button("☆");
            star.setTag(rating);
            star.setTextSize(28);
            star.setOnClickListener(v -> {
                selectedRating = rating;
                updateStars(stars);
            });
            stars.addView(star, weighted());
        }
        content.addView(stars);
        updateStars(stars);

        submit = button("Send feedback");
        submit.setTextColor(UiPalette.ON_PRIMARY);
        submit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(UiPalette.PRIMARY));
        submit.setOnClickListener(v -> submit());
        content.addView(submit, marginParams(-1, dp(52), 0, dp(16), 0, 0));

        if (!FeedbackRepository.isConfigured()) {
            TextView setup = text("Feedback is not connected yet.", 13, Color.rgb(255, 160, 80), true);
            setup.setGravity(Gravity.CENTER);
            content.addView(setup);
            submit.setEnabled(false);
        }
    }

    private MaterialButton choice(String title, String type) {
        MaterialButton choice = button(title);
        choice.setTag(type);
        choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        choice.setOnClickListener(v -> {
            selectedType = type;
            updateTypeButtons((LinearLayout) v.getParent());
        });
        return choice;
    }

    private void updateTypeButtons(LinearLayout parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            MaterialButton value = (MaterialButton) parent.getChildAt(i);
            boolean selected = selectedType.equals(value.getTag());
            value.setTextColor(selected ? UiPalette.PRIMARY : Color.WHITE);
            value.setStrokeWidth(selected ? dp(1) : 0);
            value.setStrokeColor(android.content.res.ColorStateList.valueOf(UiPalette.PRIMARY));
        }
    }

    private void updateStars(LinearLayout stars) {
        for (int i = 0; i < stars.getChildCount(); i++) {
            MaterialButton star = (MaterialButton) stars.getChildAt(i);
            int value = (int) star.getTag();
            star.setText(value <= selectedRating ? "★" : "☆");
            star.setTextColor(value <= selectedRating ? UiPalette.PRIMARY : MUTED);
        }
    }

    private void submit() {
        String copy = message.getText().toString().trim();
        if (copy.length() < 5) {
            message.setError("Please add a little more detail.");
            return;
        }
        submit.setEnabled(false);
        submit.setText("Sending...");
        FeedbackRepository.submit(this, selectedType, copy, selectedRating, "More", (id, error) ->
                runOnUiThread(() -> {
                    submit.setEnabled(true);
                    submit.setText("Send feedback");
                    if (error != null) {
                        Toast.makeText(this, "Couldn't send feedback. Try again.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    message.setText("");
                    selectedRating = 0;
                    Toast.makeText(this, "Feedback sent. Thank you.", Toast.LENGTH_LONG).show();
                    showHistory();
                }));
    }

    private void showHistory() {
        if (content == null) buildShell();
        content.removeAllViews();
        selectTab(historyTab, sendTab);
        TextView loading = text("Loading your feedback...", 14, MUTED, false);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(36), 0, 0);
        content.addView(loading);

        if (!FeedbackRepository.isConfigured()) {
            loading.setText("Feedback is not connected yet.");
            return;
        }
        FeedbackRepository.list(this, (items, error) -> runOnUiThread(() -> {
            content.removeAllViews();
            if (error != null) {
                TextView failed = text("Couldn't load your feedback. Tap to retry.", 14, MUTED, false);
                failed.setGravity(Gravity.CENTER);
                failed.setPadding(0, dp(36), 0, dp(36));
                failed.setOnClickListener(v -> showHistory());
                content.addView(failed);
                return;
            }
            if (items == null || items.isEmpty()) {
                TextView empty = text("You have not sent any feedback from this device.", 14, MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(12), dp(36), dp(12), 0);
                content.addView(empty);
                return;
            }
            for (FeedbackRepository.FeedbackItem item : items) addFeedbackCard(item);
        }));
    }

    private void addFeedbackCard(FeedbackRepository.FeedbackItem item) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(SURFACE);
        card.setRadius(dp(16));
        card.setStrokeColor(Color.rgb(45, 45, 51));
        card.setStrokeWidth(dp(1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.addView(box);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView type = text(typeLabel(item.type), 12, UiPalette.PRIMARY, true);
        top.addView(type, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView status = text(statusLabel(item.status), 12, statusColor(item.status), true);
        top.addView(status);
        box.addView(top);

        TextView copy = text(item.message, 15, Color.WHITE, false);
        copy.setPadding(0, dp(10), 0, dp(4));
        box.addView(copy);

        if (item.rating > 0) {
            TextView rating = text("★★★★★".substring(0, item.rating), 15, UiPalette.PRIMARY, false);
            box.addView(rating);
        }
        if (!item.developerReply.isEmpty()) {
            TextView reply = text("Developer reply\n" + item.developerReply, 13, Color.rgb(215, 215, 221), false);
            reply.setPadding(dp(12), dp(10), dp(12), dp(10));
            reply.setBackground(rounded(Color.rgb(35, 35, 40), 10));
            box.addView(reply, marginParams(-1, -2, 0, dp(10), 0, 0));
        }
        content.addView(card, marginParams(-1, -2, 0, dp(6), 0, dp(6)));
    }

    private void selectTab(MaterialButton selected, MaterialButton other) {
        selected.setTextColor(UiPalette.PRIMARY);
        selected.setStrokeWidth(dp(1));
        selected.setStrokeColor(android.content.res.ColorStateList.valueOf(UiPalette.PRIMARY));
        other.setTextColor(MUTED);
        other.setStrokeWidth(0);
    }

    private void label(String value) {
        TextView label = text(value, 12, MUTED, true);
        label.setPadding(dp(6), dp(16), dp(6), dp(8));
        content.addView(label);
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE));
        button.setCornerRadius(dp(14));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private String typeLabel(String type) {
        if ("feature_request".equals(type)) return "FEATURE REQUEST";
        if ("bug_report".equals(type)) return "BUG REPORT";
        return "GENERAL FEEDBACK";
    }

    private String statusLabel(String status) {
        return status.replace('_', ' ').toUpperCase(Locale.US);
    }

    private int statusColor(String status) {
        if ("completed".equals(status)) return Color.rgb(80, 210, 130);
        if ("planned".equals(status) || "in_progress".equals(status)) return UiPalette.PRIMARY;
        return MUTED;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
