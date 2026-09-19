package com.addy37.crazyshitadmin;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    static final String EXTRA_DESTINATION = "destination";
    private static final String DASHBOARD = "dashboard";
    private static final String FEEDBACK = "feedback";
    private static final String SOURCES = "sources";

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final List<AdminRepository.Item> allItems = new ArrayList<>();
    private FeedbackAdapter adapter;
    private SwipeRefreshLayout swipe;
    private TextView count;
    private String filter = "all";
    private String currentDestination = DASHBOARD;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        showStartScreen();
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!SecureTokenStore.read(this).isEmpty()) route(intent.getStringExtra(EXTRA_DESTINATION));
    }

    @Override public void onBackPressed() {
        if (!DASHBOARD.equals(currentDestination) && !SecureTokenStore.read(this).isEmpty()) {
            showDashboard();
        } else {
            super.onBackPressed();
        }
    }

    private void showStartScreen() {
        if (SecureTokenStore.read(this).isEmpty()) showPairing();
        else route(getIntent().getStringExtra(EXTRA_DESTINATION));
    }

    private void route(String destination) {
        if (FEEDBACK.equals(destination)) showInbox();
        else if (SOURCES.equals(destination)) showSourceControl();
        else showDashboard();
    }

    private void showPairing() {
        currentDestination = DASHBOARD;
        LinearLayout root = vertical(24);
        root.setGravity(Gravity.CENTER_VERTICAL);

        TextView eyebrow = label("PRIVATE CONTROL CENTER");
        TextView title = text("ZeroChill Admin", 32, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView detail = text("Connect once to manage analytics, sources, and feedback.",
                15, color(R.color.app_on_surface_variant));
        detail.setPadding(0, dp(8), 0, dp(26));

        EditText token = new EditText(this);
        token.setHint("Admin token");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setTextColor(color(R.color.app_on_surface));
        token.setHintTextColor(color(R.color.app_on_surface_variant));

        MaterialButton connect = button("Connect securely");
        connect.setMinHeight(dp(52));
        ProgressBar progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);

        root.addView(eyebrow);
        root.addView(title);
        root.addView(detail);
        root.addView(token);
        root.addView(connect, matchWrap(dp(10), 0));
        root.addView(progress);
        setContentView(root);

        connect.setOnClickListener(v -> {
            String value = token.getText().toString().trim();
            if (value.length() < 24) { token.setError("Paste the complete admin token"); return; }
            connect.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            network.execute(() -> {
                try {
                    AdminRepository.list(value);
                    SecureTokenStore.save(this, value);
                    runOnUiThread(() -> {
                        ((AdminApplication) getApplication()).scheduleNotifications();
                        showDashboard();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        connect.setEnabled(true);
                        progress.setVisibility(View.GONE);
                        toast(message(error));
                    });
                }
            });
        });
    }

    private void showDashboard() {
        currentDestination = DASHBOARD;
        LinearLayout shell = screenShell();
        LinearLayout content = column(18);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column(0);
        TextView eyebrow = label("CONTROL CENTER");
        TextView title = text("Dashboard", 30, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView subtitle = text("Live overview of ZeroChill", 14, color(R.color.app_on_surface_variant));
        titles.addView(eyebrow);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton lock = compactButton("Lock");
        header.addView(lock);
        content.addView(header);

        TextView dashboardStatus = text("Refreshing live data…", 13, color(R.color.app_on_surface_variant));
        dashboardStatus.setPadding(0, dp(12), 0, dp(2));
        content.addView(dashboardStatus);

        LinearLayout userMetrics = new LinearLayout(this);
        userMetrics.setOrientation(LinearLayout.HORIZONTAL);
        TextView today = metricNumber("–", "Today");
        TextView week = metricNumber("–", "Week");
        TextView month = metricNumber("–", "Month");
        userMetrics.addView(metricBlock(today), weighted());
        userMetrics.addView(metricBlock(week), weighted());
        userMetrics.addView(metricBlock(month), weighted());
        content.addView(panel("ACTIVE USERS", "Anonymous active installs", userMetrics));

        LinearLayout trendBody = column(0);
        TextView topCreator = text("Loading…", 22, Color.WHITE);
        topCreator.setTypeface(null, Typeface.BOLD);
        TextView creatorDetail = text("Trending creator", 13, color(R.color.app_on_surface_variant));
        trendBody.addView(topCreator);
        trendBody.addView(creatorDetail);
        content.addView(panel("TRENDING NOW", "Most popular creator this week", trendBody));

        LinearLayout feedbackBody = new LinearLayout(this);
        feedbackBody.setOrientation(LinearLayout.HORIZONTAL);
        TextView newFeedback = metricNumber("–", "New");
        TextView totalFeedback = metricNumber("–", "Total");
        feedbackBody.addView(metricBlock(newFeedback), weighted());
        feedbackBody.addView(metricBlock(totalFeedback), weighted());
        content.addView(panel("FEEDBACK", "New and total user submissions", feedbackBody));

        LinearLayout sourceBody = column(0);
        TextView sourceSummary = text("Loading source status…", 15, color(R.color.app_on_surface));
        sourceBody.addView(sourceSummary);
        content.addView(panel("SOURCE HEALTH", "Current remote source switches", sourceBody));

        LinearLayout quickBody = new LinearLayout(this);
        quickBody.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton analytics = button("View analytics");
        MaterialButton manageSources = button("Manage sources");
        quickBody.addView(analytics, weighted());
        quickBody.addView(manageSources, weighted());
        content.addView(panel("QUICK ACTIONS", "Jump straight to the tools you use most", quickBody));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomNav(DASHBOARD));
        setContentView(shell);

        lock.setOnClickListener(v -> confirmLock());
        analytics.setOnClickListener(v -> openAnalytics());
        manageSources.setOnClickListener(v -> showSourceControl());

        String token = SecureTokenStore.read(this);
        network.execute(() -> {
            try {
                AdminRepository.AnalyticsDashboard dashboard = AdminRepository.analytics(token);
                List<AdminRepository.Item> feedbackItems = AdminRepository.list(token);
                org.json.JSONObject configItem = AdminRepository.currentConfig(token);
                runOnUiThread(() -> {
                    today.setText(String.format(Locale.US, "%,d", dashboard.dailyUsers));
                    week.setText(String.format(Locale.US, "%,d", dashboard.weeklyUsers));
                    month.setText(String.format(Locale.US, "%,d", dashboard.monthlyUsers));

                    if (dashboard.creators.isEmpty()) {
                        topCreator.setText("No trend yet");
                        creatorDetail.setText("Creator interest will appear as users browse.");
                    } else {
                        AdminRepository.AnalyticsRow top = dashboard.creators.get(0);
                        topCreator.setText(top.value);
                        creatorDetail.setText(String.format(Locale.US,
                                "%,d users this week · %,d opens", top.uniqueUsers, top.eventCount));
                    }

                    int submitted = 0;
                    for (AdminRepository.Item item : feedbackItems) {
                        if ("submitted".equals(item.status)) submitted++;
                    }
                    newFeedback.setText(String.format(Locale.US, "%,d", submitted));
                    totalFeedback.setText(String.format(Locale.US, "%,d", feedbackItems.size()));
                    sourceSummary.setText(dashboardSourceStatus(configItem));
                    dashboardStatus.setText("Live data updated now");
                });
            } catch (SecurityException error) {
                runOnUiThread(() -> {
                    SecureTokenStore.clear(this);
                    toast(error.getMessage());
                    showPairing();
                });
            } catch (Exception error) {
                runOnUiThread(() -> dashboardStatus.setText("Could not refresh: " + message(error)));
            }
        });
    }

    private void showInbox() {
        currentDestination = FEEDBACK;
        LinearLayout shell = screenShell();
        LinearLayout page = column(16);
        page.setPadding(dp(18), dp(18), dp(18), 0);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column(0);
        TextView eyebrow = label("USER VOICE");
        TextView title = text("Feedback", 30, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        count = text("", 14, color(R.color.app_on_surface_variant));
        titles.addView(eyebrow);
        titles.addView(title);
        titles.addView(count);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton lock = compactButton("Lock");
        header.addView(lock);
        page.addView(header);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, dp(12), 0, dp(8));
        addFilter(filters, "All", "all");
        addFilter(filters, "Bugs", "bug_report");
        addFilter(filters, "Requests", "feature_request");
        page.addView(filters);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FeedbackAdapter(this::showDetail);
        list.setAdapter(adapter);
        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(color(R.color.app_primary));
        swipe.addView(list);
        swipe.setOnRefreshListener(this::load);
        page.addView(swipe, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        shell.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomNav(FEEDBACK));
        setContentView(shell);
        lock.setOnClickListener(v -> confirmLock());
        load();
    }

    private void showSourceControl() {
        currentDestination = SOURCES;
        LinearLayout shell = screenShell();
        LinearLayout page = column(16);
        page.setPadding(dp(18), dp(18), dp(18), dp(8));

        TextView eyebrow = label("REMOTE CONTROL");
        TextView title = text("Source Control", 30, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        TextView subtitle = text("Keep sources healthy without shipping a new APK.",
                14, color(R.color.app_on_surface_variant));
        page.addView(eyebrow);
        page.addView(title);
        page.addView(subtitle);

        TextView status = text("Loading published configuration…", 14,
                color(R.color.app_on_surface_variant));
        status.setPadding(0, dp(12), 0, dp(6));
        page.addView(status);

        TextView help = text("Use each source card for normal changes. Advanced JSON stays available when you need deeper control.",
                13, color(R.color.app_on_surface_variant));
        help.setPadding(0, 0, 0, dp(8));
        page.addView(help);

        SourceConfigEditor editor = new SourceConfigEditor(this);
        ScrollView editorScroll = new ScrollView(this);
        editorScroll.setFillViewport(true);
        editorScroll.addView(editor, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(editorScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(6), 0, 0);
        MaterialButton validate = button("Validate");
        MaterialButton publish = button("Publish");
        MaterialButton history = button("History");
        publish.setEnabled(false);
        actions.addView(validate, weighted());
        actions.addView(publish, weighted());
        actions.addView(history, weighted());
        page.addView(actions);

        shell.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomNav(SOURCES));
        setContentView(shell);

        final long[] currentVersion = {0L};
        final String[] validatedText = {""};
        editor.setOnChangedListener(() -> {
            if (!validatedText[0].isEmpty()) status.setText("Changes made. Validate again before publishing.");
            validatedText[0] = "";
            publish.setEnabled(false);
        });

        network.execute(() -> {
            try {
                org.json.JSONObject item = AdminRepository.currentConfig(SecureTokenStore.read(this));
                runOnUiThread(() -> {
                    if (item == null) {
                        org.json.JSONObject defaults = bundledDefaults();
                        editor.setConfig(defaults);
                        status.setText("No configuration is published. Bundled defaults are ready to validate.");
                        return;
                    }
                    currentVersion[0] = item.optLong("config_version");
                    org.json.JSONObject config = item.optJSONObject("config");
                    editor.setConfig(config);
                    status.setText(configStatus(item));
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("Load failed: " + message(error)));
            }
        });

        validate.setOnClickListener(v -> {
            validate.setEnabled(false);
            publish.setEnabled(false);
            status.setText("Validating changes…");
            network.execute(() -> {
                try {
                    org.json.JSONObject candidate = editor.getConfig();
                    candidate.put("configVersion", currentVersion[0] + 1L);
                    candidate.put("updatedAt", isoNow());
                    AdminRepository.validateConfig(SecureTokenStore.read(this), candidate);
                    validatedText[0] = candidate.toString();
                    runOnUiThread(() -> {
                        editor.setConfig(candidate);
                        status.setText("Validated version " + (currentVersion[0] + 1L) + ". Ready to publish.");
                        validate.setEnabled(true);
                        publish.setEnabled(true);
                    });
                } catch (Exception error) {
                    validatedText[0] = "";
                    runOnUiThread(() -> {
                        status.setText("Rejected: " + message(error));
                        validate.setEnabled(true);
                    });
                }
            });
        });

        publish.setOnClickListener(v -> {
            try {
                if (!editor.getConfig().toString().equals(validatedText[0])) {
                    publish.setEnabled(false);
                    status.setText("The configuration changed. Validate it again before publishing.");
                    return;
                }
            } catch (Exception error) {
                publish.setEnabled(false);
                status.setText("Could not read the edited configuration. Validate it again.");
                return;
            }
            publish.setEnabled(false);
            status.setText("Publishing…");
            network.execute(() -> {
                try {
                    long version = AdminRepository.publishConfig(SecureTokenStore.read(this),
                            new org.json.JSONObject(validatedText[0]));
                    currentVersion[0] = version;
                    validatedText[0] = "";
                    runOnUiThread(() -> status.setText("Published version " + version + ". Changes are live."));
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        status.setText("Publish failed: " + message(error));
                        publish.setEnabled(true);
                    });
                }
            });
        });

        history.setOnClickListener(v -> loadConfigHistory(status, currentVersion[0]));
    }

    private LinearLayout bottomNav(String selected) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(7), dp(6), dp(7));
        nav.setBackgroundColor(color(R.color.app_surface));

        nav.addView(navButton("Dashboard", DASHBOARD, selected, v -> showDashboard()), weighted());
        nav.addView(navButton("Analytics", "analytics", selected, v -> openAnalytics()), weighted());
        nav.addView(navButton("Sources", SOURCES, selected, v -> showSourceControl()), weighted());
        nav.addView(navButton("Feedback", FEEDBACK, selected, v -> showInbox()), weighted());
        return nav;
    }

    private MaterialButton navButton(String title, String destination, String selected, View.OnClickListener click) {
        boolean active = destination.equals(selected);
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
        button.setOnClickListener(click);
        return button;
    }

    private void openAnalytics() {
        startActivity(new Intent(this, AnalyticsActivity.class));
    }

    private void confirmLock() {
        new AlertDialog.Builder(this)
                .setTitle("Lock admin app?")
                .setMessage("You will need the admin token to connect again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Lock", (dialog, which) -> {
                    SecureTokenStore.clear(this);
                    showPairing();
                })
                .show();
    }

    private void loadConfigHistory(TextView status, long activeVersion) {
        status.setText("Loading configuration history…");
        network.execute(() -> {
            try {
                List<AdminRepository.ConfigVersion> versions =
                        AdminRepository.configHistory(SecureTokenStore.read(this));
                runOnUiThread(() -> showHistoryDialog(versions, activeVersion));
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("History failed: " + message(error)));
            }
        });
    }

    private void showHistoryDialog(List<AdminRepository.ConfigVersion> versions, long activeVersion) {
        LinearLayout rows = vertical(8);
        for (AdminRepository.ConfigVersion version : versions) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            String label = "Version " + version.version + (version.active ? "  ACTIVE" : "") +
                    "\n" + version.action.toUpperCase(Locale.US) + "  ·  " + formatDate(version.updatedAt);
            row.addView(text(label, 14, color(R.color.app_on_surface)),
                    new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton rollback = compactButton("Roll back");
            rollback.setEnabled(!version.active && version.version < activeVersion);
            rollback.setOnClickListener(v -> confirmRollback(version.version));
            row.addView(rollback);
            rows.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(rows);
        new AlertDialog.Builder(this).setTitle("Published versions").setView(scroll)
                .setPositiveButton("Close", null).show();
    }

    private void confirmRollback(long version) {
        new AlertDialog.Builder(this)
                .setTitle("Roll back source configuration?")
                .setMessage("This republishes version " + version + " as a new higher version for every app.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Roll back", (dialog, which) -> network.execute(() -> {
                    try {
                        long published = AdminRepository.rollbackConfig(SecureTokenStore.read(this), version);
                        runOnUiThread(() -> {
                            toast("Rolled back as version " + published);
                            showSourceControl();
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> toast("Rollback failed: " + message(error)));
                    }
                })).show();
    }

    private String dashboardSourceStatus(org.json.JSONObject item) {
        if (item == null) return "No published configuration";
        org.json.JSONObject config = item.optJSONObject("config");
        org.json.JSONObject sources = config == null ? null : config.optJSONObject("sources");
        if (sources == null) return "Published configuration loaded";
        return sourceDot(sources, "fapello", "Fapello") + "    " +
                sourceDot(sources, "bunkr", "Bunkr") + "\n" +
                sourceDot(sources, "wikifeet", "WikiFeet") + "    " +
                sourceDot(sources, "wikifeetx", "WikiFeet X");
    }

    private String sourceDot(org.json.JSONObject sources, String id, String label) {
        org.json.JSONObject source = sources.optJSONObject(id);
        return (source != null && source.optBoolean("enabled") ? "● " : "○ ") + label;
    }

    private String configStatus(org.json.JSONObject item) {
        org.json.JSONObject config = item.optJSONObject("config");
        org.json.JSONObject sources = config == null ? null : config.optJSONObject("sources");
        if (sources == null) return "Published version " + item.optLong("config_version");
        return "Published version " + item.optLong("config_version") + "\n" +
                sourceState(sources, "fapello", "Fapello") + "  ·  " +
                sourceState(sources, "bunkr", "Bunkr") + "  ·  " +
                sourceState(sources, "wikifeet", "WikiFeet") + "  ·  " +
                sourceState(sources, "wikifeetx", "WikiFeet X");
    }

    private String sourceState(org.json.JSONObject sources, String id, String label) {
        org.json.JSONObject source = sources.optJSONObject(id);
        return label + ": " + (source != null && source.optBoolean("enabled") ? "ON" : "OFF");
    }

    private static String isoNow() {
        SimpleDateFormat value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        value.setTimeZone(TimeZone.getTimeZone("UTC"));
        return value.format(new Date());
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? "Request failed" : error.getMessage();
    }

    private org.json.JSONObject bundledDefaults() {
        try (java.io.InputStream input = getAssets().open("source_config_defaults.json")) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return new org.json.JSONObject(output.toString(java.nio.charset.StandardCharsets.UTF_8.name()));
        } catch (Exception error) {
            return null;
        }
    }

    private void addFilter(LinearLayout row, String label, String value) {
        MaterialButton item = compactButton(label);
        item.setAllCaps(false);
        item.setOnClickListener(v -> {
            filter = value;
            applyFilter();
        });
        row.addView(item, weighted());
    }

    private void load() {
        if (swipe == null) return;
        swipe.setRefreshing(true);
        String token = SecureTokenStore.read(this);
        network.execute(() -> {
            try {
                List<AdminRepository.Item> items = AdminRepository.list(token);
                runOnUiThread(() -> {
                    allItems.clear();
                    allItems.addAll(items);
                    applyFilter();
                    swipe.setRefreshing(false);
                });
            } catch (SecurityException error) {
                runOnUiThread(() -> {
                    swipe.setRefreshing(false);
                    SecureTokenStore.clear(this);
                    showPairing();
                    toast(error.getMessage());
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    swipe.setRefreshing(false);
                    toast(message(error));
                });
            }
        });
    }

    private void applyFilter() {
        if (adapter == null || count == null) return;
        List<AdminRepository.Item> shown = new ArrayList<>();
        for (AdminRepository.Item item : allItems) {
            if (filter.equals("all") || filter.equals(item.type)) shown.add(item);
        }
        adapter.setItems(shown);
        count.setText(shown.size() + (shown.size() == 1 ? " submission" : " submissions"));
    }

    private void showDetail(AdminRepository.Item item) {
        LinearLayout content = vertical(0);
        TextView meta = text(displayType(item.type) + stars(item.rating) + "\n" +
                item.message + "\n\nVersion " + item.appVersion + " · Android " + item.androidVersion +
                "\n" + item.device + " · " + item.section + "\n" + formatDate(item.createdAt),
                15, color(R.color.app_on_surface));
        content.addView(meta);
        String[] statuses = {"submitted", "reviewing", "planned", "completed"};
        Spinner status = new Spinner(this);
        status.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statuses));
        for (int i = 0; i < statuses.length; i++) if (statuses[i].equals(item.status)) status.setSelection(i);
        EditText reply = new EditText(this);
        reply.setHint("Developer reply");
        reply.setMinLines(3);
        reply.setText(item.reply);
        content.addView(status);
        content.addView(reply);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Manage feedback")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            network.execute(() -> {
                try {
                    AdminRepository.update(SecureTokenStore.read(this), item.id,
                            status.getSelectedItem().toString(), reply.getText().toString().trim());
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        toast("Reply saved");
                        load();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        toast(message(error));
                    });
                }
            });
        }));
        dialog.show();
    }

    private LinearLayout screenShell() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color(R.color.app_background));
        return layout;
    }

    private LinearLayout vertical(int paddingDp) {
        LinearLayout layout = column(paddingDp);
        layout.setBackgroundColor(color(R.color.app_background));
        return layout;
    }

    private LinearLayout column(int paddingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return layout;
    }

    private MaterialCardView panel(String title, String subtitle, View body) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(R.color.app_surface));
        card.setStrokeColor(color(R.color.app_surface_variant));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(20));
        card.setCardElevation(0);

        LinearLayout wrapper = column(16);
        TextView heading = label(title);
        wrapper.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = text(subtitle, 13, color(R.color.app_on_surface_variant));
            detail.setPadding(0, dp(2), 0, dp(12));
            wrapper.addView(detail);
        } else {
            heading.setPadding(0, 0, 0, dp(12));
        }
        wrapper.addView(body);
        card.addView(wrapper);
        LinearLayout.LayoutParams params = matchWrap(dp(8), dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout metricBlock(TextView value) {
        LinearLayout block = column(2);
        block.setGravity(Gravity.CENTER);
        block.addView(value);
        return block;
    }

    private TextView metricNumber(String value, String caption) {
        TextView result = text(value + "\n" + caption, 14, color(R.color.app_on_surface_variant));
        android.text.SpannableString text = new android.text.SpannableString(value + "\n" + caption);
        text.setSpan(new android.text.style.RelativeSizeSpan(1.8f), 0, value.length(), 0);
        text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, value.length(), 0);
        result.setText(text);
        result.setGravity(Gravity.CENTER);
        return result;
    }

    private TextView label(String value) {
        TextView result = text(value, 11, color(R.color.app_primary));
        result.setTypeface(null, Typeface.BOLD);
        result.setLetterSpacing(0.08f);
        return result;
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextColor(color(R.color.app_on_primary));
        button.setAllCaps(false);
        button.setCornerRadius(dp(14));
        return button;
    }

    private MaterialButton compactButton(String value) {
        MaterialButton button = button(value);
        button.setMinHeight(dp(42));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, top, 0, bottom);
        return params;
    }

    private int color(int id) { return getColor(id); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) {
        Toast.makeText(this, value == null ? "Something went wrong" : value, Toast.LENGTH_LONG).show();
    }

    private static String displayType(String type) {
        if ("bug_report".equals(type)) return "BUG REPORT";
        if ("feature_request".equals(type)) return "FEATURE REQUEST";
        return "GENERAL FEEDBACK";
    }

    private static String stars(int rating) {
        if (rating < 1) return "";
        StringBuilder value = new StringBuilder("  ");
        for (int i = 0; i < rating; i++) value.append('★');
        return value.toString();
    }

    private static String formatDate(String raw) {
        try {
            SimpleDateFormat source = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            source.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = source.parse(raw);
            return new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(parsed);
        } catch (ParseException ignored) {
            return raw;
        }
    }

    private final class FeedbackAdapter extends RecyclerView.Adapter<FeedbackAdapter.Holder> {
        private final List<AdminRepository.Item> items = new ArrayList<>();
        private final ItemClick click;

        FeedbackAdapter(ItemClick click) { this.click = click; }

        void setItems(List<AdminRepository.Item> replacement) {
            items.clear();
            items.addAll(replacement);
            notifyDataSetChanged();
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(MainActivity.this);
            card.setCardBackgroundColor(color(R.color.app_surface));
            card.setStrokeColor(color(R.color.app_surface_variant));
            card.setStrokeWidth(dp(1));
            card.setRadius(dp(18));
            card.setCardElevation(0);
            TextView text = MainActivity.this.text("", 15, color(R.color.app_on_surface));
            text.setPadding(dp(18), dp(16), dp(18), dp(16));
            card.addView(text);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(12));
            card.setLayoutParams(params);
            return new Holder(card, text);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            AdminRepository.Item item = items.get(position);
            String preview = item.message == null ? "" : item.message.trim();
            if (preview.length() > 180) preview = preview.substring(0, 177) + "…";
            holder.text.setText(displayType(item.type) + stars(item.rating) + "\n" + preview +
                    "\n\n" + item.status.toUpperCase(Locale.US) + "  ·  " + formatDate(item.createdAt));
            holder.itemView.setOnClickListener(v -> click.open(item));
        }

        @Override public int getItemCount() { return items.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            Holder(View view, TextView text) { super(view); this.text = text; }
        }
    }

    private interface ItemClick { void open(AdminRepository.Item item); }
}
