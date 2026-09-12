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

    private AdminRepository() {}

    static List<Item> list(String token) throws Exception {
        JSONObject result = request(token, new JSONObject().put("action", "list"));
        JSONArray rows = result.optJSONArray("items");
        List<Item> items = new ArrayList<>();
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) items.add(new Item(rows.getJSONObject(i)));
        }
        return items;
    }

    static void update(String token, String id, String status, String reply) throws Exception {
        request(token, new JSONObject()
                .put("action", "update")
                .put("id", id)
                .put("status", status)
                .put("developer_reply", reply));
    }

    private static JSONObject request(String token, JSONObject body) throws Exception {
        if (token == null || token.trim().isEmpty()) throw new SecurityException("Admin token required.");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BuildConfig.ADMIN_FEEDBACK_ENDPOINT).openConnection();
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
