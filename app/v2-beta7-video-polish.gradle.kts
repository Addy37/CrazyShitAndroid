val wireV2Beta7VideoPolish by tasks.registering {
    doLast {
        val file = file("src/main/java/com/webapp/crazyshit/VideoDetailActivity.java")
        var src = file.readText()
        if (src.contains("BETA7_VIDEO_POLISH")) return@doLast

        src = src.replace(
            "    private OnBackInvokedCallback backCallback;\n",
            "    private OnBackInvokedCallback backCallback;\n    private TextView backOverlay;\n    private TextView menuOverlay;\n"
        )

        src = src.replace(
            "        playerView.setControllerAutoShow(true);\n        playerView.setControllerHideOnTouch(true);\n",
            "        playerView.setControllerAutoShow(false);\n        playerView.setControllerHideOnTouch(true);\n        playerView.setControllerShowTimeoutMs(3000);\n"
        )

        src = src.replace(
            "        TextView back = overlayButton(\"‹\", 34);\n        back.setContentDescription(\"Back\");\n        back.setOnClickListener(v -> {\n            haptic(v);\n            handleBack();\n        });\n        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(46), dp(46));\n        bp.gravity = Gravity.TOP | Gravity.START;\n        bp.setMargins(dp(8), dp(8), 0, 0);\n        playerContainer.addView(back, bp);\n\n        TextView menu = overlayButton(\"⋮\", 26);\n        menu.setContentDescription(\"Video menu\");\n        menu.setOnClickListener(v -> {\n            haptic(v);\n            showPlayerMenu(menu);\n        });\n        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(46), dp(46));\n        mp.gravity = Gravity.TOP | Gravity.END;\n        mp.setMargins(0, dp(8), dp(8), 0);\n        playerContainer.addView(menu, mp);\n",
            "        backOverlay = overlayButton(\"‹\", 34);\n        backOverlay.setContentDescription(\"Back\");\n        backOverlay.setOnClickListener(v -> {\n            haptic(v);\n            handleBack();\n        });\n        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(46), dp(46));\n        bp.gravity = Gravity.TOP | Gravity.START;\n        bp.setMargins(dp(8), dp(8), 0, 0);\n        playerContainer.addView(backOverlay, bp);\n\n        menuOverlay = overlayButton(\"⋮\", 26);\n        menuOverlay.setContentDescription(\"Video menu\");\n        menuOverlay.setOnClickListener(v -> {\n            haptic(v);\n            showPlayerMenu(menuOverlay);\n        });\n        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(46), dp(46));\n        mp.gravity = Gravity.TOP | Gravity.END;\n        mp.setMargins(0, dp(8), dp(8), 0);\n        playerContainer.addView(menuOverlay, mp);\n\n        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {\n            @Override\n            public void onVisibilityChanged(int visibility) {\n                setPlayerChromeVisible(visibility == View.VISIBLE);\n            }\n        });\n        playerView.hideController();\n        setPlayerChromeVisible(false);\n"
        )

        src = src.replace(
            "        player.setPlayWhenReady(true);\n        player.prepare();\n        player.addListener(new androidx.media3.common.Player.Listener() {\n",
            "        player.setPlayWhenReady(true);\n        player.prepare();\n        playerView.hideController();\n        setPlayerChromeVisible(false);\n        player.addListener(new androidx.media3.common.Player.Listener() {\n"
        )

        src = src.replace(
            "                requestedStartPosition = 0L;\n                updateMetadataUi();\n                buildPlayer(0L);\n",
            "                requestedStartPosition = 0L;\n                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);\n                updateMetadataUi();\n                buildPlayer(0L);\n                applyOrientation(getResources().getConfiguration().orientation);\n"
        )

        src = src.replace(
            "    private TextView overlayButton(String label, int size) {\n",
            "    // BETA7_VIDEO_POLISH\n    private void setPlayerChromeVisible(boolean visible) {\n        int state = visible ? View.VISIBLE : View.GONE;\n        if (backOverlay != null) backOverlay.setVisibility(state);\n        if (menuOverlay != null) menuOverlay.setVisibility(state);\n    }\n\n    private TextView overlayButton(String label, int size) {\n"
        )

        file.writeText(src)
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn(wireV2Beta7VideoPolish)
}
