package com.webapp.crazyshit;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps native video chrome out of the way until the user deliberately taps the player.
 * The custom Back / menu buttons follow Media3 controller visibility so they no longer sit
 * permanently over the video.
 */
final class VideoDetailControllerPolish {
    private static final int CONTROL_TIMEOUT_MS = 2600;

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

        View back = findByDescription(content, "Back");
        View menu = findByDescription(content, "Video menu");
        setChromeVisible(back, menu, false, false);

        List<PlayerView> players = new ArrayList<>();
        collect(content, players);
        for (PlayerView playerView : players) {
            try {
                playerView.setUseController(true);
                playerView.setControllerAutoShow(false);
                playerView.setControllerHideOnTouch(true);
                playerView.setControllerShowTimeoutMs(CONTROL_TIMEOUT_MS);
                playerView.setControllerVisibilityListener(visibility ->
                        setChromeVisible(back, menu, visibility == View.VISIBLE, true));
                playerView.hideController();
            } catch (Exception ignored) {
            }
        }
    }

    private static void setChromeVisible(View back, View menu, boolean visible, boolean animate) {
        setOne(back, visible, animate);
        setOne(menu, visible, animate);
    }

    private static void setOne(View view, boolean visible, boolean animate) {
        if (view == null) return;
        view.animate().cancel();
        if (visible) {
            view.setVisibility(View.VISIBLE);
            if (animate) {
                view.setAlpha(0f);
                view.animate().alpha(1f).setDuration(120L).start();
            } else {
                view.setAlpha(1f);
            }
            return;
        }

        if (!animate) {
            view.setAlpha(0f);
            view.setVisibility(View.GONE);
            return;
        }
        view.animate()
                .alpha(0f)
                .setDuration(140L)
                .withEndAction(() -> view.setVisibility(View.GONE))
                .start();
    }

    private static View findByDescription(View view, String description) {
        if (view == null) return null;
        CharSequence value = view.getContentDescription();
        if (value != null && description.contentEquals(value)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findByDescription(group.getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
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
