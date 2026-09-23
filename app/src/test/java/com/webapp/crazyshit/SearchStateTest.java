package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SearchStateTest {
    @Test public void onlyFapSearchUsesCreatorResultCards() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SearchActivity.class)
                .putExtra("scope", "bunkr");
        ActivityController<SearchActivity> controller =
                Robolectric.buildActivity(SearchActivity.class, intent).create().start().resume();

        RecyclerView recycler = ReflectionHelpers.getField(controller.get(), "recycler");
        assertTrue(recycler.getAdapter() instanceof OnlyFapCreatorSearchAdapter);

        controller.pause().stop().destroy();
    }

    @Test public void restoringFapzoneSearchKeepsTextWithoutOpeningAnAlbum() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SearchActivity.class).putExtra("scope", "bunkr");
        ActivityController<SearchActivity> original = Robolectric.buildActivity(SearchActivity.class, intent).create().start().resume();
        EditText input = ReflectionHelpers.getField(original.get(), "input");
        input.setText("ann");
        Bundle state = new Bundle();
        original.pause().saveInstanceState(state).stop().destroy();
        ActivityController<SearchActivity> restored = Robolectric.buildActivity(SearchActivity.class, intent).create(state).start().resume();
        assertEquals("ann", ((EditText) ReflectionHelpers.getField(restored.get(), "input")).getText().toString());
        assertNull(shadowOf(restored.get()).getNextStartedActivity());
        restored.pause().stop().destroy();
    }
}
