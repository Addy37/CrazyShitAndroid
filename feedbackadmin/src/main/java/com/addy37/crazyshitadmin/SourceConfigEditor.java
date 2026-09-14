package com.addy37.crazyshitadmin;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Friendly editor for the data-only source configuration used by CrazyShit. */
final class SourceConfigEditor extends LinearLayout {
    interface OnChangedListener { void onChanged(); }

    private static final int FIELD_TEXT = 0;
    private static final int FIELD_NUMBER = 1;
    private static final int FIELD_LIST = 2;

    private JSONObject config = new JSONObject();
    private OnChangedListener changedListener;
    private boolean suppressChanges;

    SourceConfigEditor(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, dp(12));
    }

    void setOnChangedListener(OnChangedListener listener) {
        changedListener = listener;
    }

    void setConfig(JSONObject value) {
        suppressChanges = true;
        try {
            config = value == null ? new JSONObject() : new JSONObject(value.toString());
        } catch (JSONException error) {
            config = new JSONObject();
        }
        render();
        suppressChanges = false;
    }

    JSONObject getConfig() throws JSONException {
        return new JSONObject(config.toString());
    }

    private void render() {
        removeAllViews();
        JSONObject global = object(config, "global");
        JSONObject sources = object(config, "sources");

        addView(section("GLOBAL"));
        addView(globalCard(global));
        addView(section("SOURCES"));
        addView(sourceCard(sources, "fapello", "Fapello"));
        addView(sourceCard(sources, "bunkr", "Bunkr"));
        addView(sourceCard(sources, "wikifeet", "WikiFeet"));
        addView(sourceCard(sources, "wikifeetx", "WikiFeet X"));

        MaterialButton raw = button("Advanced: full JSON");
        raw.setOnClickListener(v -> showRawConfigDialog());
        LayoutParams rawParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rawParams.topMargin = dp(8);
        addView(raw, rawParams);
    }

    private MaterialCardView globalCard(JSONObject global) {
        MaterialCardView card = card();
        LinearLayout body = vertical(12);
        TextView detail = text("Emergency controls shared by every source.", 13,
                color(R.color.app_on_surface_variant));
        body.addView(detail);
        body.addView(switchRow("Source kill switches",
                "Allow individual sources to be turned off remotely.",
                global.optBoolean("sourceKillSwitchesEnabled", true),
                checked -> put(global, "sourceKillSwitchesEnabled", checked)));
        body.addView(switchRow("Fallback domains",
                "Allow configured fallback domains when a primary domain fails.",
                global.optBoolean("fallbacksEnabled", true),
                checked -> put(global, "fallbacksEnabled", checked)));
        card.addView(body);
        return card;
    }

    private MaterialCardView sourceCard(JSONObject sources, String id, String label) {
        JSONObject source = object(sources, id);
        MaterialCardView card = card();
        LinearLayout body = vertical(14);

        LinearLayout heading = new LinearLayout(getContext());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = vertical(0);
        TextView title = text(label, 19, color(R.color.app_on_surface));
        title.setTypeface(null, Typeface.BOLD);
        TextView summary = text(sourceSummary(id, source), 13,
                color(R.color.app_on_surface_variant));
        summary.setPadding(0, dp(4), dp(8), 0);
        copy.addView(title);
        copy.addView(summary);
        heading.addView(copy, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialSwitch enabled = new MaterialSwitch(getContext());
        enabled.setChecked(source.optBoolean("enabled", true));
        enabled.setContentDescription(label + " enabled");
        enabled.setOnCheckedChangeListener((button, checked) -> {
            put(source, "enabled", checked);
            summary.setText(sourceSummary(id, source));
        });
        heading.addView(enabled);
        body.addView(heading);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(HORIZONTAL);
        MaterialButton edit = button("Edit");
        MaterialButton advanced = button("Advanced JSON");
        edit.setOnClickListener(v -> showCommonEditor(id, label, source));
        advanced.setOnClickListener(v -> showRawSourceDialog(sources, id, label, source));
        actions.addView(edit, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(advanced, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(actions);
        card.addView(body);
        return card;
    }

    private void showCommonEditor(String id, String label, JSONObject source) {
        LinearLayout fields = vertical(4);
        List<Field> bindings = new ArrayList<>();

        if ("fapello".equals(id)) {
            addField(fields, bindings, source, "Base URL", "baseUrl", FIELD_TEXT);
            addField(fields, bindings, source, "Fallback domains", "fallbackDomains", FIELD_LIST);
            addField(fields, bindings, source, "Retry count", "retryCount", FIELD_NUMBER);
            addField(fields, bindings, source, "Request timeout (ms)", "requestTimeoutMs", FIELD_NUMBER);
            addField(fields, bindings, source, "AJAX timeout (ms)", "ajaxTimeoutMs", FIELD_NUMBER);
            addField(fields, bindings, source, "Referer override", "refererOverride", FIELD_TEXT);
            addField(fields, bindings, source, "User-Agent", "userAgent", FIELD_TEXT);
        } else if ("bunkr".equals(id)) {
            addField(fields, bindings, source, "Index URL", "indexUrl", FIELD_TEXT);
            addField(fields, bindings, source, "Page origins", "pageOrigins", FIELD_LIST);
            addField(fields, bindings, source, "Fallback origins", "fallbackOrigins", FIELD_LIST);
            addField(fields, bindings, source, "API endpoints", "apiEndpoints", FIELD_LIST);
            addField(fields, bindings, source, "Signing URL", "signUrl", FIELD_TEXT);
            addField(fields, bindings, source, "Download root", "downloadRoot", FIELD_TEXT);
            addField(fields, bindings, source, "Retry count", "retryCount", FIELD_NUMBER);
            addField(fields, bindings, source, "Request timeout (ms)", "requestTimeoutMs", FIELD_NUMBER);
            addField(fields, bindings, source, "Signing timeout (ms)", "signTimeoutMs", FIELD_NUMBER);
            addField(fields, bindings, source, "Referer override", "refererOverride", FIELD_TEXT);
            addField(fields, bindings, source, "User-Agent", "userAgent", FIELD_TEXT);
        } else {
            addField(fields, bindings, source, "Base URL", "baseUrl", FIELD_TEXT);
            addField(fields, bindings, source, "Fallback domains", "fallbackDomains", FIELD_LIST);
            addField(fields, bindings, source, "Picture host", "pictureHost", FIELD_TEXT);
            addField(fields, bindings, source, "Thumbnail host", "thumbnailHost", FIELD_TEXT);
            addField(fields, bindings, source, "Search route", "searchRoute", FIELD_TEXT);
            addField(fields, bindings, source, "Retry count", "retryCount", FIELD_NUMBER);
            addField(fields, bindings, source, "Request timeout (ms)", "requestTimeoutMs", FIELD_NUMBER);
            addField(fields, bindings, source, "AJAX timeout (ms)", "ajaxTimeoutMs", FIELD_NUMBER);
            addField(fields, bindings, source, "Referer override", "refererOverride", FIELD_TEXT);
            addField(fields, bindings, source, "User-Agent", "userAgent", FIELD_TEXT);
        }

        ScrollView scroll = new ScrollView(getContext());
        scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Edit " + label)
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        for (Field field : bindings) field.apply(source);
                        dialog.dismiss();
                        changed();
                        render();
                    } catch (IllegalArgumentException error) {
                        Toast.makeText(getContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
    }

    private void addField(LinearLayout parent, List<Field> fields, JSONObject source,
                          String label, String key, int type) {
        TextView name = text(label, 12, color(R.color.app_on_surface_variant));
        name.setTypeface(null, Typeface.BOLD);
        name.setPadding(0, dp(10), 0, 0);
        parent.addView(name);

        EditText input = new EditText(getContext());
        input.setTextColor(color(R.color.app_on_surface));
        input.setHintTextColor(color(R.color.app_on_surface_variant));
        if (type == FIELD_NUMBER) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setSingleLine(true);
            input.setText(String.valueOf(source.optInt(key, 0)));
        } else if (type == FIELD_LIST) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setMinLines(2);
            input.setText(join(source.optJSONArray(key)));
            input.setHint("One entry per line");
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setSingleLine(false);
            input.setText(source.optString(key, ""));
        }
        parent.addView(input, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        fields.add(new Field(key, type, input));
    }

    private void showRawSourceDialog(JSONObject sources, String id, String label, JSONObject source) {
        EditText editor = jsonEditor(pretty(source));
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(label + " advanced JSON")
                .setMessage("Use this for routes, headers, selectors, patterns, CDN hosts, and other advanced fields.")
                .setView(editor)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        JSONObject replacement = new JSONObject(editor.getText().toString());
                        sources.put(id, replacement);
                        dialog.dismiss();
                        changed();
                        render();
                    } catch (JSONException error) {
                        editor.setError("Invalid JSON: " + error.getMessage());
                    }
                }));
        dialog.show();
    }

    private void showRawConfigDialog() {
        EditText editor = jsonEditor(pretty(config));
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Full source configuration JSON")
                .setMessage("Advanced use only. Validate before publishing.")
                .setView(editor)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        config = new JSONObject(editor.getText().toString());
                        dialog.dismiss();
                        changed();
                        render();
                    } catch (JSONException error) {
                        editor.setError("Invalid JSON: " + error.getMessage());
                    }
                }));
        dialog.show();
    }

    private EditText jsonEditor(String value) {
        EditText editor = new EditText(getContext());
        editor.setText(value);
        editor.setTextColor(color(R.color.app_on_surface));
        editor.setHintTextColor(color(R.color.app_on_surface_variant));
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(12);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(14);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return editor;
    }

    private View switchRow(String title, String subtitle, boolean checked, ToggleChange change) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        LinearLayout copy = vertical(0);
        TextView titleView = text(title, 15, color(R.color.app_on_surface));
        TextView subtitleView = text(subtitle, 12, color(R.color.app_on_surface_variant));
        subtitleView.setPadding(0, dp(2), dp(8), 0);
        copy.addView(titleView);
        copy.addView(subtitleView);
        row.addView(copy, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch toggle = new MaterialSwitch(getContext());
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((button, value) -> change.changed(value));
        row.addView(toggle);
        return row;
    }

    private String sourceSummary(String id, JSONObject source) {
        String endpoint;
        int timeout;
        if ("bunkr".equals(id)) {
            endpoint = source.optString("indexUrl", "No index URL");
            timeout = source.optInt("requestTimeoutMs", 0);
        } else {
            endpoint = source.optString("baseUrl", "No base URL");
            timeout = source.optInt("requestTimeoutMs", 0);
        }
        String state = source.optBoolean("enabled", true) ? "ON" : "OFF";
        int retries = source.optInt("retryCount", 0);
        String timeoutText = timeout > 0 ? formatSeconds(timeout) : "timeout ?";
        return state + " · " + compact(endpoint) + "\nRetries " + retries + " · " + timeoutText;
    }

    private static String formatSeconds(int ms) {
        if (ms % 1000 == 0) return (ms / 1000) + "s timeout";
        return String.format(Locale.US, "%.1fs timeout", ms / 1000f);
    }

    private static String compact(String value) {
        if (value == null || value.isEmpty()) return "No endpoint";
        return value.replaceFirst("^https://", "").replaceAll("/+$", "");
    }

    private void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
            changed();
        } catch (JSONException ignored) {
        }
    }

    private void changed() {
        if (!suppressChanges && changedListener != null) changedListener.onChanged();
    }

    private JSONObject object(JSONObject parent, String key) {
        JSONObject value = parent.optJSONObject(key);
        if (value != null) return value;
        value = new JSONObject();
        try { parent.put(key, value); } catch (JSONException ignored) { }
        return value;
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(getContext());
        card.setCardBackgroundColor(color(R.color.app_surface));
        card.setStrokeColor(color(R.color.app_surface_variant));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout vertical(int paddingDp) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(VERTICAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return layout;
    }

    private TextView section(String value) {
        TextView text = text(value, 12, color(R.color.app_on_surface_variant));
        text.setTypeface(null, Typeface.BOLD);
        text.setPadding(dp(4), dp(10), dp(4), dp(8));
        return text;
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(getContext());
        button.setText(value);
        button.setTextColor(color(R.color.app_on_primary));
        return button;
    }

    private TextView text(String value, int size, int textColor) {
        TextView text = new TextView(getContext());
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(textColor);
        return text;
    }

    private int color(int id) { return getContext().getColor(id); }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String pretty(JSONObject value) {
        try { return value.toString(2); }
        catch (JSONException ignored) { return value.toString(); }
    }

    private static String join(JSONArray array) {
        if (array == null || array.length() == 0) return "";
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            if (i > 0) value.append('\n');
            value.append(array.optString(i));
        }
        return value.toString();
    }

    private static JSONArray parseList(String raw) {
        JSONArray result = new JSONArray();
        if (raw == null) return result;
        String normalized = raw.replace(',', '\n');
        for (String part : normalized.split("\\n")) {
            String value = part.trim();
            if (!value.isEmpty()) result.put(value);
        }
        return result;
    }

    private static final class Field {
        final String key;
        final int type;
        final EditText input;

        Field(String key, int type, EditText input) {
            this.key = key;
            this.type = type;
            this.input = input;
        }

        void apply(JSONObject target) {
            String raw = input.getText().toString().trim();
            try {
                if (type == FIELD_NUMBER) {
                    if (raw.isEmpty()) throw new IllegalArgumentException(key + " cannot be blank");
                    target.put(key, Integer.parseInt(raw));
                } else if (type == FIELD_LIST) {
                    target.put(key, parseList(raw));
                } else {
                    target.put(key, raw);
                }
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(key + " must be a whole number");
            } catch (JSONException error) {
                throw new IllegalArgumentException("Could not update " + key);
            }
        }
    }

    private interface ToggleChange { void changed(boolean checked); }
}
