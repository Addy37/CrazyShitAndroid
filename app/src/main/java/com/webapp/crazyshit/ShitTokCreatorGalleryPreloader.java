package com.webapp.crazyshit;

import android.content.Context;

/** Keeps ShitTok creator taps on the shared creator-gallery prewarm path. */
final class ShitTokCreatorGalleryPreloader {
    private ShitTokCreatorGalleryPreloader() {
    }

    static void warm(Context context, NativeContentItem item) {
        String creator = ShitTokCreatorMetadata.creatorName(item);
        if (creator.isEmpty() || context == null) return;
        CreatorGalleryPreloader.warm(context, creator, creator, "");
    }

    static String sessionId(android.content.Context context, String creator) {
        return CreatorGalleryPreloader.sessionId(context, creator);
    }
}
