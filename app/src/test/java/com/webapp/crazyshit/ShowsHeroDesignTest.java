package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ShowsHeroDesignTest {
    @Test
    public void usesFullBleedCinematicHeroGeometry() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShowsHubView hub = new ShowsHubView(activity, item -> { }, item -> { }, item -> { });
        activity.setContentView(hub);

        ScrollView scroll = (ScrollView) hub.getChildAt(0);
        LinearLayout content = (LinearLayout) scroll.getChildAt(0);
        MaterialCardView hero = (MaterialCardView) content.getChildAt(0);

        int expectedHeight = Math.round(350f * activity.getResources()
                .getDisplayMetrics().density);
        assertEquals(expectedHeight, hero.getLayoutParams().height);
        assertEquals(0f, hero.getRadius(), 0.01f);
        assertEquals(0, content.getPaddingLeft());
        assertEquals(0, content.getPaddingRight());
        assertTrue(hasText(hero, "Shows"));
    }

    private static boolean hasText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (!(view instanceof android.view.ViewGroup)) return false;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (hasText(group.getChildAt(index), value)) return true;
        }
        return false;
    }
}
