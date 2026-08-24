package com.webapp.crazyshit;

import android.app.Activity;
import android.os.Build;
import android.transition.Fade;
import android.view.Window;

/**
 * v2.7 keeps the window transition quiet so VideoDetailImmersivePolish can make the player
 * itself feel like it expands into place instead of sliding an entire screen over the feed.
 */
final class VideoDetailTransitionPolish {
    private VideoDetailTransitionPolish() {
    }

    static void apply(Activity activity) {
        if (activity == null || activity.isFinishing() || Build.VERSION.SDK_INT < 21) return;
        Window window = activity.getWindow();
        if (window == null) return;

        window.setAllowEnterTransitionOverlap(true);
        window.setAllowReturnTransitionOverlap(true);
        window.setEnterTransition(new Fade(Fade.IN).setDuration(120L));
        window.setReturnTransition(new Fade(Fade.OUT).setDuration(130L));
        window.setExitTransition(new Fade(Fade.OUT).setDuration(110L));
        window.setReenterTransition(new Fade(Fade.IN).setDuration(150L));
    }
}
