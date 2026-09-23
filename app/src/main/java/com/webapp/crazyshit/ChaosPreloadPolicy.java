package com.webapp.crazyshit;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Controls video and comment look-ahead without changing the Chaos source mix. */
final class ChaosPreloadPolicy {
    static final String KEY_MODE = "chaos_preload_mode";
    static final String MODE_FULL = "full";
    static final String MODE_UNMETERED = "unmetered";
    static final String MODE_MINIMAL = "minimal";

    private static final String PREFS = "app_prefs";

    private ChaosPreloadPolicy() {
    }

    static String getMode(Context context) {
        String mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, MODE_FULL);
        if (MODE_UNMETERED.equals(mode) || MODE_MINIMAL.equals(mode)) return mode;
        return MODE_FULL;
    }

    static void setMode(Context context, String mode) {
        String safeMode = MODE_UNMETERED.equals(mode) || MODE_MINIMAL.equals(mode)
                ? mode
                : MODE_FULL;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, safeMode)
                .apply();
    }

    static int selectedIndex(Context context) {
        String mode = getMode(context);
        if (MODE_UNMETERED.equals(mode)) return 1;
        if (MODE_MINIMAL.equals(mode)) return 2;
        return 0;
    }

    static String modeForIndex(int index) {
        if (index == 1) return MODE_UNMETERED;
        if (index == 2) return MODE_MINIMAL;
        return MODE_FULL;
    }

    static String summary(Context context) {
        String mode = getMode(context);
        if (MODE_UNMETERED.equals(mode)) {
            return "Wi-Fi / unmetered only. Uses Minimal behavior on metered connections.";
        }
        if (MODE_MINIMAL.equals(mode)) {
            return "Minimal. Prepares only the current ShitTok video and loads comments on demand.";
        }
        return "Full. Preloads nearby ShitTok videos and comments for faster swipes.";
    }

    static boolean allowsLookAhead(Context context) {
        String mode = getMode(context);
        if (MODE_MINIMAL.equals(mode)) return false;
        if (MODE_FULL.equals(mode)) return true;
        return isActiveNetworkUnmetered(context);
    }

    static int aheadCount(Context context) {
        return allowsLookAhead(context) ? 3 : 0;
    }

    static boolean allowsCommentPreload(Context context) {
        return allowsLookAhead(context);
    }

    private static boolean isActiveNetworkUnmetered(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(
                    Context.CONNECTIVITY_SERVICE
            );
            if (manager == null) return false;
            Network network = manager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED
            );
        } catch (Exception ignored) {
            return false;
        }
    }
}
