tasks.register("wireV2Beta14FinalTabs") {
    doLast {
        val nativeFile = file("src/main/java/com/webapp/crazyshit/NativeMainActivity.java")
        var native = nativeFile.readText()
        if (!native.contains("BETA14_FINAL_TABS")) {
            native = native.replace(
                "    private static final int NAV_CATEGORIES = 3;\n    private static final int NAV_SAVED = 4;",
                "    private static final int NAV_MEMES = 3;\n    private static final int NAV_SAVED = 4;\n    private static final int NAV_CATEGORIES = 6;"
            )

            native = native.replace(
                "        TRENDING,\n        CATEGORIES,",
                "        TRENDING,\n        MEMES,\n        CATEGORIES,"
            )

            native = native.replace(
                "    private final CrazyShitRepository repository = new CrazyShitRepository();\n",
                "    private final CrazyShitRepository repository = new CrazyShitRepository();\n    private final MemeRepository memeRepository = new MemeRepository();\n"
            )

            native = native.replace(
                """        FrameLayout content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));""",
                """        HorizontalSwipeFrameLayout content = new HorizontalSwipeFrameLayout(this);
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
            )

            native = native.replace(
                "        menu.add(Menu.NONE, NAV_CATEGORIES, 2, \"Categories\").setIcon(R.drawable.ic_nav_categories);",
                "        menu.add(Menu.NONE, NAV_MEMES, 2, \"Memes\").setIcon(R.drawable.ic_nav_memes);"
            )

            native = native.replace(
                """            if (id == NAV_CATEGORIES) {
                showCategories();
                return true;
            }""",
                """            if (id == NAV_MEMES) {
                showMemes();
                return true;
            }"""
            )

            native = native.replace(
                """    private void showTrending() {
        screen = Screen.TRENDING;
        feedBaseUrl = CrazyShitRepository.TRENDING;
        feedTitle = "Trending";
        prepareFeed();
        loadFeed(false);
    }
""",
                """    private void showTrending() {
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
            )

            native = native.replace(
                "                List<NativeContentItem> result = repository.fetchFeed(this, requestBase, requestPage);",
                "                List<NativeContentItem> result = screen == Screen.MEMES\n                        ? memeRepository.fetch(this, requestPage)\n                        : repository.fetchFeed(this, requestBase, requestPage);"
            )

            native = native.replace(
                """        return screen == Screen.HOME || screen == Screen.TRENDING ||
                screen == Screen.CATEGORY || screen == Screen.SEARCH;""",
                """        return screen == Screen.HOME || screen == Screen.TRENDING || screen == Screen.MEMES ||
                screen == Screen.CATEGORY || screen == Screen.SEARCH;"""
            )

            native = native.replace(
                """    private void openNativeItem(NativeContentItem item) {
        if (item == null || item.url.isEmpty()) return;
        progress.setVisibility(View.VISIBLE);""",
                """    private void openNativeItem(NativeContentItem item) {
        if (item == null || item.url.isEmpty()) return;
        if (screen == Screen.MEMES) {
            openFallback(item.url);
            return;
        }
        progress.setVisibility(View.VISIBLE);"""
            )

            native = native.replace(
                """        addSheetAction(content, "Open full website", "Use the compatibility browser", () -> {
            openFallback(CrazyShitRepository.HOME);
            sheet.dismiss();
        });""",
                """        addSheetAction(content, "Categories", "Browse every CrazyShit category", () -> {
            showCategories();
            sheet.dismiss();
        });
        addSheetAction(content, "My profile", "Open the profile for your signed-in account", () -> {
            startActivity(new Intent(this, ProfileActivity.class));
            sheet.dismiss();
        });
        addSheetAction(content, "Open full website", "Use the compatibility browser", () -> {
            openFallback(CrazyShitRepository.HOME);
            sheet.dismiss();
        });"""
            )

            nativeFile.writeText(native)
        }

        val favoritesFile = file("src/main/java/com/webapp/crazyshit/FavoritesActivity.java")
        var favorites = favoritesFile.readText()
        if (!favorites.contains("BETA14_LIBRARY_SWIPE")) {
            favorites = favorites.replace(
                """        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(6), 0, dp(24));
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));""",
                """        HorizontalSwipeFrameLayout swipeHost = new HorizontalSwipeFrameLayout(this);
        swipeHost.setListener(new HorizontalSwipeFrameLayout.Listener() {
            @Override
            public void onSwipeLeft() {
                swipeLibraryTab(1);
            }

            @Override
            public void onSwipeRight() {
                swipeLibraryTab(-1);
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(6), 0, dp(24));
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        swipeHost.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root.addView(swipeHost, new LinearLayout.LayoutParams(-1, 0, 1f));"""
            )

            favorites = favorites.replace(
                "    private MaterialButton tabButton(String label, int target) {",
                """    // BETA14_LIBRARY_SWIPE
    private void swipeLibraryTab(int direction) {
        if (direction == 0 || listContainer == null) return;
        int next = tab + direction;
        if (next < TAB_CONTINUE || next > TAB_WATCH_LATER) return;

        float out = direction > 0 ? -dp(48) : dp(48);
        float in = -out;
        listContainer.animate()
                .translationX(out)
                .alpha(0.45f)
                .setDuration(80L)
                .withEndAction(() -> {
                    tab = next;
                    updateTabs();
                    renderItems();
                    listContainer.setTranslationX(in);
                    listContainer.setAlpha(0.45f);
                    listContainer.animate().translationX(0f).alpha(1f).setDuration(120L).start();
                })
                .start();
    }

    private MaterialButton tabButton(String label, int target) {"""
            )

            favoritesFile.writeText(favorites)
        }
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn("wireV2Beta14FinalTabs")
}
