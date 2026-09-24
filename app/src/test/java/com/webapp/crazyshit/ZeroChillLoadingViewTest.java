package com.webapp.crazyshit;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ZeroChillLoadingViewTest {
    @Test public void galleryLoaderUsesEnhancedMascotMotionOnlyWhenRequested() {
        Application app = RuntimeEnvironment.getApplication();

        ZeroChillLoadingView regular = new ZeroChillLoadingView(app, null);
        ZeroChillLoadingView gallery = new ZeroChillLoadingView(app, "Loading gallery...", true);

        assertFalse(regular.usesGalleryMotion());
        assertTrue(gallery.usesGalleryMotion());
    }
}
