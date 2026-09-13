package com.addy37.crazyshitadmin;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
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
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final List<AdminRepository.Item> allItems = new ArrayList<>();
    private FeedbackAdapter adapter;
    private SwipeRefreshLayout swipe;
    private TextView count;
    private LinearLayout root;
    private String filter = "all";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        showStartScreen();
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void showStartScreen() {
        if (SecureTokenStore.read(this).isEmpty()) showPairing(); else showInbox();
    }

    private void showPairing() {
        root = vertical(24);
        root.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("CrazyShit Admin", 30, Color.WHITE);
        TextView detail = text("Private feedback management", 16, color(R.color.app_on_surface_variant));
        detail.setPadding(0, dp(8), 0, dp(24));
        EditText token = new EditText(this);
        token.setHint("Paste admin token");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        MaterialButton connect = button("Connect securely");
        ProgressBar progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(title); root.addView(detail); root.addView(token); root.addView(connect); root.addView(progress);
        setContentView(root);
        connect.setOnClickListener(v -> {
            String value = token.getText().toString().trim();
            if (value.length() < 24) { token.setError("Paste the complete admin token"); return; }
            connect.setEnabled(false); progress.setVisibility(View.VISIBLE);
            network.execute(() -> {
                try {
                    AdminRepository.list(value);
                    SecureTokenStore.save(this, value);
                    runOnUiThread(() -> { ((AdminApplication) getApplication()).scheduleNotifications(); showInbox(); });
                } catch (Exception error) {
                    runOnUiThread(() -> { connect.setEnabled(true); progress.setVisibility(View.GONE); toast(error.getMessage()); });
                }
            });
        });
    }

    private void showInbox() {
        root = vertical(16);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Feedback", 30, Color.WHITE);
        count = text("", 14, color(R.color.app_on_surface_variant));
        LinearLayout titles = vertical(0); titles.addView(title); titles.addView(count);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton sources = button("Source Control");
        header.addView(sources);
        MaterialButton logout = button("Lock");
        header.addView(logout);
        root.addView(header);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        addFilter(filters, "All", "all");
        addFilter(filters, "Bugs", "bug_report");
        addFilter(filters, "Requests", "feature_request");
        root.addView(filters);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FeedbackAdapter(this::showDetail);
        list.setAdapter(adapter);
        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(color(R.color.app_primary));
        swipe.addView(list);
        swipe.setOnRefreshListener(this::load);
        root.addView(swipe, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        sources.setOnClickListener(v -> showSourceControl());
        logout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Lock admin app?")
                .setMessage("You will need the admin token to connect again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Lock", (dialog, which) -> { SecureTokenStore.clear(this); showPairing(); })
                .show());
        load();
    }

    private void showSourceControl() {
        LinearLayout page = vertical(16);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Source Control", 28, Color.WHITE);
        MaterialButton feedback = button("Feedback");
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(feedback);
        page.addView(header);

        TextView status = text("Loading published configuration…", 15,
                color(R.color.app_on_surface_variant));
        status.setPadding(0, dp(12), 0, dp(12));
        page.addView(status);

        EditText editor = new EditText(this);
        editor.setTextColor(color(R.color.app_on_surface));
        editor.setHintTextColor(color(R.color.app_on_surface_variant));
        editor.setHint("Published source configuration JSON");
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(12);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(18);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        page.addView(editor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton validate = button("Validate");
        MaterialButton publish = button("Publish");
        MaterialButton history = button("History");
        publish.setEnabled(false);
        actions.addView(validate, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(publish, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(history, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(actions);
        setContentView(page);

        final long[] currentVersion = {0L};
        final String[] validatedText = {""};
        feedback.setOnClickListener(v -> showInbox());
        network.execute(() -> {
            try {
                org.json.JSONObject item = AdminRepository.currentConfig(SecureTokenStore.read(this));
                runOnUiThread(() -> {
                    if (item == null) {
                        org.json.JSONObject defaults = bundledDefaults();
                        editor.setText(prettyJson(defaults));
                        status.setText("No configuration is published. Bundled defaults are ready to validate.");
                        return;
                    }
                    currentVersion[0] = item.optLong("config_version");
                    org.json.JSONObject config = item.optJSONObject("config");
                    editor.setText(prettyJson(config));
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
                    org.json.JSONObject candidate = new org.json.JSONObject(editor.getText().toString());
                    candidate.put("configVersion", currentVersion[0] + 1L);
                    candidate.put("updatedAt", isoNow());
                    AdminRepository.validateConfig(SecureTokenStore.read(this), candidate);
                    String formatted = candidate.toString(2);
                    validatedText[0] = formatted;
                    runOnUiThread(() -> {
                        editor.setText(formatted);
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
            if (!editor.getText().toString().equals(validatedText[0])) {
                publish.setEnabled(false);
                status.setText("The configuration changed. Validate it again before publishing.");
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
                    runOnUiThread(() -> status.setText("Published version " + version + "."));
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
            MaterialButton rollback = button("Roll back");
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
                        runOnUiThread(() -> { toast("Rolled back as version " + published); showSourceControl(); });
                    } catch (Exception error) {
                        runOnUiThread(() -> toast("Rollback failed: " + message(error)));
                    }
                })).show();
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
        java.text.SimpleDateFormat value = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        value.setTimeZone(TimeZone.getTimeZone("UTC"));
        return value.format(new Date());
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? "Request failed" : error.getMessage();
    }

    private static String prettyJson(org.json.JSONObject value) {
        if (value == null) return "";
        try { return value.toString(2); }
        catch (org.json.JSONException ignored) { return value.toString(); }
    }

    private org.json.JSONObject bundledDefaults() {
        try (java.io.InputStream input = getAssets().open("source_config_defaults.json")) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new org.json.JSONObject(output.toString(java.nio.charset.StandardCharsets.UTF_8.name()));
        } catch (Exception error) {
            return null;
        }
    }

    private void addFilter(LinearLayout row, String label, String value) {
        MaterialButton item = button(label);
        item.setOnClickListener(v -> { filter = value; applyFilter(); });
        row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private void load() {
        swipe.setRefreshing(true);
        String token = SecureTokenStore.read(this);
        network.execute(() -> {
            try {
                List<AdminRepository.Item> items = AdminRepository.list(token);
                runOnUiThread(() -> {
                    allItems.clear(); allItems.addAll(items); applyFilter(); swipe.setRefreshing(false);
                });
            } catch (SecurityException error) {
                runOnUiThread(() -> { swipe.setRefreshing(false); SecureTokenStore.clear(this); showPairing(); toast(error.getMessage()); });
            } catch (Exception error) {
                runOnUiThread(() -> { swipe.setRefreshing(false); toast(error.getMessage()); });
            }
        });
    }

    private void applyFilter() {
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
        content.addView(status); content.addView(reply);
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
                    runOnUiThread(() -> { dialog.dismiss(); toast("Reply saved"); load(); });
                } catch (Exception error) {
                    runOnUiThread(() -> { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); toast(error.getMessage()); });
                }
            });
        }));
        dialog.show();
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
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); return text;
    }

    private int color(int id) { return getColor(id); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value == null ? "Something went wrong" : value, Toast.LENGTH_LONG).show(); }
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
        } catch (ParseException ignored) { return raw; }
    }

    private final class FeedbackAdapter extends RecyclerView.Adapter<FeedbackAdapter.Holder> {
        private final List<AdminRepository.Item> items = new ArrayList<>();
        private final ItemClick click;
        FeedbackAdapter(ItemClick click) { this.click = click; }
        void setItems(List<AdminRepository.Item> replacement) { items.clear(); items.addAll(replacement); notifyDataSetChanged(); }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(MainActivity.this);
            card.setCardBackgroundColor(color(R.color.app_surface));
            card.setStrokeColor(color(R.color.app_surface_variant));
            card.setStrokeWidth(dp(1)); card.setRadius(dp(16));
            TextView text = MainActivity.this.text("", 15, color(R.color.app_on_surface));
            text.setPadding(dp(18), dp(16), dp(18), dp(16)); card.addView(text);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(12)); card.setLayoutParams(params);
            return new Holder(card, text);
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            AdminRepository.Item item = items.get(position);
            holder.text.setText(displayType(item.type) + stars(item.rating) + "\n" + item.message +
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
