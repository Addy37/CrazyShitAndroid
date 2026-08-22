tasks.register("wireV2Beta15LivePager") {
    dependsOn("wireV2BetaRuntime")
    dependsOn("wireV2Beta6VideoDetail")
    dependsOn("wireV2Beta14FinalTabs")

    doLast {
        val nativeFile = file("src/main/java/com/webapp/crazyshit/NativeMainActivity.java")
        var native = nativeFile.readText()
        if (!native.contains("BETA15_LIVE_PAGER")) {
            native = native.replace(
                "import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;\n",
                "import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;\nimport androidx.viewpager2.widget.ViewPager2;\n"
            )

            native = native.replace(
                "    private NativeMiniPlayer miniPlayer;\n",
                "    private NativeMiniPlayer miniPlayer;\n    private FrameLayout legacyContent;\n    private ViewPager2 primaryPager;\n    private MainPagerAdapter primaryPagerAdapter;\n"
            )

            val oldContent = """        HorizontalSwipeFrameLayout content = new HorizontalSwipeFrameLayout(this);
        content.setListener(new HorizontalSwipeFrameLayout.Listener() {
            @Override
            public void onSwipeLeft() {
                swipePrimaryTab(1);
            }

            @Override
            public void onSwipeRight() {
                swipePrimaryTab(-1);
            }
        });
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));"""

            val newContent = """        // BETA15_LIVE_PAGER
        FrameLayout content = new FrameLayout(this);
        legacyContent = content;
        legacyContent.setVisibility(View.GONE);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 0f));

        primaryPagerAdapter = new MainPagerAdapter(this, new MainPagerAdapter.Host() {
            @Override
            public void onOpenItem(NativeContentItem item, boolean meme) {
                haptic(primaryPager);
                if (meme) openFallback(item.url);
                else openNativeItem(item);
            }

            @Override
            public void onLongPressItem(NativeContentItem item, View anchor, boolean meme) {
                haptic(anchor);
                showItemMenu(item, anchor);
            }

            @Override
            public void onOpenComments(NativeContentItem item) {
                openComments(item);
            }
        });

        primaryPager = new ViewPager2(this);
        primaryPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        primaryPager.setOffscreenPageLimit(MainPagerAdapter.PAGE_COUNT - 1);
        primaryPager.setAdapter(primaryPagerAdapter);
        primaryPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showPagerChrome(position);
            }
        });
        shell.addView(primaryPager, new LinearLayout.LayoutParams(-1, 0, 1f));"""

            if (native.contains(oldContent)) native = native.replace(oldContent, newContent)

            val oldPrimaryMethods = """    private void showHome() {
        selectNavSilently(NAV_HOME);
        screen = Screen.HOME;
        feedBaseUrl = CrazyShitRepository.HOME;
        feedTitle = "Home";
        prepareFeed();
        loadFeed(false);
    }

    private void showTrending() {
        selectNavSilently(NAV_TRENDING);
        screen = Screen.TRENDING;
        feedBaseUrl = CrazyShitRepository.TRENDING;
        feedTitle = "Trending";
        prepareFeed();
        loadFeed(false);
    }

    // BETA14_FINAL_TABS
    private void showMemes() {
        selectNavSilently(NAV_MEMES);
        screen = Screen.MEMES;
        feedBaseUrl = MemeRepository.MEMES;
        feedTitle = "Memes";
        prepareFeed();
        loadFeed(false);
    }

    private void swipePrimaryTab(int direction) {
        if (direction == 0 || recycler == null) return;
        int current;
        if (screen == Screen.HOME) current = 0;
        else if (screen == Screen.TRENDING) current = 1;
        else if (screen == Screen.MEMES) current = 2;
        else return;

        int next = current + direction;
        if (next < 0 || next > 2) return;

        float out = direction > 0 ? -dp(52) : dp(52);
        float in = -out;
        recycler.animate()
                .translationX(out)
                .alpha(0.45f)
                .setDuration(85L)
                .withEndAction(() -> {
                    if (next == 0) showHome();
                    else if (next == 1) showTrending();
                    else showMemes();
                    recycler.setTranslationX(in);
                    recycler.setAlpha(0.45f);
                    recycler.animate().translationX(0f).alpha(1f).setDuration(125L).start();
                })
                .start();
    }
"""

            val newPrimaryMethods = """    private void showHome() {
        showPrimaryPage(MainPagerAdapter.PAGE_HOME, true);
    }

    private void showTrending() {
        showPrimaryPage(MainPagerAdapter.PAGE_TRENDING, true);
    }

    // BETA14_FINAL_TABS
    private void showMemes() {
        showPrimaryPage(MainPagerAdapter.PAGE_MEMES, true);
    }

    private void showPrimaryPage(int position, boolean smooth) {
        if (primaryPager == null) return;
        showPagerChrome(position);
        primaryPager.setCurrentItem(position, smooth);
    }

    private void showPagerChrome(int position) {
        if (legacyContent != null) {
            legacyContent.setVisibility(View.GONE);
            LinearLayout.LayoutParams old = (LinearLayout.LayoutParams) legacyContent.getLayoutParams();
            old.weight = 0f;
            old.height = 0;
            legacyContent.setLayoutParams(old);
        }
        if (primaryPager != null) {
            primaryPager.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams pp = (LinearLayout.LayoutParams) primaryPager.getLayoutParams();
            pp.weight = 1f;
            pp.height = 0;
            primaryPager.setLayoutParams(pp);
        }

        if (position == MainPagerAdapter.PAGE_TRENDING) {
            screen = Screen.TRENDING;
            feedBaseUrl = CrazyShitRepository.TRENDING;
            feedTitle = "Trending";
            selectNavSilently(NAV_TRENDING);
        } else if (position == MainPagerAdapter.PAGE_MEMES) {
            screen = Screen.MEMES;
            feedBaseUrl = MemeRepository.MEMES;
            feedTitle = "Memes";
            selectNavSilently(NAV_MEMES);
        } else {
            screen = Screen.HOME;
            feedBaseUrl = CrazyShitRepository.HOME;
            feedTitle = "Home";
            selectNavSilently(NAV_HOME);
        }

        if (headerTitle != null) headerTitle.setText(feedTitle);
        if (headerSubtitle != null && primaryPagerAdapter != null) {
            headerSubtitle.setText("Jeremy Edition  •  Native v2  •  " +
                    viewModeLabel(primaryPagerAdapter.viewMode(position)));
        }
    }

    private void showLegacyContent() {
        if (primaryPager != null) {
            primaryPager.setVisibility(View.GONE);
            LinearLayout.LayoutParams pp = (LinearLayout.LayoutParams) primaryPager.getLayoutParams();
            pp.weight = 0f;
            pp.height = 0;
            primaryPager.setLayoutParams(pp);
        }
        if (legacyContent != null) {
            legacyContent.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams old = (LinearLayout.LayoutParams) legacyContent.getLayoutParams();
            old.weight = 1f;
            old.height = 0;
            legacyContent.setLayoutParams(old);
        }
    }
"""

            if (native.contains(oldPrimaryMethods)) native = native.replace(oldPrimaryMethods, newPrimaryMethods)

            native = native.replace(
                "    private void showCategory(NativeContentItem category) {\n        screen = Screen.CATEGORY;",
                "    private void showCategory(NativeContentItem category) {\n        showLegacyContent();\n        screen = Screen.CATEGORY;"
            )
            native = native.replace(
                "    private void showSearch(String query) {\n        screen = Screen.SEARCH;",
                "    private void showSearch(String query) {\n        showLegacyContent();\n        screen = Screen.SEARCH;"
            )
            native = native.replace(
                "    private void showCategories() {\n        screen = Screen.CATEGORIES;",
                "    private void showCategories() {\n        showLegacyContent();\n        screen = Screen.CATEGORIES;"
            )

            native = native.replace(
                "    private String viewPreferenceKey() {\n        if (screen == Screen.TRENDING) return \"native_view_trending\";",
                "    private String viewPreferenceKey() {\n        if (screen == Screen.TRENDING) return \"native_view_trending\";\n        if (screen == Screen.MEMES) return \"native_view_memes\";"
            )

            native = native.replace(
                """    private void applyFeedLayout() {
        int mode = currentViewMode();
        feedAdapter.setViewMode(mode);""",
                """    private void applyFeedLayout() {
        int mode = currentViewMode();
        if (primaryPager != null && primaryPager.getVisibility() == View.VISIBLE && primaryPagerAdapter != null) {
            primaryPagerAdapter.setViewMode(primaryPager.getCurrentItem(), mode);
            return;
        }
        feedAdapter.setViewMode(mode);"""
            )

            native = native.replace(
                """    protected void onDestroy() {
        if (miniPlayer != null) miniPlayer.stop();""",
                """    protected void onDestroy() {
        if (miniPlayer != null) miniPlayer.stop();
        if (primaryPagerAdapter != null) primaryPagerAdapter.close();"""
            )

            nativeFile.writeText(native)
        }
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn("wireV2Beta15LivePager")
}
