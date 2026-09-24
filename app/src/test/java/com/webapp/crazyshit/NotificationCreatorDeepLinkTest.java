package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class NotificationCreatorDeepLinkTest {
    @Test
    public void creatorNotificationIntentOpensUnifiedGalleryWithFreshUrls() {
        Application app = RuntimeEnvironment.getApplication();
        NotificationCoordinator.CreatorAlert alert =
                new NotificationCoordinator.CreatorAlert("Emily Rinaudo");
        alert.fapelloProfileUrl = "https://fapello.com/emily-rinaudo/";
        alert.freshUrls.add("https://fapello.com/video/emily-rinaudo/32318226/");
        alert.freshUrls.add("https://bunkr.example/a/emily");

        Intent intent = NotificationCoordinator.creatorGalleryIntent(app, alert);

        assertNotNull(intent.getComponent());
        assertEquals(
                NativeFeedBrowserActivity.class.getName(),
                intent.getComponent().getClassName()
        );
        assertEquals(
                "Emily Rinaudo",
                intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY)
        );
        assertEquals(
                "https://fapello.com/emily-rinaudo/",
                intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_FAPELLO_PROFILE_URL)
        );
        assertEquals(
                alert.freshUrls,
                intent.getStringArrayListExtra(
                        NativeFeedBrowserActivity.EXTRA_NOTIFICATION_FRESH_URLS
                )
        );
    }

    @Test
    public void freshGalleryItemsArePrioritizedAheadOfOlderItems() {
        NativeContentItem oldFirst = item("old-1");
        NativeContentItem fresh = item("fresh");
        NativeContentItem oldSecond = item("old-2");

        List<NativeContentItem> ordered =
                NativeFeedBrowserActivity.prioritizeNotificationItems(
                        Arrays.asList(oldFirst, fresh, oldSecond),
                        Arrays.asList("fresh")
                );

        assertEquals(3, ordered.size());
        assertEquals("fresh", ordered.get(0).url);
        assertEquals("old-1", ordered.get(1).url);
        assertEquals("old-2", ordered.get(2).url);
    }

    private static NativeContentItem item(String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_IMAGE,
                url,
                url,
                "",
                "",
                "",
                ""
        );
    }
}
