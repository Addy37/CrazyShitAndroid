package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class NavigationIaTest {
    @Test public void restoredSlotThreeIsOnlyFapWithStablePublicNavigation() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).apply();
        Bundle state = new Bundle();
        state.putInt("primary_page", 3);
        ActivityController<NativeMainActivity> controller = Robolectric.buildActivity(NativeMainActivity.class)
                .create(state).start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        NativeMainActivity activity = controller.get();
        ViewPager2 pager = ReflectionHelpers.getField(activity, "primaryPager");
        BottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        MainPagerAdapter adapter = ReflectionHelpers.getField(activity, "primaryPagerAdapter");
        assertEquals(4, adapter.getItemCount());
        assertEquals(MainPagerAdapter.PAGE_ONLYFAP, pager.getCurrentItem());
        assertEquals("Home", nav.getMenu().findItem(1).getTitle());
        assertEquals("Collections", nav.getMenu().findItem(2).getTitle());
        assertEquals("ShitTok", nav.getMenu().findItem(4).getTitle());
        assertEquals("OnlyFap", nav.getMenu().findItem(3).getTitle());
        assertEquals("More", nav.getMenu().findItem(5).getTitle());
        assertNull(findMenuItem(nav, "Library"));
        assertNotNull(findByDescription(activity.getWindow().getDecorView(),
                "Show Top 50 OnlyFap creators"));
        android.widget.TextView title = ReflectionHelpers.getField(activity, "headerTitle");
        assertEquals("OnlyFap", title.getText().toString());
        controller.pause().stop().destroy();
    }

    @Test public void libraryHubActionsOpenExistingActivities() {
        ActivityController<LibraryHubActivity> controller =
                Robolectric.buildActivity(LibraryHubActivity.class).setup();
        LibraryHubActivity activity = controller.get();
        View libraryAction = findByDescription(activity.getWindow().getDecorView(), "History");
        assertNotNull(libraryAction);
        libraryAction.performClick();
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertEquals(FavoritesActivity.class.getName(), started.getComponent().getClassName());
        assertEquals(FavoritesActivity.START_HISTORY,
                started.getIntExtra(FavoritesActivity.EXTRA_START_TAB, -1));
        controller.pause().stop().destroy();
    }

    @Test public void collectionsKeepsCategoriesAsPersistedFourthSource() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).apply();
        Bundle state = new Bundle();
        state.putInt("primary_page", MainPagerAdapter.PAGE_SERIES);
        ActivityController<NativeMainActivity> controller = Robolectric.buildActivity(NativeMainActivity.class)
                .create(state).start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        NativeMainActivity activity = controller.get();
        View categories = findByDescription(activity.getWindow().getDecorView(),
                "Show Categories collections");
        assertNotNull(categories);
        categories.performClick();
        assertEquals(3, context.getSharedPreferences("app_prefs", 0)
                .getInt("native_series_source", -1));
        controller.pause().stop().destroy();
    }

    @Test public void collectionsRemovesOnlyFapAndMigratesItsOldSelectionToCrazyShit() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true)
                .putInt("native_series_source", 2)
                .apply();
        Bundle state = new Bundle();
        state.putInt("primary_page", MainPagerAdapter.PAGE_SERIES);
        ActivityController<NativeMainActivity> controller = Robolectric.buildActivity(NativeMainActivity.class)
                .create(state).start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        NativeMainActivity activity = controller.get();
        assertEquals(0, context.getSharedPreferences("app_prefs", 0)
                .getInt("native_series_source", -1));
        assertNull(findByDescription(activity.getWindow().getDecorView(),
                "Show OnlyFap collections"));
        assertNotNull(findByDescription(activity.getWindow().getDecorView(),
                "Show CrazyShit collections"));
        assertNull(findByDescription(activity.getWindow().getDecorView(), "My profile"));
        android.widget.TextView title = ReflectionHelpers.getField(activity, "headerTitle");
        assertEquals("Collections", title.getText().toString());
        controller.pause().stop().destroy();
    }

    private static View findByDescription(View view, String description) {
        if (view == null) return null;
        CharSequence contentDescription = view.getContentDescription();
        if (contentDescription != null && description.contentEquals(contentDescription)) return view;
        if (!(view instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View match = findByDescription(group.getChildAt(i), description);
            if (match != null) return match;
        }
        return null;
    }

    private static android.view.MenuItem findMenuItem(BottomNavigationView nav, String title) {
        for (int index = 0; index < nav.getMenu().size(); index++) {
            android.view.MenuItem item = nav.getMenu().getItem(index);
            if (title.contentEquals(item.getTitle())) return item;
        }
        return null;
    }
}
