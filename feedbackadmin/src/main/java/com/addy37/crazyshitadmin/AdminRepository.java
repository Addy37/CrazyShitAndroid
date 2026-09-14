package com.addy37.crazyshitadmin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class AdminRepository {
    static final class Item {
        final String id, type, message, status, reply, appVersion, androidVersion, device, section, createdAt;
        final int rating;

        Item(JSONObject value) {
            id = value.optString("id");
            type = value.optString("type", "general_feedback");
            message = value.optString("message");
            rating = value.optInt("rating", 0);
            status = value.optString("status", "submitted");
            reply = value.optString("developer_reply");
            appVersion = value.optString("app_version", "unknown");
            androidVersion = value.optString("android_version", "unknown");
            device = value.optString("device", "unknown");
            section = value.optString("section", "unknown");
            createdAt = value.optString("created_at");
        }
    }

    static final class ConfigVersion {
        final long version;
        final long basedOnVersion;
        final String updatedAt;
        final String action;
        final boolean active;

        ConfigVersion(JSONObject value) {
            version = value.optLong("config_version");
            basedOnVersion = value.optLong("based_on_version");
            updatedAt = value.optString("updated_at");
            action = value.optString("action", "publish");
            active = value.optBoolean("is_active");
        }
    }

    static final class AnalyticsRow {
        final String value;
        final long eventCount;
        final long uniqueUsers;
        final long usersToday;

        AnalyticsRow(JSONObject row) {
            value = row.optString("value", "Unknown");
            eventCount = row.optLong("event_count");
            uniqueUsers = row.optLong("unique_users");
            usersToday = row.optLong("users_today");
        }
    }

    static final class AnalyticsDashboard {
        final long dailyUsers;
        final long weeklyUsers;
        final long monthlyUsers;
        final String generatedAt;
        final List<AnalyticsRow> sections;
        final List<AnalyticsRow> sources;
        final List<AnalyticsRow> creators;
        final List<AnalyticsRow> versions;

        AnalyticsDashboard(JSONObject value) {
            JSONObject active = value.optJSONObject("active_users");
            dailyUsers = active == null ? 0L : active.optLong("daily");
            weeklyUsers = active == null ? 0L : active.optLong("weekly");
            monthlyUsers = active == null ? 0L : active.optLong("monthly");
            generatedAt = value.optString("generated_at");
            sections = analyticsRows(value.optJSONArray("sections"));
            sources = analyticsRows(value.optJSONArray("sources"));
            creators = analyticsRows(value.optJSONArray("creators"));
            versions = analyticsRows(value.optJSONArray("versions"));
        }
    }

    private AdminRepository() {}

    static List<Item> list(String token) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token,
                new JSONObject().put("action", "list"));
        JSONArray rows = result.optJSONArray("items");
        List<Item> items = new ArrayList<>();
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) items.add(new Item(rows.getJSONObject(i)));
        }
        return items;
    }

    static AnalyticsDashboard analytics(String token) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token,
                new JSONObject().put("action", "analytics"));
        JSONObject analytics = result.optJSONObject("analytics");
        return new AnalyticsDashboard(analytics == null ? new JSONObject() : analytics);
    }

    static void update(String token, String id, String status, String reply) throws Exception {
        request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token, new JSONObject()
                .put("action", "update")
                .put("id", id)
                .put("status", status)
                .put("developer_reply", reply));
    }

    static JSONObject currentConfig(String token) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "current"));
        return result.optJSONObject("item");
    }

    static List<ConfigVersion> configHistory(String token) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "history"));
        JSONArray rows = result.optJSONArray("items");
        List<ConfigVersion> items = new ArrayList<>();
        if (rows != null) for (int index = 0; index < rows.length(); index++) {
            items.add(new ConfigVersion(rows.getJSONObject(index)));
        }
        return items;
    }

    static long validateConfig(String token, JSONObject config) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "validate").put("config", config));
        if (!result.optBoolean("valid")) throw new IllegalStateException("Configuration was rejected.");
        return result.optLong("configVersion");
    }

    static long publishConfig(String token, JSONObject config) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "publish").put("config", config));
        return result.optLong("configVersion");
    }

    static long rollbackConfig(String token, long version) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "rollback").put("configVersion", version));
        return result.optLong("configVersion");
    }

    private static List<AnalyticsRow> analyticsRows(JSONArray values) {
        List<AnalyticsRow> rows = new ArrayList<>();
        if (values == null) return rows;
        for (int index = 0; index < values.length(); index++) {
            JSONObject row = values.optJSONObject(index);
            if (row != null) rows.add(new AnalyticsRow(row));
        }
        return rows;
    }

    private static JSONObject request(String endpoint, String token, JSONObject body) throws Exception {
        if (token == null || token.trim().isEmpty()) throw new SecurityException("Admin token required.");
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalStateException("The admin endpoint is not configured.");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-admin-token", token.trim());
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String raw = read(stream);
            if (status < 200 || status >= 300) {
                String detail = raw;
                try { detail = new JSONObject(raw).optString("error", raw); } catch (Exception ignored) {}
                if (status == 401) throw new SecurityException("The admin token is not valid.");
                throw new IllegalStateException(detail.isEmpty() ? "Request failed." : detail);
            }
            return raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
        }
        return value.toString();
    }
}
