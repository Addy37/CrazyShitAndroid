tasks.register("wireV2Beta6VideoDetail") {
    doLast {
        val nativeFile = file("src/main/java/com/webapp/crazyshit/NativeMainActivity.java")
        var native = nativeFile.readText()
        if (!native.contains("BETA6_VIDEO_DETAIL")) {
            native = native.replace(
                "                    openPlayer(resolved);",
                "                    openVideoDetail(resolved, item);"
            )

            val anchor = "    private void openPlayer(CrazyShitRepository.StreamInfo stream) {"
            val method = """
    // BETA6_VIDEO_DETAIL
    private void openVideoDetail(CrazyShitRepository.StreamInfo stream, NativeContentItem item) {
        Intent intent = new Intent(this, VideoDetailActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, stream.mediaUrl);
        intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, stream.pageUrl);
        intent.putExtra(PlayerActivity.EXTRA_TITLE,
                item != null && item.title != null && !item.title.trim().isEmpty()
                        ? item.title : stream.title);
        if (item != null) {
            intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);
            intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);
            intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);
        }
        try {
            intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, WebSettings.getDefaultUserAgent(this));
        } catch (Exception ignored) {
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(stream.mediaUrl);
            if ((cookies == null || cookies.isEmpty()) && stream.pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(stream.pageUrl);
            }
            if (cookies != null) intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
        } catch (Exception ignored) {
        }
        miniPlayer.stop();
        startActivityForResult(intent, PLAYER_REQUEST);
    }

"""
            if (native.contains(anchor)) {
                native = native.replace(anchor, method + anchor)
            }
            nativeFile.writeText(native)
        }
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn("wireV2Beta6VideoDetail")
}

apply(from = "v2-beta7-video-polish.gradle.kts")
