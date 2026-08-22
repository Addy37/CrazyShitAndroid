tasks.register("wireV2Beta18ChaosPolish") {
    dependsOn("wireV2Beta17Chaos")

    doLast {
        val chaosFile = file("src/main/java/com/webapp/crazyshit/ChaosFeedView.java")
        var chaos = chaosFile.readText()
        if (!chaos.contains("BETA18_CHAOS_POLISH")) {
            val oldUi = """            LinearLayout lower = new LinearLayout(activity);
            lower.setOrientation(LinearLayout.HORIZONTAL);
            lower.setGravity(Gravity.BOTTOM);
            lower.setPadding(dp(16), dp(18), dp(10), dp(18));
            lower.setBackgroundColor(Color.argb(100, 0, 0, 0));
            FrameLayout.LayoutParams lowerParams = new FrameLayout.LayoutParams(-1, -2);
            lowerParams.gravity = Gravity.BOTTOM;
            root.addView(lower, lowerParams);

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.BOTTOM);
            lower.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

            title = new TextView(activity);
            title.setTextColor(Color.WHITE);
            title.setTextSize(17);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(3);
            copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

            meta = new TextView(activity);
            meta.setTextColor(Color.rgb(215, 215, 222));
            meta.setTextSize(12);
            meta.setPadding(0, dp(5), 0, 0);
            copy.addView(meta, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.VERTICAL);
            actions.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lower.addView(actions, new LinearLayout.LayoutParams(dp(82), -2));

            save = actionButton("☆\\nSave");
            actions.addView(save, actionParams());

            TextView comments = actionButton("💬\\nComments");
            actions.addView(comments, actionParams());

            TextView share = actionButton("↗\\nShare");
            actions.addView(share, actionParams());

            TextView details = actionButton("⋯\\nDetails");
            actions.addView(details, actionParams());"""

            val newUi = """            // BETA18_CHAOS_POLISH
            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.BOTTOM);
            copy.setPadding(dp(14), dp(8), dp(8), dp(12));
            FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(-1, -2);
            copyParams.gravity = Gravity.BOTTOM;
            copyParams.setMargins(0, 0, dp(68), dp(2));
            root.addView(copy, copyParams);

            title = new TextView(activity);
            title.setTextColor(Color.WHITE);
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setShadowLayer(dp(2), 0f, dp(1), Color.BLACK);
            copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

            meta = new TextView(activity);
            meta.setTextColor(Color.rgb(225, 225, 232));
            meta.setTextSize(11);
            meta.setPadding(0, dp(4), 0, 0);
            meta.setShadowLayer(dp(2), 0f, dp(1), Color.BLACK);
            copy.addView(meta, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.VERTICAL);
            actions.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            FrameLayout.LayoutParams actionRailParams = new FrameLayout.LayoutParams(dp(58), -2);
            actionRailParams.gravity = Gravity.END | Gravity.BOTTOM;
            actionRailParams.setMargins(0, 0, dp(5), dp(10));
            root.addView(actions, actionRailParams);

            save = actionButton("☆\\nSave");
            actions.addView(save, actionParams());

            TextView comments = actionButton("💬\\nComments");
            actions.addView(comments, actionParams());

            TextView share = actionButton("↗\\nShare");
            actions.addView(share, actionParams());

            TextView details = actionButton("⋯\\nDetails");
            actions.addView(details, actionParams());"""

            if (chaos.contains(oldUi)) chaos = chaos.replace(oldUi, newUi)

            chaos = chaos.replace(
                """            button.setTextSize(12);
            button.setGravity(Gravity.CENTER);
            button.setBackgroundColor(Color.argb(105, 0, 0, 0));
            button.setPadding(dp(4), dp(7), dp(4), dp(7));""",
                """            button.setTextSize(10);
            button.setGravity(Gravity.CENTER);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setShadowLayer(dp(2), 0f, dp(1), Color.BLACK);
            button.setPadding(dp(2), dp(3), dp(2), dp(3));"""
            )
            chaos = chaos.replace(
                """            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(74), dp(60));
            params.setMargins(0, dp(4), 0, dp(4));""",
                """            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(48));
            params.setMargins(0, dp(2), 0, dp(2));"""
            )
            chaosFile.writeText(chaos)
        }

        val pagerFile = file("src/main/java/com/webapp/crazyshit/MainPagerAdapter.java")
        var pager = pagerFile.readText()
        if (!pager.contains("BETA18_CHAOS_CENTER")) {
            pager = pager.replace(
                """    public static final int PAGE_HOME = 0;
    public static final int PAGE_TRENDING = 1;
    public static final int PAGE_MEMES = 2;
    public static final int PAGE_CHAOS = 3;
    public static final int PAGE_COUNT = 4;
    private static final int FEED_PAGE_COUNT = 3;""",
                """    public static final int PAGE_HOME = 0;
    public static final int PAGE_TRENDING = 1;
    // BETA18_CHAOS_CENTER
    public static final int PAGE_CHAOS = 2;
    public static final int PAGE_MEMES = 3;
    public static final int PAGE_COUNT = 4;
    private static final int FEED_PAGE_COUNT = 4;"""
            )
            pager = pager.replace(
                "        for (Page page : pages) load(page, false);",
                "        for (Page page : pages) if (page != null) load(page, false);"
            )
            pagerFile.writeText(pager)
        }

        val nativeFile = file("src/main/java/com/webapp/crazyshit/NativeMainActivity.java")
        var native = nativeFile.readText()
        if (!native.contains("BETA18_FEATURED_CHAOS_NAV")) {
            native = native.replace(
                """        menu.add(Menu.NONE, NAV_MEMES, 2, "Memes").setIcon(R.drawable.ic_nav_memes);
        menu.add(Menu.NONE, NAV_CHAOS, 3, "Chaos").setIcon(R.drawable.ic_nav_chaos);""",
                """        menu.add(Menu.NONE, NAV_CHAOS, 2, "Chaos").setIcon(R.drawable.ic_nav_chaos);
        menu.add(Menu.NONE, NAV_MEMES, 3, "Memes").setIcon(R.drawable.ic_nav_memes);"""
            )
            native = native.replace(
                """        shell.addView(bottomNavigation, new LinearLayout.LayoutParams(-1, dp(74)));""",
                """        // BETA18_FEATURED_CHAOS_NAV
        shell.addView(bottomNavigation, new LinearLayout.LayoutParams(-1, dp(76)));
        bottomNavigation.post(() -> {
            View chaosItem = bottomNavigation.findViewById(NAV_CHAOS);
            if (chaosItem != null) {
                chaosItem.setScaleX(1.13f);
                chaosItem.setScaleY(1.13f);
                chaosItem.setTranslationY(-dp(2));
                chaosItem.setElevation(dp(5));
                chaosItem.setContentDescription("Chaos featured tab");
            }
        });"""
            )
            nativeFile.writeText(native)
        }
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn("wireV2Beta18ChaosPolish")
}
