package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AppBackupController {
    private static final int EXPORT = 8301, IMPORT = 8302;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final Activity activity;
    AppBackupController(Activity activity) { this.activity = activity; }

    void exportFile() {
        Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "CrazyShit-backup-"
                        + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date()) + ".json");
        try { activity.startActivityForResult(picker, EXPORT); }
        catch (Exception unavailable) { toast("No file picker is available."); }
    }

    void importFile() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        try { activity.startActivityForResult(picker, IMPORT); }
        catch (Exception unavailable) { toast("No file picker is available."); }
    }

    boolean onResult(int request, int result, Intent data) {
        if (request != EXPORT && request != IMPORT) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri uri = data.getData();
        IO.execute(() -> {
            try {
                if (request == EXPORT) {
                    byte[] bytes = AppBackupStore.export(activity.getApplicationContext()).toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new java.io.IOException("Couldn't open the destination.");
                        output.write(bytes); output.flush();
                    }
                    toast("Backup saved.");
                } else {
                    JSONObject backup;
                    try (InputStream input = activity.getContentResolver().openInputStream(uri)) { backup = BackupDocument.read(input); }
                    activity.runOnUiThread(() -> confirmRestore(backup));
                }
            } catch (Exception error) { toast("Backup failed: " + message(error)); }
        });
        return true;
    }

    private void confirmRestore(JSONObject backup) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String summary = backup.optJSONArray("creators").length() + " creators and "
                + backup.optJSONArray("watchLater").length() + " Watch Later items.\n\n"
                + "Add these to your saved lists and apply the backed-up settings?";
        new AlertDialog.Builder(activity).setTitle("Restore backup").setMessage(summary)
                .setNegativeButton("Cancel", null).setPositiveButton("Restore", (dialog, which) -> IO.execute(() -> {
                    try {
                        AppBackupStore.restore(activity.getApplicationContext(), backup);
                        NotificationCoordinator.onPreferencesChanged(activity.getApplicationContext());
                        toast("Backup restored.");
                        activity.runOnUiThread(() -> { if (!activity.isFinishing() && !activity.isDestroyed()) activity.recreate(); });
                    } catch (Exception error) { toast("Couldn't restore backup: " + message(error)); }
                })).show();
    }

    private String message(Exception error) { return error.getMessage() == null ? "Invalid backup file." : error.getMessage(); }
    private void toast(String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }
}
