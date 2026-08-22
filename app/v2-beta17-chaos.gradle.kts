tasks.register("wireV2Beta17Chaos") {
    dependsOn("wireV2Beta15LivePager")

    doLast {
        val nativeFile = file("src/main/java/com/webapp/crazyshit/NativeMainActivity.java")
        var native = nativeFile.readText()
        if (!native.contains("BETA17_CHAOS")) {
            native = native.replace(
                "        MEMES,\n        CATEGORIES,",
                "        MEMES,\n        CHAOS,\n        CATEGORIES,"
            )

            native = native.replace("NAV_SAVED", "NAV_CHAOS")
            native = native.replace(
                "menu.add(Menu.NONE, NAV_CHAOS, 3, \"Saved\").setIcon(R.drawable.ic_nav_saved);",
                "menu.add(Menu.NONE, NAV_CHAOS, 3, \"Chaos\").setIcon(R.drawable.ic_nav_chaos);"
            )

            native = native.replace(
                """            if (id == NAV_CHAOS) {
                startActivityForResult(new Intent(this, FavoritesActivity.class), FAVORITES_REQUEST);
                return false;
            }""",
                """            if (id == NAV_CHAOS) {
                showPrimaryPage(MainPagerAdapter.PAGE_CHAOS, true);
                return true;
            }"""
            )

            native = native.replace(
                """        } else if (position == MainPagerAdapter.PAGE_MEMES) {
            screen = Screen.MEMES;
            feedBaseUrl = MemeRepository.MEMES;
            feedTitle = "Memes";
            selectNavSilently(NAV_MEMES);
        } else {
            screen = Screen.HOME;""",
                """        } else if (position == MainPagerAdapter.PAGE_MEMES) {
            screen = Screen.MEMES;
            feedBaseUrl = MemeRepository.MEMES;
            feedTitle = "Memes";
            selectNavSilently(NAV_MEMES);
        } else if (position == MainPagerAdapter.PAGE_CHAOS) {
            screen = Screen.CHAOS;
            feedBaseUrl = CrazyShitRepository.HOME;
            feedTitle = "Chaos";
            selectNavSilently(NAV_CHAOS);
        } else {
            screen = Screen.HOME;"""
            )

            native = native.replace(
                """        if (headerTitle != null) headerTitle.setText(feedTitle);
        if (headerSubtitle != null && primaryPagerAdapter != null) {
            headerSubtitle.setText("Jeremy Edition  •  Native v2  •  " +
                    viewModeLabel(primaryPagerAdapter.viewMode(position)));
        }""",
                """        if (primaryPagerAdapter != null) primaryPagerAdapter.setPrimaryActive(position);
        if (headerTitle != null) headerTitle.setText(feedTitle);
        if (headerSubtitle != null && primaryPagerAdapter != null) {
            if (position == MainPagerAdapter.PAGE_CHAOS) {
                headerSubtitle.setText("Random video feed  •  Swipe up/down");
            } else {
                headerSubtitle.setText("Jeremy Edition  •  Native v2  •  " +
                        viewModeLabel(primaryPagerAdapter.viewMode(position)));
            }
        }"""
            )

            val categoriesAnchor = """        addSheetAction(content, "Categories", "Browse every CrazyShit category", () -> {
            showCategories();
            sheet.dismiss();
        });"""
            if (native.contains(categoriesAnchor)) {
                native = native.replace(
                    categoriesAnchor,
                    """        addSheetAction(content, "Library", "Continue, History and Watch Later", () -> {
            startActivityForResult(new Intent(this, FavoritesActivity.class), FAVORITES_REQUEST);
            sheet.dismiss();
        });
$categoriesAnchor"""
                )
            }

            native = native.replace(
                """        if (miniPlayer != null) miniPlayer.onPause();""",
                """        if (miniPlayer != null) miniPlayer.onPause();
        if (primaryPagerAdapter != null) primaryPagerAdapter.onHostPause();"""
            )
            native = native.replace(
                """        if (miniPlayer != null) miniPlayer.onResume();""",
                """        if (miniPlayer != null) miniPlayer.onResume();
        if (primaryPagerAdapter != null) primaryPagerAdapter.onHostResume();"""
            )

            native = native.replace(
                "    private void showPagerChrome(int position) {",
                "    // BETA17_CHAOS\n    private void showPagerChrome(int position) {"
            )

            nativeFile.writeText(native)
        }
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn("wireV2Beta17Chaos")
}
