package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
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
    @Test public void fullHomeKeepsZeroChillGlassAcrossLateControllersAndTabChanges() throws Exception {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", 0);
        prefs.edit().putBoolean("access_notice_2_8_3_accepted", true)
                .putBoolean("visual_refresh_2_11_1", true)
                .putBoolean("legacy_home_v2_10_restored", false)
                .putInt("home_source", 3)
                .putInt("native_view_home", NativeFeedAdapter.VIEW_CARDS).apply();
        android.os.Bundle state = new android.os.Bundle(); state.putInt("primary_page", 0);
        ActivityController<NativeMainActivity> screen = Robolectric.buildActivity(NativeMainActivity.class)
                .create(state).start().resume().visible();
        NativeMainActivity main = screen.get();
        UiFoundationCoordinator.onActivityCreated(main, state);
        UiFoundationCoordinator.onActivityResumed(main);
        MainPagerAdapter pager = ReflectionHelpers.getField(main, "primaryPagerAdapter");
        NativeContentItem section = new NativeContentItem(NativeContentItem.KIND_SECTION,
                "TODAY'S CRAZY SHIT", "section:today", "", "", "", "");
        NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "A sample video with a readable title", "https://example.invalid/cnt/medias/1-sample", "", "12K", "", "");
        ReflectionHelpers.setField(pager, "homeRepository", new HomeSourceRepository(
                (ctx, source, page) -> java.util.Arrays.asList(section, video), 1000));
        pager.refresh(MainPagerAdapter.PAGE_HOME);
        Object[] pages = ReflectionHelpers.getField(pager, "pages");
        Object home = pages[MainPagerAdapter.PAGE_HOME];
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while ((boolean) ReflectionHelpers.getField(home, "loading") && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        NativeFeedAdapter feed = ReflectionHelpers.getField(home, "feedAdapter");
        assertEquals(2, feed.getItemCount());
        assertTrue(feed.isSectionAt(0));
        assertEquals(NativeFeedAdapter.VIEW_CARDS, pager.viewMode(MainPagerAdapter.PAGE_HOME));
        assertEquals(1, prefs.getInt("home_source", -1));
        java.util.List<TextView> homeChips = ReflectionHelpers.getField(home, "homeChips");
        assertEquals(2, homeChips.size());
        View chipRow = (View) homeChips.get(0).getParent();
        View sourceBar = (View) chipRow.getParent();
        assertEquals(View.VISIBLE, sourceBar.getVisibility());
        assertFalse(((android.view.ViewGroup) sourceBar).getClipChildren());
        assertFalse(((android.view.ViewGroup) sourceBar).getClipToPadding());
        assertFalse(((android.view.ViewGroup) chipRow).getClipChildren());
        assertFalse(((android.view.ViewGroup) chipRow).getClipToPadding());
        assertEquals("CrazyShit", homeChips.get(0).getText().toString());
        assertEquals("EFukt", homeChips.get(1).getText().toString());
        homeChips.get(1).performClick();
        deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while ((boolean) ReflectionHelpers.getField(home, "loading") && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertEquals(2, feed.getItemCount());
        assertTrue(feed.isSectionAt(0));
        assertEquals(2, prefs.getInt("home_source", -1));

        com.google.android.material.bottomnavigation.BottomNavigationView nav = ReflectionHelpers.getField(main, "bottomNavigation");
        nav.setSelectedItemId(3);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        androidx.viewpager2.widget.ViewPager2 viewPager = ReflectionHelpers.getField(main, "primaryPager");
        assertEquals(MainPagerAdapter.PAGE_ONLYFAP, viewPager.getCurrentItem());
        nav.setSelectedItemId(1);
        UiPolishController.attach(main);
        ResponsiveFitmentController.applySoon(main);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        View root = ReflectionHelpers.getField(main, "overlayRoot");
        capture(root, "home-lifecycle", 360, 800);

        TextView headerTitle = ReflectionHelpers.getField(main, "headerTitle");
        TextView headerSubtitle = ReflectionHelpers.getField(main, "headerSubtitle");
        assertEquals("ZEROCHILL", headerTitle.getText().toString());
        assertEquals(View.GONE, headerSubtitle.getVisibility());
        LinearLayout shell = ReflectionHelpers.getField(main, "shell");
        assertTrue(shell instanceof FrostedNavigationLayout);
        View topBar = shell.getChildAt(0);
        assertTrue(topBar instanceof LinearLayout);
        assertEquals(2, ((LinearLayout) topBar).getChildCount());
        View search = ((LinearLayout) topBar).getChildAt(1);
        assertEquals("Global Search", String.valueOf(search.getContentDescription()));

        RecyclerView homeList = ReflectionHelpers.getField(home, "recycler");
        assertNull(homeList.getItemAnimator());
        NativeFeedAdapter.Holder visibleCard = (NativeFeedAdapter.Holder) homeList.findViewHolderForAdapterPosition(1);
        assertNotNull(visibleCard);
        NativeFeedAdapter.Holder visibleSection = (NativeFeedAdapter.Holder) homeList.findViewHolderForAdapterPosition(0);
        assertNotNull(visibleSection);
        assertEquals("TODAY'S CRAZY SHIT", visibleSection.sectionTitle.getText().toString());
        com.google.android.material.card.MaterialCardView sectionCard =
                (com.google.android.material.card.MaterialCardView) visibleSection.itemView;
        assertEquals(Color.TRANSPARENT, sectionCard.getCardBackgroundColor().getDefaultColor());
        assertEquals(0, sectionCard.getStrokeWidth());
        assertEquals(0f, sectionCard.getCardElevation(), 0f);
        assertTrue(visibleCard.info.getText().toString().startsWith("CrazyShit"));
        visibleCard.image.setImageResource(R.drawable.ic_nav_chaos);
        visibleCard.image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) visibleCard.itemView;
        assertEquals(BrowseUi.dp(main, 1), card.getStrokeWidth());
        assertEquals(main.getColor(R.color.zc_surface_glass), card.getCardBackgroundColor().getDefaultColor());
        assertEquals(main.getColor(R.color.zc_edge), card.getStrokeColor());
        capture(root, "home-lifecycle", 360, 800);
        assertEquals(MainPagerAdapter.PAGE_HOME, viewPager.getCurrentItem());
        assertEquals(main.getResources().getDimensionPixelSize(R.dimen.zc_bottom_nav_height),
                nav.getLayoutParams().height);
        assertSame(nav, ((FrostedNavigationLayout) shell).frostedNavigationViewForTest());
        assertEquals(BrowseUi.dp(main, 20),
                main.getResources().getDimensionPixelSize(R.dimen.zc_navigation_blur_radius));
        assertEquals(main.getResources().getDimensionPixelSize(R.dimen.zc_nav_indicator_height),
                nav.getItemActiveIndicatorHeight());
        assertEquals(Color.TRANSPARENT,
                nav.getItemActiveIndicatorColor().getDefaultColor());
        assertFalse(nav.getClipChildren());
        assertFalse(nav.getClipToPadding());
        assertTrue(nav.getChildAt(0) instanceof android.view.ViewGroup);
        android.view.ViewGroup navMenu = (android.view.ViewGroup) nav.getChildAt(0);
        assertFalse(navMenu.getClipChildren());
        assertFalse(navMenu.getClipToPadding());
        assertEquals(UiPalette.PRIMARY, nav.getItemIconTintList().getColorForState(new int[] {android.R.attr.state_checked}, Color.WHITE));
        assertTrue(nav.isItemActiveIndicatorEnabled());
        assertEquals(5, nav.getMenu().size());
        assertEquals("Home", nav.getMenu().findItem(1).getTitle());
        assertEquals("Shows", nav.getMenu().findItem(2).getTitle());
        assertEquals("ShitTok", nav.getMenu().findItem(4).getTitle());
        assertEquals("OnlyFap", nav.getMenu().findItem(3).getTitle());
        assertEquals("More", nav.getMenu().findItem(5).getTitle());
        for (int id : new int[] {1, 2, 4, 3, 5}) {
            View tab = nav.findViewById(id);
            assertTrue("Tab " + id + " width=" + tab.getWidth() + " nav=" + nav.getWidth(), tab.getWidth() >= BrowseUi.dp(main, 48));
            assertEquals(nav.getHeight(), tab.getHeight());
            assertEquals(0, Math.round(tab.getTranslationY()));
        }
        prefs.edit().putInt("native_view_home", NativeFeedAdapter.VIEW_GRID).apply();
        FeedViewStyleController.prepareVisualRefresh(main);
        assertEquals(NativeFeedAdapter.VIEW_GRID, prefs.getInt("native_view_home", -1));
        UiFoundationCoordinator.onActivityDestroyed(main);
        screen.pause().stop().destroy();
    }

    @Test public void collectionsCardsUseGlassWithoutLiveBlur() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        NativeCategoryAdapter adapter = new NativeCategoryAdapter(host.get(), item -> { });
        adapter.setWideCreatorCards(true);
        adapter.replace(Collections.singletonList(creator()));
        RecyclerView parent = new RecyclerView(host.get());
        int viewType = adapter.getItemViewType(0);
        NativeCategoryAdapter.Holder holder = adapter.onCreateViewHolder(parent, viewType);
        adapter.onBindViewHolder(holder, 0);

        assertEquals(host.get().getColor(R.color.zc_surface_glass),
                holder.card.getCardBackgroundColor().getDefaultColor());
        assertEquals(host.get().getColor(R.color.zc_cyan), holder.card.getStrokeColor());
        assertEquals(0.62f, holder.backdrop.getAlpha(), 0.001f);
        assertEquals(Boolean.TRUE, holder.card.getTag(R.id.zerochill_motion_installed));

        adapter.close();
        host.pause().stop().destroy();
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
    @Test public void homeListUsesWiderMediaReadableTitlesAndCompactMetadata() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        NativeFeedAdapter.Listener listener = new NativeFeedAdapter.Listener() {
            public void onOpen(NativeContentItem item) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
            public void onComments(NativeContentItem item) { }
        };
        NativeContentItem video = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "5 REASONS TO SAY \"WHAT IN THE FUC...\"",
                "https://crazyshit.com/video/example",
                "",
                "41100",
                "",
                "12000",
                ""
        );
        RecyclerView parent = new RecyclerView(host.get());

        NativeFeedAdapter homeAdapter = new NativeFeedAdapter(host.get(), listener, true);
        homeAdapter.setViewMode(NativeFeedAdapter.VIEW_LIST);
        homeAdapter.replace(Collections.singletonList(video));
        NativeFeedAdapter.Holder homeHolder = homeAdapter.onCreateViewHolder(parent, NativeFeedAdapter.VIEW_LIST);
        homeAdapter.onBindViewHolder(homeHolder, 0);
        ViewGroup homeRow = (ViewGroup) ((ViewGroup) homeHolder.itemView).getChildAt(0);
        View homeMedia = homeRow.getChildAt(0);
        assertEquals(BrowseUi.dp(host.get(), 209), homeMedia.getLayoutParams().width);
        assertEquals("5 Reasons to Say \"What in the Fuc...\"", homeHolder.title.getText().toString());
        assertEquals(3, homeHolder.title.getMaxLines());
        assertEquals("41.1K views", homeHolder.info.getText().toString());
        assertEquals("💬 12K", homeHolder.comments.getText().toString());

        NativeFeedAdapter regularAdapter = new NativeFeedAdapter(host.get(), listener);
        regularAdapter.setViewMode(NativeFeedAdapter.VIEW_LIST);
        regularAdapter.replace(Collections.singletonList(video));
        NativeFeedAdapter.Holder regularHolder = regularAdapter.onCreateViewHolder(parent, NativeFeedAdapter.VIEW_LIST);
        regularAdapter.onBindViewHolder(regularHolder, 0);
        ViewGroup regularRow = (ViewGroup) ((ViewGroup) regularHolder.itemView).getChildAt(0);
        View regularMedia = regularRow.getChildAt(0);
        assertEquals(BrowseUi.dp(host.get(), 166), regularMedia.getLayoutParams().width);
        assertEquals(video.title, regularHolder.title.getText().toString());
        assertEquals(2, regularHolder.title.getMaxLines());

        homeAdapter.close();
        regularAdapter.close();
        host.pause().stop().destroy();
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
