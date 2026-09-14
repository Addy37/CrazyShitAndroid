package com.addy37.crazyshitadmin;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Aggregate-only product analytics. No per-install activity history is exposed here. */
public final class AnalyticsActivity extends AppCompatActivity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private TextView status;
    private ProgressBar progress;
    private MaterialButton refresh;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        load();
    }

    private void buildUi() {
        LinearLayout root = vertical(16);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Analytics", 28, Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton close = button("Back");
        close.setOnClickListener(v -> finish());
        header.addView(close);
        root.addView(header);

        TextView detail = text(
                "Aggregate trends only. No search terms, media URLs, or individual viewing histories are stored.",
                13, color(R.color.app_on_surface_variant));
        detail.setPadding(0, dp(4), 0, dp(8));
        root.addView(detail);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        status = text("Loading analytics…", 13, color(R.color.app_on_surface_variant));
        statusRow.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        progress = new ProgressBar(this);
        statusRow.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));
        refresh = button("Refresh");
        refresh.setVisibility(android.view.View.GONE);
        refresh.setOnClickListener(v -> load());
        statusRow.addView(refresh);
        root.addView(statusRow);

        content = vertical(0);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void load() {
        String token = SecureTokenStore.read(this);
        if (token.isEmpty()) {
            toast("Admin token required.");
            finish();
            return;
        }
        progress.setVisibility(android.view.View.VISIBLE);
        refresh.setVisibility(android.view.View.GONE);
        status.setText("Loading analytics…");
        network.execute(() -> {
            try {
                AdminRepository.AnalyticsDashboard dashboard = AdminRepository.analytics(token);
                runOnUiThread(() -> render(dashboard));
            } catch (SecurityException error) {
                runOnUiThread(() -> {
                    SecureTokenStore.clear(this);
                    toast(error.getMessage());
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    progress.setVisibility(android.view.View.GONE);
                    refresh.setVisibility(android.view.View.VISIBLE);
                    status.setText("Could not load analytics: " + message(error));
                });
            }
        });
    }

    private void render(AdminRepository.AnalyticsDashboard dashboard) {
        progress.setVisibility(android.view.View.GONE);
        refresh.setVisibility(android.view.View.VISIBLE);
        status.setText(dashboard.generatedAt.isEmpty()
                ? "Updated now"
                : "Updated from live Supabase data");
        content.removeAllViews();

        LinearLayout users = new LinearLayout(this);
        users.setOrientation(LinearLayout.HORIZONTAL);
        users.addView(metric("Today", dashboard.dailyUsers), weighted());
        users.addView(metric("7-day period", dashboard.weeklyUsers), weighted());
        users.addView(metric("This month", dashboard.monthlyUsers), weighted());
        content.addView(card("ACTIVE USERS", users));

        addRankingCard("TRENDING CREATORS", dashboard.creators, true,
                "Creator interest will appear after users open Fapzone creator galleries.");
        addRankingCard("MOST USED SECTIONS", dashboard.sections, false,
                "Section usage will appear after analytics-enabled app sessions begin.");
        addRankingCard("SOURCE INTEREST", dashboard.sources, false,
                "Source usage will appear after users open source-specific content.");
        addRankingCard("APP VERSIONS", dashboard.versions, false,
                "Version adoption will appear after analytics-enabled users open the app.");

        if (dashboard.dailyUsers == 0 && dashboard.weeklyUsers == 0 && dashboard.monthlyUsers == 0) {
            TextView waiting = text(
                    "No analytics-enabled users have reported yet. Data starts filling in after the analytics release is installed and used.",
                    14, color(R.color.app_on_surface_variant));
            waiting.setPadding(dp(4), dp(8), dp(4), dp(20));
            content.addView(waiting);
        }
    }

    private void addRankingCard(
            String title,
            List<AdminRepository.AnalyticsRow> rows,
            boolean creator,
            String emptyText
    ) {
        LinearLayout body = vertical(0);
        if (rows.isEmpty()) {
            TextView empty = text(emptyText, 13, color(R.color.app_on_surface_variant));
            body.addView(empty);
        } else {
            int limit = Math.min(rows.size(), creator ? 15 : 10);
            for (int index = 0; index < limit; index++) {
                AdminRepository.AnalyticsRow row = rows.get(index);
                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(0, dp(7), 0, dp(7));

                String label = creator
                        ? (index + 1) + ". " + row.value
                        : friendly(row.value);
                TextView name = text(label, 15, color(R.color.app_on_surface));
                if (index < 3) name.setTypeface(null, Typeface.BOLD);
                line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                String numbers;
                if (creator) {
                    numbers = row.uniqueUsers + " users/week" +
                            (row.usersToday > 0 ? "\n" + row.usersToday + " today" : "") +
                            "\n" + row.eventCount + " opens";
                } else {
                    numbers = row.uniqueUsers + " users\n" + row.eventCount + " opens";
                }
                TextView count = text(numbers, 12, color(R.color.app_on_surface_variant));
                count.setGravity(Gravity.END);
                line.addView(count);
                body.addView(line);
            }
        }
        content.addView(card(title, body));
    }

    private LinearLayout metric(String label, long value) {
        LinearLayout block = vertical(4);
        block.setGravity(Gravity.CENTER);
        TextView number = text(String.format(Locale.US, "%,d", value), 26, Color.WHITE);
        number.setTypeface(null, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        TextView caption = text(label, 11, color(R.color.app_on_surface_variant));
        caption.setGravity(Gravity.CENTER);
        block.addView(number);
        block.addView(caption);
        return block;
    }

    private MaterialCardView card(String title, LinearLayout body) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(R.color.app_surface));
        card.setStrokeColor(color(R.color.app_surface_variant));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        LinearLayout wrapper = vertical(14);
        TextView heading = text(title, 12, color(R.color.app_on_surface_variant));
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, 0, 0, dp(6));
        wrapper.addView(heading);
        wrapper.addView(body);
        card.addView(wrapper);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout vertical(int paddingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        layout.setBackgroundColor(color(R.color.app_background));
        return layout;
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextColor(color(R.color.app_on_primary));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        return result;
    }

    private String friendly(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        switch (raw.toLowerCase(Locale.US)) {
            case "home": return "Home";
            case "collections": return "Collections";
            case "chaos": return "Chaos";
            case "categories": return "Categories";
            case "search": return "Search";
            case "favorites": return "Favorites";
            case "downloads": return "Downloads";
            case "settings": return "Settings";
            case "profile": return "Profile";
            case "creator_gallery": return "Creator galleries";
            case "crazyshit": return "CrazyShit";
            case "efukt": return "EFukt";
            case "fapzone": return "Fapzone";
            case "fapello": return "Fapello";
            case "bunkr": return "Bunkr";
            case "wikifeet": return "WikiFeet";
            case "wikifeetx": return "WikiFeet X";
            default: return raw;
        }
    }

    private String message(Exception error) {
        return error.getMessage() == null ? "Request failed" : error.getMessage();
    }

    private int color(int id) { return getColor(id); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }
}
