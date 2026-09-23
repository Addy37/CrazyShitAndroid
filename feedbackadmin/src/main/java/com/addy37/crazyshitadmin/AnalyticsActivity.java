package com.addy37.crazyshitadmin;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        load();
    }

    private void buildUi() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(color(R.color.app_background));

        LinearLayout page = vertical(18);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = vertical(0);
        TextView eyebrow = label("AUDIENCE INSIGHTS");
        TextView title = text("Analytics", 30, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView detail = text("See what people use without tracking individual viewing histories.",
                14, color(R.color.app_on_surface_variant));
        titles.addView(eyebrow);
        titles.addView(title);
        titles.addView(detail);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        refresh = compactButton("Refresh");
        refresh.setOnClickListener(v -> load());
        header.addView(refresh);
        page.addView(header);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(12), 0, dp(4));
        status = text("Loading live analytics…", 13, color(R.color.app_on_surface_variant));
        statusRow.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        progress = new ProgressBar(this);
        statusRow.addView(progress, new LinearLayout.LayoutParams(dp(30), dp(30)));
        page.addView(statusRow);

        content = vertical(0);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        shell.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomNav());
        setContentView(shell);
    }

    private void load() {
        String token = SecureTokenStore.read(this);
        if (token.isEmpty()) {
            toast("Admin token required.");
            finish();
            return;
        }
        progress.setVisibility(View.VISIBLE);
        refresh.setEnabled(false);
        status.setText("Loading live analytics…");
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
                    progress.setVisibility(View.GONE);
                    refresh.setEnabled(true);
                    status.setText("Could not load analytics: " + message(error));
                });
            }
        });
    }

    private void render(AdminRepository.AnalyticsDashboard dashboard) {
        progress.setVisibility(View.GONE);
        refresh.setEnabled(true);
        status.setText("Live Supabase data updated now");
        content.removeAllViews();

        LinearLayout users = new LinearLayout(this);
        users.setOrientation(LinearLayout.HORIZONTAL);
        users.addView(metric("Today", dashboard.dailyUsers), weighted());
        users.addView(metric("Week", dashboard.weeklyUsers), weighted());
        users.addView(metric("Month", dashboard.monthlyUsers), weighted());
        content.addView(card("ACTIVE USERS", "Anonymous active installs", users));

        addRankingCard("TRENDING CREATORS", "Who users are opening most", dashboard.creators, true,
                "Creator interest will appear after users open OnlyFap creator galleries.");
        addRankingCard("MOST USED SECTIONS", "Where users spend their time", dashboard.sections, false,
                "Section usage will appear after analytics-enabled app sessions begin.");
        addRankingCard("SOURCE INTEREST", "Which content sources attract attention", dashboard.sources, false,
                "Source usage will appear after users open source-specific content.");
        addRankingCard("APP VERSIONS", "How quickly users adopt releases", dashboard.versions, false,
                "Version adoption will appear after analytics-enabled users open the app.");
        addDeviceCard("TOP DEVICES", "Phone models active this week", dashboard.deviceModels,
                "Device models will appear as users open the updated ZEROCHILL app.");
        addDeviceCard("DEVICE BRANDS", "Active users by manufacturer", dashboard.deviceManufacturers,
                "Device brands will appear as users open the updated ZEROCHILL app.");
        addDeviceCard("ANDROID VERSIONS", "Android versions active this week", dashboard.androidVersions,
                "Android versions will appear as users open the updated ZEROCHILL app.");

        if (dashboard.dailyUsers == 0 && dashboard.weeklyUsers == 0 && dashboard.monthlyUsers == 0) {
            TextView waiting = text(
                    "No analytics-enabled users have reported yet. Data begins filling in as users open the latest app.",
                    14, color(R.color.app_on_surface_variant));
            waiting.setPadding(dp(4), dp(12), dp(4), dp(20));
            content.addView(waiting);
        }
    }

    private void addRankingCard(
            String title,
            String subtitle,
            List<AdminRepository.AnalyticsRow> rows,
            boolean creator,
            String emptyText
    ) {
        LinearLayout body = vertical(0);
        if (rows.isEmpty()) {
            body.addView(text(emptyText, 13, color(R.color.app_on_surface_variant)));
        } else {
            int limit = Math.min(rows.size(), creator ? 12 : 8);
            long max = Math.max(1L, rows.get(0).uniqueUsers);
            for (int index = 0; index < limit; index++) {
                AdminRepository.AnalyticsRow row = rows.get(index);
                LinearLayout block = vertical(0);
                block.setPadding(0, dp(7), 0, dp(7));

                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                String nameValue = creator ? (index + 1) + ". " + row.value : friendly(row.value);
                TextView name = text(nameValue, 15, color(R.color.app_on_surface));
                if (index < 3) name.setTypeface(null, Typeface.BOLD);
                line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                String numbers = creator
                        ? String.format(Locale.US, "%,d users · %,d opens", row.uniqueUsers, row.eventCount)
                        : String.format(Locale.US, "%,d users · %,d opens", row.uniqueUsers, row.eventCount);
                TextView counts = text(numbers, 12, color(R.color.app_on_surface_variant));
                counts.setGravity(Gravity.END);
                line.addView(counts);
                block.addView(line);

                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(1000);
                bar.setProgress((int) Math.min(1000L, (row.uniqueUsers * 1000L) / max));
                bar.setProgressTintList(ColorStateList.valueOf(color(R.color.app_primary)));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.app_surface_variant)));
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
                barParams.setMargins(0, dp(5), 0, 0);
                block.addView(bar, barParams);
                body.addView(block);
            }
        }
        content.addView(card(title, subtitle, body));
    }

    private void addDeviceCard(String title, String subtitle,
            List<AdminRepository.AnalyticsRow> rows, String emptyText) {
        LinearLayout body = vertical(0);
        if (rows.isEmpty()) {
            body.addView(text(emptyText, 13, color(R.color.app_on_surface_variant)));
        } else {
            long max = Math.max(1L, rows.get(0).uniqueUsers);
            for (AdminRepository.AnalyticsRow row : rows) {
                LinearLayout block = vertical(0);
                block.setPadding(0, dp(7), 0, dp(7));
                TextView name = text(row.value, 15, color(R.color.app_on_surface));
                name.setTypeface(null, Typeface.BOLD);
                block.addView(name);
                block.addView(text(String.format(Locale.US,
                        "%,d today · %,d this week · %,d events",
                        row.usersToday, row.uniqueUsers, row.eventCount),
                        12, color(R.color.app_on_surface_variant)));
                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(1000);
                bar.setProgress((int) Math.min(1000L, (row.uniqueUsers * 1000L) / max));
                bar.setProgressTintList(ColorStateList.valueOf(color(R.color.app_primary)));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.app_surface_variant)));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
                params.setMargins(0, dp(5), 0, 0);
                block.addView(bar, params);
                body.addView(block);
            }
        }
        content.addView(card(title, subtitle, body));
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

    private MaterialCardView card(String title, String subtitle, LinearLayout body) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(R.color.app_surface));
        card.setStrokeColor(color(R.color.app_surface_variant));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(20));
        card.setCardElevation(0);

        LinearLayout wrapper = vertical(16);
        TextView heading = label(title);
        wrapper.addView(heading);
        TextView detail = text(subtitle, 13, color(R.color.app_on_surface_variant));
        detail.setPadding(0, dp(2), 0, dp(12));
        wrapper.addView(detail);
        wrapper.addView(body);
        card.addView(wrapper);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(6), dp(7), dp(6), dp(7));
        nav.setBackgroundColor(color(R.color.app_surface));
        nav.addView(navButton("Dashboard", "dashboard", false), weighted());
        nav.addView(navButton("Analytics", "analytics", true), weighted());
        nav.addView(navButton("Sources", "sources", false), weighted());
        nav.addView(navButton("Feedback", "feedback", false), weighted());
        return nav;
    }

    private MaterialButton navButton(String title, String destination, boolean active) {
        MaterialButton button = new MaterialButton(this);
        button.setText(title);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setMinWidth(0);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setMinHeight(dp(44));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(13));
        button.setBackgroundTintList(ColorStateList.valueOf(active
                ? color(R.color.app_primary) : color(R.color.app_surface_variant)));
        button.setTextColor(active ? color(R.color.app_on_primary) : color(R.color.app_on_surface_variant));
        if (!active) button.setOnClickListener(v -> navigate(destination));
        return button;
    }

    private void navigate(String destination) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_DESTINATION, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout vertical(int paddingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return layout;
    }

    private TextView label(String value) {
        TextView result = text(value, 11, color(R.color.app_primary));
        result.setTypeface(null, Typeface.BOLD);
        result.setLetterSpacing(0.08f);
        return result;
    }

    private MaterialButton compactButton(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextColor(color(R.color.app_on_primary));
        button.setAllCaps(false);
        button.setCornerRadius(dp(14));
        button.setMinHeight(dp(42));
        button.setInsetTop(0);
        button.setInsetBottom(0);
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
            case "chaos": return "ShitTok";
            case "categories": return "Categories";
            case "search": return "Search";
            case "favorites": return "Favorites";
            case "downloads": return "Downloads";
            case "settings": return "Settings";
            case "profile": return "Profile";
            case "creator_gallery": return "Creator galleries";
            case "crazyshit": return "CrazyShit source";
            case "efukt": return "EFukt";
            case "fapzone": return "OnlyFap";
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

    @Override protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }
}
