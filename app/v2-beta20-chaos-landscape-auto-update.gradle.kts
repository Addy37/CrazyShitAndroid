tasks.register("wireV2Beta20ChaosLandscapeAutoUpdate") {
    dependsOn("wireV2Beta19ChaosOverlayCleanup")

    doLast {
        val nativeFile = file("src/main/java/com/webapp/crazyshit/NativeMainActivity.java")
        var native = nativeFile.readText()
        if (!native.contains("BETA20_CHAOS_LANDSCAPE")) {
            // First add the calls while showPagerChrome and showLegacyContent are still adjacent.
            native = native.replace(
                """        if (headerSubtitle != null && primaryPagerAdapter != null) {
            if (position == MainPagerAdapter.PAGE_CHAOS) {
                headerSubtitle.setText("Random video feed  •  Swipe up/down");
            } else {
                headerSubtitle.setText("Jeremy Edition  •  Native v2  •  " +
                        viewModeLabel(primaryPagerAdapter.viewMode(position)));
            }
        }
    }

    private void showLegacyContent() {""",
                """        if (headerSubtitle != null && primaryPagerAdapter != null) {
            if (position == MainPagerAdapter.PAGE_CHAOS) {
                headerSubtitle.setText("Random video feed  •  Swipe up/down");
            } else {
                headerSubtitle.setText("Jeremy Edition  •  Native v2  •  " +
                        viewModeLabel(primaryPagerAdapter.viewMode(position)));
            }
        }
        applyChaosFullscreenChrome();
    }

    private void showLegacyContent() {
        exitChaosFullscreenChrome();"""
            )

            val legacyAnchor = "    private void showLegacyContent() {\n        exitChaosFullscreenChrome();"
            val fullscreenMethods = """
    // BETA20_CHAOS_LANDSCAPE
    private void applyChaosFullscreenChrome() {
        boolean landscape = getResources().getConfiguration().orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        setChaosFullscreenChrome(screen == Screen.CHAOS && landscape);
    }

    private void setChaosFullscreenChrome(boolean fullscreen) {
        View topBar = null;
        if (headerTitle != null && headerTitle.getParent() instanceof View) {
            View labels = (View) headerTitle.getParent();
            if (labels.getParent() instanceof View) topBar = (View) labels.getParent();
            else topBar = labels;
        }
        if (topBar != null) topBar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        }

        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int types = WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars();
                if (fullscreen) {
                    controller.hide(types);
                    controller.setSystemBarsBehavior(
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                } else {
                    controller.show(types);
                }
            }
        } else {
            if (fullscreen) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    private void exitChaosFullscreenChrome() {
        setChaosFullscreenChrome(false);
    }

"""
            if (native.contains(legacyAnchor)) {
                native = native.replace(legacyAnchor, fullscreenMethods + legacyAnchor)
            }

            val backAnchor = "    @Override\n    public void onBackPressed() {"
            if (native.contains(backAnchor)) {
                native = native.replace(
                    backAnchor,
                    """    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyChaosFullscreenChrome();
    }

$backAnchor"""
                )
            }

            native = native.replace(
                """        if (appUpdater != null) appUpdater.onHostResume();""",
                """        if (appUpdater != null) {
            appUpdater.onHostResume();
            appUpdater.check(false);
        }
        applyChaosFullscreenChrome();"""
            )

            nativeFile.writeText(native)
        }

        val updaterFile = file("src/main/java/com/webapp/crazyshit/AppUpdater.java")
        var updater = updaterFile.readText()
        if (!updater.contains("BETA20_AUTO_UPDATE")) {
            updater = updater.replace(
                """        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        String key = betaChannel ? "beta_last_update_check" : "stable_last_update_check";""",
                """        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        // BETA20_AUTO_UPDATE
        if (!manual && !prefs.getBoolean("auto_update_enabled", true)) return;
        String key = betaChannel ? "beta_last_update_check" : "stable_last_update_check";"""
            )

            updater = updater.replace(
                """                    if (newer) {
                        showUpdateDialog(release, current);
                    } else if (manual) {""",
                """                    if (newer) {
                        if (!manual && prefs.getBoolean("auto_update_enabled", true)) {
                            Toast.makeText(
                                    activity,
                                    "Update " + release.version + " found. Downloading…",
                                    Toast.LENGTH_SHORT
                            ).show();
                            downloadAndInstall(release);
                        } else {
                            showUpdateDialog(release, current);
                        }
                    } else if (manual) {"""
            )
            updaterFile.writeText(updater)
        }

        val settingsFile = file("src/main/java/com/webapp/crazyshit/SettingsActivity.java")
        var settings = settingsFile.readText()
        if (!settings.contains("Automatic updates")) {
            settings = settings.replace(
                """        addSection(root, "App");
        addAction(root, "Check for updates", "Check your current beta or stable channel and install inside the app.", () -> {""",
                """        addSection(root, "App");
        addSwitch(root,
                "Automatic updates",
                "Automatically check and download new builds. Android still asks for final install confirmation.",
                "auto_update_enabled",
                true);
        addAction(root, "Check for updates", "Check your current beta or stable channel and install inside the app.", () -> {"""
            )
            settingsFile.writeText(settings)
        }
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn("wireV2Beta20ChaosLandscapeAutoUpdate")
}
