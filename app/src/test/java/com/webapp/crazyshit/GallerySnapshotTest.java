package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class GallerySnapshotTest {
    @Test public void mediaAndCursorRecoverTogetherWithoutAnActivityCallback() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String id = BunkrGallerySessionStore.createCreator("Anna", "https://fapello.com/anna/", "Anna");
        BunkrGallerySessionStore.persist(context, id);
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Photo",
                "https://fapello.com/anna/1/", "", "", "", "", "");
        JSONObject cursor = new JSONObject().put("query", "Anna").put("next", 2);
        BunkrGallerySessionStore.recordCreatorBatch(context, id, Collections.singletonList(item), false, cursor);
        // A lifecycle save after the worker result must preserve the same paired state.
        BunkrGallerySessionStore.persist(context, id);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        JSONObject disk;
        do {
            disk = ScreenSnapshotStore.read(context, id);
            if (disk != null && disk.has("cursor")) break;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        assertNotNull(disk);
        assertEquals(2, disk.getJSONObject("cursor").getInt("next"));
        assertEquals(1, disk.getJSONArray("items").length());
        Map<?, ?> sessions = ReflectionHelpers.getStaticField(BunkrGallerySessionStore.class, "SESSIONS");
        sessions.clear();
        BunkrGallerySessionStore.Snapshot restored = BunkrGallerySessionStore.restore(context, id);
        assertNotNull(restored);
        assertEquals(item.url, restored.items.get(0).url);
        assertEquals(2, new JSONObject(restored.cursor).getInt("next"));
        assertEquals(1, restored.currentPage);
    }
}
