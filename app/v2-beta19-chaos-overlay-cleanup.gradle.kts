tasks.register("wireV2Beta19ChaosOverlayCleanup") {
    dependsOn("wireV2Beta18ChaosPolish")

    doLast {
        val chaosFile = file("src/main/java/com/webapp/crazyshit/ChaosFeedView.java")
        var chaos = chaosFile.readText()
        if (!chaos.contains("BETA19_CHAOS_OVERLAY_CLEANUP")) {
            chaos = chaos.replace(
                "import android.view.HapticFeedbackConstants;\n",
                "import android.view.HapticFeedbackConstants;\nimport android.view.LayoutInflater;\n"
            )

            chaos = chaos.replace(
                """            playerView = new PlayerView(activity);
            playerView.setUseController(false);
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setBackgroundColor(Color.BLACK);
            root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));""",
                """            // BETA19_CHAOS_OVERLAY_CLEANUP
            playerView = (PlayerView) LayoutInflater.from(activity)
                    .inflate(R.layout.view_video_player_texture, root, false);
            playerView.setUseController(false);
            playerView.setControllerAutoShow(false);
            playerView.hideController();
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setBackgroundColor(Color.BLACK);
            root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));"""
            )

            // Defensive cleanup in case any older Chaos UI block survives a runtime patch.
            chaos = chaos.replace(
                "lower.setBackgroundColor(Color.argb(100, 0, 0, 0));",
                "lower.setBackgroundColor(Color.TRANSPARENT);"
            )
            chaos = chaos.replace(
                "button.setBackgroundColor(Color.argb(105, 0, 0, 0));",
                "button.setBackgroundColor(Color.TRANSPARENT);"
            )
            chaos = chaos.replace(
                "copy.setGravity(Gravity.BOTTOM);",
                "copy.setGravity(Gravity.BOTTOM);\n            copy.setBackgroundColor(Color.TRANSPARENT);"
            )
            chaos = chaos.replace(
                "actions.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);",
                "actions.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);\n            actions.setBackgroundColor(Color.TRANSPARENT);"
            )

            chaosFile.writeText(chaos)
        }
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn("wireV2Beta19ChaosOverlayCleanup")
}
