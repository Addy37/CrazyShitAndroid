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
    @Test public void showsDetailsIntentCarriesRealCollectionPresentationData() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).apply();
        ActivityController<NativeMainActivity> controller =
                Robolectric.buildActivity(NativeMainActivity.class)
                        .create().start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();

        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_SERIES,
                "Test Show",
                "https://crazyshit.com/series/test-show/",
                "https://cdn.example.com/show.jpg",
                "",
                "",
                "",
                "Real source description"
        );
        Intent intent = NativeFeedBrowserActivity.createShowDetails(
                controller.get(),
                item,
                NativeFeedBrowserActivity.SOURCE_CRAZYSHIT
        );

        assertEquals(NativeFeedBrowserActivity.class.getName(),
                intent.getComponent().getClassName());
        assertTrue(intent.getBooleanExtra(NativeFeedBrowserActivity.EXTRA_SHOW_DETAILS, false));
        assertEquals(item.title,
                intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_TITLE));
        assertEquals(item.url,
                intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_BASE_URL));
        assertEquals(item.imageUrl,
                intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_SHOW_IMAGE_URL));
        assertEquals(item.description,
                intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_SHOW_DESCRIPTION));
        assertEquals(item.kind,
                intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_SHOW_KIND));
        assertEquals(NativeFeedBrowserActivity.SOURCE_CRAZYSHIT,
                intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_SOURCE));

        controller.pause().stop().destroy();
    }

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
        assertEquals(
                MainPagerAdapter.PAGE_ONLYFAP,
                MainPagerAdapter.pageForPagerPosition(pager.getCurrentItem())
        );
        assertNull(nav.getMenu().findItem(1));
        assertEquals("Shows", nav.getMenu().findItem(2).getTitle());
        assertEquals("ShitTok", nav.getMenu().findItem(4).getTitle());
        assertEquals("OnlyFap", nav.getMenu().findItem(3).getTitle());
        assertEquals("Library", nav.getMenu().findItem(6).getTitle());
        assertNull(findMenuItem(nav, "Home"));
        assertNull(findMenuItem(nav, "More"));
        assertNotNull(findByDescription(activity.getWindow().getDecorView(), "More"));
        View search = findByDescription(activity.getWindow().getDecorView(),
                "Search OnlyFap creators");
        View favorites = findByDescription(activity.getWindow().getDecorView(),
                "Open favorite creators");
        View modeButton = findByDescription(activity.getWindow().getDecorView(),
                "Show Trending OnlyFap creators");
        View badge = findByDescription(activity.getWindow().getDecorView(), "Creator list mode");
        assertNotNull(search);
        assertNotNull(favorites);
        assertNotNull(modeButton);
        assertNotNull(badge);

        Object[] pages = ReflectionHelpers.getField(adapter, "pages");
        Object onlyFap = pages[MainPagerAdapter.PAGE_ONLYFAP];
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh =
                ReflectionHelpers.getField(onlyFap, "refresh");
        android.widget.FrameLayout.LayoutParams refreshParams =
                (android.widget.FrameLayout.LayoutParams) refresh.getLayoutParams();
        assertEquals(0, refreshParams.topMargin);

        androidx.recyclerview.widget.RecyclerView recycler =
                ReflectionHelpers.getField(onlyFap, "recycler");
        assertTrue(recycler.getAdapter() instanceof androidx.recyclerview.widget.ConcatAdapter);
        assertTrue(recycler.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager);
        androidx.recyclerview.widget.GridLayoutManager grid =
                (androidx.recyclerview.widget.GridLayoutManager) recycler.getLayoutManager();
        assertEquals(grid.getSpanCount(), grid.getSpanSizeLookup().getSpanSize(0));
        assertEquals(0, recycler.getPaddingTop());
        assertFalse(recycler.getClipToPadding());

        search.performClick();
        Intent searchIntent = shadowOf(activity).getNextStartedActivity();
        assertEquals(SearchActivity.class.getName(), searchIntent.getComponent().getClassName());

        android.widget.TextView title = ReflectionHelpers.getField(activity, "headerTitle");
        assertEquals("OnlyFap", title.getText().toString());
        controller.pause().stop().destroy();
    }

    @Test public void primaryTabsDragFromSelectedBottomCapsule() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).apply();

        ActivityController<NativeMainActivity> controller =
                Robolectric.buildActivity(NativeMainActivity.class)
                        .create().start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();

        NativeMainActivity activity = controller.get();
        ViewPager2 pager = ReflectionHelpers.getField(activity, "primaryPager");
        ZeroChillBottomNavigationView nav =
                ReflectionHelpers.getField(activity, "bottomNavigation");

        assertFalse(pager.isUserInputEnabled());
        assertEquals(
                MainPagerAdapter.PAGE_CHAOS,
                MainPagerAdapter.pageForPagerPosition(pager.getCurrentItem())
        );

        View chaos = nav.findViewById(4);
        View onlyFap = nav.findViewById(3);
        assertNotNull(chaos);
        assertNotNull(onlyFap);
        assertNull(nav.findViewById(1));

        android.graphics.Rect chaosRect = new android.graphics.Rect();
        android.graphics.Rect onlyFapRect = new android.graphics.Rect();
        chaos.getDrawingRect(chaosRect);
        onlyFap.getDrawingRect(onlyFapRect);
        nav.offsetDescendantRectToMyCoords(chaos, chaosRect);
        nav.offsetDescendantRectToMyCoords(onlyFap, onlyFapRect);

        float downX = chaosRect.exactCenterX();
        float downY = chaosRect.exactCenterY();
        float targetX = onlyFapRect.exactCenterX();
        long downTime = android.os.SystemClock.uptimeMillis();

        nav.dispatchTouchEvent(android.view.MotionEvent.obtain(
                downTime, downTime,
                android.view.MotionEvent.ACTION_DOWN,
                downX, downY, 0
        ));
        nav.dispatchTouchEvent(android.view.MotionEvent.obtain(
                downTime, downTime + 16L,
                android.view.MotionEvent.ACTION_MOVE,
                downX + ((targetX - downX) * 0.65f), downY, 0
        ));
        nav.dispatchTouchEvent(android.view.MotionEvent.obtain(
                downTime, downTime + 32L,
                android.view.MotionEvent.ACTION_UP,
                targetX, downY, 0
        ));
        shadowOf(android.os.Looper.getMainLooper()).idle();

        assertEquals(
                MainPagerAdapter.PAGE_ONLYFAP,
                MainPagerAdapter.pageForPagerPosition(pager.getCurrentItem())
        );
        assertEquals(2f, nav.pagerPositionForTest(), 0.01f);
        controller.pause().stop().destroy();
    }

    @Test public void dormantHomeRestoreFallsBackToShitTokAndLibraryIsPrimaryTab() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).apply();
        Bundle state = new Bundle();
        state.putInt("primary_page", MainPagerAdapter.PAGE_HOME);
        ActivityController<NativeMainActivity> controller =
                Robolectric.buildActivity(NativeMainActivity.class)
                        .create(state).start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();

        NativeMainActivity activity = controller.get();
        ViewPager2 pager = ReflectionHelpers.getField(activity, "primaryPager");
        BottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        assertEquals(
                MainPagerAdapter.PAGE_CHAOS,
                MainPagerAdapter.pageForPagerPosition(pager.getCurrentItem())
        );

        nav.setSelectedItemId(6);
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(
                MainPagerAdapter.PAGE_LIBRARY,
                MainPagerAdapter.pageForPagerPosition(pager.getCurrentItem())
        );
        assertNotNull(findByDescription(activity.getWindow().getDecorView(), "Library media hub"));
        android.widget.TextView title = ReflectionHelpers.getField(activity, "headerTitle");
        assertEquals("Library", title.getText().toString());

        controller.pause().stop().destroy();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test public void shitTokKeepsLegacyPortraitViewportWithPrimaryPager() {
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
        assertNotNull(homeRoot);
        assertNull(homeRoot.getParent());

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test public void shitTokFullscreenDoesNotClobberHomeFloatingRailPadding() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).apply();
        Bundle state = new Bundle();
        state.putInt("primary_page", MainPagerAdapter.PAGE_CHAOS);
        ActivityController<NativeMainActivity> controller =
                Robolectric.buildActivity(NativeMainActivity.class)
                        .create(state).start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();

        NativeMainActivity activity = controller.get();
        MainPagerAdapter adapter = ReflectionHelpers.getField(activity, "primaryPagerAdapter");
        Object[] pages = ReflectionHelpers.getField(adapter, "pages");
        Object home = pages[MainPagerAdapter.PAGE_HOME];
        androidx.recyclerview.widget.RecyclerView homeRecycler =
                ReflectionHelpers.getField(home, "recycler");
        int approvedTopPadding = Math.round(
                65 * activity.getResources().getDisplayMetrics().density
        );
        assertEquals(approvedTopPadding, homeRecycler.getPaddingTop());

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

    @Test public void libraryViewAllOpensAsStandaloneHistorySection() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        Intent intent = new Intent(context, FavoritesActivity.class)
                .putExtra(FavoritesActivity.EXTRA_START_TAB, FavoritesActivity.START_HISTORY);

        ActivityController<FavoritesActivity> controller =
                Robolectric.buildActivity(FavoritesActivity.class, intent)
                        .create().start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();

        FavoritesActivity activity = controller.get();
        ViewPager2 pager = ReflectionHelpers.getField(activity, "pager");
        assertFalse(pager.isUserInputEnabled());
        assertEquals(FavoritesActivity.START_HISTORY, pager.getCurrentItem());
        assertNotNull(findByDescription(
                activity.getWindow().getDecorView(),
                "History section"
        ));
        assertNotNull(findByDescription(
                activity.getWindow().getDecorView(),
                "Search History"
        ));
        assertNotNull(findByDescription(
                activity.getWindow().getDecorView(),
                "Section options"
        ));

        controller.pause().stop().destroy();
    }

    @Test public void showsAlwaysUsesCombinedHubAndRemovesSourceRail() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true)
                .putInt("native_series_source", 3)
                .apply();
        Bundle state = new Bundle();
        state.putInt("primary_page", MainPagerAdapter.PAGE_SERIES);
        ActivityController<NativeMainActivity> controller = Robolectric.buildActivity(NativeMainActivity.class)
                .create(state).start().resume().visible();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        NativeMainActivity activity = controller.get();

        assertEquals(4, context.getSharedPreferences("app_prefs", 0)
                .getInt("native_series_source", -1));
        assertNull(findByDescription(activity.getWindow().getDecorView(),
                "Show Featured collections"));
        assertNull(findByDescription(activity.getWindow().getDecorView(),
                "Show CrazyShit collections"));
        assertNull(findByDescription(activity.getWindow().getDecorView(),
                "Show EFukt collections"));
        assertNull(findByDescription(activity.getWindow().getDecorView(),
                "Show Categories collections"));

        MainPagerAdapter adapter = ReflectionHelpers.getField(activity, "primaryPagerAdapter");
        Object[] pages = ReflectionHelpers.getField(adapter, "pages");
        Object shows = pages[MainPagerAdapter.PAGE_SERIES];
        ShowsHubView hub = ReflectionHelpers.getField(shows, "showsHub");
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh =
                ReflectionHelpers.getField(shows, "refresh");
        assertNotNull(hub);
        assertEquals(View.VISIBLE, hub.getVisibility());
        assertEquals(View.GONE, refresh.getVisibility());
        android.widget.TextView title = ReflectionHelpers.getField(activity, "headerTitle");
        assertEquals("ZEROCHILL Shows", title.getText().toString());

        controller.pause().stop().destroy();
    }

    @Test public void showsMigratesLegacyOnlyFapSelectionToCombinedHub() {
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
        assertEquals(4, context.getSharedPreferences("app_prefs", 0)
                .getInt("native_series_source", -1));
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
