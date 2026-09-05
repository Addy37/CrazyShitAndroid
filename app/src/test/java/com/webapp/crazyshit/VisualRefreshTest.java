package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class VisualRefreshTest {
    private NativeContentItem creator() {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, "Alex Rivera",
                "https://fapello.com/alex-rivera/", "", "", "", "", "", "Alex Rivera");
    }
    private void capture(View root, String name, int width, int height) throws Exception {
        int w = BrowseUi.dp(root.getContext(), width), h = BrowseUi.dp(root.getContext(), height);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File("build/reports/visual-tests"); dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle();
    }
    @Test public void fullHomeKeepsRefreshStyleAcrossLateControllersAndTabChanges() throws Exception {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", 0);
        prefs.edit().putBoolean("access_notice_2_8_3_accepted", true)
                .putInt("native_view_home", NativeFeedAdapter.VIEW_LIST).apply();
        android.os.Bundle state = new android.os.Bundle(); state.putInt("primary_page", 0);
        ActivityController<NativeMainActivity> screen = Robolectric.buildActivity(NativeMainActivity.class)
                .create(state).start().resume();
        NativeMainActivity main = screen.get();
        UiFoundationCoordinator.onActivityCreated(main, state);
        UiFoundationCoordinator.onActivityResumed(main);
        MainPagerAdapter pager = ReflectionHelpers.getField(main, "primaryPagerAdapter");
        NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "A sample video with a readable title", "https://crazyshit.com/cnt/medias/1-sample", "", "12K", "", "");
        ReflectionHelpers.setField(pager, "homeRepository", new HomeSourceRepository(
                (ctx, source, page) -> Collections.singletonList(video), 1000));
        pager.refresh(MainPagerAdapter.PAGE_HOME);
        Object[] pages = ReflectionHelpers.getField(pager, "pages");
        Object home = pages[MainPagerAdapter.PAGE_HOME];
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while ((boolean) ReflectionHelpers.getField(home, "loading") && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        NativeFeedAdapter feed = ReflectionHelpers.getField(home, "feedAdapter");
        assertEquals(1, feed.getItemCount());
        assertEquals(NativeFeedAdapter.VIEW_CARDS, pager.viewMode(MainPagerAdapter.PAGE_HOME));
        com.google.android.material.bottomnavigation.BottomNavigationView nav = ReflectionHelpers.getField(main, "bottomNavigation");
        nav.setSelectedItemId(3);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        androidx.viewpager2.widget.ViewPager2 viewPager = ReflectionHelpers.getField(main, "primaryPager");
        assertEquals(MainPagerAdapter.PAGE_CATEGORIES, viewPager.getCurrentItem());
        nav.setSelectedItemId(1);
        OledImmersiveUiController.attachMain(main);
        UiPolishController.attach(main);
        ResponsiveFitmentController.applySoon(main);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        View root = ((android.view.ViewGroup) main.findViewById(android.R.id.content)).getChildAt(0);
        capture(root, "home-lifecycle", 360, 800);
        assertEquals(MainPagerAdapter.PAGE_HOME, viewPager.getCurrentItem());
        assertEquals(BrowseUi.dp(main, 76), nav.getLayoutParams().height);
        assertEquals(BrowseUi.dp(main, 32), nav.getItemActiveIndicatorHeight());
        assertEquals(UiPalette.PRIMARY, nav.getItemActiveIndicatorColor().getDefaultColor());
        assertEquals(Color.BLACK, nav.getItemIconTintList().getColorForState(new int[] {android.R.attr.state_checked}, Color.WHITE));
        assertTrue(nav.isItemActiveIndicatorEnabled());
        assertEquals(5, nav.getMenu().size());
        for (int id : new int[] {1, 2, 4, 3, 5}) {
            View tab = nav.findViewById(id);
            assertTrue(tab.getWidth() >= BrowseUi.dp(main, 48));
            assertTrue(tab.getHeight() >= BrowseUi.dp(main, 48));
        }
        prefs.edit().putInt("native_view_home", NativeFeedAdapter.VIEW_GRID).apply();
        FeedViewStyleController.prepareVisualRefresh(main);
        assertEquals(NativeFeedAdapter.VIEW_GRID, prefs.getInt("native_view_home", -1));
        UiFoundationCoordinator.onActivityDestroyed(main);
        screen.pause().stop().destroy();
    }

    @Test public void creatorHeaderFavoriteAndCardMenuWorkAtPhoneWidth() throws Exception {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        host.get().setTheme(R.style.Theme_CrazyShit);
        LinearLayout root = BrowseUi.screen(host.get());
        CreatorProfileHeader header = new CreatorProfileHeader(host.get(), "Alex Rivera", "Alex Rivera", creator().url);
        root.addView(header);
        TextView favorite = header.findViewWithTag("creator_favorite");
        favorite.performClick();
        assertTrue(CreatorFavoriteStore.contains(host.get(), creator()));
        AtomicInteger menus = new AtomicInteger();
        NativeFeedAdapter adapter = new NativeFeedAdapter(host.get(), new NativeFeedAdapter.Listener() {
            public void onOpen(NativeContentItem item) { }
            public void onLongPress(NativeContentItem item, View anchor) { menus.incrementAndGet(); }
            public void onComments(NativeContentItem item) { }
        });
        adapter.setViewMode(NativeFeedAdapter.VIEW_CARDS);
        NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA, "A sample video title that wraps onto two lines",
                "https://crazyshit.com/video/example", "", "12K", "", "", "");
        adapter.replace(Collections.singletonList(video));
        RecyclerView parent = new RecyclerView(host.get());
        NativeFeedAdapter.Holder holder = adapter.onCreateViewHolder(parent, NativeFeedAdapter.VIEW_CARDS);
        adapter.onBindViewHolder(holder, 0);
        holder.image.setImageResource(R.drawable.ic_nav_chaos);
        holder.image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(holder.itemView);
        host.get().setContentView(root);
        capture(root, "home-card-and-creator-header", 411, 700);
        capture(root, "compact-header-and-card", 360, 640);
        assertTrue(favorite.getWidth() >= BrowseUi.dp(host.get(), 48));
        assertTrue(favorite.getRight() <= header.getWidth() - header.getPaddingRight());
        View menu = holder.itemView.findViewWithTag("video_options");
        assertNotNull(menu); menu.performClick(); assertEquals(1, menus.get());
        adapter.close(); host.pause().stop().destroy();
    }
    @Test public void creatorGalleryRendersTheSavedGridWithoutFetching() throws Exception {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        String id = BunkrGallerySessionStore.createCreator("Alex Rivera", creator().url, "Alex Rivera");
        java.util.List<NativeContentItem> media = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) media.add(new NativeContentItem(i % 2 == 0 ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                "Sample " + i, "https://fapello.com/alex-rivera/" + (i + 1) + "/", "", "", "", "", ""));
        BunkrGallerySessionStore.replace(id, media, 1, true);
        android.content.Intent intent = new android.content.Intent(context, NativeFeedBrowserActivity.class)
                .putExtra(NativeFeedBrowserActivity.EXTRA_TITLE, "Alex Rivera")
                .putExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY, "Alex Rivera");
        android.os.Bundle state = new android.os.Bundle(); state.putString("gallery_session", id);
        ActivityController<NativeFeedBrowserActivity> screen = Robolectric.buildActivity(NativeFeedBrowserActivity.class, intent)
                .create(state).start().resume();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while ((boolean) ReflectionHelpers.getField(screen.get(), "restoringBrowser") && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        View root = ((android.view.ViewGroup) screen.get().findViewById(android.R.id.content)).getChildAt(0);
        capture(root, "creator-gallery", 411, 891);
        BunkrGalleryAdapter adapter = ReflectionHelpers.getField(screen.get(), "bunkrGalleryAdapter");
        assertEquals(8, adapter.getItemCount());
        assertEquals(Integer.valueOf(2), ReflectionHelpers.getField(screen.get(), "creatorGalleryColumns"));
        screen.pause().stop().destroy();
    }

    @Test public void searchResultsLeaveRoomForTheKeyboardAndOpenTheCreator() throws Exception {
        ActivityController<SearchActivity> screen = Robolectric.buildActivity(SearchActivity.class).create().start().resume();
        CreatorCatalog.remember(screen.get(), Collections.singletonList(creator()));
        EditText input = ReflectionHelpers.getField(screen.get(), "input");
        input.setText("Alex");
        CreatorSuggestionsController suggestions = ReflectionHelpers.getField(screen.get(), "suggestions");
        suggestions.refreshLocal();
        View root = ((android.view.ViewGroup) screen.get().findViewById(android.R.id.content)).getChildAt(0);
        capture(root, "creator-search", 411, 891);
        capture(root, "creator-search-keyboard-space", 360, 380);
        TextView action = ReflectionHelpers.getField(suggestions, "searchAll");
        assertEquals(View.VISIBLE, action.getVisibility());
        assertTrue(action.getHeight() >= BrowseUi.dp(screen.get(), 48));
        assertEquals(View.GONE, ((View) ReflectionHelpers.getField(screen.get(), "filterBar")).getVisibility());
        CreatorListAdapter adapter = ReflectionHelpers.getField(suggestions, "adapter");
        CreatorListAdapter.Holder row = adapter.onCreateViewHolder(new RecyclerView(screen.get()), 0);
        adapter.onBindViewHolder(row, 0); row.itemView.performClick();
        assertEquals(NativeFeedBrowserActivity.class.getName(), shadowOf(screen.get()).getNextStartedActivity().getComponent().getClassName());
        screen.pause().stop().destroy();
    }
}

