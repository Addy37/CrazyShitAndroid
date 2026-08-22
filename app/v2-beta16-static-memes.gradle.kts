tasks.register("wireV2Beta16StaticMemes") {
    dependsOn("wireV2Beta15LivePager")

    doLast {
        val nativeFile = file("src/main/java/com/webapp/crazyshit/NativeMainActivity.java")
        var native = nativeFile.readText()
        if (!native.contains("BETA16_STATIC_MEMES")) {
            native = native.replace(
                """                if (meme) openFallback(item.url);
                else openNativeItem(item);""",
                """                if (meme) {
                    Intent memeIntent = new Intent(this, MemeViewerActivity.class);
                    memeIntent.putExtra(MemeViewerActivity.EXTRA_TITLE, item.title);
                    memeIntent.putExtra(MemeViewerActivity.EXTRA_PAGE_URL, item.url);
                    memeIntent.putExtra(MemeViewerActivity.EXTRA_IMAGE_URL, item.imageUrl);
                    startActivity(memeIntent);
                } else {
                    openNativeItem(item);
                }"""
            )

            val itemMenuAnchor = "    private void showItemMenu(NativeContentItem item, View anchor) {"
            val memeMenuMethod = """
    // BETA16_STATIC_MEMES
    private void showMemeItemMenu(NativeContentItem item, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 2, 0, "Share");
        menu.getMenu().add(0, 3, 1, "Open meme page");
        menu.setOnMenuItemClickListener(clicked -> {
            if (clicked.getItemId() == 2) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, item.url);
                share.putExtra(Intent.EXTRA_SUBJECT, item.title);
                startActivity(Intent.createChooser(share, "Share meme"));
                return true;
            }
            if (clicked.getItemId() == 3) {
                openFallback(item.url);
                return true;
            }
            return false;
        });
        menu.show();
    }

"""
            if (native.contains(itemMenuAnchor)) {
                native = native.replace(itemMenuAnchor, memeMenuMethod + itemMenuAnchor)
                native = native.replace(
                    """    private void showItemMenu(NativeContentItem item, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);""",
                    """    private void showItemMenu(NativeContentItem item, View anchor) {
        if (item != null && item.isMeme()) {
            showMemeItemMenu(item, anchor);
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);"""
                )
            }

            nativeFile.writeText(native)
        }
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn("wireV2Beta16StaticMemes")
}
