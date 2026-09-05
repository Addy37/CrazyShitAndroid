package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class CreatorSuggestionsTest {
    private ActivityController<Activity> activity;
    private CreatorSuggestionsController controller;
    private EditText input;
    private LinearLayout panel;
    private final CountDownLatch oldStarted = new CountDownLatch(1);
    private final CountDownLatch oldReply = new CountDownLatch(1);
    private final CountDownLatch oldFinished = new CountDownLatch(1);

    private void create() {
        activity = Robolectric.buildActivity(Activity.class).setup();
        input = new EditText(activity.get());
        panel = new LinearLayout(activity.get());
        controller = new CreatorSuggestionsController(activity.get(), input, panel, item -> { }, (context, query) -> {
            if (query.equals("an")) {
                oldStarted.countDown();
                // A host may finish despite cancellation. Its old response must not change the UI.
                boolean released = false;
                while (!released) {
                    try { released = oldReply.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { }
                }
                oldFinished.countDown();
                return Collections.singletonList(CreatorCatalog.fromModel(
                        new FapelloRepository.Model("Anna", "https://fapello.com/anna/", "")));
            }
            return Collections.singletonList(CreatorCatalog.fromModel(
                    new FapelloRepository.Model("Zoe", "https://fapello.com/zoe/", "")));
        });
    }

    private void startOldRequest() throws Exception {
        input.setText("an");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(280));
        assertTrue(oldStarted.await(3, TimeUnit.SECONDS));
    }

    private List<NativeContentItem> items() {
        return ReflectionHelpers.getField(ReflectionHelpers.getField(controller, "adapter"), "items");
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        do {
            shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("Suggestion callback did not finish");
    }

    @Test public void aSlowOldReplyCannotReplaceTheNewerQuery() throws Exception {
        create(); startOldRequest();
        input.setText("zo");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(280));
        await(() -> items().size() == 1 && items().get(0).title.equals("Zoe"));
        oldReply.countDown();
        assertTrue(oldFinished.await(3, TimeUnit.SECONDS));
        await(() -> CreatorCatalog.matching(activity.get(), "anna", false, 8).size() == 1);
        assertEquals("Zoe", items().get(0).title);
        assertEquals(1, items().size());
    }

    @Test public void clearingTextKeepsTheListEmptyAfterAnOldReply() throws Exception {
        create(); startOldRequest(); input.setText(""); oldReply.countDown();
        assertTrue(oldFinished.await(3, TimeUnit.SECONDS));
        await(() -> CreatorCatalog.matching(activity.get(), "anna", false, 8).size() == 1);
        assertTrue(items().isEmpty());
        assertTrue(((TextView) panel.getChildAt(0)).getText().toString().contains("at least 2"));
    }

    @Test public void submittingSearchKeepsSuggestionsDismissed() throws Exception {
        create(); startOldRequest(); controller.hide(); oldReply.countDown();
        assertTrue(oldFinished.await(3, TimeUnit.SECONDS));
        await(() -> CreatorCatalog.matching(activity.get(), "anna", false, 8).size() == 1);
        assertFalse(controller.isShowing());
        assertEquals(View.GONE, panel.getVisibility());
    }

    @After public void cleanUp() {
        oldReply.countDown();
        if (controller != null) controller.close();
        if (activity != null) activity.pause().stop().destroy();
    }
}
