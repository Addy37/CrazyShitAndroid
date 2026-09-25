package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ShowsHubStateTest {
    @Test
    public void restoresVerticalHubPositionAfterRecreation() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShowsHubView first = hub(activity);
        first.setCrazyShit(items(12, "/series/"));
        first.setEfukt(items(8, "https://efukt.com/series/test-"));
        first.setCategories(items(10, "/category/"));
        first.finishLoading();

        layout(first);
        ScrollView firstScroll = (ScrollView) first.getChildAt(0);
        firstScroll.scrollTo(0, 260);

        Bundle saved = new Bundle();
        first.saveState(saved);
        assertTrue(saved.getInt("scroll_y", 0) > 0);

        ShowsHubView restored = hub(activity);
        restored.restoreState(saved);
        restored.setCrazyShit(items(12, "/series/"));
        restored.setEfukt(items(8, "https://efukt.com/series/test-"));
        restored.setCategories(items(10, "/category/"));
        layout(restored);
        restored.finishLoading();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ScrollView restoredScroll = (ScrollView) restored.getChildAt(0);
        assertTrue(restoredScroll.getScrollY() > 0);
    }

    private static ShowsHubView hub(Activity activity) {
        return new ShowsHubView(activity, item -> { }, item -> { }, item -> { });
    }

    private static List<NativeContentItem> items(int count, String prefix) {
        ArrayList<NativeContentItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String url = prefix.startsWith("http")
                    ? prefix + index + "/"
                    : CrazyShitRepository.BASE + prefix.substring(1) + index + "/";
            items.add(new NativeContentItem(
                    NativeContentItem.KIND_SERIES,
                    "Show " + index,
                    url,
                    "",
                    "",
                    "",
                    ""
            ));
        }
        return items;
    }

    private static void layout(View view) {
        int width = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY);
        view.measure(width, height);
        view.layout(0, 0, 1080, 600);
    }
}
