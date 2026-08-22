plugins {
    id("com.android.application")
}

android {
    namespace = "com.webapp.crazyshit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.addy37.crazyshitunofficial"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 10
        versionName = System.getenv("APP_VERSION_NAME") ?: "2.0.0"
    }

    val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

    if (
        releaseKeystorePath != null &&
        releaseKeystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
    ) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "CrazyShit Jeremy v2 Test")
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("org.jsoup:jsoup:1.23.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    val media3Version = "1.9.4"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
}

// v2 beta bridge: keep the large native source stable while the test channel is being
// hardened. The build workspace receives these small, idempotent integrations before
// Java compilation. This can be removed once v2 is promoted and the source is folded in.
val wireV2BetaRuntime by tasks.registering {
    doLast {
        val nativeFile = file("src/main/java/com/webapp/crazyshit/NativeMainActivity.java")
        var native = nativeFile.readText()

        if (!native.contains("private AppUpdater appUpdater;")) {
            native = native.replace(
                "    private NativeMiniPlayer miniPlayer;\n",
                "    private NativeMiniPlayer miniPlayer;\n    private AppUpdater appUpdater;\n"
            )
            native = native.replace(
                "        buildUi();\n        configureBack();",
                "        buildUi();\n        appUpdater = new AppUpdater(this);\n        configureBack();"
            )
            native = native.replace(
                """        addSheetAction(content, "Login / account", "Open the website account flow", () -> {
            openFallback(CrazyShitRepository.BASE + "login/");
            sheet.dismiss();
        });""",
                """        addSheetAction(content, "Login / account", "Sign in here and return automatically", () -> {
            startActivity(new Intent(this, LoginActivity.class));
            sheet.dismiss();
        });"""
            )
            native = native.replace(
                """        addSheetAction(content, "Check for updates", "Check the latest GitHub release", () -> {
            checkForUpdates(true);
            sheet.dismiss();
        });""",
                """        addSheetAction(content, "Check for updates", "Download and install updates inside the app", () -> {
            checkForUpdates(true);
            sheet.dismiss();
        });"""
            )
            val checkStart = native.indexOf("    private void checkForUpdates(boolean manual) {")
            val checkEnd = native.indexOf("    private String currentVersion()", checkStart)
            if (checkStart >= 0 && checkEnd > checkStart) {
                native = native.substring(0, checkStart) +
                    "    private void checkForUpdates(boolean manual) {\n" +
                    "        if (appUpdater != null) appUpdater.check(manual);\n" +
                    "    }\n\n" +
                    native.substring(checkEnd)
            }
            native = native.replace(
                """    protected void onResume() {
        super.onResume();
        if (miniPlayer != null) miniPlayer.onResume();
    }""",
                """    protected void onResume() {
        super.onResume();
        if (miniPlayer != null) miniPlayer.onResume();
        if (appUpdater != null) appUpdater.onHostResume();
    }"""
            )
            native = native.replace(
                """    protected void onDestroy() {
        if (miniPlayer != null) miniPlayer.stop();
        io.shutdownNow();
        super.onDestroy();
    }""",
                """    protected void onDestroy() {
        if (miniPlayer != null) miniPlayer.stop();
        if (appUpdater != null) appUpdater.close();
        io.shutdownNow();
        super.onDestroy();
    }"""
            )
            nativeFile.writeText(native)
        }

        val commentsFile = file("src/main/java/com/webapp/crazyshit/CommentsActivity.java")
        var comments = commentsFile.readText()
        if (!comments.contains("LOGIN_REQUEST = 4201")) {
            comments = comments.replace(
                "    public static final String EXTRA_COUNT = \"count\";\n",
                "    public static final String EXTRA_COUNT = \"count\";\n    private static final int LOGIN_REQUEST = 4201;\n"
            )
            comments = comments.replace(
                """    private void openLogin() {
        refreshWhenResumed = true;
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, CrazyShitRepository.BASE + "login/");
        startActivity(intent);
    }""",
                """    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_RETURN_URL, pageUrl);
        startActivityForResult(intent, LOGIN_REQUEST);
    }"""
            )
            val resumeAnchor = "    @Override\n    protected void onResume() {"
            comments = comments.replace(
                resumeAnchor,
                "    @Override\n" +
                "    protected void onActivityResult(int requestCode, int resultCode, Intent data) {\n" +
                "        super.onActivityResult(requestCode, resultCode, data);\n" +
                "        if (requestCode == LOGIN_REQUEST && resultCode == RESULT_OK) {\n" +
                "            try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}\n" +
                "            if (extractor != null) extractor.postDelayed(this::loadComments, 180L);\n" +
                "        }\n" +
                "    }\n\n" + resumeAnchor
            )
            commentsFile.writeText(comments)
        }
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn(wireV2BetaRuntime)
}
