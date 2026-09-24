package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PhoneOrientationPolicyTest {
    @Test public void launchAndBrowsingActivitiesStartPortraitBeforeOnCreate() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        Class<?>[] activities = {
                SplashActivity.class, NativeMainActivity.class, VideoDetailActivity.class,
                PlayerActivity.class, BunkrGalleryActivity.class, WebFallbackActivity.class,
                SearchActivity.class, SettingsActivity.class, CreatorsActivity.class
        };
        for (Class<?> activity : activities) {
            ActivityInfo info = app.getPackageManager().getActivityInfo(
                    new ComponentName(app, activity), 0);
            assertEquals(activity.getSimpleName(), ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    info.screenOrientation);
        }
    }

    @Test public void landscapePhoneRemainsPhoneSizedForFullscreenExit() {
        Configuration landscapePhone = new Configuration();
        landscapePhone.screenWidthDp = 891;
        landscapePhone.smallestScreenWidthDp = 411;
        assertTrue(PhoneOrientationPolicy.isPhoneSized(landscapePhone));

        Configuration tablet = new Configuration();
        tablet.screenWidthDp = 1024;
        tablet.smallestScreenWidthDp = 720;
        assertFalse(PhoneOrientationPolicy.isPhoneSized(tablet));
    }

    @Test public void fullscreenCanRotateAndReturningRestoresPortrait() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PhoneOrientationPolicy.applyBrowsingOrientation(activity);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                activity.getRequestedOrientation());
        PhoneOrientationPolicy.enterSensorFullscreen(activity);
        PhoneOrientationPolicy.applyBrowsingOrientation(activity);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                activity.getRequestedOrientation());
        PhoneOrientationPolicy.exitFullscreenVideo(activity);
        PhoneOrientationPolicy.applyBrowsingOrientation(activity);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                activity.getRequestedOrientation());
        PhoneOrientationPolicy.onActivityDestroyed(activity);
        activity.finish();
    }
}
