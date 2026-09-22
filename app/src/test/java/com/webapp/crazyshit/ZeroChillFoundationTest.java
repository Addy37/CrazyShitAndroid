package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
        assertEquals(Color.rgb(8, 146, 208), ZeroChillUi.color(activity, R.color.zc_cyan));
        assertEquals(ZeroChillUi.color(activity, R.color.zc_cyan), UiPalette.PRIMARY);
        assertTrue(Color.alpha(ZeroChillUi.color(activity, R.color.zc_surface_glass)) < 255);
        assertTrue(Color.alpha(ZeroChillUi.color(activity, R.color.zc_surface_glass)) > 180);
        assertEquals(48, Math.round(activity.getResources()
                .getDimension(R.dimen.zc_touch_target) / activity.getResources()
                .getDisplayMetrics().density));
        Drawable navigation = ZeroChillUi.navigationGlass(activity);
        Drawable panel = ZeroChillUi.panelGlass(activity);
        assertNotNull(navigation);
        assertTrue(navigation instanceof LayerDrawable);
        assertEquals(3, ((LayerDrawable) navigation).getNumberOfLayers());
        Drawable sourceRail = ZeroChillUi.sourceRailGlass(activity);
        assertNotNull(sourceRail);
        assertTrue(sourceRail instanceof LayerDrawable);
        assertEquals(3, ((LayerDrawable) sourceRail).getNumberOfLayers());
        Drawable outerNavigationGlass = ((LayerDrawable) navigation).getDrawable(0);
        assertTrue(outerNavigationGlass instanceof GradientDrawable);
        int[] navigationColors = ((GradientDrawable) outerNavigationGlass).getColors();
        assertNotNull(navigationColors);
        for (int navigationColor : navigationColors) {
            assertTrue(Color.alpha(navigationColor) <= 0x78);
        }
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

        TextView sourceChip = new TextView(activity);
        ZeroChillUi.styleSourceRailChip(sourceChip, true);
        assertTrue(sourceChip.isSelected());
        assertEquals(activity.getColor(R.color.zc_cyan), sourceChip.getCurrentTextColor());
        assertNotNull(sourceChip.getBackground());

        Drawable selectedNavigation = activity.getDrawable(R.drawable.zc_nav_selected_glass);
        Drawable shitTokRail = activity.getDrawable(R.drawable.zc_shittok_control_rail);
        assertTrue(selectedNavigation instanceof LayerDrawable);
        assertTrue(shitTokRail instanceof LayerDrawable);
        assertEquals(3, ((LayerDrawable) selectedNavigation).getNumberOfLayers());
        assertEquals(3, ((LayerDrawable) shitTokRail).getNumberOfLayers());

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

    @Test public void reactiveNavigationTracksContinuousPagerPosition() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        ZeroChillBottomNavigationView nav = new ZeroChillBottomNavigationView(activity);
        nav.getMenu().add(0, 1, 0, "Home").setIcon(R.drawable.ic_nav_home);
        nav.getMenu().add(0, 2, 1, "Shows").setIcon(R.drawable.ic_nav_series);
        nav.getMenu().add(0, 4, 2, "ShitTok").setIcon(R.drawable.ic_nav_chaos);
        nav.getMenu().add(0, 3, 3, "OnlyFap").setIcon(R.drawable.ic_nav_onlyfap);
        nav.getMenu().add(0, 5, 4, "More").setIcon(R.drawable.ic_nav_more);
        nav.setSettledPage(1);
        nav.setPagerProgress(1, 0.5f);
        assertEquals(1.5f, nav.pagerPositionForTest(), 0.001f);

        ZeroChillSegmentedRail rail = new ZeroChillSegmentedRail(activity);
        rail.addView(new TextView(activity));
        rail.addView(new TextView(activity));
        rail.addView(new TextView(activity));
        rail.setSelectedIndex(2, false);
        assertEquals(2, rail.selectedIndexForTest());
        assertTrue(nav.gpuReflectionSupportedForTest());
        assertTrue(rail.gpuReflectionSupportedForTest());
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
        activity.setTheme(R.style.Theme_CrazyShit);
        ZeroChillBottomNavigationView nav = new ZeroChillBottomNavigationView(activity);
        nav.setSettledPage(1);
        nav.setPagerProgress(1, 0.5f);
        assertEquals(1f, nav.pagerPositionForTest(), 0.001f);
        assertEquals(1f, view.getScaleX(), 0.001f);
        assertEquals(1f, view.getScaleY(), 0.001f);
        activity.finish();
    }
}
