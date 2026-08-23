package com.webapp.crazyshit;

import android.app.Activity;
import android.os.Build;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.Window;

/** Short premium-feeling window motion for native video detail screens. */
final class VideoDetailTransitionPolish {
    private VideoDetailTransitionPolish() {
    }

    static void apply(Activity activity) {
        if (activity == null || activity.isFinishing() || Build.VERSION.SDK_INT < 21) return;
        Window window = activity.getWindow();
        if (window == null) return;

        TransitionSet enter = new TransitionSet();
        enter.setOrdering(TransitionSet.ORDERING_TOGETHER);
        enter.addTransition(new Fade(Fade.IN));
        enter.addTransition(new Slide(Gravity.BOTTOM));
        enter.setDuration(220L);

        TransitionSet exit = new TransitionSet();
        exit.setOrdering(TransitionSet.ORDERING_TOGETHER);
        exit.addTransition(new Fade(Fade.OUT));
        exit.addTransition(new Slide(Gravity.BOTTOM));
        exit.setDuration(180L);

        window.setEnterTransition(enter);
        window.setReturnTransition(exit);
        window.setExitTransition(new Fade(Fade.OUT).setDuration(150L));
        window.setReenterTransition(new Fade(Fade.IN).setDuration(180L));
    }
}
