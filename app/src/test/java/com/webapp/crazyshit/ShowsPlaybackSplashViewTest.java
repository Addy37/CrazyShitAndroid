package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShowsPlaybackSplashViewTest {
    @Test
    public void playbackIdentMatchesSplashMascotSizeAndHasNoWordmarkText() {
        Context context = RuntimeEnvironment.getApplication();
        ShowsPlaybackSplashView view = new ShowsPlaybackSplashView(context);

        assertEquals(188, ShowsPlaybackSplashView.MASCOT_SIZE_DP);
        assertEquals(7, view.getChildCount());
        assertFalse(containsTextView(view));
    }

    @Test
    public void firstMascotRevealCompletesBeforeLoopRepeats() {
        assertTrue(
                ShowsPlaybackSplashView.FIRST_REVEAL_COMPLETE_MS
                        < ShowsPlaybackSplashView.LOOP_MS
        );
    }

    private boolean containsTextView(View view) {
        if (view instanceof TextView) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsTextView(group.getChildAt(i))) return true;
        }
        return false;
    }
}
