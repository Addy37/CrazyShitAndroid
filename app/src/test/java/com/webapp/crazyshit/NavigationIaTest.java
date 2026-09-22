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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
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
        assertEquals("Shows", nav.getMenu().findItem(2).getTitle());
        assertEquals("ShitTok", nav.getMenu().findItem(4).getTitle());
        assertEquals("OnlyFap", nav.getMenu().findItem(3).getTitle());
        assertEquals("More", nav.getMenu().findItem(5).getTitle());
        assertNull(findMenuItem(nav, "Library"));
        View modeButton = findByDescription(activity.getWindow().getDecorView(),
                "Show Trending OnlyFap creators");
        View badge = findByDescription(activity.getWindow().getDecorView(), "Creator list mode");
        assertNotNull(modeButton);
        assertNotNull(badge);
        View modeRow = (View) modeButton.getParent();
        View caption = (View) badge.getParent();
        android.widget.FrameLayout.LayoutParams modeParams =
                (android.widget.FrameLayout.LayoutParams) modeRow.getLayoutParams();
        android.widget.FrameLayout.LayoutParams captionParams =
                (android.widget.FrameLayout.LayoutParams) caption.getLayoutParams();
        Object[] pages = ReflectionHelpers.getField(adapter, "pages");
        Object onlyFap = pages[MainPagerAdapter.PAGE_ONLYFAP];
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh =
                ReflectionHelpers.getField(onlyFap, "refresh");
        android.widget.FrameLayout.LayoutParams refreshParams =
                (android.widget.FrameLayout.LayoutParams) refresh.getLayoutParams();
        assertTrue(captionParams.topMargin >= modeParams.topMargin + modeParams.height);
        assertEquals(0, refreshParams.topMargin);
        assertTrue(modeRow.getParent() instanceof FrostedOverlayLayout);
        FrostedOverlayLayout root = (FrostedOverlayLayout) modeRow.getParent();
        assertSame(modeRow, root.frostedOverlayForTest());
        androidx.recyclerview.widget.RecyclerView recycler =
                ReflectionHelpers.getField(onlyFap, "recycler");
        assertEquals(BrowseUi.dp(activity, 127), recycler.getPaddingTop());
        assertFalse(recycler.getClipToPadding());
        android.widget.TextView title = ReflectionHelpers.getField(activity, "headerTitle");
        assertEquals("OnlyFap", title.getText().toString());
        controller.pause().stop().destroy();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test public void shitTokKeepsLegacyPortraitViewportWhilePagerCanSwipeBehindNav() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).apply();
        Bundle state = new Bundle();
        state.putInt("primary_page", MainPagerAdapter.PAGE_CHAOS);
        ActivityController<NativeMainActivity> controller = Robolectric.buildActivity(NativeMainActivity.class)
                .create(state).start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        NativeMainActivity activity = controller.get();
        MainPagerAdapter adapter = ReflectionHelpers.getField(activity, "primaryPagerAdapter");
        ChaosFeedView chaosView = ReflectionHelpers.getField(adapter, "chaosView");

        assertTrue(chaosView.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams);
        android.widget.FrameLayout.LayoutParams chaosParams =
                (android.widget.FrameLayout.LayoutParams) chaosView.getLayoutParams();
        assertEquals(0, chaosParams.bottomMargin);

        androidx.recyclerview.widget.RecyclerView.Adapter chaosAdapter =
                ReflectionHelpers.getField(chaosView, "adapter");
        androidx.recyclerview.widget.RecyclerView parent =
                new androidx.recyclerview.widget.RecyclerView(activity);
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder =
                chaosAdapter.onCreateViewHolder(parent, 0);
        int expectedInset = activity.getResources().getDimensionPixelSize(R.dimen.zc_bottom_nav_height)
                + Math.round(8 * activity.getResources().getDisplayMetrics().density);
        assertEquals(expectedInset, holder.itemView.getPaddingBottom());

        Object[] pages = ReflectionHelpers.getField(adapter, "pages");
        View homeRoot = ReflectionHelpers.getField(pages[MainPagerAdapter.PAGE_HOME], "root");
        assertTrue(homeRoot.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams);
        android.widget.FrameLayout.LayoutParams homeParams =
                (android.widget.FrameLayout.LayoutParams) homeRoot.getLayoutParams();
        assertEquals(0, homeParams.bottomMargin);

        controller.pause().stop().destroy();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test public void shitTokOffersManualFullscreenOnlyForHorizontalVideo() {
        assertTrue(ChaosFeedView.shouldOfferLandscapeFullscreen(16f / 9f));
        assertTrue(ChaosFeedView.shouldOfferLandscapeFullscreen(4f / 3f));
        assertFalse(ChaosFeedView.shouldOfferLandscapeFullscreen(1f));
        assertFalse(ChaosFeedView.shouldOfferLandscapeFullscreen(9f / 16f));

        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).apply();
        Bundle state = new Bundle();
        state.putInt("primary_page", MainPagerAdapter.PAGE_CHAOS);
        ActivityController<NativeMainActivity> controller = Robolectric.buildActivity(NativeMainActivity.class)
                .create(state).start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        NativeMainActivity activity = controller.get();
        MainPagerAdapter adapter = ReflectionHelpers.getField(activity, "primaryPagerAdapter");
        ChaosFeedView chaosView = ReflectionHelpers.getField(adapter, "chaosView");
        androidx.recyclerview.widget.RecyclerView.Adapter chaosAdapter =
                ReflectionHelpers.getField(chaosView, "adapter");
        androidx.recyclerview.widget.RecyclerView parent =
                new androidx.recyclerview.widget.RecyclerView(activity);
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder =
                chaosAdapter.onCreateViewHolder(parent, 0);
        View fullscreen = findByDescription(holder.itemView, "Watch horizontal video fullscreen");
        assertNotNull(fullscreen);
        assertEquals(View.GONE, fullscreen.getVisibility());
        controller.pause().stop().destroy();
    }

    @Test public void landscapeControllerRestoresCachedPortraitTopInset() {
        assertEquals(42, LandscapeUiController.resolveShellTopInset(false, 0, 42));
        assertEquals(36, LandscapeUiController.resolveShellTopInset(false, 36, 42));
        assertEquals(0, LandscapeUiController.resolveShellTopInset(true, 0, 42));
    }

    @Test public void libraryHubActionsOpenExistingActivities() {
        ActivityController<LibraryHubActivity> controller =
                Robolectric.buildActivity(LibraryHubActivity.class).setup();
        LibraryHubActivity activity = controller.get();
        android.widget.FrameLayout content = activity.findViewById(android.R.id.content);
        android.view.ViewGroup root = (android.view.ViewGroup) content.getChildAt(0);
        View shell = root.getChildAt(0);
        androidx.core.view.WindowInsetsCompat safeInsets =
                new androidx.core.view.WindowInsetsCompat.Builder()
                        .setInsets(
                                androidx.core.view.WindowInsetsCompat.Type.systemBars()
                                        | androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
                                androidx.core.graphics.Insets.of(3, 24, 5, 12)
                        )
                        .build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(shell, safeInsets);
        assertEquals(3, shell.getPaddingLeft());
        assertEquals(24, shell.getPaddingTop());
        assertEquals(5, shell.getPaddingRight());
        assertEquals(12, shell.getPaddingBottom());
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
        View crazyShit = findByDescription(activity.getWindow().getDecorView(),
                "Show CrazyShit collections");
        assertNotNull(crazyShit);
        android.view.ViewGroup selector = (android.view.ViewGroup) crazyShit.getParent();
        assertFalse(selector.getClipChildren());
        assertFalse(selector.getClipToPadding());
        assertTrue(selector.getParent() instanceof FrostedOverlayLayout);
        FrostedOverlayLayout root = (FrostedOverlayLayout) selector.getParent();
        assertSame(selector, root.frostedOverlayForTest());
        MainPagerAdapter adapter = ReflectionHelpers.getField(activity, "primaryPagerAdapter");
        Object[] pages = ReflectionHelpers.getField(adapter, "pages");
        Object shows = pages[MainPagerAdapter.PAGE_SERIES];
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh =
                ReflectionHelpers.getField(shows, "refresh");
        androidx.recyclerview.widget.RecyclerView recycler =
                ReflectionHelpers.getField(shows, "recycler");
        assertEquals(0, ((android.widget.FrameLayout.LayoutParams)
                refresh.getLayoutParams()).topMargin);
        assertEquals(BrowseUi.dp(activity, 61), recycler.getPaddingTop());
        assertFalse(recycler.getClipToPadding());
        assertNull(findByDescription(activity.getWindow().getDecorView(), "My profile"));
        android.widget.TextView title = ReflectionHelpers.getField(activity, "headerTitle");
        assertEquals("Shows", title.getText().toString());
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
