package com.webapp.crazyshit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/** Publishes the four launcher actions and keeps their routing in one place. */
final class AppShortcuts {
    static final String ACTION_CHAOS = "com.webapp.crazyshit.action.OPEN_CHAOS";
    static final String ACTION_CONTINUE = "com.webapp.crazyshit.action.OPEN_CONTINUE";
    static final String ACTION_SEARCH = "com.webapp.crazyshit.action.OPEN_SEARCH";
    static final String ACTION_WATCH_LATER = "com.webapp.crazyshit.action.OPEN_WATCH_LATER";
    static final String EXTRA_SHORTCUT_ROUTED = "launcher_shortcut_routed";

    private static final String ID_CHAOS = "chaos";
    private static final String ID_CONTINUE = "continue";
    private static final String ID_SEARCH = "search";
    private static final String ID_WATCH_LATER = "watch_later";
    private static final String PREFS = "launcher_shortcuts";
    private static final String KEY_PUBLISHED_VERSION = "published_version";

    private AppShortcuts() {
    }

    static void publish(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 25) return;
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_PUBLISHED_VERSION, -1L) == BuildConfig.VERSION_CODE) return;
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return;

        ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();
        shortcuts.add(shortcut(
                context,
                ID_CHAOS,
                "Chaos",
                "Open Chaos",
                R.drawable.ic_nav_chaos,
                ACTION_CHAOS,
                0
        ));
        shortcuts.add(shortcut(
                context,
                ID_CONTINUE,
                "Continue",
                "Continue watching",
                R.drawable.ic_more_library,
                ACTION_CONTINUE,
                1
        ));
        shortcuts.add(shortcut(
                context,
                ID_SEARCH,
                "Search",
                "Search videos",
                R.drawable.ic_nav_search,
                ACTION_SEARCH,
                2
        ));
        shortcuts.add(shortcut(
                context,
                ID_WATCH_LATER,
                "Watch Later",
                "Open Watch Later",
                R.drawable.ic_nav_saved,
                ACTION_WATCH_LATER,
                3
        ));

        int limit = manager.getMaxShortcutCountPerActivity();
        List<ShortcutInfo> published = shortcuts;
        if (limit > 0 && shortcuts.size() > limit) {
            published = shortcuts.subList(0, limit);
        }
        try {
            manager.setDynamicShortcuts(published);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_PUBLISHED_VERSION, BuildConfig.VERSION_CODE)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    static boolean isShortcutAction(String action) {
        return ACTION_CHAOS.equals(action)
                || ACTION_CONTINUE.equals(action)
                || ACTION_SEARCH.equals(action)
                || ACTION_WATCH_LATER.equals(action);
    }

    static boolean isChaosAction(String action) {
        return ACTION_CHAOS.equals(action);
    }

    static void reportUsed(Context context, String action) {
        if (context == null || Build.VERSION.SDK_INT < 25) return;
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return;
        String id = idForAction(action);
        if (id == null) return;
        try {
            manager.reportShortcutUsed(id);
        } catch (Exception ignored) {
        }
    }

    private static ShortcutInfo shortcut(
            Context context,
            String id,
            String shortLabel,
            String longLabel,
            int iconRes,
            String action,
            int rank
    ) {
        Intent intent = new Intent(context, SplashActivity.class)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return new ShortcutInfo.Builder(context, id)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(Icon.createWithResource(context, iconRes))
                .setIntent(intent)
                .setRank(rank)
                .build();
    }

    private static String idForAction(String action) {
        if (ACTION_CHAOS.equals(action)) return ID_CHAOS;
        if (ACTION_CONTINUE.equals(action)) return ID_CONTINUE;
        if (ACTION_SEARCH.equals(action)) return ID_SEARCH;
        if (ACTION_WATCH_LATER.equals(action)) return ID_WATCH_LATER;
        return null;
    }
}
