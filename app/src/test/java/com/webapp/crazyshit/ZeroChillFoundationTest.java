package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ZeroChillFoundationTest {
    @Test public void tokensKeepOledBackgroundAndReadableGlassColors() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(Color.BLACK, ZeroChillUi.color(activity, R.color.zc_background));
        assertTrue(Color.alpha(ZeroChillUi.color(activity, R.color.zc_surface_glass)) < 255);
        assertTrue(Color.alpha(ZeroChillUi.color(activity, R.color.zc_surface_glass)) > 180);
        assertEquals(48, Math.round(activity.getResources()
                .getDimension(R.dimen.zc_touch_target) / activity.getResources()
                .getDisplayMetrics().density));
        Drawable navigation = ZeroChillUi.navigationGlass(activity);
        Drawable panel = ZeroChillUi.panelGlass(activity);
        assertNotNull(navigation);
        assertTrue(navigation instanceof LayerDrawable);
        assertTrue(((LayerDrawable) navigation).getNumberOfLayers() >= 4);
        assertNotNull(panel);
        activity.finish();
    }

    @Test public void sharedComponentsUseCyanAndAccessibleTextStates() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView chip = new TextView(activity);
        ZeroChillUi.styleChip(chip, true);
        assertTrue(chip.isSelected());
        assertEquals(activity.getColor(R.color.zc_cyan), chip.getCurrentTextColor());

        ZeroChillUi.styleChip(chip, false);
        assertEquals(activity.getColor(R.color.zc_text_secondary), chip.getCurrentTextColor());

        ProgressBar progress = new ProgressBar(activity);
        ZeroChillUi.styleProgress(progress);
        assertEquals(activity.getColor(R.color.zc_cyan),
                progress.getIndeterminateTintList().getDefaultColor());

        MaterialCardView card = new MaterialCardView(activity);
        ZeroChillUi.styleMaterialCard(card, R.dimen.zc_radius_medium);
        assertEquals(activity.getColor(R.color.zc_surface_glass),
                card.getCardBackgroundColor().getDefaultColor());
        assertEquals(activity.getColor(R.color.zc_edge), card.getStrokeColor());
        activity.finish();
    }

    @Test public void motionHelpersSetDeterministicEndState() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View view = new View(activity);
        ZeroChillMotion.animateSelection(view, false);
        assertEquals(1f, view.getScaleX(), 0.05f);
        assertEquals(1f, view.getScaleY(), 0.05f);
        ZeroChillMotion.installPressFeedback(view);
        ZeroChillMotion.installPressFeedback(view);
        assertEquals(Boolean.TRUE, view.getTag(R.id.zerochill_motion_installed));
        activity.finish();
    }

    @Test public void reducedMotionSkipsSelectionAnimationAndAppliesFinalState() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View view = new View(activity);
        activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE).edit()
                .putBoolean("immersive_motion_enabled", false)
                .apply();
        assertFalse(ZeroChillMotion.animationsEnabled(activity));
        ZeroChillMotion.animateSelection(view, true);
        assertEquals(1f, view.getScaleX(), 0.001f);
        assertEquals(1f, view.getScaleY(), 0.001f);
        activity.finish();
    }
}
