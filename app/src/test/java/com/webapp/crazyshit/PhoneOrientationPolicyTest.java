package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

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
