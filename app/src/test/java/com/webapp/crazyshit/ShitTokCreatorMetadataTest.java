package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ShitTokCreatorMetadataTest {
    @Test
    public void fapelloClip_usesUploaderAsCreatorName() {
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "493857_video_final",
                "https://fapello.com/video/12345/",
                "",
                "",
                "Sophie Rain",
                "",
                "OnlyFap"
        );

        assertEquals("Sophie Rain", ShitTokCreatorMetadata.creatorName(item));
        assertTrue(ShitTokCreatorMetadata.hasCreator(item));
    }

    @Test
    public void onlyHavenClip_usesTitleAsCreatorName() {
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Jane Doe",
                "https://img.cum.st/post/video.mp4",
                "",
                "OnlyHaven",
                "OnlyHaven",
                "",
                "onlyfans · OnlyHaven"
        );

        assertEquals("Jane Doe", ShitTokCreatorMetadata.creatorName(item));
        assertTrue(ShitTokCreatorMetadata.hasCreator(item));
    }

    @Test
    public void regularShitTokClip_doesNotBecomeCreatorLink() {
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Normal video title",
                "https://crazyshit.com/cnt/medias/123",
                "",
                "",
                "Uploader",
                "",
                ""
        );

        assertEquals("", ShitTokCreatorMetadata.creatorName(item));
        assertFalse(ShitTokCreatorMetadata.hasCreator(item));
    }
}
