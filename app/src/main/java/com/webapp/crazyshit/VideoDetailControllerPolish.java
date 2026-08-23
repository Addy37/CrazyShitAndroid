package com.webapp.crazyshit;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Prevents the standard Media3 controller from popping up automatically on native video pages.
 * Controls remain available when the user deliberately taps the player.
 */
final class VideoDetailControllerPolish {
    private VideoDetailControllerPolish() {
    }

    static void applySoon(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> apply(activity));
        decor.postDelayed(() -> apply(activity), 180L);
        decor.postDelayed(() -> apply(activity), 650L);
    }

    private static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        List<PlayerView> players = new ArrayList<>();
        collect(content, players);
        for (PlayerView playerView : players) {
            try {
                playerView.setUseController(true);
                playerView.setControllerAutoShow(false);
                playerView.setControllerHideOnTouch(true);
                playerView.hideController();
            } catch (Exception ignored) {
            }
        }
    }

    private static void collect(View view, List<PlayerView> out) {
        if (view instanceof PlayerView) out.add((PlayerView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collect(group.getChildAt(i), out);
        }
    }
}
